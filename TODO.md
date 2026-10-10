# LiquidDock TODO FOR AGENT

基准：**2026-10-10 `main`（`67d596d`）/ `build.gradle.kts` 中 `versionName=2.6.4` / HyperOS 3 平板、Launcher 4.50 适配主线 / libxposed API 101**。`versionName` 是源码构建版本，不等于已发布 Release 版本。

自由网格、横竖屏位置记忆、挤压循环/事务保护和 PR #275 的图标/小组件规划器路由已落地。下面的性能与兼容性条目仍是待验证或待优化事项，不代表上述功能尚未实现。

本文件只把**未完成**的优化、兼容性验证和维护工作列为 active TODO；已验证的阶段成果归入文末“已完成 / 防回归”。状态区分：`源码已证实`、`部分完成`、`待测量`、`待真机验证`，不把静态检查等同于 CI/真机验收。详细证据与风险分层见 [2026-10-10 维护性审计](docs/maintainability-audit-20261010.md)。

维护原则：不从 TODO 反推代码；文档过时先核对源码；不为了减少代码行数合并具有不同 source authority / EGL 生命周期的玻璃 Session；不删有导入导出、迁移、预设或运行时消费的隐藏配置键。

## P1 · Runtime performance hot paths

**状态：部分完成，稳定态 CPU 热点仍待量化。** GL program location 缓存和 Dock scratch 数组复用已经落地，详见文末完成项；目前重点是避免稳定帧重复扫描几何与节点。

当前最高价值的性能债务集中在“稳定态仍按帧执行”的路径。原则是优先把 `O(frame)` 工作降为 `O(event)`，而不是简单加节流或固定延迟。

### P1-A · Launcher Workspace glass per-frame node scan

`LauncherGlassSession` 当前在 Launcher root 的 `OnPreDrawListener` 中每帧执行 `syncSceneOnUiThread()`。

稳定态仍会：

- 读取 drag/static node 快照数组；
- 遍历全部 Workspace glass node；
- 对 drag sink 执行 `syncFromMaterial()`；
- 对 static node 执行 `captureGeometry()`；
- 对每个 static node 做 ancestor visibility/alpha 检查；
- 调用 `transformMatrixToGlobal()`、矩阵 invert/map；
- 检查 root rotation；
- 调用 source backend `reconcileRoot()`。

图标/文件夹/Widget glass 数量增加时，UI 线程成本近似随 node 数量线性增长；在 120/165Hz 等高刷新率设备上尤其值得优化。

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

### P1-D · GUI 玻璃弹窗动画验收

- [#318](https://github.com/yu4032/LiquidDock/pull/318) 的数值输入、默认恢复、Grid 安全提醒和重启作用域弹窗增加进出场动画；使用 Compose spring/tween 尽量延续 Prismal 的动效语言，但不替换 MIUIX 窗口的键盘与焦点管线，也不新增采样层。验收：正常打开/取消关闭、快速重入、系统返回键、输入法焦点、弹窗重启操作与底栏拖动无冲突。
- PrismalAGSL v1.0.4 没有可直接代替现有 MIUIX WindowDialog 的原生 modal，**不可声称是 Prismal 原生窗口动画**；仅是匹配其 spring 手感。确认 GPU/布局不会因弹窗动画额外抖动后再合并。

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

已具备 `Field/Method` 缓存字段与 ViewRoot 缓存，不能再把“首次引入反射成员缓存”作为待开发功能。下一步应**量化** `readSurfaceGeometry()` 与 `updateBackdropMapping()` 在稳定帧的成本，验证 ViewRoot 变化时缓存失效是否完整，再考虑用可靠的真实事件缩小轮询范围。

最终目标：

- surface/root replacement；
- rotation；
- size/layout；
- Dock geometry/reflow；
- window-frame change

这些真实事件标记 geometry dirty，再在下一帧统一 reconcile。

禁止用轮询间隔或 fixed-delay 代替真实 geometry authority。

### P1-C · GL primitive deduplication（location caching 已完成）
**状态：仅重复工具方法待整理。** 对 `LauncherGlassSession`、`RootPassBlurBackend`、`Miuix307PassBlurTextureView`、`ShortcutPopupGlassSession`、`GboardFloatingGlassSession`、`MiuiSearchboxGlassSession`、`RecentsCapsuleGlassSession`、`SecurityCenterGlassSession` 和 `SystemUiHandleMenuPrismalSession` 的源码核验发现，attribute/uniform location 已在 program 建立时获取并缓存；**不要再执行“每帧查询迁出”的旧任务**。

剩余可选工作：在逐个核对 GL context ownership、shader/attribute 契约、释放语义后，提取无状态 `compileShader/createProgram/bindQuad/unbindQuad` 等基础 helper。禁止合并不同 Session 的 source/recovery 生命周期；任何提取都必须有回归测试。

### P1-D · DockGlassCompositor stable-frame scan / dirty fast path

**状态：部分完成。** `DockGlassCompositor` 现已保留 `uiFingerprintScratch`、`proxyFingerprintScratch`、`animationSampleScratch` 与 `sceneItemScratch`，不会在每次 refresh 无条件重建这些数组/列表；容量扩张时仍可能分配。

`refreshUiSceneIfNeeded()` 当前仍在判定 fingerprint 不变之前遍历 `cached`，计算每个 Dock item 的 UI / 动画 / proxy 指纹。因此剩余问题是稳定帧的扫描成本，而非“每帧创建 long[]”：

- 先用 Perfetto / trace 对比有无动画、不同 Dock icon 数量下的真实 UI-thread 成本；
- 研究 revision、output geometry 与动画状态驱动的 fast-path，确保没有遗漏 parent-chain translation、visibility、drag/proxy、resize/recenter；
- dirty 的 item 才重新采样 geometry；保持 workstation radius、output-root transform 和 source freshness 不变；
- 不要盲目缓存可变的 vendor View 状态。

---

## P2 · 旧 GUI / 采样配置收敛（2026-10-09 审计）

**状态：待清理。** PR #290 仅收紧 GUI 描述并删除无实际控件的旧帮助文本；后续清理必须独立核验和测试。

- 核实 `ConfigSchema.Glass` 中旧 capture/dynamic 参数（如 `liquid_capture_power_limit_fps`、`liquid_capture_stop_delay`、`liquid_capture_scale`、`liquid_dynamic_*`、`liquid_black_threshold`、`liquid_home_settle_delay`）是否有真正的运行时消费或导入导出/预设依赖。无消费者才分批清理，切勿误删 `PASSBLUR_CAPTURE_SCALE` / `PASSBLUR_RENDER_FPS`。
- 检查 `liquid_edge_band`、`liquid_highlight_alpha`、`liquid_recents_prearm_distance`：当前没有可见 GUI 控件；仍须验证配置链是否仅有声明、预设或历史兼容用途，再决定删除键或保留迁移。
- 对八个旧网格独立四边边距键与两个工作台 All Apps 合并纵向偏移键，先做历史配置迁移、导入覆盖与新键缺省回退测试，之后再考虑删除旧读取分支。不得让既有布局在升级时跳变。
- 完整核对可见 GUI 的 `ConfigSchema` 写入、`LiquidDockConfig` 读取及真实 Hook 消费；不可根据“页面没有入口”直接定义业务代码为死代码。
- GUI 滑条从“松手后写入”调整为**右侧数值每跨一个合法档位就实时写入**（#314，待实机确认）。同档位去重，避免无意义的每帧写入。SharedPreferences → API101 Remote Preferences 的延迟、拖动帧率和最终落盘可靠性仍需实测；4×2 网格预检继续优先于风险档位的写入。
- **Prismal GUI 底栏视觉完整性**：已确认原生胶囊模糊/折射/回弹运行，文字因历史重影问题采用单份 overlay。[#318](https://github.com/yu4032/LiquidDock/pull/318) 尝试桥接 native `LocalPrismalBottomTabHighlightedIndex` 的拖动中即时高亮和轻缩放，需真机确认长按、快速拖动及切页不闪烁/不重影。**仍未恢复**胶囊对图文字形的实际镜片折射，也尚未桥接原生按压比例 `LocalPrismalBottomTabScale`（上游为 internal）；后续不能简单重复图文或修改上游。详见 [审查报告](docs/gui-prismal-bottom-navigation-audit-20261010.md)。

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



## P2 · WidgetGridSizing state ownership / dimensions

**状态：部分完成；旧 TODO 描述已过时。** 当前 `WidgetGridSizing` 的 process-static 开关名为 `customGridEnabled`，通过 `setCustomGridEnabled()` 控制；`isSupportedSpec(spanX, spanY)` 只检查跨度为正值，并无“仅支持 1×1 / 2×1 / 2×2 / 4×2”的硬编码白名单；`gridRect()` 还依据实际 `xs/ys` 数组做边界校验。

待办：
- 明确 `customGridEnabled` 的 install/runtime owner 与重载/退出时序，避免陈旧 process-static 状态；
- 如确需类型注册表，应先证明现有正跨度 + 实际网格边界不足，不能为了重构重新引入 widget 类型白名单；
- 保持 CellLayout/MIUI occupancy 权威，只调整最终 allocation/frame；测试旋转、边界与组件居中。 

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

## P1 · Rename custom-grid enable key across two releases

当前自定义主屏网格总开关仍使用历史键名 `home_grid_8x4`，但其语义早已不再表示 8×4；当前尺寸唯一真值已经是 `grid_columns / grid_rows`。

按两个连续版本完成迁移，不做一步到位删除：

### 第一次更新：引入 `grid_enabled`，双键过渡

- `ConfigSchema.Grid.ENABLED` 改用新键 `grid_enabled`；
- Launcher/设置进程启动迁移时，如果 `grid_enabled` 尚不存在，则把现有 `home_grid_8x4` 的布尔值原样复制到 `grid_enabled`；
- 过渡版本内同时维护两个键：GUI、preset、import/export 与任何直接写入路径更新开关时同时写 `grid_enabled` 和 `home_grid_8x4`，保证一个完整版本周期内二者一致；
- 运行时以完成迁移后的 `grid_enabled` 为主值，不再从键名推断任何 8×4 语义；
- 增加测试覆盖首次复制、双写一致性和已有 `grid_enabled` 不被旧键覆盖。

### 再下一次更新：彻底删除 `home_grid_8x4`

- 只读取和写入 `grid_enabled`；
- 从 `ConfigSchema`、preset、GUI、import/export、测试和文档中删除 `home_grid_8x4`；
- 删除第一阶段的旧键复制/双写兼容代码；
- 更新时从 SharedPreferences 中移除残留的 `home_grid_8x4`；
- 全仓搜索确认 `home_grid_8x4` 生产代码引用归零。

最终配置契约：

```json
{
  "grid_enabled": false,
  "grid_columns": 8,
  "grid_rows": 4
}
```

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

当前 `LEGACY_SOURCE_DEBT` 为 **11 项**（以 `RuntimeBehaviorTestPolicyContractTest` 的源码集合为准）：

- `GlassConfigGenerationContractTest.java`
- `Miuix307EdgeOverscanContractTest.java`
- `PrismalCompositeHotPathContractTest.java`
- `PrismalModuleBoundaryContractTest.java`
- `PrismalOfficialParityV3Test.java`
- `RestartBoundSettingsContractTest.java`
- `WidgetBackgroundRankingUiContractTest.java`
- `WidgetComponentDiscoveryContractTest.java`
- `WidgetComponentSelectionContractTest.java`
- `WidgetMamlRenderTreeDiscoveryContractTest.java`
- `WorkstationAllAppsHookContractTest.java`

逐项处理：

- 真正静态 contract -> audited allowlist；
- runtime behavior -> typed state/policy test；
- obsolete -> 删除。

该列表只能减少。另有 allowlist 内的源码字符串断言（如 `ModernSettingsArchitectureTest`）；它们适合限制静态结构，但不能替代真实状态/交互测试。GUI 拆文件时优先调整测试以检查等价行为，不能为了测试通过保留巨型文件。

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

2026-10-10 发现多个技术文档仍把 2026-10-03 / v2.6.1 写成当前主线。本文档已与当日源码重新核对关键事实；其他技术文档仅更正可核实的版本元数据，未做逐项 runtime 复验。`docs/superpowers/*` 属于历史方案，不可直接当作当前规范。

后续可以增加轻量 CI：

- 检查根文档版本号与 Gradle `versionName`；
- 检查 README scope 与 `META-INF/xposed/scope.list`；
- 检查内部相对链接；
- 检查历史 docs 是否带 archive banner；
- 检查 active docs 是否仍引用已删除生产类。

不要让文档 CI 变成脆弱的逐字字符串快照。

---

## P1 · Code maintainability / regression-safe refactor

**状态：GUI 模块拆分已完成八批，交互动画独立推进。** [#309](https://github.com/yu4032/LiquidDock/pull/309) 至 [#317](https://github.com/yu4032/LiquidDock/pull/317) 已并入 `main`，其中 #317 的 Prismal 底栏与重启弹窗拆分经用户实机验证。当前 [#318](https://github.com/yu4032/LiquidDock/pull/318) 在独立分支为三类 MIUIX 弹窗和重启作用域弹窗增加 spring/fade 过渡，并以原生 Tab 的拖动候选状态桥接单份图文的高亮与轻缩放，**尚待 CI 与实机验收，不得记为已发布效果**。主 Activity 约 536 行；GPU/Hook 大类另列维护任务。

优先顺序：
1. GUI：按页面领域拆分 Composable/IntSpec/导航/存储边界，**保留两个 UI 作用域的分离设计、Prismal 视觉和全部配置键/热更新行为**；优先增加行为测试。
2. Gesture：将 `GestureState` 的 DOWN/reset/取消/commit 定义为可测试的状态转换，覆盖连续手势、vendor handoff、代际取消。
3. PassBlur：先封装不可变几何快照和 GL 资源 owner；不改变 producer/frame sync/rotation/壁纸 generation/错误恢复时序。
4. 包结构：按域渐进搬迁；必须审核 Xposed 注入、R8、反射二进制名及测试，禁止自动批量移动所有类。

验收门槛：每批独立 PR、CI 测试、源代码 diff/配置键比对；GUI 涉及配置写入、触摸控制或 Prismal 视觉时仍需实机确认；Hook/图形/手势相关变更尤其必须实测。**不要将“源码移动和编译成功”记作全部维护性问题已修复。**

---

# 已完成并只需防回归

## 近期已落地的优化（非完整性能验收）

- **GL program location caching**：主要 Render Session 在 EGL program 初始化后缓存 attribute/uniform location；后续只考虑复用无状态 GL helper，不再把 location 查询当作持续逐帧热点。
- **Dock scratch 复用**：`DockGlassCompositor` 已用复用数组/列表减少短命对象；稳定态 O(N) 扫描仍列于 P1-D。
- **自动采样保护区**：四边 `SAMPLING_EXTRA_TOP/BOTTOM/LEFT/RIGHT` 的手动补偿配置已退役，保留自动保护区及纹理上限裁剪；不要恢复无效 GUI 控件。

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
