# LiquidDock Architecture

本文档描述当前 `main` / **v2.2.1** 的实际实现，以及 `main` 上尚未发布到新版本号的已合入变更；不是历史 1.x 捕获架构或未来目标设计。

当前兼容边界：

```text
HyperOS 3.0.307+ / com.miui.home release-4.50.x.x
HyperOS 4 / com.miui.securitycenter:ui versionCode 40011320（Global Dock + All Apps v1）
libxposed API 101
MiuiX PassBlur + OES/GLES zero-copy（Launcher）
framework pass-window blur（Security Center Global Dock）
```

旧的 ScreenCapture / bitmap readback / `DockLiquidGlassView` 捕获管线只保留在 `archive/1.x`。

## 1. 进程与配置边界

LiquidDock 的 Launcher 功能注入 `com.miui.home`，SystemUI 只提供 HOME/keyguard 时序源；Security Center v1 只在 `com.miui.securitycenter:ui` 安装专属侧边栏 glass hook。`ModuleMain` 是三者的 composition root：Launcher 才执行 legacy/config migration 与 `MainHook.install()`；Security Center 不运行 Launcher migration，也不进入 `MainHook` 生命周期。

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

Security Center 使用独立 `SecurityCenterGlassRuntimeState`。framework material 的有效状态为 `Core.ENABLED && Glass.ENABLED && Glass.SECURITY_CENTER_GLASS`；shader/session source availability 是另一条更严格的 gate，不能反向阻塞 framework Dock material。关闭 Security Center glass 时在主线程执行 full release，清理模块材质并恢复保存的 vendor material state。

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

Launcher Liquid Glass 不捕获屏幕 bitmap。核心数据流为：

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
Launcher output surface
```

`RootPassBlurBackend` 是 Launcher root-wide glass 的 source backend。它持有 native PassBlur producer、OES input、normalization、source freshness、producer recovery 与 EGL source lifecycle；ViewRoot/SurfaceControl 私有反射隔离在 `RootPassBlurEndpointBridge`。Launcher scene/Workstation/wallpaper policy 留在 Launcher 上层。

Security Center Global Dock 不把这条 shader/OES source pipeline 作为背景显示权威。已验证设备行为表明该进程中的 shader source 可能停在 source request 而没有 fresh-frame callback，因此 v1 的可靠背景后端固定为 framework pass-window blur：Turbo/root 只启用窗口级 capability，真实 Dock carrier 承载模块指定的 blur radius 与 blend colors。Security Center 不在该路径上用 ScreenCapture、PixelCopy 或 bitmap fallback。

原则：

- zero-copy only；
- 不恢复 ScreenCapture fallback；
- Launcher vendor PassBlur 或隐藏 Surface API 不可用时 fail-closed；
- sample validity 与最终 output coverage/scissor 分离；
- overscan 用来保证强折射时仍能访问可见区域外的 backdrop 像素；
- Launcher native PassBlur scale 保持 `1.0`，质量缩放只发生在 OES normalization 之后；
- Security Center framework material 不等待 OES fresh-frame 才授权背景出现。

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

`DockStrokeRenderer` 持有描边 foreground ownership。关闭 stroke 后恢复安装前 foreground；Squircle / Fill-Diff 切换会刷新已安装 renderer。

Whole-Dock shadow 是独立能力。关闭后移除 LiquidDock 自己创建的 shadow，并停止继续抑制后续 vendor shadow 调用；没有保存的 MIUI 原始 shadow 参数不会被猜测或伪造。

## 5. Launcher-wide shared glass

桌面图标、部分 Widget、小/大文件夹共享 root-wide glass session，而不是每个 material 自己建立 producer。

核心组件：

- `LauncherGlassSession`：Launcher-specific consumer，持有节点/output、Workspace projection、wallpaper 与 Workstation/rotation policy；
- `RootPassBlurBackend`：共享 producer/OES/freshness/EGL source lifecycle；
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

旋转期间旧 producer 进入 settle/rebind 边界；新 orientation endpoint 准备并提交 fresh frame 后才恢复可见输出。Launcher 的已有 rotation settle policy 仍是 Launcher 专属行为，不进入 Security Center framework Dock material path。

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

当前 `main` 在有效的 `onRecentViewHide` 返回 HOME 时执行受 coverage authority 约束的 Workstation shared-producer recovery，再解除 Recents covered；scene controller 仍保持 fresh-OES-frame barrier，因此 endpoint recreation 本身不会提前授权显示旧帧。

这修复了“从多任务返回后整个 Launcher glass layer 消失，必须长按图标才能恢复”的共享 producer 生命周期问题。

工作台结构配置仍 restart-bound，直到未来建立完整、可逆的 runtime restore。

## 8. Grid ownership

自定义桌面网格继续保持 MIUI 对 placement / occupancy 的所有权。

当前关键不变量：

- 横屏 / 竖屏分别维护 orientation-specific geometry / placement memory；
- Widget adaptation 只改变 pixel allocation / frame；
- 不通过 `addOccupied()` / `transformToHVArray()` 猜 occupied matrix；
- lazy/off-screen page 必须在显示前准备正确方向的 geometry；
- `HomeGridHook` 仍是大型模块，后续拆分必须保持这些行为不变。

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
- Security Center sidebar glass component；
- Dock customization 的可逆视觉 ownership；
- Dock stroke；
- Dock shadow；
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

## 11. Security Center Global Dock / All Apps v1

首版以已验证的 HyperOS 4 Security Center build 为反编译与行为基线；运行时 hook 不把私有类、方法或字段名作为兼容性 authority。Global Dock 是否进入 LiquidDock 由结构解析得到的 assistant-type discriminator 与支持的语义类型共同决定；不支持的类型保持 vendor-owned。

### 背景后端与 readiness

Security Center Global Dock 的可靠背景权威是 framework pass-window blur，而不是 shader fresh-frame。8 参数 configure 只建立 panel carrier 与 assistant type 关系，不作为 View-ready 事件；`SecurityCenterEarlyPrepareHook` 在 attach/pre-draw 生命周期持续等待稳定 getter 返回真实 Dock View，然后直接进入 framework material claim：

```text
semantic configure
-> wait for real Dock carrier through attach/pre-draw
-> config-only material gate
-> claim Dock carrier
-> clear/suppress vendor material on Dock
-> enable pass-window capability on Turbo/root host
-> apply module blur radius + blend colors on Dock
-> keep framework Dock material across normal panel close
```

这一链路不等待 shader source authority，也不等待 `SecurityCenterGlassSession.onFrameRendered()`。framework capability 不可用时 fail-closed，并保留 vendor presentation；不会回退到 ScreenCapture、PixelCopy、bitmap 或固定延迟。

### Material ownership 范围

- **Dock carrier**：LiquidDock 唯一的 Security Center framework material carrier。claim 时保存 vendor 最新稳定 View-API intent，清掉当前 vendor material，再应用模块 framework blur；claim 期间后续 vendor material 写入被记录但不直接覆盖屏幕状态。
- **Turbo/root**：只作为 pass-window capability host。可以启用窗口级 capability，但不作为普通材质 carrier，不清其 background/material。
- **All Apps**：保持 vendor-owned。不会被 claim、不会清 vendor material、不会应用模块 framework blur，也不会因为 Dock glass 而扩展成整页矩形玻璃。
- **其它 box/material view**：v1 的 Dock 背景路径不依赖它们；未经独立验证的 carrier 不扩大 ownership。

### Close 与 full release

普通面板关闭不是材质 teardown。Dock carrier 的模块 framework material 与 ownership 默认保留，因此下一次打开不需要依赖一次新的 shader frame，也不会出现“本次有背景、下次无背景”的交替状态。vendor 在已 claim Dock 上的稳定 material API 写入继续被镜像记录并抑制。

真正的 full release 只发生在 runtime disable、owner replacement 或明确的全局释放边界：

```text
clear module framework material
-> relinquish Dock ownership
-> replay latest mirrored vendor View-API state
-> reset framework material lifecycle
```

所有 vendor material 恢复都通过稳定 `android.view.View` API 的镜像状态执行；源码、测试和文档都不得把反编译私有类、方法或字段名作为 hook authority。任何结构语义出现 0 个或多个候选都 fail-closed，不回退到私有名称或固定延迟。

HyperOS 3 的 capability policy 始终解析为普通 `BACKGROUND_BLUR`。这是未来兼容约束，不表示 v1 已支持未分析的 HyperOS 3 Security Center build，也不会把 HOS3 宣称为 soft-light glass。

## 12. 当前重构方向

后续优先级见 [TODO.md](TODO.md)。核心方向：

1. 收紧 Workstation shared producer recovery API；
2. 拆分 `WorkstationModeController`；
3. 引入 `WidgetClassifier` / `WidgetSpecRegistry`；
4. 按职责拆分 `HomeGridHook`；
5. 将 `MainHook` 继续收缩为 Launcher composition boundary；
6. 基于真机证据扩展 Security Center 其它 toolbox scene，而不是复制 producer/backend。

任何重构都不得恢复 1.x ScreenCapture backend。
