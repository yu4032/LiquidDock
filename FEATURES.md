# LiquidDock 功能手册

本文档按当前 `main` / **v2.4.1** 的生产代码与设置页整理。Launcher 主验证基线为 **HyperOS 3.0.307+ / `com.miui.home` release-4.50.x.x**；Security Center 是独立进程、独立兼容边界。

---

## 1. 桌面网格与图标大小

### 网格 Profile

启用自定义网格后可选择：

| Profile | 横屏 | 竖屏 |
| --- | --- | --- |
| 8×4 | 8 列 × 4 行 | 4 列 × 8 行 |
| 10×6 | 10 列 × 6 行 | 6 列 × 10 行 |

当前实现保留 MIUI placement / occupancy 权威；LiquidDock 修改 grid count、几何、合法 drop/drag bounds 与方向记忆，但不通过猜测 occupied matrix 接管原生 placement。

可调项包括：

- 横/竖屏水平距离；
- 顶部/底部距离；
- 行距；
- 页面指示器 Y；
- Widget adaptation。

### Widget adaptation

当前显式覆盖常见 `1×1`、`2×1`、`2×2`、`4×2` 分配。适配目标是 pixel allocation / final frame，不修改 MIUI occupancy matrix。off-screen / lazy page 会在显示前准备对应方向的 geometry。

### Launcher 4.50 图标大小

独立开关，范围 **80%–120%**，默认 100%。通过测量事务的 typed domain 控制，不修改共享 `GridConfig`。

当前作用域：

- Workspace ShortcutIcon；
- Dock 图标；
- 1×1 小文件夹及其 4×4 preview container；
- 打开的文件夹内图标；
- Workstation App 页。

普通 All Apps / Search 不属于该缩放域。

---

## 2. Dock 自定义

Dock customization 默认开启。当前 307+ 路径以实际 HotSeats MiuiX / themed background 为 vendor lifecycle owner，并在其上组合 LiquidDock geometry、Prismal、描边和阴影。

主要设置：

| 功能 | 范围/语义 |
| --- | --- |
| 隐藏手机互联图标 | 只隐藏 Dock 入口，不修改连接状态 |
| Dock resize animation | 是否保留 MIUI resize 动画；安装选择需重启桌面 |
| 平滑 resize | 原生 resize 关闭时使用 LiquidDock 平滑调整 |
| 宽/高偏移 | −80 ~ 80 dp |
| 图标间距 | −8 ~ 12 dp；同时补偿背景宽度 |
| 底部偏移 | −30 ~ 40 dp |
| 原生 Dock blur | 0 ~ 400 |
| 圆角 / blur 圆角偏移 | 保留历史 unit/storage 兼容 |
| Squircle / Fill-Diff | 可改变当前描边轮廓 |

### Dock spacing 的 R8 注意点

307 主路径和备用路径都需要在 **Launcher ClassLoader** 中解析 `RecyclerView` / `RecyclerView$State` 来 Hook `OffsetDecoration.getItemOffsets`。R8 会识别模块自身同名 AndroidX class，因此项目使用 targeted `-keepnames` 保持跨 ClassLoader 二进制名，不能删除这条规则。

---

## 3. 描边、阴影与 Divider

### Dock Stroke

`DockStrokeRenderer` 是当前 foreground owner。支持：

- 开/关并恢复原 foreground；
- 标准圆角、Squircle、Fill-Diff；
- RGB / alpha；
- 各模式线宽与 squircle control point；
- 描边阴影。

Squircle / Fill-Diff 在运行时切换会刷新已安装 renderer。

### Whole-Dock Shadow

整个 Dock 阴影与描边阴影是不同 owner，可独立调：

- radius；
- size；
- alpha；
- Y offset。

关闭后移除 LiquidDock 自己的 shadow，并停止继续抑制未来 vendor shadow 调用；不会猜测未保存的原生参数。

### Workstation Divider

Divider 视觉开关可 live restore，参数包括宽度、相对高度、Y offset、RGBA。第一次接管前会 snapshot layout/background，关闭时精确恢复并释放 ownership。详细见 [DIVIDER.md](DIVIDER.md)。

---

# 4. Liquid Glass

## 4.1 总开关与组件

Global Liquid Glass 默认关闭。启用后可分别控制：

- Security Center glass；
- 桌面快捷菜单 glass；
- 快捷菜单深色模式适配；
- 图标 glass；
- 仅 Dock 功能图标 glass；
- Widget glass；
- Widget 深色内容适配；
- 小文件夹 glass；
- 大文件夹 glass。

图标、Widget、小/大文件夹都有独立 size offset / corner radius。普通 icon glass 关闭后，仍可单独开启“仅 Dock 功能图标玻璃”，当前 registry 覆盖搜索、小爱、全部应用、最近任务、Home、手机互联等 Launcher 系统入口。

## 4.2 Launcher shared glass

Workspace 图标、Widget、小/大文件夹的静态节点共享一个 root-wide `LauncherGlassSession` / PassBlur backdrop。静态对象本身不是 producer owner。

典型 source 数据流：

```text
MiuiX PassBlur
 -> Surface / SurfaceTexture
 -> OES texture
 -> normalization / overscan
 -> Prismal
 -> per-node/static compositor output
```

当前原则：

- native PassBlur spatial mapping 保持 `1.0` scale；
- local quality scaling 发生在 OES normalization 之后；
- scene generation / wallpaper generation / producer generation 不互相冒充；
- fresh output frame 才能解除 stale-frame barrier；
- unavailable capability -> fail closed。

## 4.3 Workspace 实时性能

设置页“工作区实时捕获性能”目前实际含义是 zero-copy source/render 质量控制，不是截图循环：

| 参数 | 语义 |
| --- | --- |
| 工作区渲染分辨率 | 降低 normalization 后 local physical FBO 像素密度；logical root 坐标不变 |
| Prismal 实时渲染上限 | 只限制昂贵 render；source frame 仍 drain，fresh generation 可绕过限制 |

PassBlur 是 source-driven：静态壁纸/静态 backdrop 可在 updates-enabled 状态下自然不产生新 buffer；动态壁纸或真实内容变化会继续产生 source frame。LiquidDock 不创建固定 FPS 的 Choreographer capture pump。

## 4.4 图标与 App launch proxy

`ShortcutIcon` 等静态 host 注册到共享 compositor。App 启动/返回 HOME 时，LiquidDock读取 `FloatingIconView2` / `FloatingIconLayer2` 的真实 proxy geometry 和 visibility owner，避免静态图标 glass 与 MIUI floating icon 重叠。

## 4.5 拖拽 glass

当前拖拽不是 snapshot：

- `DragController.createDragView()` 只用于预热正常单图标拖拽 source；
- `ViewGroup.onViewAdded/onViewRemoved` 中真实 `DragView` 生命周期是 begin/end authority；
- LiquidDock 建立不接收触摸的 `TYPE_APPLICATION_PANEL` 上层窗口；
- glass 实时采样 Launcher Workspace；
- MIUI DragView 通过 direct `draw(Canvas)` 镜像到上层；
- 只有 mirror、glass output 与 fresh backdrop 都可用后才隐藏原 MIUI DragView presentation；
- 失败/结束时先恢复 vendor DragView，再释放 LiquidDock overlay。

原 Workspace View 不作为移动几何源；移动 geometry 由实际 DragView 决定。

## 4.6 Widget glass

支持 `LauncherAppWidgetHostView` 和 MAML `MaMlHostView` 的共享 glass host。RemoteViews / MAML 生命周期变化后会重新 reconcile background ownership。

### 深色内容适配

- 深色、中性的文字可转为白色；
- MAML 优先走原生深色变量/更新路径；
- 不把图片和彩色内容当文字强制着色。

### Widget component hiding

设置页可进入组件页，基于一次性 discovery 的精确 owner/path/class/name selector 选择要隐藏的背景组件。当前方案强调可恢复 mutation，不提供任意脚本或通用表达式 DSL。

## 4.7 文件夹 glass

小文件夹与大文件夹拥有独立开关与尺寸/圆角。文件夹打开/关闭、drag、press 等 lifecycle 与 vendor material suppression 分离；关闭某一类型只释放该类型，不破坏其它 static glass。

## 4.8 桌面快捷菜单

### Glass background

“桌面快捷菜单玻璃背景”在 `ShortcutMenu.show()` 完成后直接绑定真实 popup content 的 HWUI RenderNode，通过 `RuntimeShader -> RenderEffect -> View.setBackdropRenderEffect()` 处理原生 backdrop。它不再创建独立 PassBlur source/TextureView 输出，菜单的缩放、位移、透明度动画继续由系统原生 View 层级驱动，并在真实 detach 生命周期回收。

### 深色模式适配

可独立开启，但仍位于 global Liquid Glass 功能域内：

- 所有菜单 `TextView` 文字变白；
- TextView compound drawable 变白；
- 独立 `ImageView` 不一刀切 tint；
- 首次遇到 drawable 时渲染到 20×20 临时 Bitmap，只有近黑、低色差、以暗像素为主的线条图标才 tint 白色；
- 彩色第三方应用图标完全不写 tint；
- drawable 判定使用 `WeakHashMap` 缓存；动态 layout 只复用结果。

该临时 Bitmap 仅用于菜单图标分类，不是 backdrop capture。

---

## 5. Prismal 光学

当前设置覆盖：

- blur；
- thickness；
- IOR；
- normal strength；
- dome；
- lens refraction；
- chromatic dispersion；
- tint / brightness；
- specular / rim / caustics / edge band；
- refraction inset / displacement / height transition / smoothing；
- Fresnel、dispersion R/B、vibrancy；
- OS4 reflection width/offset/strength/lighten；
- directional light angle / intensity；
- shadow color/softness/transmittance；
- backdrop scale/parallax；
- surface normals debug view。

Launcher highlights 另有两套 component profile：

- compact：图标、小文件夹、Dock 图标；
- large surface：Widget、大文件夹等。

可分别启停 sky haze、specular、lit/opposite/corner rim、face sheen、plain highlight、caustics、press glow。

---

## 6. HOME / Recents / wallpaper freshness

Launcher shared glass 不把普通 `invalidate()` 当新内容证明。

- Recents show：覆盖/隐藏 Workspace static glass；
- Recents hide：按当前 mode 做 producer recovery，再解除 covered；
- HOME reveal：等待匹配 scene generation 的 fresh OES frame；
- wallpaper change：独立 wallpaper generation；
- rotation/root replacement：旧 endpoint 退役，新 endpoint 提交 fresh source 后再恢复；
- SystemUI keyguard/HOME transition 只提供时序 authority，不渲染 Launcher glass。

Workstation Recents 返回还会处理旧 PassBlur producer 被 SurfaceFlinger 退役、但 Launcher Surface 看起来仍 valid 的情况。

---

## 7. Workstation / Laptop

当前仍标记为实验性组合适配。设置包括：

- Dock width；
- Dock icon top/bottom offset；
- Dock icon glass corner radius；
- Workspace grid horizontal offset；
- All Apps 横/竖屏 horizontal / vertical / top / bottom spacing；
- Divider。

工作台同时涉及 Dock、Grid、All Apps、Recents、PassBlur、rotation、wallpaper 和 normal-layout restore，因此 composite structure 仍以 restart-bound 为主。

---

## 8. Recents

`recents_background_blur_percent` 范围 0–100，独立缩放 Launcher Recents 的背景模糊强度。它与 Liquid Glass PassBlur backdrop source selection 是两条不同功能链。

---

## 9. Security Center glass

Xposed scope 包含 `com.miui.securitycenter`，但运行时只允许 **`com.miui.securitycenter:ui`** 初始化。

当前兼容方式不是固定 versionCode 白名单，也不是依赖混淆成员名：

1. `DockWindowManagerService.onCreate()` 得到真实 Context；
2. 校验 `TurboLayout`、sidebar AIDL implementation、相关关系型方法签名；
3. 要求稳定语义 getter / resource capability 存在；
4. 唯一解析出 assistant type / All Apps motion / terminal cleanup contract 后才提交 activation；
5. 任何缺失或歧义都 fail closed，并只给一次 unsupported 提示。

当前 coordinator 明确支持 assistant type：

- Game (`1`)；
- Video (`3`)；
- Global Dock (`4`)；
- Global Dock 下的 All Apps carrier。

一个 root-wide `SecurityCenterGlassSession` 按 root identity 复用 source，material carrier identity 独立维护 epoch/sink。`SecurityCenterPassBlurContinuousAuthority` 防止 vendor 页面切换把 LiquidDock 已 claim 的 PassBlur output/scale 改回 vendor contract；`SecurityCenterVendorMaterialState` 记录并抑制被 claim View 后续 vendor material writes，release 时重放最后观察到的 vendor state。

这条路径同样禁止 ScreenCapture / PixelCopy / backdrop Bitmap fallback。

---

## 10. Live 与 restart-bound

### 可即时释放的视觉 ownership

包括但不限于：

- icon / widget / small-folder / large-folder glass；
- widget dark-content；
- Dock customization 已保存状态的视觉部分；
- stroke / shadow / Divider；
- Security Center component glass。

### 需要重启桌面/进程的结构选择

包括：

- master switch 的完整 Hook 安装/卸载；
- Grid 主结构/profile；
- Launcher 4.50 icon-size Hook 安装；
- Dock resize Hook 选择；
- Workstation composite structure；
- shortcut popup hook 安装等在进程启动时读取的配置。

“某个 View 已恢复 vendor 外观”不等于已卸载所有结构 Hook。

---

## 11. 配置与兼容

`ConfigSchema` 是 persisted key、类型、UI default、runtime fallback、export default、范围、storage mode 的唯一登记入口。历史 key 和 import alias 会继续保留以兼容旧备份；存在于 schema 不代表旧截图时代参数仍是当前 active rendering knob。

JSON 导入/导出应通过 `ConfigCodec` / `PresetManager` / migration 链处理，不应在功能 Hook 内直接解释历史 JSON。
