# Dock frame-sync 诊断报告（Phase 2–4 静态阶段）

- 日期：2026-10-05　分支：`fix/dock-frame-sync`（基线 main @ ff3e7d35 = v2.6.2 prep）
- 状态：静态阶段完成；根因候选已锁定到具体类/方法/写入点；待真机判别实验（Phase 5–7）确认后实施最小修复。
- P8 补（2026-10-05）：÷2 半率闸已在 turner 实机库定位（见 §8）。
- P9 补（2026-10-05）：最小修复已实现并构建通过（force-refresh 续期，见 §9）；待真机复验（Phase 10）。
- 设备侧事实来源：`/mnt/extra/outputs/liquiddock_recent10min.log`（2026-10-04 10:42–10:50 logcat）。

## 0. 判别问题（任务书特别提醒）

> Dock 自身静止以后，background source 的新 frame 是否仍然能够驱动 Dock glass consumer 在下一次 VSYNC 绘制。

静态结论：**能否驱动取决于 Floating Dock 这个 SurfaceControl 上的 `updateTextureFlag` 是否维持在 true、`SetPassBlurSurface` 是否仍指向我方 producer Surface —— 这两项由「浮窗自己的 ViewRootImpl passblur 状态机」与「我方 bind 事务」共同写入，且缺守护。**

## 1. 链路（当前 v2.6.2 实际实现）

```
Workspace / 后方内容（launcher 窗口下层）
→ SF PassBlur producer（系统按 Floating Dock SC 的 update 契约把模糊结果写入我方 producer Surface）
→ SurfaceTexture.OnFrameAvailable（PBTX 监听，逐帧触发）
→ drawLatestFrame(true)（每帧消费最新 buffer, 1:1 非节流）
→ Prismal GL 合成
→ TextureView 输出 → 系统合成到屏幕
```

- Dock 绑定的 root：**独立窗口 SC `Floating Dock`**（log: `root=Floating Dock#13457 layerId=13457 ... mode=continuous-on-bind`）。
- 消费者逐帧驱动**已存在**（`Miuix307PassBlurTextureView` onFrameAvailable → `drawLatestFrame`），不是缺口。
- 绑定语义：一次性 `SetPassBlurSurface(root, ourSurface)` + `setUpdateTextureFlag(root, true, 1.0)`（continuous-on-bind），**之后不再重申**。

## 2. 机制链（关键发现，全部有代码证据）

### 2.1 Floating Dock 是独立窗口，其 ViewRootImpl 与我方共同管理同一 SC
- 厂商 `com.miui.home.launcher.dock.v3.view.DockWindowManager`（full-jadx: pad-launcher-4.50.0.1204）：
  `createWindow()` 用 `WindowManager.LayoutParams`（`type=2997`, `gravity=80`, `setTitle("Floating Dock")`）把 `DockContainerView`（含 HotSeats）作为**独立窗口**加入；`reparentToWindow()/reparentToLauncher()` 按 `DockLayer`（BelowLauncher/Current）切换。
- 该窗口的 `ViewRootImpl` 内置 passblur 状态机（framework `android.view.ViewRootImpl`，decompile-keep 全量反编译）：
  - `mTextureStateMap: HashMap<View,Boolean>`（逐 View）＋ `mTextureVis`（窗口级 OR 归约）；
  - `addTextureView`（≈L28101）：`put(view,true); mTextureVis=true; updateSfState(false)`；
  - `clearTextureView`（L28171）：view 不在 map 时**提前 return**；移除后 map 为空 ⇒ `mTextureVis=false`；随后 `updateSfState(false)`；
  - `updateTextureState`（L27813）：即使 view 不在 map 也会 `put`（可见性事件会把 view 重新加回）；
  - `updateSfState/sendSfState`（L27876/L28005）⇒ 对 `this.mSurfaceControl`（即 Floating Dock SC）写入：
    - state 0：`SetPassBlurSurface(sc, null)` + `setUpdateTextureFlag(sc, false, mDrawSfScale)`；
    - state 1/2：从 0 进入时 `checkSurTex()` + `SetPassBlurSurface(sc, new Surface(mSurTex))`（**会顶掉我方 Surface**）；随后 `setUpdateTextureFlag(sc, mTextureVis, mDrawSfScale)`（**mTextureVis=false 时把我方 update 标志改 false**）；
  - `sendSfState` 有去重：`mLastSfState==aimState && mLastSfScale==mDrawSfScale` 时跳过——但它不知道我方 bind 的直接事务。
- ⇒ **任何来自该窗口的 register/clear/visibility 变化，都可能（a）翻转 update 标志、（b）抢占 passblur Surface。此写入路径没有任何守护。**

### 2.2 我方 takeover 主动清了厂商在该窗口的状态（触发链之一）
- `MiuixGlassHook.suppressVendorGpuBlur(dockBg)` → `MiBlurBridge.clearPassWindowBlur(dockBg)` → `View.setPassWindowBlurEnabled(dockBg, false)`。
- `LauncherVendorBlurWritePolicy`：**负向写透传**（仅抑制正向）。⇒ 该调直达框架 ⇒ `ViewRootImpl.clearTextureView(dockBg)` ⇒ 若 map 清空 ⇒ `mTextureVis=false` ⇒ `sendSfState(2)` ⇒ **`setUpdateTextureFlag(FloatingDockSC, false, …)`：我方 producer 在绑定后被自家 takeover 路径关停**。
- 设备 log 实证顺序（10:43:20）：`vendor parent GPU blur disabled class=HotSeatsListContentBlurBackground2`（20.136）发生在本窗口 dock bind（20.804）之前/附近；bind 时写 true，之后无重申机制。

### 2.3 仓库内既有同类修复范式（应套用）
- `SecurityCenterPassBlurContinuousAuthority` / `MiuiSearchboxPassBlurContinuousAuthority` / `GboardPassBlurContinuousAuthority`：
  hook 框架 `SurfaceControl.Transaction.setUpdateTextureFlag` + `SetPassBlurSurface`，对已认领 root 强制 `(true, claim.scale)`、压制异面 Surface 抢占；`release()` 于 unbind。
- 三个域均有；**Dock 域没有对应 Authority**。
- 历史：Security Center 于 09-13 以同病同修上线（`fix: preserve Security Center PassBlur output authority` 等 3 连提交），问题描述即“vendor 在页面切换期间改 updateTextureFlag/scale 并重绑 SetPassBlurSurface”。

### 2.4 原生参考（Phase 4 存档）
- 原生 dock 模糊 = 窗口级 passblur：`HotSeatsListContentMiuiXBlurBackground`（os3-launcher-pad-4.50.0.1204 快照）构造时 `MiuiBlurUtils.setPassWindowBlurEnabled(this, true)`；`MiuiBlurUiHelper.applyBlur` 控制。
- 原生消费同步（ViewRootImpl `lambda$new$22`，L27845–27874）：passblur 线程每帧回调 ⇒ `mView.setTextureAvailable(true, rot, scale)` + **`mView.postInvalidate()`**——即“frame available 直接驱动 consumer 重绘”，与 View 是否静止无关。
- `setForceRefresh(sc, timeoutMs)`（`sendAuxiliaryIfNeed`, L28043+，带 `MAX_FORCE_REFRESH` 上限）是框架提供的“限时强制刷新”补充通道。
- 结论：框架在“无任何 blur view 需要”时把 update 标志降 false 是**有意的省电语义**（任务书第 2 问“为什么当时这么优化”的框架侧答案）；正确做法不是取消该语义，而是**我方在用该 SC 供 dock 玻璃期间守护我方契约**（与 SecurityCenter 先例一致）。

### 2.5 排除项
- 绵狗 V13（pull 于 08-14, manifest `V13-4.50.0.1115-1118`）的静态 Dock 快照子系统：`DeviceConfig.isMingouStaticDockBlurEnabled()` 恒 `false`（硬禁用，方法仍在）；快照 overlay 路径（apply/reset/setMingouStaticDockSnapshotMode）全部被该开关短路。设备 log 里 vendor snapshot 两个 hook 失败（方法缺失/版本差异）与之一致——**不作为本次根因**。
- 仓库内 v2.4.0→v2.6.2 对 Dock 链路净变化极小（PBTX +18/−7、ZeroCopy ≈0、Bridge ≈0），**无刷新语义回归提交**。

## 3. 诊断结论表（Phase 16）

```
Current hypothesis:
Floating Dock SC 的 update 契约（updateTextureFlag / SetPassBlurSurface）无守护：
该浮窗 ViewRootImpl 的 passblur 状态机（register/clear/visibility 事件）可把标志改 false
或把 passblur Surface 抢占为 vendor mSurTex，从而在「Dock 静止」阶段停供我方 producer 帧。
「Dock 移动」阶段厂商窗口状态机处于活跃分支（vis=true），标志被持续写回 true，因此表现正常。

Evidence for:
- 框架源码：sendSfState 写 setUpdateTextureFlag(sc, mTextureVis) / 条件重绑（行号见 §2.1）；
- 我方 takeover 存在负向写链路（clearPassWindowBlur → setPassWindowBlurEnabled(false)，透传）；
- Dock 域无 Authority（同类问题在 SecurityCenter 已有先例与修复范式）；
- 设备 log：dock bind 为 Floating Dock 独立 SC，且发生在我方清态动作之后，无后续重申。

Evidence against:
- 尚无设备侧直接观测（未经 hook 记录该 SC 上真实发生的 setUpdateTextureFlag/SetPassBlurSurface 写入序列）。

Next discriminating test:
(1) 观察钩子：记录 Floating Dock SC 上全部 setUpdateTextureFlag / SetPassBlurSurface 写入（值+调用者标记+时间），
    与 Dock 静/动状态、producer 供帧中断对齐；
(2) Case1/Case2 统计 producer/consumer fps 与 producer→draw 延迟；
(3) 若确认 = 标志翻转 ⇒ 实施 Dock 域 Continuous Authority（复用既有范式）。
```

## 4. 修复候选（Phase 9 预备，最小实现）

> P9 修定（2026-10-05）：最小修复改用 §8 的 force-refresh 抓手（续期，见 §9）；本节 Authority 方案 A/B 暂缓不实施（哨兵：DockScContractTrace 观察钩子；若复验现翻转再启用）。

**方案 A（首选）— Dock 域 Continuous Authority**：新增 `DockPassBlurContinuousAuthority`（结构复制 `SecurityCenterPassBlurContinuousAuthority`）：
- dock bind 时 `claim(rootSurface, producerSurface, scale)`；unbind / 玻璃关闭时 `release`；
- hook 框架 `setUpdateTextureFlag`：对已认领 root 强制 `(true, claim.scale)`（记录被改写值）；
- hook `SetPassBlurSurface`：压制对已认领 root 的异面重绑；
- 不引入定时器/轮询；SF 供帧保持按内容变化驱动（空闲时 producer fps→0，不做空转）。
**方案 B（备选，仅在观测显示仅存在标志翻转、无抢占时）**：只做 `setUpdateTextureFlag` 守护，不碰 rebind。

## 5. 待真机判别实验（Phase 5 诊断先行，Debug 门控）

- `[DC][DockFrameSync]`：producer 帧序号/间隔/回调线程、consumer 绘制序号/updateTexImage 时间、producer→draw 延迟、coalesce/drop 计数；每 30/60 帧或延迟 >25ms 输出一次窗口统计。
- `[DC][DockFrameSync][SC]`：Floating Dock SC 上 setUpdateTextureFlag/SetPassBlurSurface 写入观察（观察模式，不改变行为）。
- Case1（Dock 动）/ Case2（Dock 静 + 背景动）对照；状态字段：dock moving/stationary、workspace moving/stationary、updatesEnabled、freezeReason。

## 6. 回归清单（Phase 10，全部要过）

Dock 静止显示、图标拖动后尺寸动画、图标玻璃随尺寸、Workspace 玻璃、Recents、文件夹开合、深色/横竖屏、翻页、Launcher 重启、熄屏/解锁、TextureView attach/detach、session 重建。**特别保护：Dock 尺寸变化后图标玻璃随新尺寸更新（此前修复过，勿回归）。**

## 7. 反编译产物存档

- 已存回反编译库：`/mnt/extra/outputs/hyperos-analysis-passblur-publish/docs/passblur/notes-2026-10-05-dock-floating-window.md`（Floating Dock 窗口拓扑 + VRI passblur 全语义 + 行号索引）。
- 关键材料位置：framework ViewRootImpl 全量 `decompile-keep/o_android_view_ViewRootImpl/sources/android/view/ViewRootImpl.java`；厂商 launcher 全量 `hyperos-analysis-full/full-jadx/pad-launcher-4.50.0.1204/launcher/sources/`；绵狗 V13 全量 `android-dev/mingou_v10_jadx/sources/`。

## 8. Phase 8：÷2 半率闸定位（turner 实机库反汇编）

- 基准：K Pad（turner）实机库 `/system_ext/lib64/libsurfaceflinger.so`（sha256 `407be876…`），单函数反汇编 + PLT 交叉核对。
- 定位：`MiOutputManager::drawPassBlurIfNeed`（0x54d2d0）内的重绘时间门（0x54deb0–0x54df50）：
  - 每 PassBlur 对象持有重绘间隔 P=[pb+0xf8]：构造器（0x4216a4 读属性、0x4216b8 乘 1e6）取 `persist.sys.sf.draw.texture`（默认 30）×1e6 ns；本机该属性 = 14 ⇒ **P = 14 ms**。
  - 放行顺序：`now < [pb+0x70]`（force-refresh 期限）⇒ 直接放行；否则按 scale=[pb+0x6c] 判时间：
    - **scale == 1.0**（本 dock 绑定值）⇒ 放行条件 `now > [pb+0xa8] + P/2`（**7 ms**）；
    - scale != 1.0 ⇒ 基准阈值 P，另按标志位附条件放行 P/2、P/4。
  - [pb+0xa8] = 上次重绘时刻（提交点 0x54e0b8 写入）。
- **÷2 推导**：165Hz 帧距 6.06 ms < 7 ms ⇒ 重绘只能隔帧落位 ⇒ 源产帧 ≈82.5 fps = 165/2（与实测 82.5–83.6/s、比率 0.5 吻合）；内容无变化时不放行（与静止段近乎零产帧一致）。
- 候选裁决：2×VSYNC 硬节拍（否）、帧号隔帧推进（否）——**÷2 = 间隔时间门 P/2**。
- 旁路（Phase 9 抓手）：`PassBlur::setForceRefresh(ms)`（0x422380）写 [pb+0x70] = now + ms×1e6；窗口内时间门直接放行 ⇒ **活跃期续发 force-refresh 即恢复满率**（框架 VRI `sendAuxiliaryIfNeed` 同语义）。事务侧同字段：setClientState（0x421cf0，layer_state +0x5e4）。
- Bg 路径：`releaseCurRes`（0x54e4b0）每帧收割绘制 futures，未完成 → `"next Frame, will drawPassBlur again."`（下帧重试）；入口 `BackgroundExecutor::bgDrawPassBlur`（0x348b70），渲染 `drawPassBlurInternal`（0x54eba0）。
- 产物：`/mnt/extra/outputs/passblur-turner-20261005/disasm/`（单函数反汇编、PLT 解析）；`/mnt/extra/outputs/dock-frame-sync-decompile/`（blk-timegate.txt 等切片）。

## 9. Phase 9：最小修复实施（force-refresh 续期）

- 方案（由 §8 结论定）：活跃期保持 SF force-refresh 窗口（[pb+0x70]）不熄灭 ⇒ 绕开 ÷2 重绘时间门（P/2 = 7 ms）；实现为事件驱动续期（producer 帧到达即续）——无定时器、无轮询、空闲自动过期。
- 实现（`fix/dock-frame-sync`）：
  - `Miuix307PassBlurBridge`：bind 时解析 `SurfaceControl$Transaction#setForceRefresh(SurfaceControl, int)`（设备 framework.jar classes4.dex 已核对存在）；新增 `renewForceRefresh(Binding)` —— 仅 DOCK 域、需 `bound && updatesEnabled`；lease 250 ms、发送节流 ≥50 ms（活跃期 ≤20 tx/s）；失败降级 = 捕获异常、仅限频日志（≥5 s 一条），不影响其它路径。
  - `Miuix307PassBlurTextureView`：`onFrameAvailable`（render 线程）调用 `renewForceRefresh(binding)`。
  - 语义依据：native `PassBlur::setForceRefresh`（0x422380）写 [pb+0x70] = now + ms×1e6，窗口内时间门直接放行；框架 VRI `sendAuxiliaryIfNeed`（L28043+）为同款通道（`tr.setForceRefresh(mSurfaceControl, mMimeoutMs)`）。
- Continuous Authority 候选（修定）：**本轮不实施**——依据：演示段零 SC 契约写入（未见翻转）、÷2 已完整解释症状、最小化约束；保留 `DockScContractTrace` 观察钩子作哨兵，若 Phase 10 复现契约翻转再按 SecurityCenter 模板启用。
- 待办（Phase 10）：真机复验——Dock 静止+背景移动期 pFps 应接近满率（原 82.5/s）；静止段仍近零产帧；延迟/功耗对照；回归清单（§6）。
