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

LiquidDock 是一个面向 **HyperOS 3 平板系统桌面** 的 LSPosed / libxposed API 101 模块，用于为系统桌面提供可自定义的 Liquid Glass 渲染，并扩展 Dock、桌面网格、小组件、文件夹、多任务界面与工作台布局。

LiquidDock 主要针对 **HyperOS 3.0.307+** 与 **`com.miui.home` release-4.50.x.x** 开发和验证。

<p align="center">
  <img width="3008" height="1880" alt="LiquidDock on HyperOS Launcher" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" />
</p>

## 主要功能

### Liquid Glass

- 为 Dock、已适配小组件、文件夹和桌面图标提供 GPU 渲染的 Liquid Glass。
- 基于 Prismal 光学模型实现折射、色散、模糊、镜面、高光、焦散和方向光等效果。
- 支持调整玻璃尺寸、圆角、采样范围、模糊与高光等视觉参数。
- 桌面图标玻璃可配合透明图标主题使用。
- 支持动态壁纸和视频壁纸。

### Launcher / Dock 自定义

- 新增 **8×4**、**10×6** 桌面布局。
- 支持分别调整横屏 / 竖屏的边距、间距和页面指示器位置。
- 支持调整 Dock 宽度、高度、底部偏移、图标间距、圆角和模糊参数。
- 支持自定义 Dock 描边、透明度、阴影和分隔线。
- 支持小组件背景隐藏 / 适配规则。
- 支持工作台模式布局自定义，包括 8×4 桌面布局。
- 支持调整多任务界面背景模糊度。

## 渲染架构

LiquidDock 使用 HyperOS 原生 PassBlur、OES / GLES 与 Prismal 组成 zero-copy 渲染链：

```text
HyperOS SurfaceFlinger PassBlur
        ↓
external OES texture
        ↓
GLES normalization / local sampling
        ↓
Prismal blur + optical rendering
        ↓
TextureView composition
```

当前实现的主要特性：

- 背景帧保持在 GPU 路径中，不回读为 CPU Bitmap。
- PassBlur 更新由内容源驱动，不通过固定轮询持续请求帧。

<p align="center">
  <img width="704" height="440" alt="LiquidDock glass example" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" />
</p>

## 当前支持范围

| 项目 | 要求 |
| --- | --- |
| ROM | HyperOS 3.0.307+ |
| 系统桌面 | `com.miui.home` `release-4.50.x.x` |
| Hook 环境 | LSPosed / libxposed API 101 |
| 构建 JDK | JDK 17 |
| Android SDK | compileSdk 37 |

### 使用前说明

LiquidDock 直接依赖 HyperOS Launcher / SystemUI 的内部实现，以及部分 vendor 私有类和隐藏的 `SurfaceControl.Transaction` PassBlur API。系统、桌面或 SystemUI 更新后，如果这些私有接口发生变化，相关功能可能暂时失效，直到项目完成适配。

高度修改的第三方 ROM、修改较多的 Launcher / SystemUI，以及超出上述版本范围的环境不保证兼容。非玻璃功能同样依赖对应 HyperOS Launcher 类和方法存在。

### LSPosed 作用域

```text
com.miui.home
com.android.systemui
```

`com.miui.home` 是主要注入目标；`com.android.systemui` 用于与 Launcher / SystemUI 的转场和状态协同。

## 安装

1. 从 [GitHub Releases](https://github.com/yu4032/LiquidDock/releases) 下载最新 APK。
2. 在具备可用 LSPosed / libxposed 环境的设备上安装 APK。
3. 在 LSPosed 中启用 **LiquidDock**。
4. 启用以下作用域：
   - 系统桌面 — `com.miui.home`
   - 系统界面 — `com.android.systemui`
5. 重启对应进程，或直接重启设备。
6. 打开 LiquidDock，根据需要启用和调整功能。

提交兼容性问题前，请先确认当前 HyperOS 和 `com.miui.home` 版本仍处于支持范围内。

## 从源码构建

需要：

- Android SDK / compileSdk 37
- JDK 17
- libxposed API 101
- Gradle 自动解析 `io.github.libxposed:api` / `service` 依赖

### Debug

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

### Release

```bash
ANDROID_HOME=/path/to/Android ./gradlew assembleRelease --no-daemon
```

APK 输出位于：

```text
build/outputs/apk/
```

Debug 与 Release 均使用项目当前配置的 Android Gradle Plugin optimization / shrinker 路径。

## 反馈与贡献

欢迎提交 Issue 和 Pull Request。报告渲染或兼容性问题时，建议至少附带：

- HyperOS 版本；
- `com.miui.home` 版本；
- 是否启用工作台模式；
- 简短、稳定的复现步骤；
- 可获取时提供 LiquidDock / Launcher 相关日志。

请尽量让每个 Pull Request 聚焦于单一问题，避免混入无关重构。

## 风险提示

> [!WARNING]
> LiquidDock 依赖 HyperOS 的私有接口，并会通过 LSPosed / libxposed Hook 系统桌面和 SystemUI。系统、桌面或 SystemUI 更新后可能出现兼容性问题。建议在使用前做好必要备份，并确保有可用的恢复方式。

LiquidDock 是非官方社区项目，与 Xiaomi、LSPosed 等相关厂商或项目不存在隶属关系。“HyperOS”和“MIUI”等名称仅用于兼容性说明。

本项目按现状（“AS IS”）提供，使用风险由使用者自行承担。许可与责任限制以 [GPL-3.0](LICENSE) 为准。

## 感谢

- **Prismal** — Liquid Glass 光学模型与 Shader 参数设计参考。
- **LSPosed / libxposed** — Hook API 与模块运行框架。
- **HyperCeiler** — HyperOS 模块工程实践与项目文档结构参考。

## 开源许可

LiquidDock 基于 [GNU General Public License v3.0](LICENSE) 开源。
