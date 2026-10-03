# LiquidDock TODO FOR AGENT

当前主线：**v2.5.3 / HyperOS 3.0.307+ / Launcher 4.50 / libxposed API 101**。

本文件只记录当前生产代码仍存在的工程债务、兼容性风险和未完成收口。已经落地并通过真机/CI 验证的修复不再保留为 active TODO。

## P1 · Runtime performance hot paths

**状态：已完成静态热点审计，尚未系统优化。**

当前最高价值的性能债务集中在“稳定态仍按帧执行”的路径。原则是优先把 `O(frame)` 工作降为 `O(event)`，而不是简单加节流或固定延迟。

### P1-A · Launcher Workspace glass per-frame node scan

`LauncherGlassSession` 当前在 Launcher root 的 `OnPreDrawListener` 中每帧执行 `syncSceneOnUiThread()`。

稳定态仍会：

- 复制 drag/static node 集合；
- 遍历全部 Workspace glass node；
- 对 drag sink 执行 `syncFromMaterial()`；
- 对 static node 执行 `captureGeometry()`；
- 对每个 static node 做 ancestor visibility/alpha 检查；
- 调用 `transformMatrixToGlobal()`、矩阵 invert/map；
- 检查 root rotation；
- 调用 source backend `reconcileRoot()`。

图标/文件夹/Widget glass 数量增加时，UI 线程成本近似随 node 数量线性增长；在 120Hz 设备上尤其值得优化。

目标：

- 建立 explicit dirty-node / dirty-scene 模型；
- layout / attach / detach / visibility / scale / translation / drag / proxy 等真实事件只标记对应 node dirty；
- Workspace scroll 继续使用现有 scroll projection，而不是为每个 node 重算完整全局矩阵；
- 下一帧只更新 dirty node；
- 完全静止的 Workspace 应接近 `O(1)` UI-thread bookkeeping，而不是 `O(N)` node scan。

必须保持：

- HOME spring scaling；
- Workspace paging；
- drag/proxy geometry；
- icon/folder/widget visibility；
- current-page reconciliation；
- fresh generation / source authority；
- 不重新引入固定时间 capture pump。

### P1-B · Dock PassBlur geometry polling

`Miuix307PassBlurTextureView` 当前 root pre-draw 每帧：

1. `refreshProducerGeometryInPlace()`；
2. `updateBackdropMapping()`。

其中 `readSurfaceGeometry()` 会反复读取/反射 ViewRoot / Surface 状态，包括：

- `mSurfaceSize`；
- `getSurfaceControl()`；
- config rotation；

`updateBackdropMapping()` 还会：

- 读取 `mWinFrameInScreen`；
- `getLocationOnScreen()`；
- 重新计算 sampling insets / UV mapping；
- 调用 `DockGlassCompositor.refreshUiSceneIfNeeded()`。

即使最终判定 geometry 未变化，上述前置工作已经发生。

第一步低风险收口：

- 缓存稳定的 `Field/Method` 解析结果；
- 避免每帧重新 lookup / `setAccessible`；
- 只在 ViewRoot identity 变化时重新解析。

最终目标：

- surface/root replacement；
- rotation；
- size/layout；
- Dock geometry/reflow；
- window-frame change

这些真实事件标记 geometry dirty，再在下一帧统一 reconcile。

禁止用轮询间隔或 fixed-delay 代替真实 geometry authority。

### P1-C · GL program location caching

当前多个 production session 在 render loop 中重复调用：

- `glGetAttribLocation(program, "aPosition")`；
- `glGetAttribLocation(program, "aUv")`；
- `glGetUniformLocation(program, "uTexture")`；
- `glGetUniformLocation(program, "uCropRect")`；
- 其它 normalize/composite uniform 查询。

受影响路径包括但不限于：

- `LauncherGlassSession`；
- `RootPassBlurBackend`；
- `Miuix307PassBlurTextureView`；
- `ShortcutPopupGlassSession`；
- `GboardFloatingGlassSession`；
- `MiuiSearchboxGlassSession`；
- `RecentsCapsuleGlassSession`；
- `SecurityCenterGlassSession`；
- `SystemUiHandleMenuPrismalSession`。

目标：

- program link 成功后一次性解析 attribute/uniform location；
- program 生命周期内直接复用 cached int；
- draw loop 禁止重新 `glGet*Location`；
- 后续结合公共 GL primitive 收口 `compileShader/createProgram/bindQuad/unbindQuad` 重复。

这是低风险、高确定性的优化，优先于大规模 GL 架构重写。

### P1-D · DockGlassCompositor stable-frame allocations

`DockGlassCompositor.refreshUiSceneIfNeeded()` 当前即使最终场景未变化，也可能先：

- 按 icon 数量分配 `long[] uiFingerprints`；
- 分配 `long[] proxyFingerprints`；
- 分配 `DockIconAnimationState.Sample[]`；
- 遍历全部 Dock item；
- 每个 item 计算 parent-chain UI fingerprint；
- 构造临时输出 `ArrayList`。

目标：

- 充分利用 `DockGlassItemRegistry.revision()`；
- 如果 registry revision、output geometry、workstation state 均未变化且没有 active icon animation/proxy，直接 stable fast-path return；
- scratch arrays / sample storage 尽量复用；
- 只有真正 dirty 的 item 重新 capture geometry；
- 稳定 Dock 不应持续制造短命数组/列表对象。

必须保持：

- Dock resize/recenter；
- icon add/remove/reorder；
- drag/floating proxy；
- opacity animation；
- workstation radius；
- output-root transform。

---

## P2 · Secondary performance cleanup

**状态：未完成。**

这些路径目前不是首要瓶颈，但可以在 P1 hot paths 收口后继续处理。

### Launcher Dialog material guard

`LauncherDialogGlassCoordinator` 仍通过 pre-draw 检查：

- dialog dim；
- panel background；
- transparent clone alpha；
- pass-window blur gate。

后续目标参考已经完成的 Launcher HotSeats / Gboard ownership 模式：

- vendor setter/write boundary interception；
- claimed 期间记录 latest vendor intent；
- release 时 replay；
- 尽量移除 material-state pre-draw reassertion。

Dialog 生命周期短，因此优先级低于 Workspace/Dock。

### Geometry-only pre-draw observers

继续评估以下 pre-draw 是否可缩为“仅动画/移动期间启用”或真实 layout/translation dirty event：

- Gboard floating geometry；
- Shortcut popup geometry；
- Searchbox geometry；
- Recents capsule geometry；
- page indicator translation guard。

不要为了消灭 `OnPreDrawListener` 本身而牺牲实时跟随；只有在真实事件足够完整时才替换。

### Debug logging I/O

`MainHook.log()` 在 debug logging 开启时不仅输出 API/logcat，还同步：

- 创建时间格式对象；
- open file；
- append write；
- close file。

高频动画/producer/trace 日志会明显污染性能测量。

目标：

- 默认行为保持 debug off；
- 如需长期文件日志，改为独立线程 + buffered writer / batched flush；
- per-frame trace 只在显式诊断模式启用；
- 性能测试时不能让同步文件日志成为主要干扰源。

### Remove historical production trace

`DockAnimationTrace` 仍是生产源码中的短期诊断设施，触发时会：

- 安装 pre-draw listener；
- 调用反射/语义查询；
- 构造大量状态字符串；
- 进入 debug logging。

若当前 Dock handoff 问题已经不再需要该 trace，应删除或迁为显式 debug-only instrumentation。

---

## P1 · Workstation composite runtime restore

**状态：未完成。**

单个 Workstation visual owner 已经可以独立恢复，但完整 Workstation structure 仍跨越：

- Dock width / geometry；
- Dock icon top/bottom offset；
- Dock icon glass radius；
- Workspace Grid；
- All Apps geometry；
- Divider；
- Recents producer recovery；
- wallpaper / rotation freshness；
- normal-layout backup / restore。

如果未来要支持完整热切换，需要定义一个可验证的 composite restore transaction，而不是逐个 toggle 子模块。

必须保持：

- 普通桌面无回归；
- retired producer 不重新冒充 fresh source；
- Recents/HOME 仍经过 fresh-frame authority；
- Divider 等独立 owner 的 snapshot/restore 不被覆盖。

---

## P1 · WidgetGridSizing state and widget classification

**状态：部分完成。**

`WidgetGridSizing` 当前仍有 process-static：

```java
private static volatile boolean widgetAdaptationEnabled;
```

并且只显式识别：

- 1×1；
- 2×1；
- 2×2；
- 4×2。

后续目标：

- 把 adaptation enable 交给明确的 install/runtime owner；
- 收口 widget type 判定；
- 建立集中式 Widget spec registry；
- 让 `WidgetGridSizing` 只保留纯 geometry/allocation 计算。

不变量：

- 只调整最终 allocation/frame；
- MIUI placement / occupancy 继续权威；
- 不重新接管 occupied matrix。

---

## P1 · Expanded integration compatibility corpus

**状态：功能已上线，跨版本覆盖仍需要真实样本。**

v2.5.1 已经同时覆盖：

- SystemUI app-caption menu；
- Security Center sidebar；
- Gboard floating keyboard / toolbar；
- MIUI Searchbox；
- Launcher uninstall/remove dialogs。

这些路径都依赖外部应用/系统组件的真实结构。后续需要积累不同版本的反编译与真机 contract corpus。

### SystemUI

验证：

- MIUI caption-menu path；
- AOSP/WMShell handle-menu path；
- open/close surface animation；
- callback failure isolation；
- stock fallback。

### Security Center

验证：

- Game；
- Video；
- Global Dock；
- All Apps；
- root replacement；
- carrier/material epoch；
- terminal cleanup。

保持 ambiguity -> reject，不增加混淆名表或 version whitelist。

### Gboard

验证：

- floating geometry；
- toolbar/capsule；
- handle drag；
- non-floating keyboard 不误触发；
- vendor update 后结构识别。

### MIUI Search

验证：

- SearchActivity 主背景；
- day/night；
- resume；
- background view replacement；
- search box 本体不被误改。

---

## P1 · RootPassBlur / session ownership audit

**状态：已经形成共享基础层，但 feature session 数量继续增加。**

当前 domain 包括 Launcher Workspace、Dock、ShortcutMenu、Drag、Dialog、Recents capsule、SystemUI handle menu、Security Center、Gboard、Searchbox。

继续审计：

- endpoint bind / release / rollover；
- Surface / SurfaceTexture ownership；
- EGLDisplay / EGLContext / EGLSurface ownership；
- OES texture lifecycle；
- root replacement；
- terminal failure；
- source freshness；
- output cleanup。

不要预设需要“大一统 GlassEngine”。只有生命周期真的相同的代码才抽成公共 primitive。

必须保持：

- active backdrop 不回退 screenshot；
- source drain 不被 render FPS cap 阻塞；
- fresh generation 可以越过普通 throttle；
- feature-specific geometry/presentation failure 不污染其他 domain。

---

## P1 · Signed-build / R8 compatibility regression coverage

v2.5.1 已经出现并修复两个真实的跨 ClassLoader R8 问题：

1. Launcher RecyclerView signature；
2. Dialog native-night AppCompatDialog class-string 被改写成 `v9`。

后续目标：

- 扫描新 `Class.forName` / exact signature call；
- 检查模块是否也打包了同名类；
- 优先使用目标进程参数语义、继承关系、资源关系；
- 必须保留二进制名时仅增加 targeted `-keepnames`；
- 对高风险 path 增加 signed/release smoke test 指南或自动检查。

不允许用 LiquidDock 整包 keep 掩盖问题。

---

## P2 · Third-party profile configuration ownership

Gboard 已有 `ConfigSchema.Gboard` 与 shared profile bridge；MIUI Search 仍由 `MiuiSearchboxGlassPreferences` 管理公开设置。

当前行为有效，但后续可评估：

- 是否把公开第三方 adapter key 统一纳入一个明确 registry/schema；
- JSON 导入导出如何表达第三方 adapter profile；
- profile default / public UI key / hidden shared-profile key 的 ownership 是否足够清晰；
- 不破坏已有备份和现有用户值。

这不是行为 bug，不要为了“统一”贸然迁移 key。

---

## P2 · Widget component discovery diagnostics

Widget component hiding 已进入生产，但 discovery/parser 的降级信息仍可继续收紧。

目标：

- required bundled resource 缺失与 optional parse failure 分开；
- 真实 degradation 输出 one-shot structured diagnostic；
- 不产生 per-frame/per-bind 日志；
- selector 失败不产生部分 mutation；
- backup/import 失败不覆盖已有有效规则。

不扩展成任意脚本/方法调用 DSL。

---

## P2 · ShortcutMenu dark-mode persisted naming

当前 persisted key：

```text
liquid_shortcut_popup_dark_text
```

实际功能已覆盖“文字 + 适合处理的近黑图标”。

为兼容旧配置暂时保留旧 key 名。

若未来重命名：

1. 新 schema key；
2. migration；
3. import/export alias；
4. 至少一个兼容周期；
5. 再删除旧 key。

---

## P2 · Test architecture debt

`RuntimeBehaviorTestPolicyContractTest` 已建立 production-source-reader default deny。

当前 `LEGACY_SOURCE_DEBT` 仍为 11 项：

- `GlassConfigGenerationContractTest.java`
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

逐项处理：

- 真正静态 contract -> audited allowlist；
- runtime behavior -> typed state/policy test；
- obsolete -> 删除。

该列表只能减少。

---

## P2 · Settings i18n

Compose 设置页仍有大量硬编码中文用户字符串，包括近期新增：

- dialog；
- SystemUI handle menu；
- Gboard；
- Searchbox；
- widget component pages；
- highlight pages。

目标：

- 迁入 Android string resources；
- 中英文术语统一；
- 不在 i18n PR 中改变行为、key 或 default。

---

## P2 · Documentation automation

本轮已经重新同步根文档与当前 main，并把历史 superpowers 文档标记为归档。

后续可以增加轻量 CI：

- 检查根文档版本号与 Gradle `versionName`；
- 检查 README scope 与 `META-INF/xposed/scope.list`；
- 检查内部相对链接；
- 检查历史 docs 是否带 archive banner；
- 检查 active docs 是否仍引用已删除生产类。

不要让文档 CI 变成脆弱的逐字字符串快照。

---

# 已完成并只需防回归

## MainHook composition refactor

`MainHook` 已经收缩为安装顺序/composition root，不再是旧文档中的大规模 feature state 容器。

## HomeGrid split

旧 profile/count/centering/vertical-bounds 栈已删除。当前 HomeGrid 由 GridController authority、动态 dimensions、统一 cell geometry、orientation memory/mutation、通用 rotation planner、drop/drag bounds 等 owner/policy 组成；所有 2×2～10×6 配置共用同一路径。

## Single default configuration

双预设/归零默认已经移除。当前只保留一套内置默认配置；首次空 store seed，升级不覆盖已有配置。

Security Center、SystemUI handle-menu 与 debug log 默认明确关闭。

## Launcher dialog glass and native dark mode

卸载/移除/二次确认 glass 已进入主线；native dark mode 的 R8 class-string 问题已修复为 `Context + Dialog + Window` constructor semantics。

## ShortcutMenu Dock coverage

Workspace、Home-forwarded Dock 和非桌面 direct Dock 均有 pre-show capture 路径；`ACTION_CANCEL` 与 Dock owner churn 的真实问题已经修复。

## Workspace source-driven glass

普通 HOME 不依赖固定 capture pump；freshness、wallpaper generation、producer generation 已分离。

## Launcher live drag

真实 DragView + upper overlay + live source 已进入生产，不再使用 frozen drag-start backdrop。

## Widget component hiding

Discovery、用户选择、可恢复 mutation、backup/import 已进入生产。

## Recents capsule glass

Clear All 与设备互联按钮已经有独立玻璃替换和 stock fallback。

## Gboard / MIUI Search adapters

两者已通过独立第三方 adapter registry 进入生产，并有各自设置页面与 fail-safe fallback。
