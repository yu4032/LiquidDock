# LiquidDock TODO FOR AGENT

基准：**2026-10-10 `main` / HyperOS 3 平板 / Launcher 4.50 / libxposed API 101**。

本文件只记录当前仍需要代码、验证或设计决策的事项。
已经完成的功能、历史探索、已确认不继续推进的方向不再保留在 active TODO。

原则：

- 不为了减少文件行数拆分具有明确生命周期的渲染组件。
- 不使用固定延迟替代真实事件驱动。
- 不以静态分析代替真机验证。
- 不删除仍被迁移、预设、导入导出或运行时使用的配置。

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

**状态：待清理。**

重新核对：

- Glass capture/dynamic 历史参数；
- 无 GUI 控件但仍存在的键；
- 旧网格边距键；
- 工作台旧偏移键。

要求：

- 先确认真实消费链；
- 检查导入导出和预设迁移；
- 确认无用户数据依赖后再删除。

禁止仅因为 GUI 没入口就判断死代码。

---

## 5. Grid configuration migration

**状态：未完成。**

迁移：

- `home_grid_8x4`
- 新 grid enable key

要求：

- 新安装读取新键；
- 老配置平滑迁移；
- 后续删除旧读取路径。

---

## 6. Geometry-only pre-draw observer review

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

## 7. Debug logging I/O optimization

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

## 8. RootPassBlur / glass session ownership audit

**状态：持续维护。**

检查：

- Surface 生命周期；
- EGL owner；
- texture release；
- recovery path。

原则：不同 Session 不强行合并。

---

## 9. Signed build / R8 regression coverage

**状态：保留。**

继续检查：

- Class.forName；
- reflection keep rules；
- release build 行为。

---

## 10. Settings i18n cleanup

**状态：低优先级。**

迁移剩余硬编码 UI 文本到资源文件。

---

# Validation queue

## GUI PR acceptance

待实机确认：

- #319 数值控制；
- #320 Surface/Cell 拆分。

检查：

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

以下不再进入 active TODO：

- GUI 大规模拆分；
- ModernSettingsUi 进一步机械拆分；
- Prismal 弹窗动画开发；
- Perfetto 接入；
- GL location caching；
- WorkspaceGridSizing 旧状态设计；
- 第三方 profile schema 扩展；
- ShortcutMenu dark-mode key 重命名；
- 文档自动化建设。

这些内容如需变化，应重新建立新的明确任务，而不是恢复旧 TODO。
