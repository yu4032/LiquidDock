# LiquidDock TODO FOR AGENT

基准：**2026-10-29 `main` / v2.7.6 / HyperOS 3 / Launcher 4.50 / libxposed API 102**。

Workspace 性能清单状态更新：**2026-10-18**；P0 静态几何复用已合并并实机验收，详见 [收尾报告](docs/perf/workspace-static-geometry-p0-20261018.md)。

活跃清单只记录仍需代码、验证或设计决策的事项；完成与主动取消的项目在末尾分别归档。
任务状态按 `main` 已合并的代码记录；开发中的 PR 单独标注。

原则：

- 不为了减少文件行数拆分具有明确生命周期的渲染组件。
- 不使用固定延迟替代真实事件驱动。
- 不以静态分析代替真机验证。
- 删除配置键之前核对当前 GUI、运行时、预设和当前格式导入导出的真实消费链。
- 配置层不再维护老用户迁移、历史别名回退或旧键与新键双写；旧键无须兼容。
- Hook API 升级与用户配置数据迁移分别追踪。

---

# P1 · Runtime performance

## 1. Workspace glass dynamic-scene / frame-time investigation

**状态：P0 静态几何重复捕获已完成（2026-10-18）；P1 动态帧及端到端掉帧归因未完成。**

已完成并进入 `main`（[P0 收尾记录](docs/perf/workspace-static-geometry-p0-20261018.md)）：

- 静态节点有效几何复用、共用 root transform 与 Widget 同帧网格基准去重（[#420](https://github.com/yu4032/LiquidDock/pull/420)）。
- 空几何文件夹的有效帧 token 与稳定期复用（[#421](https://github.com/yu4032/LiquidDock/pull/421)）。
- Widget 逐帧核对原生 `spanX` / width delta 后复用稳定几何；同帧共享祖先 View 采样，保留节点独立历史指纹（[#422](https://github.com/yu4032/LiquidDock/pull/422)）。
- 实机验收与 Perfetto 复核：17 个稳定候选从 **8 次完整捕获 / 9 次缓存命中**降为 **0 次捕获 / 17 次命中**；其中 Widget 命中 8。20:12 诊断构建记录共享祖先读取 281→79 次、复用 202 次；临时纳秒计时与采样次数探针已在 #422 合并前清理。

**尚待研究，不计入上述 P0 完成状态：**

- 分页、HOME/解锁、Recents、拖拽与 native proxy 动画仍需原生运动帧的几何 authority，分页强制 17/17 捕获是当前保守且受验证的策略；只有确认处于帧截止期关键路径，才设计更细的事件失效模型。
- 在 CPU scheduler、频率/温度、RenderThread、EGL swap、GPU 和 SurfaceFlinger / FrameTimeline 层定位真实漏帧原因，进行同条件 A/B；不能从 `SceneSync` 单阶段 p95 推断整体 FPS。
- 静止低频唤醒的长尾、allocation/GC 及不同实际节点数量的伸缩性仍需独立测量；旧 `home_few`/`home_many` 实际均为 17 候选，不能用于 O(N) 系数推断。

保持事件驱动、Workspace scroll projection、Widget resize/stretch、HOME spring、旋转、文件夹与 proxy 几何正确性；**不以去掉 OEM 检查换取更高命中率**。

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

**状态：已合并。**

libxposed API 102 现已接入，Hook 按注册位置、目标 `Executable` 与优先级维护独立槽位，同一槽位重复注册时使用 `HookHandle.replaceHook()` 原子替换。ART 调用栈识别已适配。

实现见 [PR #465](https://github.com/yu4032/LiquidDock/pull/465)、[PR #474](https://github.com/yu4032/LiquidDock/pull/474) 和 [API 102 Hook 文档](docs/api102-atomic-hook-replacement.md)。

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

## GUI 组织与验收

**状态：统一设置分组与搜索已进入 `main`；实机视觉与搜索定位复查列入验收。**

- [PR #477](https://github.com/yu4032/LiquidDock/pull/477) 已统一玻璃、文件夹、菜单、Gboard、网格、描边和阴影等设置的 Cell 分组、参数索引与跳转定位。
- 早期 [#319](https://github.com/yu4032/LiquidDock/pull/319)、[#320](https://github.com/yu4032/LiquidDock/pull/320) 已关闭，未采用其独立分支合并路径；数值输入、实时滑块与页面组件以当前主线实现为准。
- 实机复查重点：深色主题、玻璃卡片间距、搜索定位、数值输入焦点、长列表滑动与转场动画。

## Workspace performance validation

**状态：P0 静态几何复用的稳定桌面 / 分页 Perfetto 与实机验收已完成；端到端帧率归因仍待验证。**

- 已完成：静止桌面及分页的多轮 trace、`StaticCandidates/StaticGeometryReads/StaticCacheHits` 守恒、Widget 缓存命中和同帧祖先复用验证（见 [P0 收尾记录](docs/perf/workspace-static-geometry-p0-20261018.md)）。
- 仍需：可复现拖动、HOME/解锁及文件夹→多任务交互的完整 FrameTimeline；旧 24-trace 部分 `app_home`/`unlock` 样本有事件丢失。
- 重点核对 UI thread / RenderThread、`StaticNodes`、CPU 调度/频率、allocation/GC、EGL Swap、GPU 与 SurfaceFlinger，不能仅依据缓存命中推断帧率。

---

# Completed / archive

## 已完成并进入 `main`

- 既定阶段的 GUI 结构清理；不再以减少类大小为目标机械拆分；
- Prismal 弹窗动画开发；
- Perfetto 分段诊断接入，用于采集独立的 UI、RenderThread 与图形管线时序；Dock 及端到端 FPS 归因列在性能任务中；
- Workspace 稳态静态几何缓存 P0：[PR #420](https://github.com/yu4032/LiquidDock/pull/420) 稳定几何/网格基准复用、[PR #421](https://github.com/yu4032/LiquidDock/pull/421) 空文件夹有效帧缓存、[PR #422](https://github.com/yu4032/LiquidDock/pull/422) Widget 稳定几何与祖先采样复用；均已合并、完成实机/Perfetto 验收，详见 [2026-10-18 收尾记录](docs/perf/workspace-static-geometry-p0-20261018.md)；
- 文件夹打开时其他玻璃渐隐与独立时长配置（[PR #423](https://github.com/yu4032/LiquidDock/pull/423)，实机验收、已合并；属于视觉行为修复，不作为静态几何性能收益）；
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
