# LiquidDock OS4 TODO

## 1. HyperOS 4 横屏预旋转下 `ComposeTextures` 采样 `snap` 旋转 90° 的引擎缺陷

- **现象**：平板横屏（逻辑 `3008×1880`，Vulkan 预旋转缓冲 `1880×3008`）下，当桌面小组件（原版 `DlGlassImageFilter::pass_blur_rate == 0`）被强制改为 `pass_blur_rate = -1` 以绕过 `0.25x` 共享缓存时，`Canvas::AcquireGlassBlurTextures` 会额外回读当前 `EntityPass` 渲染目标 `snap`（`1880×3008`）并传入 `MiHyperMaterialEngine::ComposeTextures`。
- **根因**：
  - `ComposeTextures`（`libhyper_os_flutter.so`）采样实时壁纸 `wp`（`PassBlurState::ConsumePendingAHB`，`470×752`）的 UV 方向正确（0°），但采样 `snap`（`InlinePassContext::GetTexture()`，`1880×3008`）的 UV 在横屏预旋转场景下存在 **90° 转置（`x <-> y`）**。
  - 任何绘制顺序晚于其他组件、且其在 `1880×3008` 缓冲空间的 `(x_buf, y_buf)` 与前面组件转置后 `(y_buf, x_buf)` 重叠的区域（典型即首页左上角堆叠小组件）都会直接采样到旋转 90° 的其他桌面图标/小组件（无论静止还是开关文件夹缩放动画期间）。
  - 原版 HyperOS 4 未暴露此缺陷是因为桌面组件 `pass_blur_rate == 0`，原版 `AcquireGlassBlurTextures` 在 `pass_blur_rate == 0 && wp != NULL` 时直接跳过 `EndPass()` 令 `snap = NULL`。
- **当前处理**：
  - 保留完整实时 `snap` 采样（保证拖动文件夹/小组件时实时折射下方内容不失效），保留大文件夹首帧 vtable 学习修复与无二次下采样（`s0 = 1.0`），暂不做 `snap = NULL` 屏蔽。
- **后续根治方向**：
  - 反汇编 `MiComposeTexturesFragmentShader` / `ComposeTextures::$_1` 的 `ComposeInfo` UBO 矩阵计算，定位为何横屏预旋转下堆叠小组件采样 `snap` 时出现 90° UV 转置，而拖动文件夹时却能正常显示下方内容，从 UBO/坐标层面直接根治。
