# LiquidDock TODO

当前主线为 **v2.2.1 / HyperOS 3.0.307+ / MiuiX 307 PassBlur + OES/GLES zero-copy**。旧 ScreenCapture / bitmap readback 架构仅保留在 `archive/1.x`，不再作为当前 TODO。

本文件只记录当前 `main` 尚未完成、仍需结构收口或仍需真机验收的事项。已经进入 production 且有代码/测试依据的工作不继续以“待实现”形式保留；已完成的重要边界记录在文末，避免后续审计重复打开已经关闭的问题。

## 1. Workstation / Laptop：剩余 ownership 与真机验收

**状态：Recents shared producer correctness 的最小修复已经落地；完整 Workstation 适配仍未完成。**

当前 production 已经具备：

- `LauncherGlassSceneController.vendorRecentsCovered` 作为 Recents covered authority；
- duplicate / non-covered `onRecentViewHide` 不再触发 Workstation producer rollover；
- `LauncherGlassSession.workstationBindEpoch` 拒绝 rollover 前排队的 stale `finishBind()`；
- Workstation-only `rebindWorkstationProducer(...)`，不改 unlock / rotation / generic rebind 路径；
- request 与 endpoint recreation 的 structured diagnostics；
- endpoint recreation 不直接授权 reveal，仍由现有 scene generation / fresh OES frame barrier 决定何时显示。

除非出现新的、可复现的现实失败路径，**不要重新引入**单独的 producer recovery episode state machine、terminal multi-session aggregate 或另一套 fresh-frame authority。

剩余工作：

- 拆出单一 `WorkstationModeController`，接管 mode probe、vendor callback、normal layout backup/restore、transition generation/cancellation；
- `MainHook` 不再直接持有 Workstation mutable state；
- 审计并约束现有 delayed restore/recheck callback，使 stale callback 具备 generation/cancellation 保护；
- 真机完成以下矩阵：
  - 进入/退出工作台；
  - 普通桌面位置 backup/restore；
  - Dock 宽度、图标 spacing/offset；
  - All Apps 横竖屏；
  - Recents 连续往返；
  - Recents 附近旋转；
  - PassBlur/OES producer suspend/rebind/fresh-frame；
  - wallpaper freshness；
  - Liquid Glass suspension/recovery；
  - 普通模式无回归。

结构性 Workstation 配置保持 restart-bound，除非未来建立完整、可逆的 runtime restore 路径。

## 2. Widget classification / span extensibility

**状态：部分完成。legacy `HomeGridHook.adaptTwoByOneWidget(...)` 已不在 production；统一 classifier/registry 仍未落地。**

剩余工作：

- 移除 `WidgetGridSizing` 的 static `widgetAdaptationEnabled` 全局状态，由安装模块持有 immutable config；
- 引入 `WidgetClassifier`，集中现有 `ItemInfo.isWidget()` 主路径、`itemType` fallback 与未来 HyperOS 变体；
- 从 `HomeGridHook` 删除散落的 `itemType == 4 || itemType == 5 || itemType == 19` 判断；
- 引入 `WidgetSpecRegistry`，当前登记 1×1、2×1、2×2、4×2，使新增 span 不再修改核心 Hook control flow；
- `WidgetGridSizing` 只保留纯 geometry / allocation 计算。

必须保持：

- Widget adaptation 只修改 pixel allocation/frame；
- 不 Hook `addOccupied()` / `transformToHVArray()`；
- 不接管 MIUI occupied matrix / placement authority。

## 3. `HomeGridHook` ownership 拆分

**状态：已有 orientation memory、profile overlay、drag/bounds 等辅助 Hook/policy，但 `HomeGridHook` 本体仍同时承担多个 runtime owner。**

当前 `HomeGridHook` 仍覆盖 cell count、orientation-specific geometry、Widget frame adaptation、page indicator、folder alignment、rotation/refresh 与 lazy/off-screen page preparation。后续按低风险到高风险继续收口：

1. Widget adaptation；
2. Page indicator；
3. Folder alignment；
4. Cell geometry；
5. Grid rotation / refresh（最后拆）。

不要重复抽已经存在的 pure policy/helper；目标是迁移真实 runtime ownership，而不是机械增加类数量。

必须保持：

- 当前横竖屏布局行为与 orientation-specific memory；
- lazy/off-screen page 几何准备；
- `LayoutTransformRuleGridChanged` metadata；
- MIUI native rotation/occupancy transform authority；
- Workspace drop 行为。

## 4. `MainHook` 收缩为 composition root

**状态：未完成。已有部分独立 Hook/controller，但 `MainHook` 仍直接拥有 feature-level mutable state 与大量安装/恢复逻辑。**

剩余工作：

- 把 Dock/Grid/Glass/Workstation 的剩余 runtime ownership 迁到对应 installer/controller/session/view；
- 移走 `workstationMode`、`workstationModeHookConfirmed`、normal layout backup、Dock resize animator 等 feature-level mutable state；
- 顶层只负责读取 immutable config、构造/安装模块、process-level wiring 与真正全局的 capability/logging；
- 不把 `MainHook` 变成 service locator，不增加 setter bag 或无语义的 `Manager`/`Util`；
- 重新审计 master-switch 边界：完整卸载结构 Hook 仍是 restart-bound，不允许用“视觉 owner 已释放”冒充完整 runtime uninstall。

## 5. Launcher-wide glass / GPU ownership 收敛

**状态：部分完成。`LauncherGlassSession` / `LauncherGlassSessionRegistry` / `LauncherGlassSceneController` 与若干纯 transition/freshness policy 已经形成边界，但底层 producer/EGL/OES/renderer ownership 仍需审计。**

后续先审计 owner graph，再决定是否抽公共 primitive；不要预设需要“大一统 GlassEngine”。重点检查：

- PassBlur producer endpoint lifecycle / bind / release；
- EGLDisplay / EGLContext / EGLSurface ownership；
- OES texture / `SurfaceTexture` ownership；
- producer generation 与 scene/wallpaper generation 的边界；
- static output / drag output registry；
- rotation settle / producer generation transition；
- shader/program/FBO/release helper 是否存在真正相同的低层生命周期；
- `LauncherGlassSession`、`Miuix307PassBlurTextureView`、`PrismalRenderer` 之间哪些重复应共享，哪些因 Dock/Launcher 生命周期不同必须保留。

必须保持：

- zero-copy only，不恢复 ScreenCapture / PixelCopy / bitmap readback；
- Recents / HOME 不显示 stale frame；
- Workstation producer pulse/pause 与现有最小 recovery semantics；
- rotation settle 后才允许新 producer 发布；
- wallpaper/scene freshness generation 继续作为内容权威；
- SystemUI unlock authority 不因 GPU 重构被替换。

## 6. CI / build / engineering hygiene

### CI / build

仍需：

- `actions/setup-java@v4` 升级到 `v5`；
- 检查 `checkout` / `upload-artifact` / `gradle/actions` 的 Node 24 兼容版本并清掉 Node 20 deprecation warning；
- workflow 中 APK `retention-days: 7`、source `retention-days: 3` 与仓库实际 2 天上限统一，避免服务端自动降级 warning；
- 修复 `MiuixLauncherStaticGlassHook.installMamlBackgroundOwnershipHook(...)` 的 javac inexact-varargs warning，明确 `Class<?>[]` 的展开语义，不能只 suppress warning。

Debug 与 Release 当前都已经启用 Android optimization/R8，不再作为待实现项；后续只需保持这一 gate。

### 测试架构债务

`RuntimeBehaviorTestPolicyContractTest` 已经建立 default-deny source-reader gate，但 `LEGACY_SOURCE_DEBT` 仍有 13 个历史测试。后续逐项分类为：

- 合法 static architecture/API contract；
- 可迁移到 production-used typed state/policy 的 runtime behavior；
- obsolete test。

该 debt list 只能缩小，禁止为了 CI green 新增例外。

### Diagnostics

`WidgetBackgroundRuleEngine.loadBundled()` / `parse()` 当前仍会在 bundled resource 缺失或解析异常时静默退化为 `EMPTY`。需要区分 optional parser hardening 与 required bundled-rule failure，并为真实 silent degradation 增加 one-shot structured diagnostic；不要制造 log spam。

### i18n

Compose/settings 仍存在用户可见硬编码中文/英文字符串。迁移到 Android resources，不改变 preference key、schema 或行为。

## 已完成的重要边界（不再作为 active TODO）

### Workstation Recents producer 最小 correctness

已完成 covered authority gate、duplicate/non-covered hide rejection、stale `finishBind()` epoch invalidation、Workstation-only rebind 与 request/endpoint diagnostics。现有 scene-generation / fresh-frame barrier 保持唯一 reveal authority。

### Unlock -> HOME wallpaper / capture authority

已通过 SystemUI authority 收口：Launcher `PREPARE` 只负责提前 freeze；释放 authority 是 SystemUI `LOCKSCREEN -> GONE FINISHED TransitionStep`。之后经过 serial-protected producer rollover，失败 fail closed；Workspace glass 仍等待匹配的新 scene generation / fresh frame 才显示。该问题不再标记为“已知未完全解决”，后续重构只需防止回归。

### Dock stroke shadow

foreground `DockStrokeRenderer` 已经是当前描边 owner；stroke shadow 已在现有 renderer/native MiShadow ownership 下实现，不再需要“实现或 deprecated”二选一。保留历史配置 key 的兼容读取。

### Widget 组件隐藏自定义

旧的“未来增加用户可编辑 widget rule DSL”已经由更窄、更安全的 component-selection 方案替代并进入 production：

- 用户手动触发一次性 widget component discovery；
- RemoteViews / MAML 组件按精确 owner/path/class/name 选择；
- 设置页逐组件启用/禁用隐藏；
- mutation 可 release 并恢复原状态；
- bundled compatibility rules 与 user selection 有明确 claim/release 顺序；
- 独立备份使用 format/version，并在导入时只读取已知字段、校验 selector 后原子覆盖当前选择；
- 默认仍要求重启 Launcher 应用选择，不开放任意脚本、方法调用或通用表达式入口。

除非未来出现明确产品需求，不新增自由规则脚本/DSL，也不把 runtime hot reload 作为当前 correctness 目标。
