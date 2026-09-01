# LiquidDock 2.2 Hook 点总览

本文档记录当前 `main` / **v2.2.1** 的主要 libxposed Hook、Android listener、反射边界与 GPU 生命周期。

当前注入范围：

```text
com.miui.home
com.android.systemui   # read-only timing source
```

旧 ScreenCapture / bitmap readback Hook 不属于当前主线。

## 1. API101 入口

`ModuleMain` 根据包名分流。

### SystemUI

只安装：

- `SystemUiKeyguardGoneSource.install(classLoader)`；
- `SystemUiHomeTransitionSource.install(classLoader)`。

异常必须 fail-open，不能影响 SystemUI/WMShell 正常 transition。

### Launcher

完成配置 migration / runtime state 初始化后安装 Grid、Dock、Workstation、Glass、Recents、Widget discovery 等模块，并注册 SystemUI timing runtime receiver。

## 2. SystemUI HOME transition source

目标类：

```text
com.android.wm.shell.transition.HomeTransitionObserver
```

当前 Hook：

| 方法 | 用途 |
|---|---|
| `onTransitionReady(IBinder, TransitionInfo, Transaction, Transaction)` | 建立当前 ready token 作用域 |
| `notifyHomeVisibilityChanged(boolean)` | 只在该 ready 作用域内记录 HOME visibility |
| `onTransitionStarting(IBinder)` | 发布精确 HOME START |
| `onTransitionFinished(IBinder, boolean)` | 发布 matching HOME FINISH / aborted |
| `onTransitionMerged(IBinder, IBinder)` | 合并 token lifecycle |

这样可以过滤 `RecentsTransitionHandler` 在 `HomeTransitionObserver.onTransitionReady` 外部直接调用 `notifyHomeVisibilityChanged()` 的情况。

Source 只发送 phase/homeVisible/serial/elapsedRealtimeNanos 等时序信息，不解析 Launcher View，也不创建渲染 surface。

## 3. Keyguard Gone source

`SystemUiKeyguardGoneSource` 提供解锁 transition 的系统级 FINISHED 边界。

Launcher 侧 `UnlockAnimationStateMachine.PREPARE` 只负责提前 freeze Workspace capture；release authority 保留给 SystemUI Keyguard Gone FINISHED。

该 gate 不能暂停独立 Floating Dock producer。

## 4. Launcher HOME fallback

`LauncherGlassHomePresentationHook` 继续 Hook Launcher 4.50：

- `com.miui.home.recents.anim.WindowElement.animTo(Object)`；
- `WindowElement$mRectFSpringAnimListener$1.onAnimationEnd(RectFSpringAnim)`；
- `UnlockAnimationStateMachine.setState(...)`。

`CLOSE_TO_HOME / CLOSE_TO_HOME_CENTER` 是 Launcher fallback。SystemUI HOME START 到达后，Launcher end 不得提前释放 barrier；matching SystemUI FINISH 才是 authority。

跨进程事件使用 serial + `SystemClock.elapsedRealtimeNanos()` 做 stale-event 防护。

## 5. Widget ↔ App transition

`LauncherWidgetTransitionHook` 覆盖 Launcher 4.50 独立 Widget animation target，包括：

- `com.miui.home.launcher.LauncherWidgetView`；
- `com.miui.home.launcher.maml.MaMlWidgetView`；
- `com.miui.home.launcher.anim.WidgetTypeAnimTarget`；
- closing-widget lookup 路径，例如 `WindowAnimParamsProvider` 及实际手势返回调用点。

目标不是模拟 ShortcutIcon proxy，而是维护 Widget 自己的 transition ownership：

- vendor 隐藏 Widget host 前开始 Glass fade-out；
- return target 确认后抑制旧 StaticLayer 中对应 Widget；
- HOME barrier 解除后继续等待 fresh scene generation；
- fresh render 后才 fade-in；
- launch-only suppression 在 vendor 恢复 VISIBLE 时释放；return suppression 不允许被该 VISIBLE 绕过。

## 6. Dock / MiuiX zero-copy

主要边界：

| 目标 | 方法/边界 | 作用 |
|---|---|---|
| `Launcher` | `setupViews()` | 解析 HotSeats/Workspace/Dock background 并建立绑定 |
| HotSeats / Dock material | attach / geometry / live-blur lifecycle | 同步 Dock producer 与 vendor material |
| `BlurUtilities` | blur mutation | 在 LiquidDock ownership 下接管对应参数 |
| Dock layout manager | spacing / background / Workstation offset | Dock geometry |

`Miuix307PassBlurBridge.bind()` 基础语义是 **continuous-on-bind**。

- Dock 使用持续更新；
- Workspace session 才可以显式 `requestSingleUpdate()` / `pauseUpdates()`；
- 不得根据窗口 focus 或 Floating Dock 层级推导 producer 策略。

## 7. Launcher-wide Static Glass

`MiuixLauncherStaticGlassHook` 发现并绑定图标、Widget、文件夹到 root-wide session。

典型入口：

- `ShortcutIcon` lifecycle；
- `LauncherAppWidgetHostView` constructor / `updateAppWidget(RemoteViews)`；
- MAML host constructor / resume / color update；
- Workspace current-page/layout reconcile；
- Launcher resume 的轻量 reconcile。

StaticNode 通过 `transformMatrixToGlobal` / root inverse 得到完整 root-space geometry。

v2.2.1 起 StaticNode 使用 `LauncherGlassGeometry.resolveStatic()`：部分离屏保留完整形状，只记录可见 crop，完全离屏才 cull。

## 8. Drag / sink geometry

拖拽与需要局部 output crop 的 sink 路径仍使用 clipped `LauncherGlassGeometry.resolve()`。

不要为了修 StaticLayer 边缘行为全局删除 clipped resolver；两套 geometry 语义是刻意分离的。

## 9. Widget component discovery

`LauncherWidgetComponentDiscovery` 支持：

### RemoteViews

递归真实 View tree，记录 background/image/whole-node action，以及：

- render ordinal；
- depth；
- area ratio；
- `View.getZ()`。

### MAML

真实 render tree：

```text
ScreenElementRoot.mInnerGroup
  -> ElementGroup.mElements
```

同时保留 `ScreenElementRoot.findElement(name)` 对已有 name-based selector 的稳定性。

`WidgetComponentRanking` 只是目录排序 policy；metadata 不进入 selectorKey，不改变用户已保存隐藏规则。

## 10. Folder Glass

`MiuixFolderGlassHook` 管理 small/large folder：

- folder host bind；
- vendor blur/material suppression；
- folder open/close covered state；
- touch interaction；
- large-folder draw/cover ownership。

small/large folder 使用独立 live gate。

## 11. Recents scene

`LauncherGlassRecentsHook` 使用 `RecentsServiceDispatcher.onRecentViewShow/onRecentViewHide` 管理 scene covered。

Workstation 返回时可先执行 shared Launcher producer rollover/rebind，再解除 covered 并等待 fresh OES frame。

这只针对 Workspace shared producer，不改变 Dock continuous-on-bind。

## 12. Wallpaper freshness / rotation

`LauncherWallpaperFreshnessHook` 维护 wallpaper generation。普通 invalidate 不能成为内容新鲜度证明。

Rotation 使用 settle / producer generation 边界；旧 endpoint 不能在新 orientation 已开始后重新发布 stale scene。

## 13. Runtime visual ownership

### Glass

`GlassRuntimeState`：

- global glass；
- icon；
- widget；
- small-folder；
- large-folder。

true→false 必须先 publish false，再 dispatch teardown。

### Dock visuals

`VisualRuntimeState`：

- Dock customization；
- stroke；
- whole-Dock shadow；
- Divider；
- Squircle / Fill-Diff refresh。

只恢复实际保存过的 vendor state。

## 14. Divider / Workstation

`DockDividerHook` 从 Workstation line holder/bind 生命周期获得 divider View，首次 mutation 前 snapshot layout/background，disable 后取消 pending listener、恢复并释放 snapshot。

工作台整体仍是实验性 composite path。

## 15. Grid

Grid Hook 覆盖：

- cell count / profile overlay；
- orientation memory；
- mutation capture；
- horizontal centering / vertical bounds；
- Widget frame adaptation；
- drop legality / drag bounds；
- lazy/off-screen page preparation。

不接管 MIUI occupied matrix。

## 16. Recents 背景模糊

`RecentsBackgroundBlurHook` 只调整 Launcher Recents blur，与 Liquid Glass backdrop producer 独立。

## 17. 维护原则

新增/修改 Hook 时必须保持：

- SystemUI source read-only、fail-open；
- Dock continuous producer 不被 Workspace 优化影响；
- stale async callback 在执行时重查 live state；
- producer/content freshness 不依赖普通 redraw；
- Widget return 不显示 stale backdrop；
- StaticLayer 与 clipped sink geometry 分离；
- selector identity 与 discovery metadata 分离；
- 不恢复 1.x ScreenCapture/bitmap pipeline。
