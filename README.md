# LiquidDock

<p align="center">
  <img src="artwork/liquid-dock-screenshot.jpg" alt="LiquidDock detail"/>
</p>

LiquidDock 是一个面向 **HyperOS 3 平板系统桌面** 的 LSPosed / libxposed API 101 模块。当前 `main` 对应 **v2.2.1**，目标环境为 **HyperOS 3.0.307+ / `com.miui.home` release-4.50.x.x**。

项目当前的 Liquid Glass 主线已经完全迁移到 **MiuiX PassBlur + SurfaceTexture/OES + GLES + Prismal** 的 zero-copy GPU 管线；1.x 的 ScreenCapture / bitmap readback 实现仅保留在 `archive/1.x`。

## 主要功能

- **Liquid Glass**：支持 Dock、桌面图标、部分 RemoteViews/MAML 小组件、小/大文件夹，以及拖拽/应用启动代理。提供折射、色散、模糊、镜面、焦散、边缘光、高光、描边、尺寸和圆角等参数。
- **Launcher-wide 共享玻璃场景**：Workspace 图标、Widget、文件夹共享 root-wide `LauncherGlassSession`，避免每个组件单独建立 PassBlur producer。
- **HOME / APP / Recents 动画适配**：Launcher 提供语义 fallback，`com.android.systemui` 仅作为只读精确时序源；App→Home、Widget↔App、Recents→Home 均有独立的 freshness / visibility 生命周期。
- **Widget 背景自定义**：可扫描 RemoteViews 与 MAML 的真实组件结构，显示 `Render # / Depth / Area / Z`，并将“疑似底层背景”候选优先展示。已有 selector 规则保持兼容，诊断元数据不会改变 selectorKey。
- **Workspace 边缘几何**：静态玻璃滑出页面时保留完整形状、中心和圆角，由 framebuffer 自然裁屏；不会再贴住屏幕边缘只缩短宽度。
- **桌面网格**：支持横屏 8×4 / 竖屏 4×8、自定义边距/行距/页面指示器，并保持方向独立的 placement memory。
- **Dock 外观**：宽高、底部偏移、图标间距、圆角、原生模糊、Squircle / Fill-Diff、描边、整体阴影等。
- **工作台 / Laptop**：已有 Dock、Grid、All Apps、Divider、Recents producer recovery 等实验适配，但整体仍不视为完整支持。
- **多任务界面**：支持 Recents 背景模糊度调整。

## Zero-copy 数据流

```text
HyperOS MiuiX PassBlur
        ↓
Surface / SurfaceTexture
        ↓
GL_TEXTURE_EXTERNAL_OES
        ↓
GPU normalization / overscan
        ↓
Prismal optical renderer
        ↓
Dock / Launcher output surface
```

关键约束：

- 不恢复 ScreenCapture / bitmap fallback；vendor 私有接口不可用时 glass fail-closed。
- **Dock producer 始终保持 continuous-on-bind / 实时更新语义**。Workspace shared producer 才允许根据 HOME / Recents / rotation / freshness 生命周期 pulse 或 pause；不要把 Workspace 策略外推到 Dock。
- SystemUI 不参与玻璃渲染或内容捕获，只提供 HOME transition / Keyguard Gone 的系统级时序信号。
- 返回 HOME 时必须遵守 scene generation / fresh-frame barrier；返回目标为 Widget 时，Widget 本身必须等本代 fresh backdrop 完成后再淡入，避免旧背景跳变。

## 注入边界

```text
com.miui.home        Launcher 4.50.x.x：主要 UI、Glass、Grid、Dock、Widget 逻辑
com.android.systemui 只读时序源：HOME transition / Keyguard Gone
```

## 设置页重启操作

- **根页面**：显示“重启系统桌面”和“重启系统界面”。
- **子页面**：只显示“重启系统桌面”。

SystemUI 重启入口仍保留，因为 SystemUI 是系统级 transition timing source；它不是 Glass capture backend。

## 兼容性

Liquid Glass 依赖 HyperOS 3.0.307+ 的 MiuiX PassBlur、隐藏 `SurfaceControl.Transaction` API，以及 Launcher 4.50.x.x 内部类。ROM / Launcher 更新后若私有 API 改变，对应能力可能 fail-closed。

非玻璃功能同样依赖对应 HyperOS Launcher 类与方法存在。

## 构建

要求：

- Android SDK / compileSdk 37；
- minSdk 33 / targetSdk 37；
- JDK 17；
- Gradle 9.6.1；
- libxposed API 101。

Debug / CI：

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Release：

```bash
ANDROID_HOME=/path/to/Android ./gradlew assembleRelease --no-daemon
```

Debug 与 Release 都经过 AGP optimization / R8 路径。APK 位于 `build/outputs/apk/` 下对应构建类型目录。

## 文档

- [FEATURES.md](FEATURES.md) — 用户功能与运行时边界
- [ARCHITECTURE.md](ARCHITECTURE.md) — 当前 zero-copy / scene / process 架构
- [HOOKS.md](HOOKS.md) — 主要 Hook 与时序入口
- [CONTRIBUTING.md](CONTRIBUTING.md) — 开发约束与验证规则
- [DIVIDER.md](DIVIDER.md) — Workstation Divider ownership
- [TODO.md](TODO.md) — 后续开发优先级
- [CHANGELOG.md](CHANGELOG.md) — release 历史

## 分支

- **`main`** — 当前 v2.x 开发主线。
- **`archive/1.x`** — 旧 ScreenCapture / bitmap-readback 实现，仅作历史保留。

## 免责声明

本项目为非官方社区项目，与小米公司无关。"HyperOS" 与 "MIUI" 为其所有者商标，此处仅用于兼容性描述。

本项目仅供学习与研究使用。使用者自行承担使用风险；项目禁止商用。

## 感谢

- **Prismal** — Liquid Glass 光学模型与 Shader 参数设计参考。
- **LSPosed / libxposed** — Hook API 与模块运行框架。
- **HyperCeiler** — HyperOS 模块工程实践参考。
- **HyperLight** — 仅作为 1.x 屏幕捕获历史设计参考。

## 开源许可

本项目基于 [GPL-3.0](LICENSE) 许可开源。
