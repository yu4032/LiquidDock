# Deprecated Source / Historical Architecture

本文档不再维护已经删除源码的逐符号清单。

当前 `main` / **v2.2.1** 已完全迁移到：

```text
HyperOS 3.0.307+
MiuiX PassBlur + SurfaceTexture/OES + GLES
Prismal zero-copy renderer
```

旧的 ScreenCapture / bitmap readback / capture cadence / freeform capture exclusion / `DockLiquidGlassView` capture lifecycle 等 1.x 架构，已经从当前主线移除。

## 历史实现位置

需要研究旧实现时，请查看：

```text
archive/1.x
```

其中包含曾经使用过的：

- ScreenCapture / SurfaceFlinger bitmap capture；
- foreground app layer / freeform exclusion 推断；
- dynamic capture cadence；
- black-frame guard；
- APP/HOME capture source policy；
- capture settle / probe / retry；
- `DockLiquidGlassView` 截图时代职责。

这些内容只用于历史参考，不应重新接回 `main`。

## 当前主线替代关系

| 1.x 概念 | 当前 v2.x 对应 |
|---|---|
| ScreenCapture / bitmap backdrop | MiuiX PassBlur zero-copy producer |
| per-capture frame freshness | OES fresh-frame barrier / generation |
| capture scale / cadence | OES normalization 后的 local FBO scale + source-driven producer lifecycle |
| HOME/APP screenshot source policy | Launcher scene + PassBlur producer state |
| screenshot black-frame guard | fail-closed producer/freshness barrier |
| per-view capture owner | shared `LauncherGlassSession` |
| capture-era Workstation recovery | covered-gated Workstation producer rebind + bind epoch + fresh OES reveal |

## 禁止重新引入

除非未来有新的架构设计和明确设备证据，否则不要在 `main` 中重新引入：

- `ScreenCapture` fallback；
- PixelCopy / bitmap readback；
- 通过 `getRunningTasks()` / layer-name guessing 决定 glass source；
- 以普通 View redraw 代替 content freshness；
- 旧 capture cadence / black-frame heuristic 作为 zero-copy fallback；
- 为已收口的 Workstation Recents recovery 再增加第二套 episode/aggregate/freshness authority。

## 当前真正需要继续清理的内容

后续清理已经从“删除 capture shell”转为 ownership 收口：

- 将 Workstation mode / delayed recheck / normal-layout backup 从 `MainHook` 迁到单一 controller；
- 给 stale delayed callback 增加 generation/cancellation 保护；
- 引入 `WidgetClassifier` / `WidgetSpecRegistry`，移除 `WidgetGridSizing` static mutable config；
- 按真实 runtime ownership 缩小 `HomeGridHook`；
- 对 `LauncherGlassSession` / `Miuix307PassBlurTextureView` / Prismal 先做 resource owner graph，再决定最小 EGL/OES primitive；
- 缩小 `LEGACY_SOURCE_DEBT` 与 CI/i18n hygiene debt。

Workstation Recents shared-producer correctness 已有当前最小方案；除非出现新的现实失败，不再把“继续收紧 recovery API”作为默认清债方向。

请以 [TODO.md](../TODO.md) 为当前清理优先级来源；目标设计与第一阶段计划见：

- [Technical-Debt Cleanup Design](superpowers/specs/2026-09-07-technical-debt-cleanup-design.md)
- [Technical-Debt Cleanup Phase 1 Implementation Plan](superpowers/plans/2026-09-07-technical-debt-cleanup-phase1.md)
