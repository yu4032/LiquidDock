# HyperOS 4 柔光玻璃移植到 OS3 实施计划

> 按用户最新确认执行：柔光作为液态玻璃下的可选模式；柔光开启时替换所有 LiquidDock 玻璃表面的共享材质参数；柔光使用独立参数页和独立持久化键组；用户负责验收。

**目标：** 在 OS3 上复用 Prismal/PassBlur 管线提供可选柔光材质，同时保证普通液态玻璃配置不受影响。

**实现：** 在 ConfigSchema 定义柔光键组；由 `LiquidDockConfig.Glass` 唯一选择有效光学配置；把柔光标记沿 Prismal 适配器传到活跃着色器；增加独立设置入口；最后构建并在已连接 OS3 设备呈现结果。

**设计：** `docs/superpowers/specs/2026-10-25-hyperos4-soft-glass-os3-design.md`

## 工作区约束

- 当前工作区已有用户未提交更改，且这些更改包含本任务需要兼容的 OS4 和 `ConfigSchema` 修改；继续在此工作区增量操作，不切换/重置/覆盖这些改动。
- 保持实现未提交，便于用户检查与验收。
- 按当前执行环境约束，不新增或运行自动化测试；使用 `assembleDebug` 和连接设备检查。

## 任务

### Task 1: 独立柔光配置和统一运行时选择

**文件：** `ConfigSchema.java`, `LiquidDockConfig.java`, `ConfigReader.java`, `LiveGlassConfigState.java`

- [x] 增加柔光模式开关与完整独立光学参数键组，并加入配置注册/导出枚举。
- [x] 实现标准参数到柔光参数的只读映射，组件开关、尺寸、内容和采样质量继续读取普通键。
- [x] 将柔光开关及全部柔光键加入实时更新监听；保证普通光学参数仍可独立调整且仅在普通模式生效。
- [x] 记录无自动化测试执行的约束。

### Task 2: 柔光模式传入活跃 Prismal 渲染器

**文件：** `Miuix307PrismalMaterial.java`, `Miuix307PrismalAdapter.java`, `ThirdPartyPrismalParams.java`, `PrismalParams.java`, `PrismalRenderer.java`, `PrismalOpticalEdgeShader.java`

- [x] 添加 `softGlassMode` 参数并保持 immutable 参数复制、适配、缓存完整。
- [x] 在活跃 OS3 shader 中按 OS4 SDF 曲线替换旧反射/高光，复用既有折射/模糊采样实现边缘折射、反射、暗部压低和双向方向光；OS4 独立背景颜色采样复用现有 PassBlur H/V 通道生成第二张纹理。
- [x] 模式关闭的现有 shader 分支和普通配置保持原样。

### Task 3: 独立柔光设置页

**文件：** `SettingsNavigationModel.kt`, `GlassSettingsPages.kt`, `SettingsOptionSpecs.kt`, `ComposeSettingsActivity.kt`, `SettingsSearch.kt`, `strings.xml`

- [x] 在液态玻璃页面增加柔光开关和独立参数页入口。
- [x] 参数页只编辑柔光配置键，可在模式关闭时调整；启用状态受液态玻璃总开关约束。
- [x] 接入设置搜索和导航；既有普通配置页保持原键和原行为。

### Task 4: 构建并提交可验收结果

- [x] `./gradlew assembleDebug` 成功并保留 APK。
- [x] 用 `adb install -r` 安装至已连接 OS3 turner 设备；重启后确认 LSPosed 加载模块且 GL 帧开始运行。
- [ ] 如设备当前桌面/授权状态允许，拍摄图标、Dock、文件夹、小组件玻璃；OS4 fuxi 截图用作参考。
- [ ] 汇报改动点、APK 路径、构建/设备观察结果，以及仍需用户视觉验收的具体项目。

## 视觉检查点

- 明亮壁纸下保留图标和组件颜色，不出现大面积白化。
- 图标边缘柔和连续，不形成高对比硬环或光晕。
- Dock 和文件夹圆角上的反射连续，方向一致。
- 小组件文字保持可读，背景内容不过度位移。
- 低光或均匀背景下仍保留柔和轮廓。
