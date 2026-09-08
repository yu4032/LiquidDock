# LiquidDock TODO

当前主线为 **v2.2.1 / HyperOS 3.0.307+ / MiuiX 307 PassBlur + OES/GLES zero-copy**。旧 ScreenCapture / bitmap readback 架构仅保留在 `archive/1.x`，不再属于当前 TODO。

本文件只记录当前 `main` 尚未完成、仍需结构收口或真机验收的事项。已经进入 production 且有代码/测试依据的工作不继续以“待实现”形式保留。

当前技术债务清理的目标设计与第一阶段执行计划：

- [Technical-Debt Cleanup Design](docs/superpowers/specs/2026-09-07-technical-debt-cleanup-design.md)
- [Technical-Debt Cleanup Phase 1 Implementation Plan](docs/superpowers/plans/2026-09-07-technical-debt-cleanup-phase1.md)

## 1. Phase 1：确定性 ownership 清理

**状态：Tasks 1–5 已完成代码实现并通过 CI；最终真机矩阵待完成。**

当前代码事实：

- `WorkstationModeController` 已接管 mode、vendor-confirmed state、monotonic generation 与 normal-layout backup；初始化 2 秒 fallback 捕获 generation，vendor callback 或更新 transition 会使旧 callback 失效；
- `WidgetClassifier` 已集中 `ItemInfo.isWidget()` 主路径与 itemType 4/5/19 compatibility fallback；
- `WidgetSpecRegistry.DEFAULT` 是当前 1×1、2×1、2×2、4×2 span 白名单；
- `WidgetGridSizing` 已变为 stateless geometry/allocation helper，不再持有 process-global adaptation flag；
- `HomeGridWidgetAdaptationHook` 已接管 `setupLayoutParam()` Widget allocation 与 `onLayout()` 后 final-frame enforcement；
- `WidgetBackgroundRuleEngine` 已区分 `LOADED` / `MISSING_RESOURCE` / `PARSE_FAILED`，失败仍 fail-safe 到空规则集；`LauncherMamlBackgroundRuleExecutor` 只在 bundled load degradation 时发一次 `[DC][WidgetRules]` structured diagnostic；
- 本阶段没有修改 producer/EGL/OES/fresh-frame authority，也没有接管 MIUI placement/occupancy。

代码/CI 证据与最终设备矩阵统一记录在：

- [Phase 1 Verification](docs/superpowers/verification/2026-09-07-technical-debt-cleanup-phase1.md)

Phase 1 当前唯一关闭门是最终 production head 的真机矩阵。必须完成并记录：

- normal Launcher startup；
- Workstation enter / exit / quick re-enter；
- vendor callback 先于 delayed fallback，以及 stale delayed fallback；
- normal-layout backup/restore；
- HOME -> Recents -> HOME 连续 5 次；
- Recents-adjacent rotation；
- 1×1 / 2×1 / 2×2 / 4×2 Widget 横竖屏；
- Widget adaptation disabled；
- normal mode 无回归；
- normal bundled Widget-rule load 不出现 degradation warning。

在该矩阵完成前，不把 Phase 1 标记为完成，也不开始 EGL/OES/producer lifecycle extraction。

## 2. `MainHook` 收缩为 composition root

**状态：未完成；Phase 1 已迁出 Workstation ownership，后续继续收缩其它 feature owner。**

后续继续把 Dock/Grid/Glass/Workstation 的 feature-level runtime ownership 迁到已有 installer/controller/session/view。

原则：

- `MainHook` 只负责 immutable config、模块构造/安装、process-level wiring、真正全局 capability/logging；
- 不把 `MainHook` 改造成 service locator；
- 不增加 setter bag 或无语义 `Manager` / `Util`；
- master switch 的完整结构卸载仍是 restart-bound，不能用“视觉 ownership 已释放”冒充完整 uninstall。

Dock resize animator 等 state 只有在确认其真实 owner 后再迁移，不为了缩文件机械搬运。

## 3. `HomeGridHook` ownership 拆分

**状态：Widget frame adaptation 的真实 Hook ownership 已在 Phase 1 迁出；本体仍拥有其余 Grid runtime owner。**

`HomeGridWidgetAdaptationHook` 当前独立拥有：

- `CellLayout.setupLayoutParam()` 的 Widget allocation/frame 调整；
- `CellLayout.onLayout()` 之后的 Widget final-frame enforcement。

`HomeGridHook` 当前仍覆盖：

- cell count；
- orientation-specific geometry；
- page indicator；
- folder alignment；
- rotation/refresh；
- lazy/off-screen page preparation。

后续安全拆分顺序：

1. Page indicator；
2. Folder alignment；
3. Cell geometry；
4. Grid rotation / refresh（最后）。

目标仍是迁移真实 Hook 安装和 runtime ownership，不是继续增加只被 `HomeGridHook` 调用的薄 helper。

## 4. Launcher-wide glass / GPU ownership 审计

**状态：correctness authority 已较稳定，resource ownership 仍需审计。Phase 1 不修改该层。**

先建立 owner graph，再决定是否抽公共 primitive。必须逐项记录：

- PassBlur producer endpoint lifecycle / bind / release；
- EGLDisplay / EGLContext / EGLSurface owner；
- OES texture / `SurfaceTexture` owner；
- producer generation 与 scene/wallpaper generation 边界；
- static output / drag output registry；
- rotation settle / producer generation transition；
- shader/program/FBO/release helper；
- `LauncherGlassSession`、`Miuix307PassBlurTextureView`、`PrismalRenderer` 生命周期是否真正相同。

禁止预设“大一统 GlassEngine”。Dock/Launcher 生命周期不同的资源即使代码相似也保持分离。

必须保持：

- zero-copy only；
- HOME continuous/source-driven PassBlur；
- native PassBlur scale `1.0`；
- FPS gate 只限制昂贵 render，所有 source frame 仍 drain；
- Recents/HOME 不显示 stale frame；
- rotation settle 后才允许新 producer 发布；
- SystemUI unlock authority 不被 GPU 重构替换。

## 5. 测试架构债务

`RuntimeBehaviorTestPolicyContractTest` 已建立 default-deny source-reader gate，但 `LEGACY_SOURCE_DEBT` 当前仍有 13 个历史测试。

每个 debt test 只能进入以下三类之一：

1. 合法 static architecture/API contract；
2. 迁移为 production-used typed state/policy runtime test；
3. obsolete test，删除。

该 debt list 只能缩小，禁止新增例外。

当前 13 个：

- `GlassConfigGenerationContractTest.java`
- `HomeGridOrientationMemoryHookContractTest.java`
- `HomeGridProfileOverlayContractTest.java`
- `Miuix307EdgeOverscanContractTest.java`
- `PrismalModuleBoundaryContractTest.java`
- `PrismalOfficialParityV3Test.java`
- `RestartBoundSettingsContractTest.java`
- `WidgetBackgroundRankingUiContractTest.java`
- `WidgetComponentDiscoveryContractTest.java`
- `WidgetComponentSelectionContractTest.java`
- `WidgetMamlRenderTreeDiscoveryContractTest.java`
- `WorkspaceDropRuleHookContractTest.java`
- `WorkstationAllAppsHookContractTest.java`

## 6. CI / build / engineering hygiene

仍需：

- `actions/setup-java@v4` 升级到 `v5`；
- 检查 `checkout` / `upload-artifact` / `gradle/actions` 的 Node 24 兼容版本并清掉 Node 20 deprecation warning；
- workflow APK `retention-days: 7`、source `retention-days: 3` 与仓库实际 2 天上限统一；
- 修复 `MiuixLauncherStaticGlassHook.installMamlBackgroundOwnershipHook(...)` javac inexact-varargs warning，明确 `Class<?>[]` 展开语义；
- 把 Compose/settings 用户可见硬编码中文/英文迁移到 Android resources。

Debug 与 Release 已启用 Android optimization/R8，不重新打开该项。

## 已完成的重要边界（不要重复打开）

### 1.x capture architecture

旧 ScreenCapture / bitmap readback / capture cadence / screenshot-era `DockLiquidGlassView` 生命周期已经从当前主线删除，仅保留在 `archive/1.x`。不恢复任何 screenshot fallback。

### Workspace continuous / source-driven PassBlur

HOME shared producer 不再消费一帧后 pause。静态壁纸可以在 bound / updates-enabled 下没有新 OES frame；动态壁纸或真实 backdrop 更新由 source 驱动。没有 Choreographer/vsync pump 或固定轮询。

### Workspace quality / mapping

Native PassBlur 固定 `1.0`；`liquid_passblur_capture_scale` 只降低 OES normalization 之后的 local physical FBO；logical root 保持完整。`liquid_passblur_render_fps` 只限昂贵 render，所有 source frame 仍 drain，fresh generation 绕过限流。

### Workstation Recents producer correctness

已完成 covered authority gate、duplicate/non-covered hide rejection、stale `finishBind()` epoch invalidation、Workstation-only rebind 与 request/endpoint diagnostics。除非出现新的可复现现实失败，不新增 producer recovery episode state machine、terminal aggregate 或第二套 freshness authority。

### Unlock -> HOME authority

Launcher `PREPARE` 只提前 freeze；释放 authority 是 SystemUI `LOCKSCREEN -> GONE FINISHED TransitionStep`。之后 serial-protected producer rollover，失败 fail-closed；Workspace glass 仍等待匹配的新 scene generation / fresh frame。

### Dock stroke / stroke shadow

`DockStrokeRenderer` 是当前 foreground stroke owner。stroke shadow 已在当前 renderer/native MiShadow ownership 下实现；历史配置 key 继续兼容，但不再属于“未来实现或 deprecated”待决项。

### Widget component-selection customization

一次性 component discovery、RemoteViews/MAML 精确组件选择、claim/release、备份 format/version 与导入校验已经进入 production。除非出现明确产品需求，不新增任意脚本/DSL，也不把 runtime hot reload 作为当前 correctness 目标。
