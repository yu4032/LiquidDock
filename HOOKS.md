# LiquidDock 2.1 Hook 点总览

本文档记录当前 `main` 源码安装的主要 libxposed Hook、Android listener、系统反射调用和 GPU 渲染阶段。

## 运行时边界

Xposed scope：

```text
com.miui.home
```

API 101 入口为 `ModuleMain`。`onPackageReady()` 的安装顺序为：

1. `LegacyConfigMigration.migrateAtProcessStart()`；
2. 读取 `LiquidDockConfig` runtime snapshot；
3. `new MainHook().install(classLoader)`；
4. master switch 与 custom grid 同时开启时安装 `WorkspaceDropRuleHook`。

`MainHook.install()` 依次安装工作台状态、Dock 描边、Dock 几何、分隔线、主屏幕网格、多任务模糊和液态玻璃。`Miuix307MaterialPipeline` 成功安装后接管当前玻璃路径。

## Dock 与 zero-copy 玻璃

### Material owner

`Miuix307MaterialPipeline` 支持以下 vendor background：

```text
com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground
com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2
```

### Hook 点

| 目标 | 方法 | 作用 |
|---|---|---|
| `Launcher` | `setupViews()` | 读取 `mHotSeats`、`mWorkspace` 和实际 background，绑定 zero-copy glass |
| `Launcher` | `onResume()` | 工作台模式重新绑定 PassBlur producer |
| `HotSeats` | `onAttachedToWindow()` | 按当前 HotSeats background 恢复玻璃绑定与几何 |
| `HotSeats` | `setMingouStaticDockSnapshotMode(boolean)` | 同步静态 Dock snapshot 状态与 producer 更新状态 |
| `HotSeats` | `setMingouStaticDockLiveBlurVisible(boolean)` | 在对应系统版本同步 live blur 可见状态与 producer 更新状态 |
| `BlurUtilities` | `setBackgroundBlur(View,int,float[],int[][])` | 将当前 glass material owner 的 vendor background blur radius 设为 0 |
| `HotSeatsListContentMiuiXBlurBackground` | `setBackgroundWidth(int)` | 应用 Dock width offset 并同步 glass size |
| 同上 | `setBackgroundHeight(int)` | 应用 Dock height offset 并同步 glass size |
| 同上 | `setBackgroundRadius(float)` | 应用圆角设置并同步 glass geometry |
| `HotSeatsListContentBlurBackground2` | `onAttachedToWindow()` | 绑定主题提供的 Dock background |
| 同上 | `setBackgroundWidth(int)` | 同步主题 background width |
| 同上 | `setBackgroundHeight(int)` | 同步主题 background height |
| 同上 | `setBackgroundRadius(float)` | 同步主题 background radius |
| 同上及其父类 | 非 static `triggerMeasure(...)` overload | 在 vendor 测量结束后同步玻璃几何 |

`MiuixGlassHook` 将 vendor background 作为 Dock 几何来源，在其内部创建 `DockLiquidGlassHostView` 和 `Miuix307PassBlurTextureView`。View attach 与 global-layout listener 跟踪 background 替换、重新挂载和布局变化。

### SurfaceFlinger PassBlur 反射调用

以下项目是系统隐藏 API 的反射调用：

| 对象 | 调用 | 作用 |
|---|---|---|
| `View` / `ViewRootImpl` | `getViewRootImpl()` / `getSurfaceControl()` | 获取 Floating Dock root `SurfaceControl` |
| `SurfaceControl.Transaction` | `SetPassBlurSurface(SurfaceControl,Surface)` | 将 PassBlur 输出绑定到 LiquidDock producer `Surface` |
| 同上 | `setUpdateTextureFlag(SurfaceControl,boolean,float)` | 控制 PassBlur texture 更新 |
| 同上 | `setMiBlurWinExc(SurfaceControl,String[])` | 设置 compositor exclusion layer 名单 |

exclusion layer 包含：

```text
NavigationBar
StatusBar
GestureStub
DockAssistantView
```

### GPU 数据流

`Miuix307PassBlurTextureView` 的渲染线程维护 EGL、OES 和 FBO：

```text
SurfaceFlinger PassBlur Surface
  → external OES texture
  → Dock-local RGBA normalization FBO
  → horizontal Gaussian blur
  → vertical Gaussian blur
  → Prismal scene compositor
  → TextureView
```

Prismal scene 同时绘制 Dock body、Dock 图标和工作区静态玻璃节点。光学参数、高光分组和动画状态通过 `PrismalParams` 传入共享 renderer。

## 工作区玻璃

### 图标与小组件

`MiuixLauncherStaticGlassHook` 安装以下 Hook：

| 目标 | 方法 | 作用 |
|---|---|---|
| `Launcher` | `setupViews()` | 扫描已存在的工作区页面并注册静态玻璃节点 |
| `Launcher` | `onResume()` | 重新协调当前工作区节点和场景状态 |
| `ShortcutIcon` | constructor | 注册图标 host |
| `ShortcutIcon` | `setIconImageView(...)` | 在图标材质更新后重新绑定节点 |
| `LauncherAppWidgetHostView` | constructor | 注册小组件 host |
| 同上 | `updateAppWidget(RemoteViews)` | RemoteViews 更新后重新绑定小组件材质 |
| `MaMlHostView` | constructor | 注册 MAML 小组件 host |
| 同上 | `onResume()` | MAML 恢复后重新绑定材质 |
| 同上 | `updateColor(int)` | MAML 颜色更新后重新绑定材质 |

静态节点由 `LauncherGlassSceneController` 和 `DockGlassItemRegistry` 管理。节点几何来自实际 material View 的屏幕坐标、尺寸、缩放和圆角。

### 文件夹

`MiuixFolderGlassHook` 安装以下 Hook：

| 目标 | 方法 | 作用 |
|---|---|---|
| `ItemIcon` | `setIconImageView(Drawable,Bitmap)` | 为 `FolderIcon` 绑定文件夹玻璃材质 |
| `FolderIcon1x1` / `FolderIcon2x2` | constructor | 注册小文件夹和大文件夹 host |
| `BlurUtilities` | `setFolderIconBlur(...)` | 将已绑定文件夹的 vendor blur 转交给共享玻璃节点 |
| `FolderStatusServiceImpl` | `dispatchFolderOpen()` | 设置文件夹打开覆盖状态 |
| 同上 | `dispatchFolderClose()` | 清除文件夹打开覆盖状态 |
| `FolderIcon` | `dispatchTouchEvent(MotionEvent)` | 将按压状态与触点坐标传入 Prismal interaction |
| `FolderIcon` | `onOpen()` | 隐藏打开文件夹对应的桌面静态玻璃 |
| `FolderIcon` | `onClose()` | 跟随系统关闭流程更新状态 |
| `Folder` | `onClose(boolean,Runnable)` | 在系统关闭完成回调中恢复桌面文件夹玻璃 |
| `FolderIcon2x2` | `drawChild(Canvas,View,long)` | 在大文件夹绘制前同步原生背景材质状态 |

文件夹、图标和小组件的显隐由同一个场景控制器执行。动画时长来自“动画”页面的工作区显隐配置。

### 多任务场景

| 目标 | 方法 | 作用 |
|---|---|---|
| `RecentsServiceDispatcher` | `onRecentViewShow()` | 隐藏工作区静态玻璃 |
| 同上 | `onRecentViewHide()` | 恢复工作区静态玻璃 |

### 壁纸内容代际

`LauncherWallpaperFreshnessHook` 使用以下 Launcher 回调更新场景壁纸代际：

| 目标 | 方法 | 作用 |
|---|---|---|
| wallpaper callback | `onWallpaperChanged(WallpaperColors,String,int)` | 创建新的壁纸内容代际 |
| `Workspace` | `onWallpaperColorChanged()` | 提交工作区壁纸候选帧 |
| wallpaper callback | `onWallpaperFirstFrameRendered(int)` | 标记首帧为当前壁纸内容 |
| wallpaper callback | `onDrawFrameEnd()` | 标记当前绘制周期壁纸内容完成 |

## Dock 图标动画与拖拽

### Dock 图标启动动画

`DockIconAnimationGlassHook` 安装：

| 目标 | 方法 | 作用 |
|---|---|---|
| `ShortcutIcon` | `setAnimTargetVisibility(int)` | 在 Dock 图标恢复可见时结束玻璃启动动画状态 |
| `FloatingIconView2` | `update(...)` | 按退出动画 progress 更新 Dock 图标玻璃状态 |
| `FloatingIconLayer2` | `update(...)` | 在对应系统实现中按 progress 更新 Dock 图标玻璃状态 |

Dock 图标玻璃在系统动画期间隐藏，并按“Dock 图标玻璃恢复”时长淡入。

### 工作区拖拽

`MiuixLauncherDragOverlayHook` 安装：

| 目标 | 方法 | 作用 |
|---|---|---|
| `ViewGroup` | `onViewAdded(View)` | 在 DragView 加入拖拽容器时创建移动玻璃节点 |
| `ViewGroup` | `onViewRemoved(View)` | 在 DragView 移除时释放移动玻璃节点 |
| 静态材质 class | `onDragContainerBgAnimAlpha(boolean,boolean)` | 同步原位置静态玻璃的拖拽抑制状态 |

移动节点沿用对应图标、文件夹或小组件的尺寸、圆角和组件类型。

## Native Dock 自定义

| 目标 | 方法 | 作用 |
|---|---|---|
| `HotSeatsListContentBlurBackground2` | `setBackgroundWidth(int)` | 应用普通 Dock width offset |
| 同上 | `setBackgroundHeight(int)` | 应用普通 Dock height offset |
| 同上 | `setBackgroundRadius(float)` | 同步 blur radius、stroke radius 和 shadow geometry |
| 同上 | `updateBackgroundSize(int,int,float)` | 应用 Dock resize animation 设置 |
| `HotSeatsListContentLayoutManager$OffsetDecoration` | `getItemOffsets(...)` | 应用 Dock icon spacing 与工作台上下偏移 |
| `HotSeatsListContentLayoutManager` | `updateBackgroundView(FrameLayout,int,int,float)` | 按图标数量补偿 Dock background width |
| `DeviceConfig` | `getHotSeatsMarginBottom()` | 应用普通 Dock bottom offset |
| `BlurUtilities` | `setBackgroundBlur(...)` | 应用 native Dock blur radius |
| `HotSeats` | `getMingouStaticDockBlurShadowTarget()` | 记录系统 Dock shadow target |
| `MiShadowUtils` | `applyViewShadow(...)` | 对系统 Dock shadow target 应用自定义 shadow 策略 |
| `Launcher` | `setupViews()` | 初始化 native background 与 Dock shadow |

`DockStrokeRenderer` Hook `HotSeatsListContentBlurBackground2.setBackgroundRadius(float)`。Dock 描边使用 outer path 与 inner path 形成轮廓环。307 glass 通过 `configureReplacingForeground(...)` 使用同一 renderer。

## 工作台模式

### 状态

状态来源为：

```text
LauncherModeController.isLaptopMode()
LaptopStateManager.onLaptopModeChanged(boolean)
```

兼容接口为：

```text
DeviceConfig.isMingouLaptopPcModeEnabled()
DeviceConfig.setMingouLaptopPcModeEnabled(boolean)
```

工作台状态变化同步 `HomeGridHook`、`WorkstationDockGeometryHook` 和 zero-copy producer。

### Dock 几何

| 目标 | 方法 | 作用 |
|---|---|---|
| `HotSeatsListContentLayoutManager$OffsetDecoration` | `getItemOffsets(...)` | 应用工作台 Dock icon top / bottom offset |
| `HotSeatsListContentAdapter$LineViewHolder` | `bindView()` | 定位工作台 `DockContainer` 并应用独立 width offset |

## Dock 分隔线

`DockDividerHook` Hook：

```text
HotSeatsListContentAdapter$LineViewHolder.bindView()
```

该 Hook 设置分隔线 width、height percent、Y offset 和 RGBA color。

## 多任务背景模糊

`RecentsBackgroundBlurHook` 安装：

| 目标 | 方法 | 作用 |
|---|---|---|
| `BlurUtils` | `fastBlur(float,Window,boolean)` | 应用多任务背景模糊百分比 |
| 同上 | `fastBlurWhenEnterRecents(...)` | 标记进入多任务的同步模糊调用范围 |
| 同上 | `fastBlurWhenGestureResetTaskView(...)` | 标记手势复位的同步模糊调用范围 |
| 同上 | `fastBlurWhenEnterMultiWindowMode(...)` | 标记进入多窗口的同步模糊调用范围 |
| 同上 | `fastBlurWhenDontUseNoBlurTypeWhenRecents(...)` | 缩放手势过程模糊比例 |
| 同上 | `fastBlurWhenUseCompleteRecentsBlur(...)` | 缩放完整多任务模糊比例 |

## 主屏幕网格

`HomeGridHook` 与其辅助 Hook 覆盖以下范围：

- `LauncherCellCountCompatPadDevice` 的 X/Y cell count；
- `GridConfig` 的 count getter、setter 和 `checkCellCount()`；
- `CellLayout.calculateXsAndYs()`、`setupLayoutParam(...)`、`onLayout(...)`；
- `FolderIcon1x1.onMeasure(...)`；
- `Launcher.setupViews()`、`onConfigurationChanged(...)`；
- `ScreenView.updateIndicatorPositions()`；
- 横竖屏 profile overlay、边界计算、居中、旋转快照和布局恢复。

`WorkspaceDropRuleHook` Hook：

```text
LayoutDropRuleForSwapPlaces.isLegalXY(int,int,int,int)
```

该 Hook 在自定义网格中返回合法坐标，实际 occupancy 与 placement 继续由 Launcher 网格实现处理。
