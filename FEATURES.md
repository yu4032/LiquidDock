# LiquidDock 功能手册

本文档按当前 `main` / **v2.2.1** 整理。当前 Liquid Glass 主线目标环境为 **HyperOS 3.0.307+ / `com.miui.home` release-4.50.x.x / MiuiX PassBlur + OES/GLES zero-copy**。

旧 ScreenCapture / bitmap readback 实现只保留在 `archive/1.x`。

---

# 桌面网格 (Grid)

主开关 `home_grid_8x4` 启用后，桌面使用横屏 8×4 / 竖屏 4×8 自定义网格，并保留 orientation-specific placement memory。

当前主要能力：

- 横/竖屏独立水平、顶部、底部距离调整；
- 横/竖屏独立行距；
- 页面指示器垂直位置；
- lazy/off-screen page 在显示前准备目标方向 geometry；
- `WorkspaceDropRuleHook` 扩展 custom-grid 合法坐标，但不接管 MIUI occupancy matrix。

## Widget grid adaptation

- 优先使用 `ItemInfo.isWidget()`；fallback item type 当前包含 `4 / 5 / 19`；
- 显式适配 1×1、2×1、2×2、4×2；
- 只修改 allocation/frame，不修改 MIUI placement / occupied matrix；
- 属于结构 Hook，启停需要重启 Launcher。

---

# Dock 外观

支持：

- Dock 宽度/高度/底部偏移；
- 图标间距与背景宽度补偿；
- 原生 blur amount；
- Round / Squircle / Fill-Diff；
- Dock foreground stroke；
- whole-Dock shadow；
- Dock resize animation / LiquidDock smooth resize；
- Dock 镜像快捷方式显示控制；
- Workstation Dock 的独立参数。

`DockStrokeRenderer` 持有 foreground ownership；关闭描边后恢复保存过的原 foreground。Whole-Dock shadow 与历史 stroke-shadow 是独立能力。

## Dock Glass 实时性

Dock Liquid Glass 使用 MiuiX PassBlur zero-copy producer，**bind 后保持 continuous 更新**。它不是静态壁纸缓存，也不会因为 Workspace idle/Recents 优化而改成单帧捕获。

Workspace shared producer 可以在明确生命周期中 pulse/pause，但该策略不能应用到 Floating Dock。

---

# Liquid Glass

## 支持对象

当前支持：

- Dock background；
- Workspace 图标（建议透明图标主题）；
- 部分 RemoteViews Widget；
- MAML Widget；
- 小文件夹 / 大文件夹；
- Workspace drag node；
- Dock / Workspace app-launch proxy。

Workspace 静态对象共享 root-wide `LauncherGlassSession` / StaticLayer，而不是每个组件维护独立 PassBlur producer。

## 光学参数

Prismal renderer 当前覆盖：

- backdrop / optical blur；
- glass thickness；
- IOR；
- normal strength；
- dome / convexity；
- lens refraction；
- chromatic dispersion；
- depth effect；
- brightness；
- tint RGB / alpha；
- specular sharpness / strength；
- rim light；
- caustics；
- edge band / edge thickness。

`liquid_highlight_width` 当前表示 Prismal 物理边缘带厚度，不再额外绘制独立 Canvas 描边。

## Overscan

GPU overscan 与最终输出 coverage 分离。上下左右额外 overscan 用于强折射时访问可见区域外的 backdrop 像素，不会因为玻璃靠近屏幕/Dock 边缘就提前截断输入采样。

---

# HOME / APP / Recents 动画

## SystemUI timing source

LiquidDock 会注入 `com.android.systemui`，但只读取系统级 transition timing：

- WMShell HOME transition；
- Keyguard Gone。

SystemUI 不参与 Glass 捕获/渲染。Launcher 4.50 的 `WindowElement` HOME animation 仍保留为 fallback。

## App → Home

普通 App→Home：

1. HOME START 冻结新 backdrop generation；
2. 已准备的 static scene 可以随系统 HOME 动画提前淡入；
3. HOME FINISH 解除 fresh-capture barrier；
4. 新 OES frame 到达后替换 scene，不做第二次 fade。

这样避免因为等待 capture 而错过系统动画，同时防止 transition 中途 backdrop 代际跳变。

## Widget → App

Launcher 4.50 的 Widget 使用独立 `WidgetTypeAnimTarget`。LiquidDock 在 vendor 隐藏 Widget 内容前接管 Glass visibility，使 Widget Glass 正常 `1 -> 0` 淡出，并在淡出期间保留最后有效 geometry。

## App → Widget

返回目标一旦被识别为 Widget：

- 立即隐藏该 Widget 在旧 StaticLayer 中的 Glass；
- 不允许 cached HOME scene 先显示该大块 Widget；
- HOME barrier 解除后继续等待当前 scene generation 的 fresh render；
- fresh HOME backdrop 真正可见后才 `0 -> 1` 淡入 Widget Glass。

因此不会出现“旧背景先出来一下，再突然跳到新背景”的大面积 Widget 缓存跳变。

## Recents

`LauncherGlassRecentsHook` 使用 HyperOS semantic Recents dispatcher，而不是根据某个 View attach/focus 猜测场景。Workstation 返回还有 shared producer rollover 恢复路径。

---

# Workspace 页面滑动与边缘裁切

v2.2.1 起，StaticLayer 使用完整 shape geometry：

- 圆角矩形部分滑出左/右/上/下边缘时，原始 width/height/center/radius 均保持不变；
- framebuffer 自然裁掉屏幕外像素；
- 完全离屏后才 cull；
- 不再出现一侧“吸”在屏幕边缘、只剩长度缩短的现象。

这一规则只用于 Workspace StaticLayer。确实需要 visible-crop 的 sink/drag 路径仍使用旧 clipped geometry。

---

# Widget 背景自定义与组件发现

Widget component discovery 支持 RemoteViews 与 MAML。

## RemoteViews

按真实 View tree 扫描，可发现：

- View background；
- ImageView drawable；
- 可隐藏的子 View；
- hierarchy path / class / resource identity。

## MAML

不会只依赖 `ScreenElementRoot.mElements` 名字索引，而是从：

```text
ScreenElementRoot.mInnerGroup
    -> ElementGroup.mElements
```

遍历真实 render tree，因此匿名/未加入 name map 的视觉元素也可进入诊断目录；命名元素仍保留稳定 name-based selector。

## Render 元数据

重新“载入当前小组件”后，目录会显示：

- **Render #N**：真实遍历/渲染顺序；
- **Depth**：距离 render/View root 的深度；
- **Area %**：零件占 Widget 根面积比例；
- **Z**：RemoteViews 使用 `View.getZ()`；MAML 无可靠 Z 时显示未知。

## 疑似底层背景

`WidgetComponentRanking` 综合：

- background/image/container 类型；
- Area；
- Render #；
- Depth；
- Z；

给出背景置信评分。达到阈值的“疑似底层背景”会：

1. 在 Widget 详情页单独放到最前；
2. 在具体组件类型列表中继续优先排序；
3. 显示诊断元数据，便于人工确认。

**排序不会改变 selectorKey。** 已保存的 RemoteViews/MAML 隐藏规则继续兼容；旧 catalog 没有 metadata 时只显示未知，不会被误判为背景。

---

# Runtime ownership

## 可即时生效 / 释放

- icon/widget/small-folder/large-folder glass；
- Dock customization 的视觉 ownership；
- Dock stroke；
- Dock shadow；
- Divider；
- Squircle / Fill-Diff renderer refresh。

Runtime disable 遵循“先发布 disabled，再 teardown ownership”，防止已排队 callback 在释放后重新 claim。

## 需要重启 Launcher

- LiquidDock master switch 的完整启停；
- custom home grid；
- widget grid adaptation；
- Dock resize-animation Hook selection；
- LiquidDock smooth resize Hook selection；
- Workstation composite customization；
- 其它结构性 Hook。

---

# 设置页重启按钮

- 根页面：显示“重启系统桌面”和“重启系统界面”。
- 子页面：仅显示“重启系统桌面”。

SystemUI restart 保留在根页面，是因为 SystemUI 当前承担只读系统 transition timing source，而不是因为它参与 Glass capture。

---

# Workstation / Laptop（实验性）

已有能力包括：

- Workstation 状态检测；
- Dock width / icon top-bottom offset；
- Workspace horizontal offset；
- All Apps 横/竖屏 offset；
- Divider 自定义；
- Workspace shared producer suspend/pulse/rebind policy；
- Recents shared-glass recovery；
- 普通布局 backup/restore。

工作台整体仍需要持续真机回归，不能因为某个子模块可 live restore 就视为完整热切换支持。

---

# 兼容性与失败模式

当前 Liquid Glass 依赖 HyperOS 3.0.307+ 的 MiuiX PassBlur、隐藏 Surface/SurfaceControl 行为和 Launcher 4.50.x.x 内部类。

因此：

- ROM / Launcher 升级后若私有 API 变化，相关 Hook 可能 fail-closed；
- zero-copy glass 不会回退到 ScreenCapture；
- Widget glass 仍属于按实际 provider/结构逐步适配；
- Workstation / Laptop 仍是实验性路径；
- 解锁后 HOME wallpaper freshness 仍属于高风险、需要持续真机验证的区域，详见 [TODO.md](TODO.md)。
