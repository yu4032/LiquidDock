# LiquidDock TODO

当前主线：**v2.4.1 / HyperOS 3.0.307+ / MiuiX PassBlur + OES/GLES + Prismal / libxposed API 101**。

本文件只记录当前生产代码中仍存在的工程债务、兼容性风险和未完成的结构收口。已经落地并通过回归的修复不再保留为 active TODO。

---

## P1 · Workstation composite runtime restore

**状态：未完成。单个视觉 owner 可以 live release，但 Workstation structure 仍 restart-bound。**

工作台同时跨越：

- Dock width/geometry；
- Dock icon top/bottom offset；
- Dock icon glass radius；
- Workspace Grid；
- All Apps geometry；
- Divider；
- Recents producer recovery；
- wallpaper/rotation freshness；
- normal-layout backup/restore。

后续如果要支持完整热切换，必须建立一个可证明的 composite restore transaction，而不是逐个 toggle 子模块。

必须保持：

- 普通模式无回归；
- old producer endpoint 不被复用为 fresh source；
- Recents/HOME 仍经过 fresh-frame barrier；
- Divider 等独立 owner 的 exact restore 不被 composite restore 覆盖。

---

## P1 · Widget classification / span extensibility

**状态：部分完成。当前 Widget adaptation 仍包含分散的类型/span knowledge。**

目标：

- `WidgetGridSizing` 去掉 static `widgetAdaptationEnabled` 全局状态，改由安装 owner/immutable config 驱动；
- 引入统一 `WidgetClassifier`，收口 `ItemInfo.isWidget()` 与历史 `itemType` fallback；
- 从 `HomeGridHook` 移除散落的 `itemType == 4/5/19` 判定；
- 引入 `WidgetSpecRegistry`，集中登记当前 `1×1`、`2×1`、`2×2`、`4×2` allocation；
- `WidgetGridSizing` 最终只保留纯 geometry/allocation 计算。

不变量：

- Widget adaptation 只改 pixel allocation/final frame；
- MIUI placement/occupancy 继续权威；
- 不通过 `addOccupied()` / `transformToHVArray()` 接管 matrix。

---

## P1 · `HomeGridHook` ownership 拆分

**状态：持续进行。多个 production-used policy/hook 已拆出，但 `HomeGridHook` 本体仍过重。**

已经独立的边界包括：

- profile overlay；
- orientation memory；
- mutation capture；
- device-config count；
- horizontal centering；
- vertical bounds；
- workspace drop legality；
- drag bounds。

剩余拆分建议按风险从低到高：

1. Widget adaptation；
2. page indicator；
3. folder alignment；
4. cell geometry；
5. rotation / refresh authority（最后）。

目标是迁移真实 runtime ownership，不是机械增加 helper 类。

---

## P1 · `MainHook` 收缩为 composition root

**状态：未完成。`ModuleMain` 已承担顶层 process composition，但 `MainHook` 仍保留大量 feature-level state。**

优先迁出：

- `workstationMode` / confirmation state；
- normal-layout backup；
- Dock resize animator；
- feature-specific installer/recovery state；
- 剩余 Dock/Grid/Workstation mutable ownership。

目标：`MainHook` 只保留真正跨功能的 Launcher composition/capability/logging，而不是 service locator 或 setter bag。

完整 master-switch uninstall 仍是 restart-bound；不要用“视觉 owner 已恢复”冒充结构 Hook 已卸载。

---

## P1 · Launcher GPU ownership audit

**状态：部分收口。root-wide session/backend 已形成，但 producer/EGL/OES/output owner graph 仍值得继续压缩。**

重点审计：

- native PassBlur endpoint bind/release/rollover；
- EGLDisplay / EGLContext / EGLSurface ownership；
- OES texture / `SurfaceTexture` ownership；
- source endpoint generation vs scene/wallpaper generation；
- static compositor vs drag overlay vs popup output 的共享/独立边界；
- rotation/root replacement cleanup；
- `LauncherGlassSession`、`RootPassBlurBackend`、`Miuix307PassBlurTextureView`、`PrismalRenderer` 中真正重复的 low-level lifecycle。

不要预设要创建“大一统 GlassEngine”。只有完全相同的生命周期才能抽成公共 primitive。

必须保持：

- active backdrop zero-copy；
- HOME/Recents 不展示 stale frame；
- Workspace source-driven updates；
- fresh generation 可越过 render FPS cap；
- SystemUI timing authority 与 Launcher source authority 分离。

---

## P1 · Security Center compatibility corpus

**状态：核心 resolver 已从 fixed version/member map 迁移到 semantic capability gate，但兼容覆盖仍需要真实样本扩展。**

后续工作：

- 增加不同 HyperOS/Security Center build 的反编译 contract corpus；
- 验证 Game / Video / Global Dock / All Apps 的 getter/resource/signature relation 是否稳定；
- 对 resolver reject 原因提供更结构化的一次性 diagnostic；
- 继续保持 ambiguity -> reject，不加入“第一个匹配项” fallback；
- 不重新把 versionCode 或 obfuscated field/method name 变成主 compatibility authority。

任何新 build 支持都必须验证：

- PassBlur continuous authority；
- vendor material snapshot/suppression/replay；
- root/session identity；
- carrier/material epoch；
- attach/detach/root replacement；
- runtime disable fail-closed restore。

---

## P2 · Widget component discovery diagnostics

`WidgetBackgroundRuleEngine.loadBundled()` / parser 等路径仍存在 silent-degrade 风险。

目标：

- required bundled resource 缺失与 optional rule parse failure 分开；
- 真实 degradation 输出 one-shot structured diagnostic；
- 不制造每 frame / 每 bind log spam；
- user selector 解析失败不产生部分 mutation。

不新增任意脚本/方法调用 DSL。

---

## P2 · Shortcut-menu dark-mode naming compatibility

当前 persisted key 仍叫：

```text
liquid_shortcut_popup_dark_text
```

但功能已经扩展为“文字 + 近黑线条图标”的完整 dark-mode adaptation。该 key 为兼容旧设置暂时保留。

除非未来有正式 schema migration，不要直接 rename persisted key。若要清理命名，应：

1. 增加新 schema key；
2. migration 一次性搬迁；
3. JSON import/export alias；
4. 保持旧备份可读；
5. 删除旧 key 前经过至少一个兼容周期。

这不是行为 bug，优先级低于 runtime correctness。

---

## P2 · R8/reflection audit 持续化

已经修复两类真实 R8 failure：

1. LiquidDock 自有字段/方法的字符串 self-reflection；
2. 跨 Launcher ClassLoader 的 AndroidX 类名字符串被 identifier adaptation（`RecyclerView$State -> RecyclerView$a`）。

后续：

- 新 project-owned API 默认 typed；
- 定期扫描 `HookUtil.getField/invoke`、`getDeclaredField/Method`、`Class.forName`；
- 对跨 ClassLoader literal 检查模块是否也打包同名 class；
- targeted `-keepnames` 只在有确证需求时增加；
- 不扩大为 LiquidDock 整包 keep；
- 保持 R8-enabled debug CI。

---

## P2 · Test architecture debt

`RuntimeBehaviorTestPolicyContractTest` 已建立 production-source-reader default deny。

当前 `LEGACY_SOURCE_DEBT` **13 项**：

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

逐项分类：

- 真正静态 architecture/API contract -> 移入 audited allowlist；
- runtime behavior -> 改成调用 production-used typed state/policy；
- obsolete -> 删除。

该列表只能减少，禁止为了 CI green 增加新 debt。

---

## P2 · CI maintenance

当前 workflow 仍使用：

- `actions/checkout@v4`；
- `actions/setup-java@v4`；
- `gradle/actions/setup-gradle@v4`；
- `actions/upload-artifact@v4`。

待维护：

- 评估升级到当前 Node runtime 兼容版本；
- 统一 artifact retention 配置与仓库实际策略；
- 清理编译器 warning，而不是通过 suppress 掩盖不精确 varargs/反射语义；
- 保持 `testDebugUnitTest assembleDebug` 与 Security Center/root PassBlur zero-copy audit。

Debug/Release R8 optimization 已经完成，不再是 TODO。

---

## P2 · Settings i18n

Compose settings 仍有不少硬编码中文/英文用户字符串。

目标：

- 迁入 Android string resources；
- 保持 preference key / schema / runtime behavior 不变；
- 中文与英文 README/设置说明统一术语；
- 不在 i18n PR 中顺便改 feature behavior。

---

# 已完成的重要边界

以下内容不再作为 active TODO，后续只需防回归。

### Workspace source-driven PassBlur

普通 HOME 不再消费一帧后自动 pause。静态 source 可自然不产生新 buffer，动态 source 可持续更新；没有固定 capture timer/vsync pump。

### Workspace render-quality spatial correctness

native PassBlur scale 与 local physical FBO 已分离；50%/75%/100% 不改变 behind-content 空间映射。source frame 仍 drain，fresh generation 绕过普通 render cap。

### Workstation Recents producer recovery

已经有 covered authority、duplicate/non-covered rejection、producer rollover/rebind 和 fresh-frame reveal contract。

### Unlock -> HOME authority

SystemUI transition source 与 Launcher runtime protocol 已收口；解锁后不靠固定延迟恢复 Workspace glass。

### Dock stroke / stroke shadow

foreground `DockStrokeRenderer` 已成为正式 owner；描边阴影不再是“待决定是否实现”的功能。

### Widget component hiding

精确 discovery/selector、用户选择、可恢复 mutation、backup format/version 已进入 production；当前不计划扩展成任意 DSL。

### Shortcut-menu glass / dark-mode adaptation

独立 popup glass、动态 menu text white adaptation、近黑线条图标采样分类与 weak cache 已进入 production，并已避免彩色第三方图标误 tint。

### Launcher live drag glass

DragView lifecycle + upper application-panel + live Workspace source + direct-draw visual mirror 已进入 production；不再使用 frozen drag-start backdrop。
