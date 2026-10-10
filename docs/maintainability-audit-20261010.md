# LiquidDock 可维护性审计与重构边界（2026-10-10）

> **类型：静态源码审计记录，不是性能报告或功能验收报告。**
> 
> 基线：`yu4032/LiquidDock` `main@67d596d9463b96614ae122bd34ea3e1762bfb425`；`build.gradle.kts` 当前 `versionName=2.6.4`，不是“v2.6.4 已发布”的证明。2026-10-10 读取 Git 树与部分核心源码；未运行 CI、Lint、静态分析器或设备测试。当前任务清单以根目录 [TODO.md](../TODO.md) 为准。

## 1. 审计方法及证据级别

- GitHub recursive tree 统计生产文件规模，再对部分最大/关键类读取源码、字段、方法、异常处理、测试；**这不等于已逐方法检查整个项目**。
- “源码已证实”仅表明该实现目前存在，不意味着实际生效、性能改善或厂商系统兼容性已经通过真机验证。
- 不把复杂、较长、使用反射或第三方适配代码直接认定为 AI 生成；所谓“vibe coding 痕迹”在本文中指可复现的维护风险（职责膨胀、重复实现、状态生命周期不明、测试锁死实现细节）。
- 此审计不提供准确的 cyclomatic complexity、覆盖率、内存峰值或 FPS 值；它们需要独立工具/测量。

## 2. 规模与高风险文件

生产主模块包含 310 个 Java/Kotlin 源码文件，其中 Java 根包 `src/main/java/com/hellovoid/liquiddock/` 直接容纳 283 个文件；`src/main/java/com/hellovoid/liquiddock/config/` 有 9 个。根包过于扁平会削弱静态模块边界，但**不要为了形成漂亮目录树进行大批量搬迁**。

| 文件 | 源码行数（静态读取） | 主要职责交叉 | 建议 |
| --- | ---: | --- | --- |
| [ComposeSettingsActivity.kt](../src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt) | 2,725 | 页面导航、配置规格、设置保存、网格异步校验、Compose 页面 | 首先按页面职责拆分，保留两个 UI 作用域与原行为 |
| [Miuix307PassBlurTextureView.java](../src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java) | 2,086 | producer、EGL/Surface、mapping、frame queue、恢复 | 首先把映射快照和资源所有权显式化 |
| [LauncherGlassSession.java](../src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java) | 1,436 | source/frame、静态/拖拽输出、几何、transition、恢复 | 分离观测与渲染决策前先冻结 freshness 契约 |
| [ModernSettingsUi.kt](../src/main/kotlin/com/hellovoid/liquiddock/ModernSettingsUi.kt) | 1,299 | Scaffold、Prismal surface、控件、Dialog | 通过组件边界拆分，保留触摸采样与原生外观 |
| [Launcher450SideSlideHoldHook.java](../src/main/java/com/hellovoid/liquiddock/Launcher450SideSlideHoldHook.java) | 1,219 | vendor Hook、触摸、侧边确认、handoff、generation | 将手势状态转换与重置规则测试化 |
| [RootPassBlurBackend.java](../src/main/java/com/hellovoid/liquiddock/RootPassBlurBackend.java) | 1,000 | producer binding、EGL、帧调度、错误清理 | 只提取真正共享的无状态 GL primitive |

与上述不同，[ConfigSchema.java](../src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java) 虽接近 800 行，主要是声明式配置，不应该按超大业务类处理。当前源码扫描识别到 **225 项已声明 `ConfigKey`，225 项恰好一次注册于 `all()`**（仅此注册表的静态核对，不保证全部键有真实 GUI/Hook 消费者）。

## 3. 2026-10-10 确认的旧 TODO 偏差

| 旧表述 | 核验结果 | 处理 |
| --- | --- | --- |
| “当前主线是 v2.6.1，更新于 10-03” | 构建源码已为 `versionName=2.6.4` | 更新文档基线，不推断 Release 现状 |
| “每个生产 Render Session 在每帧查询 uniform/attribute location” | 九条相关 Session 的源码目前在 program 初始化时获取并缓存这些位置 | 移出活动性能任务，保留 GL helper 去重 |
| “DockGlassCompositor 每帧重新创建 fingerprint 数组和临时列表” | `uiFingerprintScratch`、`proxyFingerprintScratch`、`animationSampleScratch`、`sceneItemScratch` 已复用；但仍每次计算 item 指纹 | 任务改为稳定态扫描与 dirty fast path，保留性能测量门槛 |
| “WidgetGridSizing 只有 1×1、2×1、2×2、4×2 白名单，使用 widgetAdaptationEnabled” | 实际是 `customGridEnabled`；`isSupportedSpec` 接受正跨度；`gridRect` 用有效的 `xs/ys` 范围检查 | 更正源事实，只保留 owner 生命周期审计 |
| “LEGACY_SOURCE_DEBT 为 10 项” | `RuntimeBehaviorTestPolicyContractTest` 中确有 11 项；旧清单多了 `WorkspaceDropRuleHookContractTest`，少了 `PrismalCompositeHotPathContractTest` | 与真实 allowlist/debt 集合同步 |
| “GUI 隐藏项即死代码” | 工作台 Dock 长度与下偏移仍存在 Schema、配置读入、runtime Hook 路径 | 保留兼容与真实消费者，先测试再清理 |

## 4. 风险分级

**P1 / 可控优先**：拆分 GUI 页面文件与规格/控件，但保持视觉、交互、配置键、热更新和页面路由；补充 typed policy/state 行为测试，不用源码字符串验证运行效果。

**P1 / 需要测量**：`LauncherGlassSession.syncSceneOnUiThreadInternal()` 的 pre-draw node scan、`Miuix307PassBlurTextureView.updateBackdropMapping()` 的几何采样、`DockGlassCompositor.refreshUiSceneIfNeeded()` 的稳定帧指纹扫描。目标是准确的事件驱动更新，而非固定延迟或伪造 freshness。

**P2 / 条件具备后再整理**：`Launcher450SideSlideHoldHook.GestureState` 的状态转换、原子重置、vendor handoff；多个 Session 的 `compileShader/createProgram/bindQuad/unbindQuad` 等 **无状态 helper**；配置监听器管理与分包边界。

**P3 / 高回归风险**：PassBlur EGL 资源 owner、producer/recovery 的大幅重新组织；跨 Session 的共享引擎；根包 Java 类大批量移动。这些必须先有生命周期契约、R8/classloader 审查与设备级回归。

## 5. 现有工程约束（不可因“精简”删除）

- `MainHook` 已主要充当 composition root；`HomeGrid` 已拆分多个 owner/policy，避免重新集中。
- GUI 两个 UI 作用域是有意分离的设计；Prismal 的顶/底栏、冻结玻璃 cell、触摸激活的控件采样、数值输入、按钮反馈与现有界面行为必须保留。
- 渲染不得退回 ScreenCapture/PixelCopy/Bitmap 背景捕获。保持 source / producer / wallpaper / scene generation 的边界、帧同步租约、旋转与过期帧拒绝。
- Hook 安装顺序、跨 ClassLoader 反射语义、R8 keep 约束和 fail-closed/原生 fallback 不得因目录调整而更改。
- 设置键必须遵循 `ConfigSchema → ConfigReader / LiquidDockConfig → runtime consumer`，同时检验 Preset、Codec、Migration、JSON import/export。GUI 不可见不等于可安全删除。
- `ModernSettingsArchitectureTest` 包含大量 `source.contains(...)` 静态断言；将其作为架构约束而非行为通过证据。保留 `RuntimeBehaviorTestPolicyContractTest` 对新增源码测试的限制。
- 环境不匹配时，不把 CI 绿灯等同于 HyperOS 目标设备验证。

## 6. 每批重构的完成标准

1. 每个 PR 聚焦一个职责边界，并附基线、修改前后调用/状态表与不可变化的用户行为清单。
2. 配置键名、默认值、可见控件、页间导航、原始 Prismal 光学与动画、Hot Hook class-loader 入口未发生无意变化。
3. 至少 `./gradlew testDebugUnitTest assembleDebug --stacktrace` 与现有安全门禁通过；若未运行则明确标记“未验证”。
4. 渲染、手势、Launcher、SystemUI、SecurityCenter、Gboard、Searchbox 相关 PR 根据实际影响补真机回归；日志需按开关控制，不通过 debug 文件 I/O 伪造性能结果。
5. 完成后同步更新 `TODO.md`：将已验证子项移至“完成 / 防回归”，保留尚未测量与未验证的风险。不得删除历史 evidence 文档。

## 7. GUI 结构重构进度（2026-10-10）

以 GitHub 合并状态、源码及验收反馈为准，不把文件迁移视为渲染性能优化。

已合并至 `main`：

- [#309](https://github.com/yu4032/LiquidDock/pull/309)：参数规格抽离到 `SettingsOptionSpecs.kt`，原 Activity 2725 → 2312 行；CI 通过、用户确认有效。
- [#310](https://github.com/yu4032/LiquidDock/pull/310)：Animation 五个叶页面单独成文件，2312 → 2166 行；CI 通过、用户确认有效。
- [#311](https://github.com/yu4032/LiquidDock/pull/311)：Dock、Divider、Workstation、Recents 七个页面独立，2166 → 1902 行；CI 通过，已合并。
- [#312](https://github.com/yu4032/LiquidDock/pull/312)：Grid 五个页面独立；**同时包含之后补充的滑条连续拖动、预览最近合法精度、松手吸附再提交的行为修复**，所以不能将整个 PR 声称为纯结构性调整。当前合并后主 Activity 为 **1646 行**（最初从 Grid 迁移时的 1630 行是中间快照）。

第五批 [PR #313](https://github.com/yu4032/LiquidDock/pull/313) 已合并：十个 Glass 页面迁出到 `GlassSettingsPages.kt`，原 Activity **1646 → 1198** 行。此批仅移动文件和调整必要声明可见性，CI 已通过。

独立修复 [PR #314](https://github.com/yu4032/LiquidDock/pull/314)（CI 通过、冲突整合后已合并；仍待实机验证）：Prismal 滑块连续拖动、松手弹簧吸附不变；右侧数值跨过合法整数、0.1 或离散档位即保存，重复档位去重；Miuix 滑条同样实时写入。Grid 风险尺寸先经过 4×2 widget 预检，失败不保存。**这是行为变更**，取代此前“拖动仅预览、回弹结束保存”的旧契约；快速连续拖动、阻断回滚、热更新延迟仍需真机测试。


**第六批（进行中、未合并）**：`refactor/gui-auxiliary-pages-20261010` 以整合后的 `main@7be4f71` 为基线。仅将 `StrokePage`、`ShadowPage` 迁入 `DockDecorationSettingsPages.kt`，并将 `SecurityCenterSidebarPage`、`DataPage`、`AboutPage` 连同私有 URL 打开和默认配置恢复 helper 迁入 `SettingsUtilityPages.kt`。主 Activity **1209 → 987 行**；5 个 Composable 和相关 ConfigSchema 引用均完整保留。数据备份、默认重置红色按钮、侧滑设置及诊断日志功能不应改变。独立 CI 与设备验收是合并门槛。

**第七批（本分支 CI 已通过，仍待实机验收与合并）**：`refactor/gui-navigation-controls-extraction-20261010` 在 #315 分支 `1b040bd` 上继续按职责拆分：

- `SettingsNavigationModel.kt` 拥有原 `Page` 枚举、`ROOT_PAGES`、`isRootPage`、`HubEntry` 和所有 hub entry list；必要的 `private → internal` 仅改变可见性，保持常量、资源 ID、入口排列和 root 判定完全一致。
- `SettingsControls.kt` 拥有原 `LocalSettingsPreferenceRevisions` composition local 与 `groupedIntSettings`、`DenseSettingsList`、`SettingsList`、`PageHeader`、`SettingsCard`、`BooleanSetting`、`IntSetting`。移动的控制逻辑逐字保留，包括 #314 的右侧数值实时写入、去重、Grid preflight callback；没有重写 Prismal 滑条或控件。
- `ComposeSettingsActivity.kt` **984 → 536 行**，保留 Activity 生命周期、单一 `SharedPreferences` 监听器、Miuix theme、滚动容器、重启作用域、页面路由、返回栈和主菜单页面。移出的文本不算删除功能；13 个 Composable 和 8 处 `ConfigSchema` 引用在三个文件的合集完全保留。
- 测试契约继续核对导航结构、持久化键来源、热更新 listener 以及每档实时写入。[CI #38036812829](https://github.com/yu4032/LiquidDock/actions/runs/38036812829) 已通过完整 unit tests、Debug APK 构建和安全检查。**这仍不等同于真机手势/功能验收**；不能以源码 `contains` 测试替代设备验证。

风险界限：GUI 的文件拆分不触及 PassBlur/EGL、渲染 producer、LSPosed Hook。所有编译和静态检查结果必须与实机验收区别记录，完成后更新 TODO 状态。
