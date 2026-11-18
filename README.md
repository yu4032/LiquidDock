# LiquidDock4

<p align="center">
  <strong>简体中文</strong> · <a href="./README_EN.md">English</a>
</p>

<p align="center">
  <a href="https://github.com/yu4032/LiquidDock/actions/workflows/build.yml"><img alt="Build" src="https://github.com/yu4032/LiquidDock/actions/workflows/build.yml/badge.svg"></a>
  <a href="https://github.com/yu4032/LiquidDock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/yu4032/LiquidDock"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/yu4032/LiquidDock"></a>
</p>

面向 **HyperOS 4 / HyperOS 3** 平板的 LSPosed 桌面与系统界面定制模块，提供原生柔光/液态玻璃质感、自由桌面网格、大文件夹与小组件尺寸增强，以及工作台和多任务体验扩展。

- **HyperOS 4（`v3.0+` / `main`）**：针对新一代基于 `hyos_spawner` 的系统桌面提供原生层适配，并与独立进程 LSPosed Hook 协同工作。
- **HyperOS 3（旧版桌面）**：兼容 `com.miui.home release-4.50.x.x`（旧版完整归档见 [`archive/2.7.9`](https://github.com/yu4032/LiquidDock/tree/archive/2.7.9) 分支）。

<p align="center">
  <a href="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6"><img width="680" alt="桌面总览" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" /></a>
</p>

<details>
<summary><strong>更多效果展示</strong></summary>

<table align="center">
<tr>
<td width="50%" align="center" valign="top">
<p><strong>快捷菜单</strong></p>
<a href="https://github.com/user-attachments/assets/4a8104b4-d4de-4319-b6d8-99b3d9603b20"><img width="300" alt="快捷菜单" src="https://github.com/user-attachments/assets/4a8104b4-d4de-4319-b6d8-99b3d9603b20" /></a>
</td>
<td width="50%" align="center" valign="top">
<p><strong>桌面对话框</strong></p>
<a href="https://github.com/user-attachments/assets/7b4a152b-bb7a-4b36-a8de-841ec0ea2618"><img width="300" alt="桌面对话框" src="https://github.com/user-attachments/assets/7b4a152b-bb7a-4b36-a8de-841ec0ea2618" /></a>
</td>
</tr>
<tr>
<td width="50%" align="center" valign="top">
<p><strong>安全中心侧边栏</strong></p>
<a href="https://github.com/user-attachments/assets/ec773cda-b8cf-4ac8-9a78-afce2437519c"><img height="280" alt="安全中心侧边栏" src="https://github.com/user-attachments/assets/ec773cda-b8cf-4ac8-9a78-afce2437519c" /></a>
</td>
<td width="50%" align="center" valign="top">
<p><strong>系统搜索</strong></p>
<a href="https://github.com/user-attachments/assets/41c5e987-bc6d-45d3-ac2f-c14b44d61057"><img width="300" alt="系统搜索" src="https://github.com/user-attachments/assets/41c5e987-bc6d-45d3-ac2f-c14b44d61057" /></a>
</td>
</tr>
</table>

<p align="center">
<strong>玻璃细节</strong><br />
<a href="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4"><img width="440" alt="玻璃细节" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" /></a>
</p>

<p align="center">
<strong>应用顶部菜单</strong><br />
<a href="https://github.com/user-attachments/assets/9f39ca0c-3fd3-4cc4-ba6c-d8af01354788"><img width="640" alt="应用顶部菜单" src="https://github.com/user-attachments/assets/9f39ca0c-3fd3-4cc4-ba6c-d8af01354788" /></a>
</p>

<p align="center">
<strong>多任务操作按钮</strong><br />
<a href="https://github.com/user-attachments/assets/badcd56c-b013-4a12-a018-bc505ee943f2"><img width="640" alt="多任务操作按钮" src="https://github.com/user-attachments/assets/badcd56c-b013-4a12-a018-bc505ee943f2" /></a>
</p>

</details>

## 核心特性

### HyperOS 4（OS4）

- **原生柔光玻璃**：自定义系统桌面柔光玻璃光学参数（边缘带、反射、暗部补光、方向光），支持组件玻璃、拖拽态材质、图标圆角自适应及 MAML 小组件材质控制。
- **桌面网格与大文件夹**：支持自由网格行列数、横竖屏独立边距与指示器偏移；支持平板大文件夹、内部网格行列自定义、4×2 对称预览内边距、图标对齐与标题控制。
- **小组件增强**：支持 **1×1 ↔ 2×2** 与 **2×1 ↔ 4×2** 菜单快速切换、防止小组件拉伸，以及水平偏移调整。
- **Dock 与工作台**：支持 Dock 尺寸/位置/分割线配置、工作台任务栏模式与悬空边距、Dock/图标垂直偏移、所有应用（All Apps）横竖屏独立边距及背景模糊调节。
- **独立进程扩展与安全**：支持多任务操作按钮、应用顶部菜单、安全中心侧边栏、系统搜索与 Gboard 液态玻璃；支持开启系统多分屏（最多 6 个）；内置 Hook 状态监控与 **20 秒内连续崩溃 8 次自动熔断安全模式**。

### HyperOS 3（OS3）

- **全组件液态玻璃**：为 Dock、图标、小组件、大小文件夹、长按快捷菜单、桌面弹窗、拖拽态及系统扩展界面提供统一液态玻璃效果，支持模糊、折射、色散、高光、描边与外阴影的全局继承或独立覆写。
- **自由桌面网格**：支持横屏 **2–10 列、2–6 行**（竖屏独立保存位置），取消旋转映射与 2×1/2×2/4×2 小组件放置限制，支持 80%–120% 桌面图标缩放。
- **Dock 与行为控制**：支持尺寸、间距、圆角、阴影、分隔线，以及隐藏手机互联入口与 Dock 近期应用黑名单。
- **RemoteViews 与文件夹细节**：支持基于部件标题的 RemoteViews 背景隐藏与精确白化、真实 2×2 大文件夹占位及展开文件夹独立液态玻璃背景。
- **系统联动与动画**：支持手势小白条隐藏、禁用壁纸缩放、工作区渐显与自定义过渡速率。

> 详细功能与参数说明请参阅 [FEATURES.md](FEATURES.md)。

## 兼容环境

| 项目 | 要求 / 支持范围 | 实测环境 |
| --- | --- | --- |
| HyperOS 版本 | **HyperOS 4（OS4）** / **HyperOS 3（3.0.307+）** | `OS4.0.3.0.XAOCNXM` |
| Android 版本 | Android 13+（`minSdk = 33`） | Android 17（SDK `37`） |
| 架构 | `arm64-v8a`（支持 16KB 页面对齐） | `arm64-v8a` |
| 系统桌面 | • **OS4**：Flutter/Dart + Rust AOT 桌面<br>• **OS3**：`release-4.50.x.x` | `RELEASE-8.01.02.8583` / `8602` / `9239` |
| 框架与权限 | 支持 libxposed API 102 的 LSPosed + Root（`su`） | LSPosed `v2.2.1` + Zygisk Next `1.5.3` |

## 快速开始

1. 从 [GitHub Releases](https://github.com/yu4032/LiquidDock/releases) 下载并安装最新 APK。
2. 在 **LSPosed** 中启用 **LiquidDock4**，并按需勾选作用域：

| 作用域 | 说明 |
| --- | --- |
| `com.miui.home` | **必选**：桌面网格、Dock、大文件夹、小组件、工作台与柔光玻璃 |
| `system` | **必选**：小组件布局约束与系统动画同步（变更后需重启设备） |
| `com.android.systemui` | 可选：手势小白条、分屏/手柄菜单与系统转场联动 |
| `com.miui.securitycenter` | 可选：安全中心侧边栏玻璃 |
| `com.google.android.inputmethod.latin` | 可选：Gboard 悬浮键盘玻璃 |
| `com.android.quicksearchbox` | 可选：MIUI 系统搜索框玻璃 |

3. 重启对应进程或重启设备。
4. 打开 **LiquidDock4** 按需开启功能并调整参数。

> **提示**：更新或清空过桌面数据后，建议在模块内点击「重启系统桌面」以重新同步配置；若触发安全模式，可在模块内手动退出。

## 相关文档

- [FEATURES.md](FEATURES.md) — 完整功能与设置说明
- [CHANGELOG.md](CHANGELOG.md) — 版本更新日志
- [CONTRIBUTING.md](CONTRIBUTING.md) — 参与贡献与开发约定
- [docs/release-signing.md](docs/release-signing.md) — 构建与发布签名说明

## 致谢

- [Compose Miuix](https://github.com/compose-miuix-ui/miuix) — MIUIX Compose 界面框架
- [PrismalAGSL](https://github.com/styropyr0/PrismalAGSL) / [Prismal](https://github.com/styropyr0/Prismal) — 液态玻璃组件与光学模型参考
- [LSPosed / libxposed](https://github.com/LSPosed/LSPosed) — Hook 框架与 API
- [HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) — HyperOS 模块开发参考

## License

[GNU General Public License v3.0](LICENSE)
