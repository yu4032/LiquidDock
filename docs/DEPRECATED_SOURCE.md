# Deprecated Source / Historical Architecture

本文档不再维护已经删除源码的逐符号清单。

当前 `main` / **v2.1.1** 已完全迁移到：

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
| capture scale / cadence | GPU overscan + producer lifecycle |
| HOME/APP screenshot source policy | Launcher scene + PassBlur producer state |
| screenshot black-frame guard | fail-closed producer/freshness barrier |
| per-view capture owner | shared `LauncherGlassSession` |
| capture-era Workstation recovery | producer rollover/rebind + fresh OES reveal |

## 禁止重新引入

除非未来有新的架构设计和明确设备证据，否则不要在 `main` 中重新引入：

- `ScreenCapture` fallback；
- bitmap readback；
- 通过 `getRunningTasks()` / layer-name guessing 决定 glass source；
- 以普通 View redraw 代替 content freshness；
- 旧 capture cadence / black-frame heuristic 作为 zero-copy fallback。

## 当前需要继续清理的内容

当前真正的后续清理不再是“删除 capture shell”，而是：

- 收紧 Workstation shared producer recovery API；
- 将 `LauncherGlassSession` 的 producer/freshness/EGL/renderer 生命周期进一步拆分；
- 清理过时 compatibility facade / dead helper；
- 缩小 `MainHook` / `HomeGridHook` 职责。

请以 [TODO.md](../TODO.md) 为当前清理优先级来源。