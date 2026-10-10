# LiquidDock TODO FOR AGENT

基准：**2026-10-10 `main` / HyperOS 3 平板 / Launcher 4.50 / libxposed API 101**。

活跃清单只记录仍需代码、验证或设计决策的事项；完成与主动取消的项目在末尾分别归档。
状态以**已经合并到 `main` 的代码**为准；未合并 PR 必须显式标注，不能提前算作完成。

原则：

- 不为了减少文件行数拆分具有明确生命周期的渲染组件。
- 不使用固定延迟替代真实事件驱动。
- 不以静态分析代替真机验证。
- 删除配置键之前核对当前 GUI、运行时、预设和当前格式导入导出的真实消费链。
- 配置层不再维护老用户迁移、历史别名回退或旧键与新键双写；旧键无须兼容。
- API 101/102 的运行环境兼容属于 Hook 框架升级，不等同于配置数据迁移。

---

# P1 · Runtime performance

## 1. Workspace glass dirty-scene optimization

**状态：待测量 / 待设计。**

当前 Workspace glass 在 Launcher root `OnPreDraw` 中同步：

- drag/static node 快照；
- static node geometry capture；
- ancestor visibility/alpha；
- global matrix transform；
- root rotation；
- source backend reconcile。

Perfetto 分段诊断已经进入 main：

- `LD.Workspace.SceneSync`
- `LD.Workspace.DragNodes`
- `LD.Workspace.StaticNodes`
- `LD.Workspace.SourceReconcile`

下一步：

- 使用 stable/paging/drag/unlock 场景采样真实成本；
- 设计 dirty-node / dirty-scene 模型；
- layout、attach、detach、visibility、scale、translation、drag、proxy 等真实事件驱动更新；
- 保留 Workspace scroll projection；
- 保证 HOME spring、分页、widget、folder、proxy geometry 不回归。

目标：静止 Workspace 从 O(N) node scan 降低到接近 O(1) bookkeeping。

---

## 2. Dock PassBlur geometry reconciliation

**状态：待量化。**

当前 Dock PassBlur root pre-draw 仍可能执行：

- surface geometry 读取；
- ViewRoot / Surface 状态检查；
- sampling mapping 更新；
- DockGlassCompositor scene refresh。

已有：

- Field/Method 缓存；
- PassBlur frame sync 修复。

下一步：

- Perfetto 分析稳定帧成本；
- 使用真实 geometry authority 事件标记 dirty；
- 避免固定 polling interval。

---

## 3. DockGlassCompositor stable-frame scan

**状态：部分完成。**

已有 scratch 复用：

- ui fingerprint scratch；
- proxy fingerprint scratch；
- animation sample scratch；
- scene item scratch。

剩余：

- 测量稳定帧 fingerprint scan 成本；
- 研究 revision / dirty item fast path；
- 保持 animation、drag、resize、recenter、source freshness 正确。

---

# P2 · Configuration and maintenance

## 4. ConfigSchema dead-key audit

**状态：部分完成；旧迁移与历史键删除已实现，剩余键仍需按当前消费链核验。**

重新核对：

- Glass capture/dynamic 历史参数；
- 无 GUI 控件但仍存在的键；
- 旧网格边距键；
- 工作台旧偏移键。

要求：

- 先确认当前运行时及 GUI 的真实消费链；
- 检查当前 `ConfigSchema`、预设和 JSON 导入导出是否消费该键；
- 只依据**现行功能依赖**决定保留或删除，历史用户存量值不构成保留理由。

禁止仅因为 GUI 没入口就判断死代码，也禁止为了兼容旧配置而重新引入迁移。

---

## 5. Geometry-only pre-draw observer review

**状态：低优先级。**

评估：

- Gboard floating geometry；
- Shortcut popup geometry；
- Searchbox geometry；
- Recents capsule geometry；
- page indicator translation guard。

目标：

仅动画/移动期间启用，或改为真实事件驱动。

禁止为了删除 OnPreDraw 而破坏实时跟随。

---

## 6. Debug logging I/O optimization

**状态：低优先级。**

当前 debug logging 仍可能同步：

- 时间格式化；
- 文件打开；
- append；
- close。

目标：

- 降低调试模式自身干扰；
- 保持普通用户路径零影响。

---

# P3 · Architecture and compatibility

## 7. RootPassBlur / glass session ownership audit

**状态：持续维护。**

检查：

- Surface 生命周期；
- EGL owner；
- texture release；
- recovery path。

原则：不同 Session 不强行合并。

---

## 8. Signed build / R8 regression coverage

**状态：保留。**

继续检查：

- Class.forName；
- reflection keep rules；
- release build 行为。

---

## 9. libxposed API 102 migration

**状态：待规划。**

当前基线：libxposed API 101。

目标：评估并迁移到 API 102，同时保持 HyperOS / LSPosed 环境兼容。

检查范围：

- API 版本差异；
- Hook 注册流程；
- 回调生命周期；
- reflection / classloader 行为；
- 与现有 API 101 fallback 的兼容策略。

要求：

- 不为了版本升级改变现有 Hook 语义；
- 保留 API 101 可运行路径直到 API 102 真机验证完成；
- 使用 CI + 真机验证确认桌面、SystemUI、输入法等 Hook 范围无回归。

---

## 10. Settings i18n cleanup

**状态：低优先级。**

迁移剩余硬编码 UI 文本到资源文件。

---

## 11. MAML precise widget foreground adaptation

**状态：仅 MAML 待技术核验；RemoteViews 部分已完成并真机验证（PR #326）。**

RemoteViews 的精确 TextView/ImageView 白化已实现并通过真机验证；MAML 内部元素仍由脚本直接绘制，不使用宿主 View 的全局颜色滤镜代替精确节点适配。
需要先确认 Launcher 4.50 MAML Text/Image/Shape 可逆颜色 API 与动态表达式更新时序，再决定是否扩展。

---

# Validation queue

## GUI PR integration and acceptance

**状态：未完成；相关 PR 仍为 Open，尚未合并到 `main`。**

- [#319](https://github.com/yu4032/LiquidDock/pull/319)：数值输入、实时滑块与 Prismal stepper；需要对齐最新主线、解决合并状态并核验 CI / 真机。
- [#320](https://github.com/yu4032/LiquidDock/pull/320)：Surface / Cell 结构拆分；当前以 #319 分支为 base，属叠加 PR，应先处理 #319 后再决定合并顺序。
- #325 的滑块数值宽度修复**已合并**，不能替代 #319 / #320 的结构性验收。

真机检查：

- Prismal 背景；
- 深色主题；
- 弹窗动画；
- 输入焦点；
- 滑动性能。

## Workspace performance validation

待采样：

- 静止桌面；
- 页面切换；
- 拖动图标；
- 解锁进入桌面。

关注：

- UI thread p95；
- StaticNodes 成本；
- allocation/GC。

---

# Completed / archive

## 已完成并进入 `main`

- 既定阶段的 GUI 结构清理；不再以减少类大小为目标机械拆分；
- Prismal 弹窗动画开发；
- Perfetto 分段诊断接入（**不代表** Workspace / Dock 性能优化已完成）；
- GL location caching；
- 滑块数值区域宽度修复（[PR #325](https://github.com/yu4032/LiquidDock/pull/325)）；
- RemoteViews 精确白化、与隐藏的同节点互斥、隐藏规则导入冲突处理（[PR #326](https://github.com/yu4032/LiquidDock/pull/326)，真机验证通过）；
- IOR 折射率参与边缘透镜位移及采样边距修复（[PR #327](https://github.com/yu4032/LiquidDock/pull/327)，真机验证通过）；
- 旧配置迁移链、历史网格键及 JSON 旧别名清理，统一使用当前键（[PR #324](https://github.com/yu4032/LiquidDock/pull/324)）。

## 已归档或主动取消（不等于代码已实现）

- ModernSettingsUi 继续机械拆分（目前没有继续拆分的必要）；
- WorkspaceGridSizing 旧状态设计；
- 第三方 profile schema 扩展；
- ShortcutMenu dark-mode persisted naming；
- 文档自动化建设。

历史配置迁移**不再是目标**。旧迁移链与旧网格键的移除属于 PR #324；其余配置键继续按当前消费链审计。

上述项目如需重新推进，应新建明确任务，不直接恢复旧 TODO。
