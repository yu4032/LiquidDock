# LiquidDock TODO FOR AGENT

当前主线：**v2.5.0 / HyperOS 3.0.307+ / Launcher 4.50 / libxposed API 101**。

本文件只记录当前生产代码仍存在的工程债务、兼容性风险和未完成收口。已经落地并通过真机/CI 验证的修复不再保留为 active TODO。

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

v2.5.0 已经同时覆盖：

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

v2.5.0 已经出现并修复两个真实的跨 ClassLoader R8 问题：

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

当前 `LEGACY_SOURCE_DEBT` 仍为 13 项：

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

Profile、orientation、mutation、count、centering、bounds、cell geometry、folder alignment、page indicator、rotation refresh、drop、drag bounds 已拆成独立 owner/policy。

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
