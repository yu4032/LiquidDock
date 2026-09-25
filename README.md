# LiquidDock

<p align="center">
  <strong>简体中文</strong> · <a href="./README_EN.md">English</a>
</p>

<p align="center">
  <a href="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml"><img alt="Build" src="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml/badge.svg"></a>
  <a href="https://github.com/yu4032/LiquidDock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/yu4032/LiquidDock"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/yu4032/LiquidDock"></a>
</p>

LiquidDock 是一个面向 HyperOS 平板体验的 LSPosed 模块，用来调整桌面布局、Dock、多任务界面，并把液态玻璃效果扩展到更多系统界面和部分第三方应用。

<p align="center">
  <img width="3008" height="1880" alt="LiquidDock on HyperOS Launcher" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" />
</p>

## 主要功能

### 液态玻璃

LiquidDock 可以为以下界面加入统一的液态玻璃效果：

- Dock；
- 桌面图标和 Dock 功能图标；
- 小组件；
- 小文件夹和大文件夹；
- 拖动中的图标、小组件和文件夹；
- 长按桌面图标弹出的快捷菜单；
- 多任务界面的“清除全部”和设备互联操作按钮；
- 桌面卸载、移除和二次确认弹窗；
- 应用顶部的分屏、小窗等控制菜单；
- 安全中心侧边栏；
- MIUI 系统搜索主界面；
- Gboard 悬浮键盘及相关工具栏。

可以单独调整模糊、折射、色散、颜色、亮度、阴影、高光、圆角和不同类型的高光层。

<p align="center">
  <img width="704" height="440" alt="LiquidDock glass example" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" />
</p>

### 桌面布局

支持 **8×4 / 4×8** 和 **10×6 / 6×10** 两套桌面网格，并可分别调整横屏与竖屏的：

- 水平距离；
- 顶部和底部距离；
- 行距；
- 页面指示器位置；
- 小组件尺寸适配。

还可以单独调整工作区、Dock、文件夹等位置的图标大小。

### Dock

可以调整：

- 宽度和高度；
- 底部位置；
- 图标间距；
- 模糊和圆角；
- 描边；
- 方圆形轮廓；
- 整体阴影和描边阴影；
- 工作台分隔线。

手机互联入口可以单独隐藏，不会关闭系统本身的互联功能。

### 小组件与文件夹

小组件支持玻璃背景和深色内容适配。对于部分小组件，还可以扫描当前桌面上的实际组件，并手动选择要隐藏的内部背景区域；隐藏规则可以单独备份和恢复。

小文件夹和大文件夹可以分别控制玻璃、尺寸和圆角。

### 快捷菜单与对话框

长按桌面图标的快捷菜单可以替换为玻璃背景，并提供适合深色背景的白色文字和图标模式。

桌面卸载、移除和二次确认弹窗可以独立启用玻璃，并可设置：

- 是否保留背景压暗；
- 原生深色模式；
- 独立颜色；
- 独立模糊度；
- 恢复继承全局玻璃外观。

### 多任务与工作台

多任务界面支持：

- 调整背景壁纸模糊；
- 取消壁纸压暗；
- 为“清除全部”和设备互联按钮启用玻璃。

工作台模式提供独立的 Dock 长度、图标位置、图标圆角、桌面水平位置、所有应用页面间距和分隔线设置。

### 系统界面与安全中心

可选的系统界面适配包括：

- 应用顶部的分屏、小窗等控制菜单；
- 安全中心游戏工具箱、视频工具箱、Global Dock 和 All Apps 等已支持侧边栏界面。

这两类功能默认不会自动开启，需要用户主动启用。

### MIUI 系统搜索与 Gboard

MIUI 系统搜索可以把主界面的背景替换为液态玻璃，并单独设置颜色和模糊度。

Gboard 支持悬浮键盘玻璃、相关工具栏玻璃，以及独立颜色和模糊度；还可以控制拖动底部手柄后是否自动进入大小调整。

### 配置、动画与备份

可以调整工作区显隐、Dock 图标恢复、按压反馈、Dock 尺寸变化和设置页面切换速度。

设置页支持：

- 恢复内置默认配置；
- 导出当前配置为 JSON；
- 导入 JSON 配置；
- 单独备份和恢复小组件隐藏规则。

首次使用会自动采用当前项目内置的默认配置；已有设置不会在升级时被默认配置覆盖。

## 兼容范围

当前主要开发和验证环境：

| 项目 | 当前范围 |
| --- | --- |
| HyperOS | 主要面向 3.0.307 及以上平板版本 |
| 系统桌面 | `com.miui.home` release-4.50.x.x |
| LSPosed | 支持 libxposed API 101 的版本 |

系统桌面、SystemUI、安全中心、Gboard 或系统搜索更新后，对应功能可能需要重新适配。

## 安装

1. 从 [GitHub Releases](https://github.com/yu4032/LiquidDock/releases) 下载最新 APK。
2. 安装后在 LSPosed 中启用 LiquidDock。
3. 根据需要勾选作用域：

| 作用域 | 用途 |
| --- | --- |
| `com.miui.home` | 必选：桌面、Dock、多任务、文件夹、小组件等核心功能 |
| `com.android.systemui` | 推荐：桌面转场，以及可选的应用顶部菜单玻璃 |
| `com.miui.securitycenter` | 仅安全中心侧边栏玻璃需要 |
| `com.google.android.inputmethod.latin` | 仅 Gboard 适配需要 |
| `com.android.quicksearchbox` | 仅 MIUI 系统搜索玻璃需要 |

4. 重启对应进程，或直接重启设备。
5. 打开 LiquidDock，根据需要开启和调整功能。

部分结构性设置需要重启桌面、系统界面或对应应用后生效，设置页会尽量标明。

## 反馈问题

提交 Issue 时建议附上：

- HyperOS 版本；
- 系统桌面版本；
- 涉及功能对应的应用版本；
- 当时开启的 LiquidDock 选项；
- 稳定的复现步骤；
- 必要时附上相关日志。

## 文档

- [FEATURES.md](FEATURES.md) — 当前功能与设置说明
- [ARCHITECTURE.md](ARCHITECTURE.md) — 当前运行架构
- [HOOKS.md](HOOKS.md) — 当前适配入口与边界
- [CONTRIBUTING.md](CONTRIBUTING.md) — 开发约定
- [TODO.md](TODO.md) — 当前仍未完成的工作
- [CHANGELOG.md](CHANGELOG.md) — 版本变化
- [DIVIDER.md](DIVIDER.md) — 工作台分隔线说明

`docs/superpowers/` 下的 plans/specs/verification 是历史开发记录，不代表当前实现。

## 风险提示

> [!WARNING]
> LiquidDock 会修改系统桌面、系统界面、安全中心和部分第三方应用的显示行为。系统组件或应用升级后可能出现兼容问题，更新前请保留可用的恢复方式。

LiquidDock 是社区项目，与 Xiaomi、LSPosed 或其他相关项目没有隶属关系。

## Credits

- **Prismal** — 液态玻璃视觉效果参考
- **LSPosed / libxposed** — 模块运行环境
- **HyperCeiler** — HyperOS 模块开发参考

## License

[GNU General Public License v3.0](LICENSE)
