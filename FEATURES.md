# LiquidDock 功能手册

本文档按当前 `main` / **v2.1.1** 的实际实现整理。当前 Liquid Glass 主线仅支持 **HyperOS 3.0.307+ / `com.miui.home` release-4.50.x.x / MiuiX PassBlur + OES/GLES zero-copy**。

旧的 ScreenCapture / bitmap readback / `DockLiquidGlassView` 捕获链只保留在 `archive/1.x`，不属于当前功能。


---

## 桌面网格 (Grid)

主开关 `home_grid_8x4` 启用后，桌面使用横屏 8×4 / 竖屏 4×8 的自定义网格，并保留 orientation-specific placement memory。

| 参数 | 当前范围 | 说明 |
|------|------:|------|
| Widget adaptation | 开/关 | 独立控制 Widget frame 适配；关闭时保留 MIUI Widget 几何 |
| 横屏水平距离偏移 | −600 ~ 600 dp | 调整横屏左右水平方向距离 |
| 横屏顶部距离偏移 | −600 ~ 600 dp | 调整横屏顶部距离 |
| 横屏底部距离偏移 | −600 ~ 600 dp | 调整横屏底部距离 |
| 竖屏水平距离偏移 | −600 ~ 600 dp | 调整竖屏左右水平方向距离 |
| 竖屏顶部距离偏移 | −600 ~ 600 dp | 调整竖屏顶部距离 |
| 竖屏底部距离偏移 | −600 ~ 600 dp | 调整竖屏底部距离 |
| 横屏行距偏移 | −200 ~ 400 dp | 调整横屏图标纵向行距 |
| 竖屏行距偏移 | −200 ~ 400 dp | 调整竖屏图标纵向行距 |
| 横屏页面指示器 Y | −160 ~ 160 dp | 横屏页面指示器垂直偏移 |
| 竖屏页面指示器 Y | −160 ~ 160 dp | 竖屏页面指示器垂直偏移 |

### Widget adaptation 当前边界

当前活动实现：

- 优先使用 `ItemInfo.isWidget()` 判断；
- fallback item type：`4`、`5`、`19`；
- 当前针对 `1×1`、`2×1`、`2×2`、`4×2` 做显式适配；
- `setupLayoutParam()` 写入目标 allocation，`onLayout()` 后再次断言最终 frame；
- lazy / off-screen page 在实际显示前也会准备几何；
- 不修改 MIUI occupancy matrix / placement 所有权。

Widget 类型和 span 规则目前仍有硬编码，后续计划迁移到 `WidgetClassifier` / `WidgetSpecRegistry`。

---

## Dock 外观

| 参数 | 当前范围 | 说明 |
|------|------:|------|
| Dock customization | 开/关 | 普通 Dock 几何/模糊自定义总开关；视觉 ownership 可即时释放 |
| Dock resize animation | 开/关 | 是否保留 MIUI 原生 resize 动画；Hook 选择属于 restart-bound |
| 平滑 resize animation | 开/关 | 原生 resize 被禁用时使用 LiquidDock 平滑过渡；Hook 选择属于 restart-bound |
| 宽度偏移 | −80 ~ 80 dp | 相对系统 Dock 背景宽度增减 |
| 高度偏移 | −80 ~ 80 dp | 相对系统 Dock 背景高度增减 |
| 图标间距 | −8 ~ 12 dp | 调整相邻 Dock 图标间距，并补偿背景宽度 |
| 底部偏移 | −30 ~ 40 dp | 调整 Dock 与屏幕底部距离 |
| 原生模糊强度 | 0 ~ 400 | 原生 blur Dock 的模糊量 |
| 描边圆角偏移 | −50 ~ 100 dp* | `corner_offset`；历史兼容语义由 typed config 保留 |
| 内部模糊圆角偏移 | −50 ~ 100 dp | `blur_corner_offset` |
| 方圆形 | 开/关 | 使用 squircle 轮廓；已安装描边会即时刷新 |
| Fill-Diff | 开/关 | 使用 outer/inner 轮廓差形成描边；已安装描边会即时刷新 |

关闭 Dock customization 后，LiquidDock 会停止继续修改 vendor Dock View，并在已保存原状态的路径上释放自己的视觉 ownership；不会伪造未知的 MIUI 原生 shadow 参数。

---

## 描边 (Stroke)

当前描边由 `DockStrokeRenderer` 持有 foreground ownership。

| 参数 | 当前范围 | 说明 |
|------|------:|------|
| 描边开关 | 开/关 | `dock_stroke`；关闭后立即恢复原 foreground |
| 描边底色 R/G/B | 0 ~ 255 | RGB |
| 描边透明度 | 0 ~ 255 | 与 renderer 内部 alpha 共同形成最终透明度 |
| 方圆形控制点 | 40 ~ 80 | `sq_outer_cp` |
| 方圆形描边宽度 | 1 ~ 10 dp | squircle 模式宽度 |
| 方圆形外扩/内缩量 | 0 ~ 16 dp | `sq_stroke_off` |
| Fill-Diff 描边宽度 | 1 ~ 6 dp | `stroke_w` |
| 标准描边宽度 | 1 ~ 10 dp | `std_stroke_w` |

`SQUIRCLE` / `FILL_DIFF` 在运行中切换时会主动刷新已经安装的 renderer，不再等待下一次 Dock radius/config 更新。

### 描边阴影

历史 `stroke_shadow` / `shadow_radius` / `shadow_alpha` 配置仍保留兼容，但旧 overlay 描边阴影已经不再是当前 renderer 的正式实现。后续要么设计适配 foreground renderer 的新方案，要么正式标记为 deprecated。

---

## 整体 Dock 阴影 (Dock Shadow)

这与历史“描边阴影”是独立功能。

| 参数 | 当前范围 | 说明 |
|------|------:|------|
| Dock 阴影开关 | 开/关 | 独立整个 Dock shadow；支持即时释放 |
| 阴影柔和度 | 1 ~ 40 dp | `dock_shadow_radius` |
| 阴影扩散 | 1 ~ 60 dp | `dock_shadow_size` |
| 阴影浓度 | 0 ~ 200 | `dock_shadow_alpha` |
| 阴影 Y 偏移 | −24 ~ 24 dp | `dock_shadow_y` |

LiquidDock 启用自定义 Dock shadow 时会抑制对应 HyperOS 原生 shadow target；关闭后不再继续拦截 vendor 调用。

---

# Liquid Glass

## 当前架构

v2.x Liquid Glass 已完全迁移到 zero-copy 管线：

```text
HyperOS MiuiX PassBlur producer
        ↓
Surface / SurfaceTexture
        ↓
GL_TEXTURE_EXTERNAL_OES
        ↓
GPU normalization / overscan
        ↓
Prismal optical renderer
        ↓
Launcher / Dock output surface
```

核心原则：

- **zero-copy only**；
- 不使用 `ScreenCapture`、bitmap readback 或截图 fallback；
- PassBlur producer / OES / EGL / Prismal 全部保持 GPU 路径；
- vendor 私有接口不可用时 glass fail-closed，不退回 1.x 截图方案。

---

## 支持的 Glass 对象

当前支持：

- **Dock glass**：整个 Dock 背景；
- **桌面图标 glass**：建议配合透明图标主题；
- **Widget glass**：部分 Launcher Widget / MAML Widget；
- **小文件夹 glass**；
- **大文件夹 glass**；
- **拖拽 / launch proxy glass**：在 Workspace drag / app launch 动画期间跟随代理几何。

图标、Widget、文件夹的静态 glass 不再各自建立独立渲染器，而是共享一个 root-wide `LauncherGlassSession` / static compositor；拖拽输出继续使用同一 backdrop/source 生命周期。

---

## 核心光学

当前 Prismal 光学模型支持：

| 参数 | 说明 |
|------|------|
| Glass blur | GPU backdrop blur / optical blur 参数 |
| Glass thickness | 虚拟玻璃厚度 |
| IOR | 折射率 |
| Normal strength | 法线强度 |
| Dome / convexity | 穹顶凸起 |
| Lens refraction | 边缘折射偏移 |
| Chromatic dispersion | RGB 色散 |
| Depth effect | 深度透镜效果 |
| Brightness | 玻璃亮度 |
| Tint RGB / alpha | 玻璃底色 |
| Specular sharpness / strength | 镜面高光 |
| Rim light | 边缘光 |
| Caustics | 焦散 |
| Edge band | Prismal 物理边缘带 |
| Edge thickness | `liquid_highlight_width`，当前设置页语义为玻璃边缘厚度，50%–300% |

`liquid_highlight_width` 不再额外绘制一圈独立 Canvas 描边，而是直接缩放 Prismal 的物理光学边缘带；边缘使用 derivative anti-aliasing（`fwidth`）。

---

## Highlight Profile

Launcher glass 支持按对象类型选择 highlight profile，使 Dock、普通 Launcher glass 和大面积 surface 可以使用不同的高光组合。

当前 shader / renderer 可以分别控制：

- specular；
- rim light；
- caustics；
- edge band / edge thickness。

这套 profile 与共享 backdrop 分离，因此调整对象的 highlight 不需要建立新的 PassBlur producer。

---

## Zero-copy 采样与 Overscan

当前采样逻辑由 GPU overscan 和可见区域 coverage 分离：

- 固定 overscan 用于保证强折射时仍能访问 Dock / glass 边缘外的 backdrop 像素；
- 上 / 下 / 左 / 右额外 overscan 可独立配置；
- sample validity 与最终可见 coverage/scissor 分开计算；
- 不再存在 ScreenCapture resolution / capture FPS / black-frame threshold / capture cadence 等 1.x 参数的活动实现。

这样强折射靠近 Dock 边缘时，不会因为输出裁剪边界而提前截断输入采样。

---

## Freshness / Scene Lifecycle

共享 Launcher glass 使用明确的 scene generation / wallpaper generation / fresh-frame barrier 管理内容新鲜度。

### HOME / APP / Recents

- 离开 HOME 或进入 Recents 时，static layer 可以隐藏或暂停 producer；
- 返回 HOME 后先请求新的 backdrop；
- **只有新的 OES frame 已经被消费并完成 static render，才允许重新显示 glass layer**；
- 不会因为简单 `alpha=1` 或普通 redraw 而展示 stale Recents / APP frame。

### Wallpaper freshness

壁纸内容更新使用独立的 wallpaper generation token。scene generation 和 wallpaper generation 分离，避免几何刷新被错误当作壁纸内容刷新。

### Rotation

旋转期间会进入 settle barrier：

- 旧 producer endpoint 暂停；
- 等待 HyperOS / Shell rotation leash 完成；
- settle 后 rollover producer；
- 新 orientation endpoint 建立后才允许发布 fresh frame。

---

## Workstation Recents 恢复

HyperOS Workstation 可能在进入 Recents 后保留同一个“仍然 valid”的 Launcher `SurfaceControl`，但实际退役其 PassBlur BufferQueue producer。

v2.1.1 对此增加专门恢复：

1. `onRecentViewHide` 返回 HOME；
2. Workstation 模式下先 rollover shared Launcher glass producer；
3. 再解除 Recents covered；
4. static layer 继续等待新的 OES frame；
5. fresh frame 到达后才重新显示整个 shared glass layer。

这修复了“从多任务返回后整个 glass layer 消失、必须长按图标才能恢复”的共享 producer 生命周期问题。

> 该路径仍属于工作台实验适配的一部分，需要继续真机回归。

---

## Glass Runtime Ownership

v2.1.1 起，以下组件开关使用 live runtime state：

- icon glass；
- widget glass；
- small-folder glass；
- large-folder glass。

关闭组件时遵循：

1. 先发布 live=false；
2. 已排队的 `post` / `postOnAnimation` / RemoteViews / MAML / recovery callback 再执行时先看到 false；
3. 主线程释放对应 static/drag/vendor-material ownership；
4. 其它 glass 类型不受影响。

因此关闭 icon glass 不会一起销毁 widget glass，关闭 small-folder 也不会释放 large-folder 的 material ownership。

重新开启时保留轻量 inert hooks，并通过 Workspace reconcile / attach lifecycle 重新取得 ownership，无需为了普通视觉开关重启 Launcher。

---

## 动画时序

v2.1.1 支持多组可配置 glass 动画时序，包括：

- Workspace glass 可见性过渡；
- Dock icon launch / return glass 恢复；
- 按压进入 / 退出；
- Dock resize；
- 设置页相关视觉过渡。

文件夹打开/关闭期间，LiquidDock 会继续隐藏其已经接管的 vendor material，并用可逆 alpha transition 切换 Liquid Glass，降低 native background 闪现。

---

# Runtime 生效边界

并不是所有开关都适合热切换。

## 可即时生效 / 释放 ownership

当前包括：

- icon / widget / small-folder / large-folder glass；
- Dock customization 的视觉 ownership；
- Dock stroke；
- Dock shadow；
- stroke-shadow renderer 状态；
- Dock Divider；
- squircle / Fill-Diff 已安装描边刷新。

## 需要重启 Launcher 的结构性选项

当前明确 restart-bound：

- LiquidDock **主开关的完整启用/停用**；
- custom home grid 主开关；
- widget grid adaptation；
- Dock resize-animation hook selection；
- LiquidDock smooth resize hook selection；
- Workstation Dock customization；
- 其它会改变 Grid / Cell / All Apps / Workstation 结构 Hook 安装方式的设置。

原因是这些选项在 Launcher 启动时决定 Hook 结构。运行中只释放一部分视觉状态会留下“视觉已恢复、结构 Hook 仍存在”的混合状态。

---

# 工作台 / Laptop（实验性）

工作台目前已有：

- Workstation 状态检测；
- 工作台 Dock width offset；
- Dock icon top / bottom offset；
- Grid horizontal offset；
- All Apps 横/竖屏独立 horizontal / vertical offset；
- Divider 自定义；
- PassBlur producer suspend / single-frame pulse / rebind policy；
- Recents shared-glass recovery；
- 普通布局位置 backup / restore。

相关参数：

| 参数 | 当前范围 |
|------|------:|
| 工作台 Dock 长度偏移 | −240 ~ 240 dp |
| 工作台桌面水平偏移 | −240 ~ 240 dp |
| All Apps 横屏水平偏移 | −240 ~ 240 dp |
| All Apps 横屏垂直偏移 | −240 ~ 240 dp |
| All Apps 竖屏水平偏移 | −240 ~ 240 dp |
| All Apps 竖屏垂直偏移 | −240 ~ 240 dp |
| 工作台 Dock icon top offset | −48 ~ 48 dp |
| 工作台 Dock icon bottom offset | −48 ~ 48 dp |

---

## 工作台 Divider

`DockDividerHook` 与普通 Dock dimension unit 独立。

| 参数 | 持久化范围 | 运行时语义 |
|------|------:|------|
| Divider 开关 | 开/关 | live visual ownership；关闭后恢复原布局与背景 |
| width | 0 ~ 160 | 历史 raw `0.1 dp` 整数；运行时除以 10 |
| height scale | 0 ~ 100 | 相对父容器高度百分比 |
| Y offset | −80 ~ 80 | 历史 raw `0.1 dp` 整数；运行时除以 10 |
| R/G/B | 0 ~ 255 | Divider 颜色 |
| Alpha | 0 ~ 255 | Divider alpha |

第一次接管 Divider 时会保存 width、height、四边 margin 和 background；关闭时移除 pending layout listener 并恢复快照。Drawable background 会保存独立副本，避免 `setBackgroundColor()` 原地修改导致恢复值被污染。

详见 [DIVIDER.md](DIVIDER.md)。

---

# 多任务 (Recents)

当前支持自定义 Recents 背景模糊度，并且 Liquid Glass scene 使用 HyperOS semantic Recents dispatcher 处理 HOME / Recents coverage，而不是依赖单个 View 的挂载状态猜测场景。

Recents 与 Launcher static glass 的关系由 fresh-frame barrier 控制；Workstation 另有 shared producer rollover 恢复路径。

---

# 兼容性与失败模式

当前 Liquid Glass 依赖 HyperOS 3.0.307+ 的 vendor 私有接口，包括 MiuiX PassBlur、隐藏 Surface / SurfaceControl 行为和 Launcher 4.50.x.x 内部类。

因此：

- ROM / Launcher 升级后若 vendor class 或私有 API 变化，相关 Hook 可能 fail-closed；
- zero-copy glass 不会回退到 ScreenCapture；
- Widget glass 仍只属于“部分支持”；
- Workstation / Laptop 仍属于实验性适配；
- 普通 Grid / Dock / Glass 的 restart-bound 与 live-runtime 边界以设置页说明为准。
