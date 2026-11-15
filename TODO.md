# LiquidDock TODO

## HyperOS 4 (OS4)

- [ ] **继续消除 `layout_runtime.h` 中对 `ItemInfoModel` 的 Dart 堆偏移读写（FRB 落库边界迁移）**
  - **现状**：已通过 `os4/native/frb_boundary_adapter.h` 拦截 `Vec<ItemInfo>::sse_encode` 接管冷启动加载推送与 `4×2` 小组件状态写入，旁路了 `layout_load.h` 中的 Dart `ItemInfo` DTO 堆偏移读写（`+0x1f/+0x23/+0x27`）。
  - **方案**：在 `Vec<ItemInfo>::sse_decode`（Dart → Rust 批量落库 `launcher_model_update_item_database_batch`）边界接入同样的零硬编码结构体布局解析，将旋转/保存时的权威布局同步与事务写回迁移至 FRB 边界，逐步淘汰 `layout_runtime.h` 中的 `ld_process_mid` 及 `ItemInfoModel` 堆偏移读写；同步评估将 `glass_filter_hook.h` 与 `glass_superellipse.h` 整理为独立的 Engine 材质适配层。
- [ ] **OS4 文件夹展开后居中玻璃背景绘制（对齐 v2.7.9 `FolderCling` 视觉）**
  - **背景**：`_openFolderIconBgAnim` 是文件夹图标自身的展开动画，锚点跟随桌面图标位置而非居中容器；OS4 桌面运行在纯 Flutter（`SurfaceView` + `libhyper_os_flutter.so`）下，原生 Android View 无法插在全屏模糊（`FolderClingBlurWidget`）与文件夹图标网格（`FolderGridView`）之间。
  - **方案**：在 `libapp.so` 中通过 `.gnu_debugdata` 符号跟踪文件夹打开/关闭状态及居中宽高（`FolderClingWidget.getFolderClingWidth`、`FolderGridViewGetxController.calGridHeight`、`FolderClingGetxController.headerHeight`），并在 `libhyper_os_flutter.so` 的 `FolderClingBlurWidget` 之后、`FolderGridView` 之前调用 `Canvas::Save` → `Canvas::ClipGeometry`（居中超椭圆）→ `Canvas::DrawHyperMaterial`（复用全局 `native_glass`）→ `Canvas::Restore`。
- [ ] **将剩余 OS4 AOT 字节指纹（含 `GetStream.add` 等）升级为 `.gnu_debugdata` 符号解析**
  - **背景**：目前 `glass_targets`、`glass_observers`、`rv_targets` 等已通过 `Os4GlassSymbols` 从 `libapp.so` 的 `.gnu_debugdata` 符号表按函数名解析；但 `GetStream.add` 及部分 `icon_glass_fingerprints.h` / 小组件容器目标仍依赖 `ld_find_aot_pattern` 字节指纹，桌面大版本升级后可能出现多处匹配歧义或 0 匹配导致功能静默退化。
  - **方案**：将 `GetStream.add` 等剩余关键符号纳入 `Os4GlassSymbols` 符号表解析并随 `liquiddock-native.conf` 下发，AOT 字节指纹仅保留作兜底。
- [ ] **原生 Android `RemoteViews` 小组件扫描与隐藏支持**（如智能助理 `com.miui.personalassistant` 的股票行情、快捷功能，以及日历 `com.android.calendar` 等）
  - **背景**：OS4 桌面（`com.miui.home`）运行在 `hyos_spawner`（纯 Flutter/Dart + Rust，无 ART 虚拟机），Java 层 Xposed Hook 不加载；且其 `RemoteViews` 小组件由 `libapp.so` 内的 Dart 代码（`LauncherAppWidgetHostViewState` / `[AppWidgetHostView] updateAppWidgetImpl: INFLATE path`）直接解析和渲染，不使用系统 `android.appwidget.AppWidgetHostView` View 树。
  - **方案**：在 `os4/native`（`libliquiddock4.so`）中逆向 `libapp.so` 的 Dart AOT `AppWidgetHostView` / `updateAppWidgetImpl` 解析与渲染管线，实现原生层的组件节点扫描、名称映射与隐藏/样式规则下发。
- [ ] **增加「小组件重缩放到图标网格大小」开关**（OS4 网格基础设置）
  - **背景**：当前未修改水平偏移时小组件已沿用系统原生宽度；若希望所有小组件（含 `1×1`、`2×1` 等）像手机端一样按图标网格宽度（`(span - 1) * cell_width + icon_size`）对齐缩放，需在 GUI 网格基础页增加独立开关并通过 `liquiddock-native.conf` 下发至 `home_widgets.h`。
- [ ] **横屏模式下 Dock 高度自定义视觉上限研究**
  - **现象**：在平板横屏模式下，`DockBackgroundLayoutParams.size.height`（`_recomputeDockBgParams` / `_applyDiffWithAnimation`）以及 `_DockLightContentState._stableHeight`、`GlobalHotseatWindowManager.dockWindowHeight` 均已加上 `dock_geometry_height`（如 `86.2 -> 139.0`），宽度和圆角均正常生效，但屏幕上的 Dock 背景玻璃高度仍停留在约 `86.2dp`。
  - **方向**：排查主屏 `HotseatLayerPad` / `DockLightContent` / `HotseatWidgetPad` 容器的布局约束（如 `Positioned` / `SizedBox` / `LayoutBuilder` 传入 `DockBackgroundView` 的 `maxHeight` 限制）。
