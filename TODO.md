# LiquidDock TODO

当前主线为 **v2.2.1 / HyperOS 3.0.307+ / MiuiX PassBlur + OES/GLES zero-copy**。

v2.2.1 已完成：SystemUI HOME timing 接入、Widget↔App 独立动画/fresh gate、Widget component background ranking、Workspace StaticLayer 边缘完整几何。以下只列仍需要推进的事项。

## 1. 解锁后 HOME wallpaper freshness 权威边界

**最高优先级，仍需真机硬化。**

当前已有：

- Launcher `UnlockAnimationStateMachine.PREPARE` 提前隐藏 Workspace StaticLayer；
- unlock capture gate 阻止 Workspace PassBlur bind/pulse/resume；
- SystemUI Keyguard Gone FINISHED 作为 release authority；
- producer rollover 与 fresh-frame barrier；
- gate 不阻断独立 Floating Dock continuous producer。

仍需建立更强的“内容权威”而不只是“动画结束”：

- 引入明确 unlock backdrop epoch，例如 `LOCKED/PRESENTING -> WAIT_HOME_WALLPAPER -> WAIT_FRESH_FRAME -> VISIBLE`；
- 找到 Launcher 4.50 / Wallpaper / Shell 中 HOME wallpaper 真正提交完成的 authoritative signal；
- producer rollover 后只接受严格晚于 release fence/timestamp 的新 OES buffer；
- wallpaper candidate pulse 在 unlock epoch 内 defer，不能绕过 gate；
- 无法确认 HOME wallpaper 时 fail-closed，继续隐藏 Workspace Glass。

真机必须覆盖指纹/密码/滑动、多次连续锁解、解锁后立即 Recents/App/rotation/Workstation。

## 2. Workstation / Laptop 完整适配

工作台仍未视为完整支持。

需要继续验证：

- 进入/退出和普通布局 backup/restore；
- Dock width、icon spacing/offset；
- Grid / All Apps 横竖屏；
- Recents 连续往返；
- rotation；
- Workspace PassBlur suspend/pulse/rebind；
- wallpaper generation / fresh frame；
- 普通模式无回归；
- Floating Dock continuous producer 不受 Workspace policy 影响。

同时收紧 shared producer recovery API：避免反射假阳性、重复 `onRecentViewHide` 导致重复 rollover，并增加明确成功/失败诊断。

## 3. HOME transition transport / diagnostics

当前 SystemUI WMShell source + Launcher fallback 已工作，但跨进程 timing 仍可继续硬化：

- 记录 START/FINISH serial、source elapsedRealtimeNanos 与 Launcher receipt latency；
- 保持 stale START rejection 和 merged transition 测试；
- 真机确认 App→Home、手势 Home、Recents→Home、Widget return 不发生 authority 竞争；
- 只有在广播延迟确实影响视觉时再评估更直接的 IPC，不要无证据引入新的 binder 生命周期复杂度。

## 4. Widget component discovery / background ranking 硬化

基础能力已经完成，剩余工作：

- 提高 MAML `getWidth/getHeight` 不可用时的 Area 推导质量；
- 对 custom drawing / 非标准 z-order 的 RemoteViews 增加诊断，不把 child index 当绝对 GPU order；
- GUI 可考虑显示 background score/理由，而不是只显示“疑似背景”；
- 提供更明确的 preview/restore 反馈；
- ranking 永远只负责排序/提示，不自动执行 destructive hide；
- selectorKey 格式保持向后兼容。

## 5. Widget grid classifier / span registry

- 移除 `WidgetGridSizing` 的 static global gate；
- 引入集中 `WidgetClassifier`；
- 引入 `WidgetSpecRegistry`，先覆盖现有 1×1、2×1、2×2、4×2；
- Widget adaptation 继续只修改 allocation/frame，不接管 placement/occupancy。

## 6. `HomeGridHook` / Grid 模块拆分

按低风险到高风险拆分：

1. Widget adaptation；
2. Page indicator；
3. Folder alignment；
4. Cell geometry；
5. rotation / refresh。

必须保持 orientation-specific memory、lazy/off-screen page preparation，以及 MIUI occupancy ownership。

## 7. `MainHook` 收缩为 composition root

- Dock/Grid/Glass/Workstation 安装进一步模块化；
- mutable static 状态下沉到 controller/view lifecycle owner；
- 顶层只负责 immutable config + module wiring；
- 重新审计 master-switch restart-bound 语义。

## 8. `LauncherGlassSession` 职责拆分

不改变现有视觉行为的前提下逐步拆出：

- PassBlur endpoint/rebind policy；
- scene/wallpaper generation；
- EGL/OES input；
- static/drag output registry；
- rotation settle；
- Prismal scene composition。

硬约束：

- zero-copy only；
- Dock continuous producer 不进入这一 shared Workspace 优化策略；
- Recents/HOME/Widget return 不显示错误 stale frame。

## 9. 描边阴影后续决定

foreground `DockStrokeRenderer` 已替代旧 overlay。后续二选一：

- 设计适配 foreground renderer 的新 stroke-shadow；或
- 正式标记该视觉能力 deprecated，同时保留历史配置 key 的 import/read 兼容。

不要把 1.x overlay 路径重新接回主线。

## 10. CI / 构建清理

- `actions/setup-java@v4` 升级到 v5；
- 检查 checkout/upload-artifact/gradle actions 的 Node 24 版本；
- artifact retention 与仓库最大值保持一致；
- Debug/Release 均继续走 optimization/R8；
- 高风险 transition/producer 变更保持 RED→GREEN contract test + 真机回归。

## 11. 回归矩阵维护

长期保持以下场景有明确回归：

- App↔Home；
- Widget↔App；
- Recents↔Home；
- folder open/close；
- drag；
- page scroll / edge clipping；
- rotation；
- unlock；
- wallpaper change；
- Workstation；
- Dock continuous update；
- Widget discovery old/new catalog compatibility。
