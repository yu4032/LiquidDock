# LiquidDock Architecture

本文档描述当前 `main` / **v2.1.1** 的实际实现，而不是历史 1.x 捕获架构或未来目标设计。

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

v2.1.1 在 `onRecentViewHide` 返回 HOME 时先 rollover shared Launcher producer，再解除 Recents covered；scene controller 仍保持 fresh-OES-frame barrier，因此不会提前显示旧帧。

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

## 11. 当前重构方向

后续优先级见 [TODO.md](TODO.md)。核心方向：

1. 收紧 Workstation shared producer recovery API；
2. 拆分 `WorkstationModeController`；
3. 引入 `WidgetClassifier` / `WidgetSpecRegistry`；
4. 按职责拆分 `HomeGridHook`；
5. 将 `MainHook` 收缩为 composition root；
6. 将 `LauncherGlassSession` 的 producer、freshness、EGL/OES 和 renderer 生命周期进一步分层。

任何重构都不得恢复 1.x ScreenCapture backend。