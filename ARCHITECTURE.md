# LiquidDock Architecture

本文档描述当前 `main` / **v2.2.1** 的实际实现。旧的 ScreenCapture / bitmap readback / `DockLiquidGlassView` 捕获链只保留在 `archive/1.x`。

当前兼容边界：

```text
HyperOS 3.0.307+
com.miui.home release-4.50.x.x
libxposed API 101
MiuiX PassBlur + OES/GLES zero-copy
```

## 1. 进程边界

LiquidDock 当前注入两个包，但职责严格分离：

```text
com.miui.home
  ├─ Grid / Dock / Workstation
  ├─ Widget / Folder / Icon ownership
  ├─ LauncherGlassSession / SceneController
  └─ 实际 Glass 渲染与 PassBlur producer

com.android.systemui
  └─ read-only timing source
       ├─ Keyguard Gone
       └─ WMShell HomeTransitionObserver
```

`ModuleMain` 在 SystemUI 进程只安装 `SystemUiKeyguardGoneSource` 与 `SystemUiHomeTransitionSource`；不创建 Glass surface、不解析 Launcher View、不接管 Dock，也不参与 backdrop 内容生产。

Launcher 进程安装 `SystemUiKeyguardGoneRuntime` / `SystemUiHomeTransitionRuntime` 接收时序信号，并与 Launcher 4.50 自身 callback 组合。

## 2. 配置与 Runtime state

配置链路：

```text
Settings SharedPreferences
    -> ConfigMigration / LegacyConfigMigration
    -> ConfigSchema / ConfigCodec / PresetManager
    -> API101 Remote Preferences
    -> ConfigReader
    -> LiquidDockConfig immutable snapshot
    -> runtime state / hooks / renderers
```

`GlassRuntimeState` 管理 icon/widget/small-folder/large-folder 等可即时释放的 glass ownership；`VisualRuntimeState` 管理 Dock customization、stroke、shadow、Divider 等 live visual state。

结构性 Hook 选择仍是 restart-bound，例如 Grid 主结构、Widget grid adaptation、Dock resize Hook 与 Workstation composite customization。

## 3. Zero-copy Glass 数据流

```text
HyperOS MiuiX PassBlur producer
        ↓
Surface / SurfaceTexture
        ↓
GL_TEXTURE_EXTERNAL_OES
        ↓
GPU normalization + overscan
        ↓
Prismal optical renderer
        ↓
Dock / Launcher output surface
```

原则：

- zero-copy only；
- 不恢复 ScreenCapture fallback；
- 不做 bitmap readback；
- vendor PassBlur / 隐藏 Surface API 不可用时 fail-closed；
- sample validity 与最终 output coverage 分离；
- overscan 为强折射提供边界外采样空间。

### Dock 与 Workspace producer 的关键区别

`Miuix307PassBlurBridge.bind()` 的基础语义始终是 **continuous-on-bind**。

- **独立 Dock**：依赖持续实时更新，不允许改成缓存背景或按需采一帧；Dock 不能因为 Workspace 优化而被 pause/pulse 化。
- **Workspace shared session**：可以在明确 scene/freshness 生命周期中使用 `requestSingleUpdate()` / `pauseUpdates()` / `resumeUpdates()`，例如 idle、Recents、rotation、Workstation 等。

是否属于 Workspace 由 binding 自身记录，不能从 Floating Dock 的窗口层级或 `hasWindowFocus()` 猜测。

## 4. Dock Glass

Dock 使用 MiuiX Dock background 作为几何/material owner，主要拆分为：

- PassBlur producer / OES input；
- Dock geometry；
- Prismal optical render；
- Dock item glass registry；
- whole-Dock shadow；
- `DockStrokeRenderer` foreground ownership；
- resize / Workstation 动画 ownership。

关闭自定义视觉时只恢复已真实 snapshot 的 vendor state，不伪造未知 shadow/background 参数。

## 5. Launcher-wide shared Glass

图标、Widget、小/大文件夹共享 root-wide Glass session：

- `LauncherGlassSession`：PassBlur/OES/EGL/renderer 生命周期；
- `LauncherGlassSessionRegistry`：按稳定 Launcher root 管理 session；
- `LauncherGlassSceneController`：scene state、generation、fresh-frame barrier、static layer；
- `LauncherGlassStaticNode`：静态图标/Widget/文件夹节点；
- `MiuixLauncherDragOverlayHook`：drag node；
- `LauncherGlassHomePresentationHook`：HOME / unlock presentation barrier；
- `LauncherWidgetTransitionCoordinator`：Widget↔App 独立 visibility/freshness ownership。

StaticLayer 是 root-wide 整屏 EGL 输出；GPU framebuffer 自己负责屏幕边界裁切。

## 6. HOME / APP 时序权威

### SystemUI HOME source

当前 HyperOS SystemUI 的 `com.android.wm.shell.transition.HomeTransitionObserver` 是 HOME transition 的系统级分类器。

`SystemUiHomeTransitionSource` 监听：

- `onTransitionReady(...)`；
- `notifyHomeVisibilityChanged(boolean)`；
- `onTransitionStarting(IBinder)`；
- `onTransitionFinished(IBinder, boolean)`；
- `onTransitionMerged(...)`。

只在 `HomeTransitionObserver.onTransitionReady` 生命周期内记录 HOME visibility，从而过滤 `RecentsTransitionHandler` 对 `notifyHomeVisibilityChanged()` 的直接调用。

`onTransitionStarting` 是 WMShell 在 setup animation hierarchy / handler animation 前的精确 START 边界；matching FINISH serial 才能释放 SystemUI authority 下的 HOME barrier。

### Launcher fallback

Launcher 4.50 的 `WindowElement.animTo(CLOSE_TO_HOME / CLOSE_TO_HOME_CENTER)` 与动画 end callback 始终保留为 fail-open fallback。

跨进程 START 晚到时，timestamp/serial 防护会拒绝在 Launcher fallback 已结束之后重新 arm 一个旧 HOME transition。

## 7. Scene freshness 与早期 reveal

普通 App→Home 采用两阶段语义：

1. HOME START：冻结 fresh capture，阻止 backdrop generation 在动画中途切换；已准备好的 static scene 可以开始 early reveal。
2. HOME FINISH：解除 capture barrier，请求/消费新的 OES frame；fresh frame 替换 scene，但不再触发第二次 fade。

因此“允许 cached scene 在 HOME 动画中出现”与“允许 stale backdrop 永久留存”是两件不同的事。

Recents、rotation、wallpaper generation 仍各自拥有 freshness barrier；普通 redraw 不能替代 fresh OES frame。

## 8. Widget ↔ App transition

Launcher 4.50 对 Widget 使用独立的 `WidgetTypeAnimTarget`，不能套用 `ShortcutIcon` 的 launch proxy 语义。

LiquidDock 的规则：

- Widget→App：在 vendor 将 Widget host alpha/visibility 隐藏前取得 transition ownership，让 Glass 正常淡出，并在淡出期间保留最后有效 geometry。
- App→Widget：通过 closing-widget lookup 确认返回目标后，立即把该 Widget 从旧 StaticLayer 中抑制。
- HOME FINISH 只解除 producer barrier；返回 Widget 必须等当前 scene generation 已真正 render 为 `HOME_VISIBLE` 后才 `0 -> 1` 淡入。
- 快速往返会取消旧 return ownership，避免 suppression 卡死。

这保证大尺寸 Widget 不会先显示旧 backdrop、随后突然切换为新背景。

## 9. Static geometry 与视口裁切

StaticLayer 的 shape geometry 与 visible crop 已解耦。

`LauncherGlassGeometry.resolveStatic()`：

- 部分出屏时保留原始 `left/top/width/height/center/cornerRadius`；
- 只在 `crop*` 字段记录屏内交集；
- 完全离屏后才 cull；
- framebuffer 自然裁掉屏幕外像素。

这样页面滑动时圆角矩形会完整平移出屏幕，不会出现“左边钉在屏幕边缘、只缩短宽度”的错误。

旧 `LauncherGlassGeometry.resolve()` 保持 clipped 语义，供 sink/drag 等确实需要可见区域裁切的路径使用。

## 10. Widget component discovery / ranking

Widget 背景自定义分为“selector identity”和“discovery metadata”两层。

RemoteViews discovery 记录实际 View tree；MAML 从 `ScreenElementRoot.mInnerGroup -> ElementGroup.mElements` 真实 render tree 建立顺序，同时保留 name-based selector 兼容。

目录 metadata 包括：

- `renderOrdinal` / GUI `Render #N`；
- `depth`；
- `areaRatio`；
- RemoteViews 的 `effectiveZ`，MAML 无可靠 Z 时保持 unknown。

`WidgetComponentRanking` 综合组件类型、面积、render ordinal、depth 与 Z 给出背景分数，将高置信度“疑似底层背景”排到最前。

**这些字段不进入 selectorKey。** 已保存的 R2/M/M2 规则不因重新扫描而失效。

## 11. Keyguard / Unlock

Launcher 的 `UnlockAnimationStateMachine.PREPARE` 只是早期 freeze signal，不具有 release 权限。SystemUI `Keyguard Gone FINISHED` 是 release authority。

Unlock gate 只阻断 Launcher Workspace producer/binding；独立 Floating Dock 不能被这个 gate 误暂停。

解锁后桌面壁纸内容权威仍是需要持续真机硬化的高风险区域，详见 [TODO.md](TODO.md)。

## 12. Workstation / Laptop

工作台仍属于实验性路径，横跨：

- Dock geometry / icon offset；
- Workspace Grid；
- All Apps offset；
- Divider；
- PassBlur producer lifecycle；
- Recents；
- wallpaper freshness；
- 普通布局 backup/restore。

Workstation Recents 返回时可能需要 rollover shared Launcher producer；这不改变独立 Dock continuous-on-bind 的硬约束。

## 13. Grid ownership

自定义 Grid 保持 MIUI 对 placement / occupancy 的所有权：

- 横竖屏分别维护 orientation-specific geometry / placement memory；
- Widget adaptation 只改变 pixel allocation / frame；
- 不通过 `addOccupied()` / `transformToHVArray()` 猜 occupancy matrix；
- lazy/off-screen page 在显示前准备正确方向 geometry。

## 14. Restart-bound 与 live 边界

### Live visual

- icon/widget/small-folder/large-folder glass；
- Dock customization 的可逆视觉 ownership；
- Dock stroke；
- Dock shadow；
- Divider；
- Squircle / Fill-Diff renderer refresh。

### Restart-bound

- LiquidDock master switch 的完整启停；
- Grid 结构与 Widget grid adaptation；
- Dock resize Hook selection；
- Workstation composite customization；
- 其它安装期决定的结构 Hook。

## 15. 架构不变量

后续改动必须保持：

1. zero-copy only，不恢复 1.x capture backend；
2. Dock 实时 producer 不缓存、不按需化；
3. SystemUI 只提供时序，不拥有 Glass rendering；
4. scene/content freshness 不由普通 redraw 证明；
5. Widget return 不允许 stale backdrop 先显示；
6. StaticLayer 部分离屏保留完整 shape geometry；
7. selector identity 与 discovery/ranking metadata 分离；
8. runtime disable 必须先 publish false，再 teardown ownership。

后续优先级见 [TODO.md](TODO.md)。
