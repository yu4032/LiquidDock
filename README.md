# LiquidDock

<p align="center">
  <strong>简体中文</strong> · <a href="./README_EN.md">English</a>
</p>

<p align="center">
  <a href="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml"><img alt="Build" src="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml/badge.svg"></a>
  <a href="https://github.com/yu4032/LiquidDock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/yu4032/LiquidDock"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/yu4032/LiquidDock"></a>
  <a href="https://github.com/libxposed/api"><img alt="libxposed API 101" src="https://img.shields.io/badge/libxposed-API%20101-6f42c1"></a>
</p>

LiquidDock 是面向 HyperOS 平板桌面的 LSPosed / libxposed API 101 模块。当前 `main` 版本为 **2.4.1**，主要针对 **HyperOS 3.0.307+ / `com.miui.home` release-4.50.x.x** 开发和验证，并为 Security Center 侧边栏提供独立的、能力驱动的 Liquid Glass 适配。

<p align="center">
  <img width="3008" height="1880" alt="LiquidDock on HyperOS Launcher" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" />
</p>

## 当前功能

### Liquid Glass

- Dock、桌面图标、Dock 系统功能图标、支持的小组件、小/大文件夹使用 Prismal Liquid Glass。
- Workspace 静态对象共享一个 root-wide PassBlur source/session，不为每个图标或小组件重复创建 producer。
- 拖拽使用独立上层窗口进行**实时** Workspace 采样，并镜像 MIUI DragView 视觉；不是拖拽开始时的静态截图。
- 桌面快捷菜单可选独立玻璃背景，并可开启深色模式适配：文字变白，独立图标只对近黑、低色差的线条图标做白色 tint，彩色第三方图标保持原样。
- 小组件支持深色内容适配、背景 ownership 管理，以及用户可选择的组件隐藏规则。
- Security Center `:ui` 进程支持能力驱动的 Game / Video / Global Dock / All Apps glass；不再依赖固定混淆字段名作为兼容契约。
- 支持折射、色散、blur、厚度、IOR、法线、镜面、rim、caustics、方向光、highlight profile 等 Prismal 参数。

### Launcher / Dock

- 自定义桌面网格：**8×4**、**10×6**，横竖屏独立几何与 placement memory。
- Launcher 4.50 图标大小缩放（80%–120%），覆盖 Workspace、Dock、小文件夹预览、打开的文件夹内容与 Workstation App 页，同时避免普通 All Apps / Search 被误缩放。
- Dock 宽高、底部位置、图标间距、模糊、圆角、squircle / Fill-Diff。
- Dock 描边、描边阴影、整个 Dock 阴影、Workstation Divider。
- 可隐藏 Dock 的手机互联入口，不改变系统连接能力。
- Workstation / Laptop 的 Dock、Grid、All Apps 和图标位置适配仍属于实验性路径。
- Recents 背景模糊比例可调。
- Workspace、Dock 图标恢复、按压、Dock resize 和设置页切换动画时长可调。

## 渲染架构

Launcher Liquid Glass 的主路径是 GPU zero-copy：

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
Dock / Launcher / popup / drag / Security Center output
```

关键约束：

- 活动 Liquid Glass backdrop 不使用 ScreenCapture、PixelCopy、CPU bitmap readback 或 texture re-upload 作为 fallback；
- native PassBlur geometry 保持权威，Workspace 质量缩放发生在 OES normalization 之后；
- source frame 与昂贵的 Prismal/output render 分离，FPS gate 不阻塞 `SurfaceTexture.updateTexImage()` drain；
- scene / wallpaper / producer freshness 由 generation 与 fresh-frame barrier 管理，返回 HOME 不直接展示 stale frame；
- vendor/private capability 不可用时 fail closed，保留或恢复系统材质。

> `ShortcutMenuDarkModeController` 会把菜单小图标首次渲染到 20×20 临时 Bitmap 做颜色分类。这只是 UI 图标判定，不属于 glass backdrop 捕获链。

<p align="center">
  <img width="704" height="440" alt="LiquidDock glass example" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" />
</p>

## 兼容边界

| 项目 | 当前边界 |
| --- | --- |
| LiquidDock | `main` / 2.4.1 |
| Android | minSdk 33，targetSdk / compileSdk 37 |
| Launcher | HyperOS 3.0.307+，`com.miui.home` release-4.50.x.x 为主要验证基线 |
| Hook runtime | libxposed API 101 |
| Build JDK | JDK 17 |
| Security Center | `com.miui.securitycenter:ui`；运行时通过语义结构与资源能力验证，能力缺失或歧义时不接管 |

LiquidDock 依赖 HyperOS 私有 Launcher / SystemUI / Security Center 行为以及隐藏的 PassBlur / `SurfaceControl` API。系统组件升级后，即使包名不变，也可能因为私有结构变化暂时失效。

### Xposed scope

```text
com.miui.home
com.android.systemui
com.miui.securitycenter
```

- `com.miui.home`：主要功能与 Launcher Liquid Glass。
- `com.android.systemui`：只提供 HOME / keyguard 转场时序权威，不作为 Launcher glass renderer。
- `com.miui.securitycenter`：仅在实际进程为 `com.miui.securitycenter:ui` 时进入 Security Center glass 初始化。

## 安装

1. 从 [GitHub Releases](https://github.com/yu4032/LiquidDock/releases) 下载 APK。
2. 在支持 libxposed API 101 的 LSPosed 环境安装并启用 LiquidDock。
3. 勾选上述三个 scope；不需要 Security Center 功能时仍可关闭对应 Liquid Glass 开关。
4. 重启受影响进程或设备。
5. 在 LiquidDock 设置页启用需要的功能。

结构性 Hook（例如 Grid 主结构、部分 Dock / Workstation 安装路径）通常需要重启桌面；部分视觉 ownership 开关可运行时释放。设置页会尽量明确提示生效边界。

## 从源码构建

要求：Android SDK 37、JDK 17、Gradle 9.6.1、libxposed API 101。

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Release：

```bash
ANDROID_HOME=/path/to/Android ./gradlew assembleRelease --no-daemon
```

Debug 与 Release 都启用 Android optimization / R8。CI 的 debug APK 因此也会经过 shrinker，用来提前发现反射和 keep-rule 回归。

## R8 与反射约束

LiquidDock 自有类之间禁止用字符串反射访问字段/方法，使用 typed Java / package-private API。反射主要保留在 Android / HyperOS vendor 边界。

跨 ClassLoader 的类名也必须谨慎：例如 Dock spacing 需要用 Launcher ClassLoader 解析 `RecyclerView$State`，因此项目对 `RecyclerView` / `$State` 使用 targeted `-keepnames`，避免 R8 把字符串改成模块自身的混淆名。不要通过整包 `-keep` 掩盖自反射问题。

## 文档

- [FEATURES.md](FEATURES.md) — 功能、设置与生效边界
- [ARCHITECTURE.md](ARCHITECTURE.md) — 运行时架构、ownership 与 freshness
- [HOOKS.md](HOOKS.md) — 当前 Hook / listener / reflection 边界
- [CONTRIBUTING.md](CONTRIBUTING.md) — 开发、测试、R8 与兼容规则
- [DIVIDER.md](DIVIDER.md) — Workstation Divider ownership
- [TODO.md](TODO.md) — 当前剩余工程债务
- [CHANGELOG.md](CHANGELOG.md) — 发布历史与 current-main 变更

`docs/superpowers/plans` 与 `docs/superpowers/specs` 是历史设计/实施记录；判断当前行为时应以生产代码和上述根文档为准。

## 反馈

报告兼容问题时建议附带：HyperOS 版本、Launcher 版本、是否启用 Workstation、相关设置、稳定复现步骤，以及 `[DC]` 日志。Security Center 问题还应说明实际 `com.miui.securitycenter` 版本和触发的是 Game / Video / Global Dock / All Apps 哪一条路径。

## 风险提示

> [!WARNING]
> LiquidDock Hook 系统 Launcher、SystemUI 和 Security Center 的私有实现。升级系统组件前请保留可用的恢复方式。本项目与 Xiaomi、LSPosed 或相关厂商/项目没有隶属关系。

项目按 GPL-3.0 “AS IS” 提供，风险与责任以 [LICENSE](LICENSE) 为准。

## Credits

- **Prismal** — Liquid Glass 光学模型与 shader 参数参考
- **LSPosed / libxposed** — Hook API 与模块运行时
- **HyperCeiler** — HyperOS 模块工程实践与设置结构参考

## License

[GNU General Public License v3.0](LICENSE)
