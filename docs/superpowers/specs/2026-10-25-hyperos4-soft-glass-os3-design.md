# HyperOS 4 柔光玻璃移植到 OS3

## 目标

在 LiquidDock OS3 现有 Prismal/PassBlur 玻璃管线上增加可选的柔光玻璃材质。液态玻璃总开关开启时，用户可以在普通液态玻璃和柔光玻璃之间切换。启用柔光后，所有 LiquidDock 液态玻璃表面统一使用柔光材质参数；图标、Dock、文件夹、小组件、弹窗等各自现有的启用范围、形状、尺寸、采样生命周期和交互保持原样。

柔光配置必须与原液态玻璃配置完全分开保存。用户切换模式或编辑柔光参数时，不得改写普通材质参数；关闭柔光后，普通材质应恢复到原值。柔光要有独立的参数设置入口，可在当前模式关闭时预先调整。

## 范围与非目标

- 移植 HyperOS 4 柔光玻璃的方向光、宽柔边缘反射与色彩过渡，不移植 OS4 原生 Hook。
- 所有已有 LiquidDock Prismal/PassBlur 表面使用同一材质模式；不逐组件新增模式选项。
- 保留组件开关、几何、内容适配、背景捕获、刷新率与性能设置。
- 保留普通液态玻璃全部配置值，柔光配置单独存储。
- 不接管 HyperOS Launcher 原生玻璃、不改其他应用与系统原生表面、不改布局或手势、不增加依赖。

## 设计

1. 在 `ConfigSchema` 添加柔光启用键和独立光学材质键组。柔光页只显示 shader 实际使用的设置：PassBlur 半径、OS4 折射厚度/IOR/边缘带/反射/方向光/暗部/内层色彩曲线、玻璃底色和透明度，以及 Prismal 形状与采样控制。穹顶、色散、焦散、Prismal 高光、阴影等不参与这条 OS4 材质路径，因此不显示成无效的柔光选项。PassBlur 捕获分辨率、帧率等性能设置继续由原有全局设置管理。
2. 在 `LiquidDockConfig.Glass` 创建单一材质选择层：组件行为和几何始终读取普通全局配置；模式关闭时读取普通光学配置，模式开启时将柔光键组映射到现有 Prismal 字段。所有现存调用点因此自然读取同一份有效材质，无需按组件分别改渲染器。
3. 柔光模式位与独立 `PrismalSoftGlassProfile` 随 `LiquidDockConfig.Glass` 通过适配器传到活跃的 `PrismalRenderer`。柔光分支在现有 fragment shader 里提前完成 OS4 色彩、折射、反射和方向光阶段，不再叠加 Prismal 高光；独立窄边缘光用 `mi_ltc` 的处理后 SDF 曲线和方向光近似。分支复用 OS3 当前几何、`distMask`、背景采样和 PassBlur 输出；模式关闭时使用原 opacity 和原 Prismal 渲染分支，不新增捕获或模糊通道。
4. 液态玻璃页新增柔光开关和独立设置页入口。柔光参数页使用独立柔光键，不复用普通材质 `ConfigKey`。切换模式及调整柔光键纳入 `LiveGlassConfigState`，让已载入的 OS3 玻璃实例走原有实时刷新链路。
5. 弹窗/第三方应用等已有独立外观覆盖仍按它们当前的专用策略工作；柔光模式替换其共享基础材质，而不改变组件级覆盖的所有权。

## OS4 原生实现对照与覆盖审计

依据只读反汇编 fuxi（HyperOS 4.0.27.0.XEOCNXM / Launcher 8.01.02.8584）的 `mi_glass_sdf`、`mi_ltc` 片元着色器，柔光分支逐阶段对应如下：

| OS4 阶段 | OS3 映射 |
| --- | --- |
| SDF 边缘距离、曲线、法线 | 使用 Prismal 当前 `distMask`、`os4SdfGradient` 和现有圆角几何；几何保持 OS3。主 `mi_glass_sdf` 边缘曲线幂次固定为 1；可调 `shapeEdgePow` 只用于 `mi_ltc` 窄边缘光。 |
| 厚度与 IOR 折射 | 在 `shapeEdgePx` 内使用 OS4 折射率、厚度 ramp 和现有背景纹理；SDF 边缘外折射位移归零。 |
| 暗部压低与反射 | 对折射源、反射源及背景色源使用独立暗部阈值，并混入 OS4 深紫色；反射使用法线偏移、反射强度和补光系数。 |
| 图标移动/命中 | `uHit` 映射到 OS3 按压状态；`uIconMove` 动态光映射到现有触点/发光中心。OS3 没有 OS4 的图标 mask 与独立动态光方向，因此局部权重采用现有 Prismal glow 中心和强度近似。 |
| 亮度曲线、饱和度、亮度偏移 | 使用 OS4 cubic luminance 系数/强度，再执行饱和度和加法亮度。OS3 截屏纹理为不透明源；OS4 亮度偏移乘源 alpha，直接照搬 1.0 会每通道加 1 并裁成白色，因此 OS3 柔光初始偏移设为 0，保留独立滑块供校准。 |
| `uBgColorTex`、背景饱和度/亮度、内层提白 | 复用 Prismal/PassBlur 的同一采样与高斯核，再以独立半径生成背景颜色纹理；可在原始截屏与该纹理间单独混合，然后执行暗部、饱和度、亮度和提白。 |
| 命中色、burn、玻璃 tint、inner mix、底部渐变 | 按原生阶段顺序执行，并使用独立柔光参数。 |
| 主/反向方向光、亮度响应、颜色幂次、unshade | 在处理后底色上执行；方向光只影响 SDF 边缘体积。 |
| `mi_ltc` 窄方向光 | 在同一 Prismal fragment 中采样处理后的 SDF，使用原生 `2.5px` edge mask、主/反向光、`uMainLightLength` 和环境边缘光，再叠加窄的轻量提亮。 |

## OS3 管线差异

OS4 分别准备 `uRefractTex` 与 `uBgColorTex`。OS3 仍复用现有 PassBlur 程序和 0.5×纹理尺寸，但在柔光模式下以独立半径再运行一次分离高斯核，生成 `uBgColorTex` 对应纹理；`blur_refract` 对应柔光折射采样半径，turner 已保存的 14.2px 值保持不变。`blur_background` 的 0–1000 数值映射到 OS3 PassBlur 的 0–60px 半径，500 对应 30px；31-tap 核有最大有效支持范围，因此两种 OS 的极大模糊尺度仍不能逐像素等效。额外背景纹理只在柔光模式首次需要时分配。OS4 的亮度偏移乘输入 alpha；OS3 归一化的桌面截屏是 alpha=1 的不透明源，所以两个加法偏移的安全初始值为 0，避免默认把每个表面裁成白色。

OS4 的预计算 SDF 法线会随边缘深度逐渐过渡；OS3 复用的解析 SDF 在平直边上只有恒定横向梯度。柔光分支在整个边缘带内连续减小横向法线，使折射与两路方向光平滑接入平坦主体，避免形成边缘亮带与内部之间的接缝。

OS4 的 SDF 与 icon mask 资源不存在于 OS3 Prismal；形状使用 OS3 的实际 `distMask`，局部动态光使用现有按压/发光状态。OS4 的色彩空间转换由其原生渲染输出管线处理，本实现沿用 OS3 EGL/Surface 输出转换。

OS4 反汇编显示 `uDirectionalLightEdgePow` 与主 shader 的 `uDirectionalLightLightWidth` 仅声明、未参与该版本 `mi_glass_sdf` 运算；`mi_ltc` 使用的是 `uShapeEdgePow` 与 `uMainLightLength`，所以没有把未消费的字段做成柔光控制项。fuxi 上没有已启用的 native glass override profile；`Os4NativeGlassOptions` 中的数值只能作为重置建议，不能当作该设备当前活动材质参数。柔光其他可对应参数以这些建议值和 OS3 已保存的柔光模糊值为起点；两个加法亮度偏移按 OS3 不透明截屏源设为中性 0，避免源 alpha 差异造成整块发白。

## 验收

- 普通模式视觉与参数保持不变；开关柔光不会修改普通键组。
- 柔光模式下所有已有 LiquidDock Prismal/PassBlur 表面获得同一材质档案；几何、启用范围及采样管线保持不变。
- 柔光参数在单独页面可调，实时应用，退出后仍保存；关闭柔光后普通材质原样恢复。
- OS3 设备构建、安装并可观察图标、Dock、文件夹、小组件等表面。用连接的 OS4 设备作为观感参考；设备差异由视觉校准记录说明。
- 自动化测试按当前执行环境规则不新增、不运行；通过 Gradle debug 构建和 OS3 设备上的安装/视觉检查确认交付。
