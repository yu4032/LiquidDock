# LiquidDock Architecture

本文档描述当前 `main` / **v2.2.1** 的实际实现，以及 `main` 上尚未发布到新版本号的已合入变更；不是历史 1.x 捕获架构或未来目标设计。

当前兼容边界：

```text
HyperOS 3.0.307+
com.miui.home release-4.50.x.x
libxposed API 101
MiuiX PassBlur + OES/GLES zero-copy
```

旧的 ScreenCapture / bitmap readback / `DockLiquidGlassView` 捕获管线只保留在 `archive/1.x`。

## 1. 进程与配置边界

LiquidDock 当前主要注入 `com.miui.home`。`ModuleMain` 在包加载后完成 legacy migration、读取 API101 Remote Preferences，并安装 Launcher 侧模块。

配置链路：

```text
Settings SharedPreferences
    -> ConfigMigration
    -> ConfigSchema / ConfigCodec / PresetManager
    -> API101 Remote Preferences
    -> ConfigReader
    -> LiquidDockConfig immutable snapshot
    -> runtime state / hooks / renderers
```

规则：

- `ConfigSchema` 是 persisted key、类型、默认值、范围和存储语义的权威来源；
- `ConfigCodec` 负责 JSON import/export 与 legacy alias；
- `ConfigMigration` 只负责设置进程升级；
- `LiquidDockConfig.load()` 不应产生跨模块副作用；
- live visual toggle 通过独立 runtime state 管理，不要求重新构造整个 immutable config。

## 2. Runtime state

### `GlassRuntimeState`

管理当前可即时生效的 Launcher glass 开关：

- global glass；
- icon glass；
- widget glass；
- small-folder glass；
- large-folder glass。

关闭组件时先发布 `false`，再在主线程释放对应 static/drag/vendor material ownership，因此已排队的 callback 会先看到 disabled 状态，不能在 teardown 之后重新抢占资源。

### `VisualRuntimeState`

管理 Dock 侧 live visual state：

- Dock customization；
- Dock stroke；
- Dock shadow；
- stroke shadow gate；
- Divider；
- Squircle / Fill-Diff renderer refresh。

这类状态只接管可逆视觉 ownership。结构性 Hook 选择仍保持 restart-bound。

## 3. Zero-copy Liquid Glass 数据流

当前 Liquid Glass 不再捕获屏幕 bitmap。核心数据流为：

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
- vendor PassBlur 或隐藏 Surface API 不可用时 fail-closed；
- sample validity 与最终 output coverage/scissor 分离；
- overscan 用来保证强折射时仍能访问可见区域外的 backdrop 像素。

### Workspace PassBlur 的 cadence 与质量域

Workspace shared glass 的 HOME 路径现在保持 PassBlur update permission 持续开启；消费一帧 OES 不是暂停 producer 的理由。该路径仍然是 **source-driven**，LiquidDock 不创建 Choreographer/vsync pump：静态壁纸在内容不变时可以保持 bound / updates-enabled 但没有新的 OES buffer，动态壁纸或真实 backdrop 更新才会持续产生 source frame。

质量控制严格分成两个域：

- HyperOS native PassBlur / SurfaceTexture geometry 始终使用 `1.0` scale，作为屏幕位置到 backdrop 内容的空间权威；
- `liquid_passblur_capture_scale`（设置页“工作区渲染分辨率”）只把 OES normalization 之后的本地 physical FBO 降到 50%–100%；
- Prismal 的 logical framebuffer 仍是完整 Launcher root，`PrismalRenderer.width/height` 保持逻辑像素语义，`renderWidth/renderHeight` 才是物理纹理/FBO 尺寸；
- blur sigma 会换算到 physical pixel domain，因此降低渲染分辨率只降低像素密度，不改变 glass 与后方内容的空间对应；
- `liquid_passblur_render_fps` 只限制昂贵的 Prismal/output render。被限流的 OES frame 仍通过 `updateTexImage()` drain，避免 BufferQueue backpressure；新的 scene generation 无条件越过限流以维持 fresh-frame barrier。

改变本地渲染分辨率只触发 backdrop rebuild，不重建 native PassBlur BufferQueue endpoint。

## 4. Dock glass

Dock glass 使用 MiuiX Dock background 作为几何和 material owner 来源。主要职责拆分为：

- PassBlur producer / OES input；
- Dock geometry；
- Prismal optical render；
- Dock item glass registry；
- Dock whole-shadow；
- foreground stroke renderer；
- resize / animation ownership。

`DockStrokeRenderer` 持有描边 foreground ownership。关闭 stroke 后恢复安装前 foreground；Squircle / Fill-Diff 切换会刷新已安装 renderer。历史 stroke-shadow 配置继续兼容，其当前实现已归入现有 foreground renderer / native MiShadow ownership，不再作为独立 overlay 待实现项。

Whole-Dock shadow 是独立能力。关闭后移除 LiquidDock 自己创建的 shadow，并停止继续抑制后续 vendor shadow 调用；没有保存的 MIUI 原始 shadow 参数不会被猜测或伪造。

## 5. Launcher-wide shared glass

桌面图标、部分 Widget、小/大文件夹共享 root-wide glass session，而不是每个 material 自己建立 producer。

核心组件：

- `LauncherGlassSession`：共享 PassBlur/OES/EGL/renderer 生命周期；
- `LauncherGlassSessionRegistry`：按稳定 Launcher root 管理 session；
- `LauncherGlassSceneController`：负责 scene visibility、fresh-frame barrier 和 static layer；
- `LauncherGlassStaticNode`：静态图标/Widget/文件夹节点；
- `MiuixLauncherDragOverlayHook`：拖拽移动节点；
- `LauncherGlassVendorMaterialSuppressor` / folder ownership 路径：可逆接管 vendor material。

静态节点与拖拽节点共享同一 backdrop 内容权威，但输出和节点生命周期独立。

## 6. Freshness 与 scene 生命周期

共享 glass 不以“View 被 invalidate”作为内容新鲜度证明。

### HOME / APP / Recents

- 进入 Recents 时 static layer 隐藏或 producer 暂停；
- 返回 HOME 后请求新的 backdrop；
- static layer 只有在新的 OES frame 被消费并完成 render 后才重新显示；
- 不允许通过简单 `alpha=1` 展示 stale Recents/APP frame。

### Wallpaper generation

壁纸内容变化使用独立 generation。geometry refresh、scene refresh 与 wallpaper content generation 不混用。

### Rotation

旋转期间旧 producer 进入 settle/rebind 边界；新 orientation endpoint 准备并提交 fresh frame 后才恢复可见输出。

## 7. Workstation / Laptop

工作台目前仍属于**实验性、未完整支持**路径。

它横跨：

- Dock geometry；
- icon top/bottom offset；
- Workspace grid offset；
- All Apps offset；
- Divider；
- PassBlur producer lifecycle；
- Recents；
- wallpaper freshness；
- 普通布局 backup/restore。

### Recents producer recovery

HyperOS Workstation 可能在 Recents 往返时继续保留一个看似 valid 的 Launcher Surface，但已经退役旧 PassBlur BufferQueue producer。

当前 `main` 使用 `LauncherGlassSceneController.vendorRecentsCovered` 作为 covered authority。duplicate / non-covered `onRecentViewHide()` 不触发 Workstation producer rollover；有效返回 HOME 时仅走 Workstation-specific rebind。`LauncherGlassSession.workstationBindEpoch` 会拒绝 rollover 前排队的 stale `finishBind()`。endpoint recreation 只表示新 producer endpoint 建立，scene/wallpaper generation 与 fresh OES frame 仍是唯一 reveal authority。

除非出现新的、可复现的现实失败路径，不再为该问题增加 producer recovery episode state machine、terminal multi-session aggregate 或第二套 freshness authority。

### 当前 Workstation ownership 债务

Workstation mode 探测、vendor confirmation、普通布局 backup/restore 等 feature-level mutable state 仍部分直接位于 `MainHook`。初始化路径还保留一次固定 2 秒的 mode re-query fallback；当前仅由 `workstationModeHookConfirmed` 避免已确认路径重复覆盖，尚未拥有完整的 transition generation/cancellation 保护。

这属于当前重构目标，不应被误写为新的 producer-recovery 问题。

工作台结构配置仍 restart-bound，直到未来建立完整、可逆的 runtime restore。

## 8. Grid ownership

自定义桌面网格继续保持 MIUI 对 placement / occupancy 的所有权。

当前关键不变量：

- 横屏 / 竖屏分别维护 orientation-specific geometry / placement memory；
- Widget adaptation 只改变 pixel allocation / frame；
- 不通过 `addOccupied()` / `transformToHVArray()` 猜 occupied matrix；
- lazy/off-screen page 必须在显示前准备正确方向的 geometry；
- `HomeGridHook` 仍是大型模块，后续拆分必须保持这些行为不变。

当前 Widget classification 优先调用 `ItemInfo.isWidget()`，失败时仍在 `HomeGridHook` 使用 item type `4` / `5` / `19` fallback；支持 span 仍固定为 1×1、2×1、2×2、4×2。`WidgetGridSizing` 仍持有 static adaptation flag。这些是 active ownership debt，不是新的功能语义。

## 9. Divider ownership

`DockDividerHook` 在首次修改前保存原始 layout/background 状态：

- width / height；
- margins；
- background Drawable 副本。

关闭 Divider 时取消 pending layout listener，恢复 snapshot，`requestLayout()` 并释放 ownership。Drawable snapshot 必须避免 `setBackgroundColor()` 原地修改导致 alias 污染。

详见 [DIVIDER.md](DIVIDER.md)。

## 10. Restart-bound 与 live 边界

### Live visual

- icon/widget/small-folder/large-folder glass；
- Dock customization 的可逆视觉 ownership；
- Dock stroke；
- Dock shadow；
- stroke-shadow renderer 状态；
- Divider；
- Squircle / Fill-Diff renderer refresh。

### Restart-bound

- LiquidDock master switch 的完整启停；
- Grid 主开关与结构 geometry；
- Widget grid adaptation 的结构 Hook；
- Dock resize Hook 选择；
- Workstation composite customization；
- 其它安装期决定的结构 Hook。

UI 文案必须区分“立即释放视觉 ownership”和“完整结构变更需重启桌面”。

## 11. 当前重构方向

当前 active debt 与优先级以 [TODO.md](TODO.md) 为准；目标设计见 [Technical-Debt Cleanup Design](docs/superpowers/specs/2026-09-07-technical-debt-cleanup-design.md)，第一阶段执行步骤见 [Phase 1 Implementation Plan](docs/superpowers/plans/2026-09-07-technical-debt-cleanup-phase1.md)。

当前顺序：

1. 将 Workstation mode / delayed recheck / normal-layout ownership 从 `MainHook` 迁到单一 controller，并给 delayed callback 加 generation/cancellation 保护；
2. 引入 `WidgetClassifier` / `WidgetSpecRegistry`，移除 `WidgetGridSizing` static mutable config；
3. 先迁出 `HomeGridHook` 的真实 Widget adaptation ownership，再按 page indicator -> folder alignment -> cell geometry -> rotation/refresh 顺序继续拆；
4. 为 bundled Widget rule silent degradation 增加 one-shot structured diagnostic；
5. 对 `LauncherGlassSession` / `Miuix307PassBlurTextureView` / Prismal 先画 resource owner graph，再决定最小公共 EGL/OES primitive；
6. 继续缩小 `LEGACY_SOURCE_DEBT` 与 CI/i18n hygiene debt。

Workstation Recents shared-producer correctness 与 unlock->HOME authority 已经有现有最小 correctness 方案；除非出现新的现实失败，不把它们重新设计为更大的状态机。

任何重构都不得恢复 1.x ScreenCapture backend。
