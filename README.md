# LiquidDock

<p align="center">
  <strong>简体中文</strong> · <a href="./README_EN.md">English</a>
</p>

<p align="center">
  <a href="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml"><img alt="Build" src="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml/badge.svg"></a>
  <a href="https://github.com/yu4032/LiquidDock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/yu4032/LiquidDock"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/yu4032/LiquidDock"></a>
</p>

LiquidDock 是一个面向 **HyperOS 3 平板桌面** 的 LSPosed 模块，主要用来调整桌面、Dock 和液态玻璃效果。


<p align="center">
  <img width="3008" height="1880" alt="LiquidDock on HyperOS Launcher" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" />
</p>

## 它可以做什么

### 液态玻璃

可以给 Dock、桌面图标、小组件和文件夹加入统一的液态玻璃效果，并调整模糊、折射、高光、色散、圆角等外观。

拖动图标或小组件时，玻璃效果也会跟随移动。桌面图标的长按快捷菜单也可以换成玻璃背景，并提供适合深色背景的白色文字和图标模式。

<p align="center">
  <img width="704" height="440" alt="LiquidDock glass example" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" />
</p>

### 桌面布局

支持 **8×4** 和 **10×6** 桌面网格，可以分别调整横屏、竖屏下的间距和位置。

还可以单独调整桌面、Dock、文件夹等位置的图标大小，普通应用抽屉和搜索页不会跟着一起变化。

### Dock

可以调整 Dock 的宽度、高度、底部位置、图标间距、圆角和模糊效果，也可以自定义描边、阴影和工作台分隔线。

手机互联入口可以单独隐藏，只隐藏 Dock 上的图标，不会关闭系统本身的互联功能。

### 小组件和文件夹

小组件可以使用玻璃背景，并提供深色内容适配。部分小组件中不想保留的背景区域，也可以在设置页里选择隐藏。

小文件夹和大文件夹可以分别开关玻璃效果，并单独调整大小和圆角。

### 多任务和工作台

可以调整多任务界面的背景模糊，并提供一部分工作台模式下的 Dock、桌面和应用页布局选项。

工作台适配仍在持续完善，使用前建议保留默认参数，逐项调整。

### 安全中心侧边栏

LiquidDock 也可以为新版 HyperOS 4 安全中心侧边栏页面加入液态玻璃效果，包括游戏工具箱、视频工具箱、Global Dock 和 All Apps。

不同系统版本的安全中心差异较大，如果当前版本不兼容，LiquidDock 会保留系统原来的界面。

## 兼容范围

当前主要开发和测试环境：

| 项目 | 建议版本 |
| --- | --- |
| HyperOS | 3.0.307 及以上 |
| 系统桌面 | `com.miui.home` release-4.50.x.x |
| LSPosed | 支持 libxposed API 101 的版本 |

系统桌面和安全中心更新后，部分功能可能需要重新适配。如果遇到异常，建议先确认系统桌面版本是否仍在当前支持范围内。

## 安装

1. 从 [GitHub Releases](https://github.com/yu4032/LiquidDock/releases) 下载最新 APK。
2. 安装后在 LSPosed 中启用 LiquidDock。
3. 勾选：
   - 系统桌面 `com.miui.home`
   - 系统界面 `com.android.systemui`
   - 安全中心 `com.miui.securitycenter`
4. 重启对应进程，或者直接重启设备。
5. 打开 LiquidDock，按需要开启功能。

部分布局类选项需要重启桌面后生效，设置页中会尽量标明。

## 反馈问题

提交 Issue 时请附上：

- HyperOS 版本；
- 系统桌面版本；
- 出问题前开启了哪些 LiquidDock 选项；
- 简单、稳定的复现步骤；
- 如果方便，附上相关日志。

工作台或安全中心相关问题，也请注明当时使用的具体页面或模式。

## 更多文档

想了解所有设置，可以查看 [FEATURES.md](FEATURES.md)。

开发、适配和内部实现相关内容放在：

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [HOOKS.md](HOOKS.md)
- [CONTRIBUTING.md](CONTRIBUTING.md)
- [TODO.md](TODO.md)
- [CHANGELOG.md](CHANGELOG.md)

## 风险提示

> [!WARNING]
> LiquidDock 会修改系统桌面、系统界面和安全中心的部分表现。系统组件升级后可能出现兼容问题，更新前请保留可用的恢复方式。

LiquidDock 是社区项目，与 Xiaomi、LSPosed 或其他相关项目没有隶属关系。

## Credits

- **Prismal** — 液态玻璃视觉效果参考
- **LSPosed / libxposed** — 模块运行环境
- **HyperCeiler** — HyperOS 模块开发参考

## License

[GNU General Public License v3.0](LICENSE)
