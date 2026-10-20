# Workspace 静态几何性能 P0 收尾记录（2026-10-18）

**状态：已完成、已实机验收、已合并至 `main`。** 此状态仅覆盖 *Workspace 稳态静态节点重复几何捕获及同帧重复祖先读取*；不表示动态分页、RenderThread、EGL、SurfaceFlinger 或整体掉帧已经修复。

## 1. 问题及已合并修复

原始 24-trace 阶段（PR #420 的历史观察）显示，稳态 Workspace 仍存在大量静态节点反复执行 `captureGeometry()`；运动帧全量捕获和低频调度波动又使单纯比较全局 P50/P95 不可靠。

| 合并 PR | 已完成的 P0 工作 | 合并提交 |
| --- | --- | --- |
| [#420](https://github.com/yu4032/LiquidDock/pull/420) | 有效静态几何复用，根坐标变换与 Widget 网格基准在同帧共享 | `2087c77eafa7652e1ffcf0bfe976e4f2b11dda82` |
| [#421](https://github.com/yu4032/LiquidDock/pull/421) | 为**成功捕获的空几何文件夹**创建可复用帧 token，避免将未捕获状态误认为已验证的空状态 | `e0387b41087bdf38b4a9ed11cf92a82785627104` |
| [#422](https://github.com/yu4032/LiquidDock/pull/422) | Widget 稳态几何复用、原生 span/横向宽度补偿实时校验、同帧 View 祖先采样复用 | `5026e5e62df50d23ffd13ab6484be3913e6931a7` |

同期间 [#423](https://github.com/yu4032/LiquidDock/pull/423) 修复文件夹打开时其他玻璃突然消失，使用独立的渐隐时长配置（合并提交 `b2525347b58c4f2e8f701d0a9c4116445a209934`）。这是已验收的**视觉生命周期修复**，不要计入几何缓存速度提升。

## 2. 验收数据

以下均来自用户设备多轮 Perfetto；百分比与时长只用于所描述的帧子集，不外推为整个系统帧率。

| 观测 | P0 改进前 / 过程 | 合并前最终诊断版 |
| --- | --- | --- |
| 稳定 Workspace（17 个候选） | 8 次完整几何捕获、9 次缓存命中（#421 后基线） | **0 次完整捕获、17 次缓存命中，其中 8 个 Widget 命中** |
| 单帧 281 次共享祖先访问 | 281 次 View 属性采样 | **79 次独立采样、202 次帧内复用（71.9%）** |
| 稳定零捕获帧、`StaticGuard` P50 | 19:46 诊断版 **749.4 μs** | 20:12 诊断版 **470.0 μs**（−37.3%） |
| 稳定零捕获帧、`SceneSync` P50 | 19:46 诊断版 **1,077.3 μs** | 20:12 诊断版 **774.9 μs**（−28.1%） |

20:12 六份 trace 有 **2,654 个**完整 `SceneSync`，其中 **196** 帧为 17/0/17、Widget 命中 8；静止与分页稳定期观察均一致。三组分页动态帧仍各有 **806/810/805** 帧执行 17/17 完整捕获，没有为了提升缓存率绕过原生动画几何更新。

**证据边界：** 19:46→20:12 是跨轮次非随机化对照，CPU 频率、温度、调度和手势节奏未严格匹配；因此只能说 guard 与 `SceneSync` 阶段观察到一致下降，**不能宣称整体 FPS 提升 28%**。原始归档没有可独立校验的 APK SHA 或全面的 TraceProcessor 丢包证明。早期 24 份样本里个别 app_home/unlock 有事件丢失，并且 `home_few` 和 `home_many` 的真实静态候选都为 17，不能充当节点数量扩展性实验。

合并前临时诊断量 `StaticGuardNs`、`StaticWidgetGuardNs`、`StaticViewReads`、`StaticViewReuseHits` 及逐节点 `System.nanoTime()` 已从生产路径清理；常规 Perfetto 的 `StaticCandidates`、`StaticGeometryReads`、`StaticCacheHits`、`StaticNullCacheHits`、`StaticWidgetCacheHits` 等保留。上述数字来自**清理前的验收版本**，不能假定合并后的 APK 仍输出临时计数。

## 3. 保留的正确性边界

- 每个 Widget 在可复用的 PreDraw 中仍读取当前 native span owner、`spanX` 和 `widgetHorizontalSpanDeltaPx`，不跨帧缓存系统 GridConfig/宽度为新事实。
- 每个节点维持独立的旧帧 View 指纹；共享 `IdentityHashMap<View, ViewSample>` **每帧重置**，仅复用同帧属性观测。检查祖先布局、滚动、透明度、矩阵与 animation matrix，运动或未知状态回退完整捕获。
- HOME spring、unlock、Recents、旋转、root 尺寸变化与 native proxy 沿用强制/失效路径。保持 Wallpaper/PassBlur source、EGL 和渲染物料的原有 ownership。
- #422 的单元测试、Debug 构建及用户实机功能验收完成后才合并。此处的“完成”是静态几何 P0 验收结论，不覆盖所有帧间状态证明。

## 4. 剩余任务（不是 P0 阻塞项）

1. **动态分页与转场**：强制全量捕获仍可能有优化空间；需先证明其落在实际截止期关键路径，不能简单使用 root-size-only cache 或跳帧。
2. **端到端掉帧归因**：采集 FrameTimeline、UI/RenderThread 调度、GPU、EGL swap 和 SurfaceFlinger，匹配 165Hz（约 6.06 ms）预算分析 missed deadline。
3. **低频静止唤醒尾延迟及严格 A/B**：记录 CPU 频率、温度、实际页面节点、APK SHA、trace loss 和相同操作序列，必要时再研究 dirty-scene / event-revision 模型。

当前维护状态以 [TODO.md](../../TODO.md) 为准；2026-10-10 的 [最初采样方案](https://github.com/yu4032/LiquidDock/blob/fb15884469678d9c02a2ba6d0a753e92ff2c1192/docs/workspace-ui-node-scan-perfetto-20261010.md)（已归入 Git 历史）和原始 24-trace 审计均是**当时的历史观察**，不得倒改为后来优化后的统计。
