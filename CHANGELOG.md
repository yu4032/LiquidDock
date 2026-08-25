# Changelog

## v2.1.1 (2026-08-26)

### Glass optics and animation

- 新增可配置动画时序：Workspace 可见性、Dock 图标恢复、按压进入/退出、Dock resize 与设置页动画均可独立调整
- 文件夹打开/关闭期间继续保持原生 source material 隐藏，并通过可逆 alpha transition 完成 Liquid Glass 的显示/隐藏，减少切换瞬间的原生背景闪现
- `liquid_highlight_width` 的可见语义收敛为“玻璃边缘厚度”：直接缩放 Prismal 的物理光学边缘带，而不是额外绘制一圈独立描边
- 光学边缘加入 derivative anti-aliasing（`fwidth`）并统一到 Prismal renderer / MiuiX 307 shader 路径；GUI 范围调整为 50%–300%

### Runtime visual ownership

- 图标、小组件、文件夹与 Dock 视觉开关改为运行时状态驱动；关闭后立即释放对应玻璃、描边、阴影和分隔线所有权，重新开启时恢复
- 修复工作台模式从多任务返回后共享玻璃 producer 丢失的问题，并保留 R8 所需的恢复入口
- 修复 Dock 描边方式实时切换未刷新、Divider 背景快照别名污染，以及关闭自定义后原始 Divider 状态未恢复的问题
- 设置页明确区分即时生效与需要重启桌面的结构性选项，并补齐中英文说明

### Documentation

- README 更新当前兼容边界：HyperOS 3.0.307+、`com.miui.home` release-4.50.x.x，并明确 `main` 为当前 zero-copy 开发主线、`archive/1.x` 为旧屏幕捕获实现

## v2.1.0 (2026-08-24)

### Launcher-wide Liquid Glass

- 将 Prismal 抽成独立模块，并建立共享 renderer / geometry / parameter / highlight-profile 边界，供 Dock 与 Launcher glass scene 复用
- Liquid Glass 从 Dock 扩展到桌面图标、部分小组件、文件夹与拖拽代理；静态 Workspace 使用共享场景合成，避免每个 material 各自维护独立渲染链
- 重建主文件夹 glass 生命周期与拖拽 overlay，修复编辑态 transform 下的 drag carrier 对齐，最终以 host-space geometry 作为位置权威
- Dock 图标进入应用动画期间隐藏对应 glass node，并在返回后淡入恢复；支持 Dock icon 独立 highlight profile 与工作台图标圆角
- 接管 Launcher 原生 widget/folder fallback background、large-folder cover/drawable 等背景 owner，并在 RemoteViews / MAML 异步刷新后重新声明 glass ownership，减少玻璃后方原生底板重现

### Zero-copy sampling and freshness

- 为 PassBlur/OES zero-copy 管线加入固定 GPU overscan，并恢复 GUI 上下额外采样控制；新增左右独立 overscan，均直接使用像素语义
- 将 overscan sample validity 与可见 Dock coverage/scissor 分离，修复强折射靠近 Dock 边缘时采样提前截断的问题
- 新增 wallpaper freshness generation 与 HOME resume reconciliation，使壁纸内容变化、场景恢复和静态 glass 刷新由明确 generation/state 驱动
- Launcher glass state / geometry authority 收敛：移除 shadow state，静态 Workspace geometry 统一由最终 snapshot 决定，ViewTreeObserver ownership 改为 identity-aware

### Layout, Workstation and runtime ownership

- 桌面布局新增 orientation-specific memory、profile overlay、placement planner、drop legality / drag bounds / centering 等独立策略，降低横竖屏切换与 off-screen page 的状态漂移
- Dock bottom geometry 收敛为单一 owner；修复 whole-Dock shadow 在 zero-copy、主题切换和工作台切换中的生命周期保持
- 增强工作台 Dock / 图标半径 / producer policy，并补充 Recents 背景模糊控制
- 运行时配置在 snapshot 与 Remote Preferences reconciliation 前完成 migration，修复审计发现的配置与视觉 owner 生命周期问题

## v2.0.0 (2026-08-19)

### Zero-copy-only glass architecture

- Liquid Glass 从 1.x 的 ScreenCapture/bitmap readback 架构迁移到 HyperOS 3.0.307+ 的 MiuiX PassBlur + OES/GLES zero-copy 路径
- Zero-copy 成为唯一 Liquid Glass backend；正式移除 capture fallback，失败时 fail-closed，不再回退到 Launcher/Workspace 截图
- 建立三阶段 GPU 管线与 Prismal 光学适配，保留折射、色散、模糊、镜面、焦散和高光参数，同时避免旧截图链中的递归图标、黑帧、capture worker 与旋转缓存问题
- 删除旧 `LiveScreenCapture`、capture scene/cadence、`DockLiquidGlassView` 与相关 screenshot-era 状态；旧实现保留在 `archive/1.x`

### HyperOS / Launcher integration

- 新增 MiuiX 307 material pipeline、PassBlur bridge、TextureView/OES backdrop mapping、Prismal material/shader 与 zero-copy renderer
- 完成 Launcher / SystemUI 场景 ownership 与 transition handoff 的多轮收敛，避免 HOME↔APP、Recents、手势过渡期间错误冻结或使用旧 generation
- 工作台 Dock geometry、Divider、All Apps spacing 与 Workspace drop rule 开始迁移到独立 policy/hook；普通模式与工作台参数进一步隔离
- 文档与 release contract 同步改为 zero-copy-only 语义

## v1.2.0 (2026-08-15)

### Dynamic liquid highlight (RuntimeShader)

- 高光从 Canvas 静态渐变改为 RuntimeShader 实时逐像素计算（specular/rimLight/caustics 几何光照），配合 self-blur 折射保持锐利
- 高光参数热重载：GUI 滑条调整即时生效
- squircle 圆角高光平滑修复

### API101 configuration convergence

- 新增类型化 `ConfigKey` / `ConfigSchema`，集中登记 persisted key、类型、UI default、runtime fallback、export default、范围、存储模式和导出策略
- 新增纯 `ConfigCodec`，接管 JSON 导入/导出与 legacy alias 转换，移除 `SettingsActivity` 中的大量手写 key 列表
- `grid_widget_adaptation` 现已正确参与配置导入/导出
- 新增 `ConfigMigration`，把设置进程历史 SharedPreferences 升级逻辑从 Activity 中分离
- 新增 `PresetManager`，统一默认预设与动态 iPad 风格预设写入
- Compose 设置页开始绑定 schema key/default/range，减少 UI 与运行时配置漂移
- `LiquidDockConfig.load()` 改为 side-effect-free snapshot；Widget adaptation 的运行时 gate 暂时显式迁到 `MainHook.install()`
- pre-API101 JSON 迁移移到 `ModuleMain.onPackageReady()` 的 `LegacyConfigMigration` compatibility boundary；普通 runtime config load 不再写 Remote Preferences
- 保留 `liquid_home_settle_delay` 历史 `_tenths` round-trip
- Divider width/Y 明确保留历史 DIRECT raw-tenths JSON 语义，避免错误通用 `DP_TENTHS` sidecar 与 clamp 漂移
- 增加 forced Dock/Liquid dimension、absent-default、legacy migration、storage compatibility 等回归测试

### Liquid Glass advanced material blur

- 新增 `liquid_blur_mode`：默认 `shader` 保持现有行为，可选 `advanced_material` 使用 HyperOS/MIUI SurfaceFlinger self-blur
- 新增缓存反射 `MiBlurBridge`，直接调用 `View.setMiSelfBlur`、`setPassTextureScale` 与 self-blur enhance flag；能力失败仅回退当前运行时 backend，不改写用户配置
- RuntimeShader 增加 `shaderBlurEnabled`，高级材质实际生效时绕过原 40-sample blur kernel；Shader 模式和 fallback 继续使用原 kernel
- Liquid Glass 拆为 `DockLiquidGlassHostView` + `DockLiquidGlassView` + `DockStrokeOverlayView`：glass body 负责折射/模糊，overlay 保持 Canvas 高光和可配置描边锐利
- 最终 round/squircle clip 移到 host 合成层；高级模式下 self-blurred child 不预先裁圆角，修复实验中左上圆角区域没有模糊的问题
- 两条 `Launcher.setupViews()` 路径统一使用同一 Liquid Glass layer assembly；workstation 仍保持未完成适配状态
- Floating Dock View detach 时清理 MIUI self-blur 状态；同一 View 重新 attach 后按保存的 advanced 请求自动重施

### Documentation / known status

- 更新 README、ARCHITECTURE、HOOKS、FEATURES、DIVIDER、CONTRIBUTING、TODO 以匹配当前 API101 实现
- 明确当前只完成配置架构 Phase 1；`MainHook`、`HomeGridHook`、`DockLiquidGlassView` 的后续模块拆分尚未完成
- 明确 Widget detection/span registry 尚未实现；当前仍固定支持 1×1、2×1、2×2、4×2
- 明确工作台虽已有实验性 Hook/参数/快照逻辑，但**整体仍未完成适配，不属于受支持功能**
- 明确 foreground `DockStrokeRenderer` 替代旧描边 overlay 后，历史描边阴影效果已失效；相关 key 暂为配置兼容保留

## v1.0.3 (2026-08-12)

- **Dock 分隔竖线控制**：工作台模式图标间竖线支持宽度、高度比例、垂直偏移、颜色和透明度独立调节
- **工作台捕获实验修复**：加入 wallpaper-only/native snapshot 方向的兼容尝试；工作台整体适配仍未完成
- **HookUtil 反射修复**：`hookMethod(Class,...)` 改为父类链查找
- **per-frame scene 检测**：`onPreDraw` 帧触发替代轮询
- **RECENTS→HOME 立即捕获**：scene 转变时立即 `scene-settle-home`
- **Haptic 预触发取消**：`prearmRecentsCapture` 强制取消进行中捕获

## v1.0.2 (2026-08-05)

- 旋转黑帧过滤：阈值钳位 > 0
- HOME settle barrier：延迟方案，GUI 滑块可配
- 壁纸条带缓存短路修复
- 旋转稳定收敛：3s 窗口，连续两帧签名一致停止

## v1.0.1 (2026-07-28)

- 配置同步迁移至 LSPosed Remote Preferences（不再依赖 su）
- 导出/导入包含 `homeSettleDelay`
- DragController hooks 进程级一次性安装

## v1.0.0 (2026-07-20)

- 首个签名发布版
- libxposed API 101 迁移完成
- 统一反射层 HookUtil
- 液态玻璃光学模型（Prismal 参考实现）
