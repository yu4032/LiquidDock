# Deprecated Source / Historical Architecture

本文档对应当前 `main` / **v2.5.0**，说明哪些旧架构已经退出主线。

## 1. 已退役的 1.x backdrop 架构

当前生产代码不再使用以下方案作为活动液态玻璃背景来源：

- ScreenCapture；
- SurfaceFlinger Bitmap readback；
- PixelCopy backdrop fallback；
- CPU backdrop bitmap pipeline；
- screenshot capture cadence；
- black-frame heuristic 作为主 freshness authority；
- 通过 running task / layer-name guessing 决定 HOME/APP source；
- 旧 `DockLiquidGlassView` screenshot lifecycle。

旧实现仅用于历史研究，不应重新接回 `main`。

## 2. 历史实现位置

需要研究截图时代实现时，请查看仓库的历史分支/归档，例如：

```text
archive/1.x
```

其中可能包含：

- ScreenCapture / SurfaceFlinger capture；
- foreground/freeform exclusion 尝试；
- capture cadence；
- black-frame guard；
- APP/HOME screenshot source policy；
- capture settle / probe / retry；
- 早期 Dock screenshot glass。

## 3. 当前替代关系

| 旧概念 | 当前主线 |
| --- | --- |
| Screenshot/Bitmap backdrop | native PassBlur source + OES/GPU |
| 每次截图判断新鲜度 | scene/wallpaper/producer generation + fresh source/output |
| 固定 capture cadence | source-driven frame lifecycle |
| APP/HOME screenshot source policy | Launcher/SystemUI transition authority + feature source domain |
| 黑帧 heuristic | fail-closed freshness/presentation barrier |
| 每个组件独立 screenshot owner | shared Launcher source + feature-specific session/output |
| 固定 Recents→HOME 等待 | Launcher/Wallpaper/Recents 的真实 lifecycle authority |

## 4. 当前独立 glass domains

v2.5.0 已不仅有 Launcher Workspace。当前 source domain 还包括：

- Dock；
- ShortcutMenu；
- Drag overlay；
- Launcher dialog；
- Recents operation capsules；
- SystemUI app-caption menu；
- Security Center；
- Gboard；
- MIUI Search。

这些 domain 的 lifecycle 不完全相同，不能因为底层都使用 glass source 就强行合并成一个旧式 capture manager。

## 5. 禁止重新引入

除非未来有新的架构设计和明确设备证据，不要在当前 active backdrop path 中重新引入：

- ScreenCapture fallback；
- PixelCopy fallback；
- CPU backdrop Bitmap；
- texture readback + CPU re-upload；
- fixed delay 代替 content freshness；
- 通过普通 View redraw 推断 wallpaper/source 已更新；
- 通过混淆成员名猜 source owner。

小型、有限、非 backdrop 用途的 Bitmap 不在此禁令中，例如 ShortcutMenu 图标颜色分类。

## 6. Historical superpowers documents

`docs/superpowers/plans`、`specs`、`verification` 保存各开发阶段的方案和验证记录。

它们会保留原始内容，并统一标记为 historical record。文件中的 TODO、类名、架构结论或失败方案都不能覆盖当前生产源码。

当前事实请优先查看：

- [README.md](../README.md)
- [FEATURES.md](../FEATURES.md)
- [ARCHITECTURE.md](../ARCHITECTURE.md)
- [HOOKS.md](../HOOKS.md)
- [TODO.md](../TODO.md)
