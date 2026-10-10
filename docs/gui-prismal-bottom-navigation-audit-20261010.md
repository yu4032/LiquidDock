# Prismal 底栏集成审查（2026-10-10）

**对象**：设置 GUI 底栏 `ModernBottomNavigation`，不是桌面的 LiquidDock。基线 `main@030d4c2b`；PrismalAGSL 版本固定为 `v1.0.4`。对照上游源码：
- `Prismal/src/main/java/com/styropyr0/prismal/components/PrismalGlassBottomTabs.kt`
- `Prismal/src/main/java/com/styropyr0/prismal/components/PrismalGlassBottomTab.kt`
（仓库 `styropyr0/PrismalAGSL` 的 `v1.0.4` tag）

## 结论

**原生玻璃底栏存在，但不是完整的原生 Prismal 视觉集成。** 现有实现有意绕开了库对图标文字的原生渲染/折射，以避免过去在长按或拖动时出现的双重字符重影。不得简单恢复两份文字或取消外层单遍绘制。

### 已经接入并保留

- `ModernSettingsScaffold` 仍采集 `backgroundLayer` 与 `screenLayer`，通过 `rememberPrismalMergedSource(backgroundLayer, screenLayer)` 把真实页面与背景提供给底栏（与顶栏共用）。
- `PrismalGlassBottomTabs` 本身未替换为普通 `Row`；其 v1.0.4 实现包括玻璃背景的 backdrop blur、边缘 lens/refraction、specular、按压 ripple、胶囊形状以及 `PrismalSpringMotion` 选中/拖动回弹、拖动速度挤压形变。
- 原生 `PrismalGlassBottomTab` 保留点击命中范围与 `Role.Tab`；玻璃关闭时才使用纯色 fallback。
- 现有底栏单独渲染一次可见图标/文字，避免隐藏录制层中另一份文字参与高折射后叠加造成重影。

### 目前缺失或弱化的原生能力

1. **图文没有参与 Prismal 的原生 Tab 内容渲染。** `PrismalGlassBottomTab(onClick = ...) {}` 内容为空；标签和图标是外面的独立 `Row`。因此原生 `LocalPrismalBottomTabScale` 的按压缩放不会作用于它们。
2. **拖动胶囊时图文的即时高亮不同步。** 当前显示颜色用 `index == selectedIndex`；上游的 `LocalPrismalBottomTabHighlightedIndex` 跟随 `dragHighlightIndex`，但只在上游 Tab `CompositionLocalProvider` 内可用，外层 sibling `Row` 读取不到。所以高亮要等选中 callback，而不是跟随拖动中的候选位置。
3. **胶囊不会折射图标文字本身。** 上游的第二份隐藏 Tab 内容通过 `tabsBackdrop` 被胶囊采样。内容为空时，这个采样源没有图文。底栏背景仍可以折射页面；缺的是原生 *内容与玻璃交互*，不是整个玻璃效果消失。
4. **选中胶囊的部分高级光学仅在按住/拖动时增强。** 上游 v1.0.4 的 droplet lens 高度、折射量、specular、depth shadow/inset 等乘 `pressProgress`；静止时视觉会相对平淡。这属于库默认策略，并非 LiquidDock 关闭了 shader。背景 pill 的基础 lens/blur/specular 仍持续存在。
5. `tintDropletContent=false` 是原生合法开关，用于保留图文固有颜色；由于传入的 native Tab 内容为空，该参数目前无法让图文参与 droplet 着色。不可把它误判成整个折射被禁用。

### 安全的后续修复方向（本批不改视觉行为）

- 保持**可见字形始终只有一份**和一次有效的 click/drag 手势命中；不能同时在上游 native content 与独立 overlay 中完整显示同一文字。
- 若要在不修改上游的前提下恢复原生拖动高亮，应在底栏实现内建立轻量 `drag-highlight index` 状态桥接，让可见单遍图文根据真正的手势目标更新，而非恢复上游双层图文。需要确认回弹、切页取消和长按时的状态一致性；不能通过额外屏幕捕获推断。
- 若目标还包括**镜片折射图文**，需设计一个单源视觉 / 分层内容屏蔽方案，避免再次把重复字形送入隐藏的 `tabsBackdrop`。优先用局部 Compose draw/capture 控制，而不是新建第三个全屏录制器；未经真机验证不能宣称重影已修复。
- 保留原本 Prismal 的按压/速度形变、胶囊高光、原始合成层。不得更改 upstream PrismalAGSL，亦不启用 `ScreenCapture`、`PixelCopy`、`Bitmap`。
- 建议以**两个独立小 PR**评估：A. 仅恢复拖动中标签高亮和适度原生 spring scale（无新采样）；B. 探索不重影的图文镜片。两项都需要真机视频、触摸测试和对比，单靠静态断言或编译不能证明。

## 本轮工作

`refactor/gui-prismal-bottom-navigation-20261010` 只将既有底栏函数原样迁入 `SettingsBottomNavigation.kt`，重启作用域弹窗迁入 `SettingsRestartScopesDialog.kt`；`ModernSettingsUi.kt` 仍负责现有 backdrop compositing/Prismal 容器。移动不会自动补齐上面缺少的视觉能力。审计发现列入 TODO，不以纯重构 PR 偷渡不易回归的触摸/图文变更。

## 后续实现记录：PR #318（待 CI 和真机确认）

- `SettingsBottomNavigation.kt` 在**原生** `PrismalGlassBottomTab` 的内容作用域内读取公开的 `LocalPrismalBottomTabHighlightedIndex`，借助 `SideEffect` 把候选 index 转交外层唯一可见图文层。拖动中根据候选 index 动态着色，并对跨 Tab 拖动候选应用轻量 spring scale；镜片、背景、触摸命中仍由上游 `PrismalGlassBottomTabs` 负责。
- 为避免隐藏采样再次包含字形，原生 Tab content 只有无视觉的索引观察器，**没有 Text/Icon**；不会恢复历史两份文字。只在第一个 Tab 注册报告逻辑，避免四个 Tab 每帧都重复提交同一索引。
- **限制**：这并不等同于完整原生 `LocalPrismalBottomTabScale` 的按压比例（其上游可见性为 internal），也不恢复 glyph 的 droplet lens 折射。不能仅凭 CI 判断拖动/长按无闪烁；须做 UI 实机验证。
- 与此同时 #318 增加的通用 MIUIX 弹窗过渡采用 Compose spring/tween，而不是声称 Prismal v1.0.4 自带 modal 组件；共享采样链不增加任何 layer。
