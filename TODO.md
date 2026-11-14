# LiquidDock TODO

## HyperOS 4 (OS4)

- [ ] **原生 Android `RemoteViews` 小组件扫描与隐藏支持**（如智能助理 `com.miui.personalassistant` 的股票行情、快捷功能，以及日历 `com.android.calendar` 等）
  - **背景**：OS4 桌面（`com.miui.home`）运行在 `hyos_spawner`（纯 Flutter/Dart + Rust，无 ART 虚拟机），Java 层 Xposed Hook 不加载；且其 `RemoteViews` 小组件由 `libapp.so` 内的 Dart 代码（`LauncherAppWidgetHostViewState` / `[AppWidgetHostView] updateAppWidgetImpl: INFLATE path`）直接解析和渲染，不使用系统 `android.appwidget.AppWidgetHostView` View 树。
  - **方案**：在 `os4/native`（`libliquiddock4.so`）中逆向 `libapp.so` 的 Dart AOT `AppWidgetHostView` / `updateAppWidgetImpl` 解析与渲染管线，实现原生层的组件节点扫描、名称映射与隐藏/样式规则下发。
- [ ] **横屏模式下 Dock 高度自定义视觉上限研究**
  - **现象**：在平板横屏模式下，`DockBackgroundLayoutParams.size.height`（`_recomputeDockBgParams` / `_applyDiffWithAnimation`）以及 `_DockLightContentState._stableHeight`、`GlobalHotseatWindowManager.dockWindowHeight` 均已加上 `dock_geometry_height`（如 `86.2 -> 139.0`），宽度和圆角均正常生效，但屏幕上的 Dock 背景玻璃高度仍停留在约 `86.2dp`。
  - **方向**：排查主屏 `HotseatLayerPad` / `DockLightContent` / `HotseatWidgetPad` 容器的布局约束（如 `Positioned` / `SizedBox` / `LayoutBuilder` 传入 `DockBackgroundView` 的 `maxHeight` 限制）。
