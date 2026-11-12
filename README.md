# LiquidDock

<p align="center">
  <strong>简体中文</strong> · <a href="./README_EN.md">English</a>
</p>

<p align="center">
  <a href="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml"><img alt="Build" src="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml/badge.svg"></a>
  <a href="https://github.com/yu4032/LiquidDock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/yu4032/LiquidDock"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/yu4032/LiquidDock"></a>
</p>

LiquidDock 是一个面向 HyperOS 3 平板体验的 LSPosed 模块，用来调整桌面布局、Dock、多任务界面，并把液态玻璃效果应用到桌面、系统界面和部分第三方应用。
<p align="center">
<strong>桌面总览</strong><br />
<a href="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6"><img width="640" alt="桌面总览" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" /></a>
</p>
<details>
<summary><strong>效果展示</strong></summary>




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

## 主要功能

### 液态玻璃

开启 LiquidDock 和液态玻璃总开关后，可以为以下界面加入统一的液态玻璃效果；图标、小组件、文件夹、多任务按钮和各类扩展界面还需开启对应功能：

- Dock；
- 桌面图标和 Dock 功能图标，支持玻璃轮廓裁剪；
- 小组件；
- 小文件夹和大文件夹；
- 拖动中的图标、小组件和文件夹；
- 长按桌面图标弹出的快捷菜单；
- 多任务界面的“清除全部”和设备互联操作按钮；
- 桌面卸载、移除和二次确认弹窗；
- 应用顶部的分屏、小窗等控制菜单；
- 新版安全中心侧边栏；
- MIUI 系统搜索主界面；
- Gboard 悬浮键盘及相关工具栏。

支持调节模糊、折射、色散、亮度、高光、阴影、圆角与颜色。染色调整背景色调，底色控制纯色覆盖；描边和外阴影支持全局继承与各组件独立设置。

已经运行的玻璃 Hook 支持实时调整材质；首次安装 Hook、调整网格等结构设置后，重启对应作用域生效。

### 桌面布局

开启“自由主界面网格”后，可选横屏 **2–10 列、2–6 行**布局，竖屏交换行列，并取消了旋转位置映射，单独保留横竖屏图标与小组件位置。可分别调整横屏与竖屏桌面网格的：

- 水平距离；
- 顶部和底部距离；
- 行距；
- 页面指示器位置；
- 小组件尺寸适配。

此外还取消 2x1、2x2 和 4x2 小部件的位置限制，允许将小部件放置在任意位置。
缩小网格时会检查现有 4×2 小组件是否适配，并提示调整组件；修改网格后重启桌面生效。

还可独立开启 **80%–120% 图标大小**调节，作用于桌面、Dock、文件夹预览及工作台应用页中的受支持图标，不会让普通应用抽屉和搜索页统一缩放。

### Dock

开启 Dock 自定义后，可以调整 Dock 尺寸、位置、图标间距等参数。还可在外观页面设置描边、阴影和分隔线：

- 宽度和高度；
- 底部位置；
- 图标间距；
- 模糊和圆角；
- 描边；
- 整体阴影和描边阴影；
- 分隔线。

支持单独隐藏 Dock 中的手机互联入口，系统互联和流转功能保持运行。

可使用最近应用黑名单，阻止指定应用出现在 Dock 最近应用推荐区；也可通过「**Dock → 行为 → 隐藏 Dock 最近任务图标**」直接关闭整个最近任务推荐区的图标显示。此开关**默认关闭**，不影响固定的 Dock 图标和系统多任务界面，修改后重启桌面生效。

「原生 Dock 阴影」只在开启 Dock 自定义且关闭液态玻璃时生效；启用液态玻璃后，可在独立的「液态玻璃阴影」页面调整 Prismal 外阴影与光照描边。

### 小组件与文件夹

小组件支持玻璃背景和深色内容适配。在「小组件部件管理」中可通过**可识别的部件标题**选择桌面小组件的具体处理目标：

- **隐藏背景部件**：选取小组件内不需要的背景区域；隐藏规则支持单独备份和恢复。
- **部件白化 · 深色适配**：将选中的 RemoteViews 文字变为白色，或将图片显示为保留原有透明度的白色图形。
- **规则管理**：在隐藏与白化之间切换单个部件，两类规则分别保存。

精确白化适用于 **RemoteViews TextView / ImageView**；MAML 小组件使用背景隐藏与常规材质适配。修改部件规则后重启桌面。

小文件夹和大文件夹可以分别控制玻璃效果开关、尺寸和圆角。

展开文件夹支持独立液态玻璃背景，保留系统原生打开与关闭动画。在「外观 → 玻璃组件 → 文件夹」中可调节模糊、折射率、厚度、染色以及**独立底色 RGB 与透明度**；底色每项设为 **−1** 时继承全局，设为 0–255 时使用独立数值。外部原生模糊和其他玻璃组件的渐隐时长也可独立调节。首次启用展开文件夹玻璃后重启桌面。

四宫格、九宫格大文件夹支持真实 **2×2 桌面占位**。在桌面原生文件夹设置中，可按文件夹开启真实大文件夹和标题；在「外观 → 文件夹」可调整内部图标大小（**60%–110%，默认 100%**）及原生描边。内部图标大小调节独立于玻璃开关。

绵狗修改版桌面提供独立兼容开关（默认关闭），用于恢复带标记的真实 2×2 大文件夹尺寸。

### 快捷菜单与对话框

长按桌面图标弹出的快捷菜单支持玻璃背景、深色文字适配，以及独立的染色、底色、模糊与光学参数。也可一键恢复继承全局配置。

桌面卸载、移除和二次确认弹窗可开启玻璃背景，并可设置：

- 是否保留背景压暗；
- 原生深色模式；
- 独立颜色；
- 独立模糊度；
- 恢复继承全局玻璃外观。

弹窗「**全部参数**」页面还提供折射、高光、底色覆盖等独立光学设置与全局继承恢复。

### 多任务与工作台

多任务界面支持：

- 调整背景壁纸模糊；
- 取消壁纸压暗；
- 为“清除全部”和设备互联按钮启用玻璃。

工作台「任务栏模式」可让 Dock 背景贴合屏幕左右及底边，去除相应圆角和边缘高光；不论使用液态玻璃还是原生背景都可生效，并保留系统 Dock 飞入、飞出动画。工作台还支持 Dock **整体垂直平移**、横竖屏独立的**图标垂直偏移**及图标玻璃圆角。

All Apps 除了可分别调节横竖屏图标区左右、上下间距，还支持**横屏和竖屏独立的页面指示器高度偏移**（-160～160 dp，正值下移、负值上移），不影响普通桌面的页面指示器。Dock 图标设置需开启工作台自定义；分隔线由独立开关控制。

### 系统界面与安全中心

可选的系统界面适配包括：

- 应用顶部的分屏、小窗等控制菜单；
- 安全中心游戏工具箱、视频工具箱、Global Dock 和 All Apps 等已支持侧边栏界面，且适配了侧滑停顿手势。

应用顶部菜单可独立调整玻璃开关、模糊、颜色和光学参数，无需沿用全局玻璃效果。

这两类玻璃功能需同时开启 LiquidDock 总开关、液态玻璃总开关和对应功能开关，并依赖目标系统适配可用。

### MIUI 系统搜索与 Gboard

MIUI 系统搜索支持独立玻璃背景、染色与模糊设置。「**全部参数**」还可以调整折射、高光与底色覆盖，未单独设置的参数继承全局。

Gboard 支持悬浮键盘和工具栏玻璃，可独立设置染色、底色、模糊与完整光学参数，并在「**全部参数**」中恢复全局继承。底部手柄支持单独控制拖动后的自动大小调整。

### 配置、动画与备份

可以调整工作区显隐、**解锁时工作区玻璃渐显**、Dock 图标恢复、文件夹打开时其他玻璃渐隐、按压反馈、Dock 尺寸变化和设置页面切换速度。从应用返回桌面时，Dock 图标玻璃会在原生返回动画的末段渐显。另有可选的「禁用壁纸缩放」开关，通过限制 Launcher 发出的壁纸缩放目标，尝试减少进出应用和多任务的壁纸变换负担，不改变工作区动画；首次安装需要重启桌面，具体效果依赖系统 Launcher 实现。

在「动画与过渡 → 系统联动」中还可以选择进入桌面或多任务后**隐藏手势小白条**，离开这些界面时恢复系统控制；该选项默认关闭，首次安装对应 Hook 需要重启系统界面。

设置界面采用 MIUIX + Prismal 风格，设置项按功能分组。**四个根页面支持下拉搜索**，可以查找染色、底色、圆角、文件夹、网格、描边与阴影等具体参数，并定位到所属 Cell。滑块支持实时拖动、直接输入、加减与重置；底部玻璃胶囊支持滑动切换页面。

可以通过「IOS玻璃效果」开关调整设置界面自身的外观；重启作用域对话框支持滚动和多选，不影响桌面玻璃功能的独立开关。

设置页支持：

- 恢复内置默认配置；
- 导出当前配置为 JSON；
- 导入 JSON 配置；
- 单独备份和恢复小组件隐藏规则。

首次安装载入内置默认配置；升级沿用已保存的用户设置。

## 兼容范围

兼容环境：

| 项目 | 当前范围 |
| --- | --- |
| HyperOS | HyperOS 3 平板版本（3.0.307 及以上）；HyperOS 4 适配在独立分支开发 |
| 系统桌面 | `com.miui.home` release-4.50.x.x |
| LSPosed | 支持 libxposed API 102 的版本 |

系统桌面、SystemUI、安全中心、Gboard 或系统搜索更新后，对应功能可能需要重新适配。

## 安装

1. 从 [GitHub Releases](https://github.com/yu4032/LiquidDock/releases) 下载最新 APK。
2. 安装后在 LSPosed 中启用 LiquidDock。
3. 根据需要勾选作用域：

| 作用域 | 用途 |
| --- | --- |
| `com.miui.home` | 必选：桌面、Dock、多任务、文件夹、小组件等核心功能 |
| `com.android.systemui` | 必选：桌面转场，以及可选的应用顶部菜单玻璃 |
| `com.miui.securitycenter` | 仅安全中心侧边栏玻璃和手势需要 |
| `com.google.android.inputmethod.latin` | 仅 Gboard 适配需要 |
| `com.android.quicksearchbox` | 仅 MIUI 系统搜索玻璃需要 |
| `system`（System Framework） | 仅可选的壁纸 GPU 渲染开关需要，启用后重启设备 |

4. 重启对应进程，或直接重启设备。
5. 打开 LiquidDock，根据需要开启和调整功能。

多数已加载 Hook 的玻璃外观参数可即时更新；结构性布局、首次启用的适配以及少数 Hook 安装选项仍需重启桌面、系统界面或对应应用。可在设置界面通过「重启作用域」勾选需要刷新的进程。

## 反馈问题

提交 Issue 时建议附上：

- HyperOS 版本；
- 系统桌面版本；
- 涉及功能对应的应用版本；
- 当时开启的 LiquidDock 选项；
- 稳定的复现步骤；
- 必要时附上相关日志或对应版本应用安装包。

## 文档

- [FEATURES.md](FEATURES.md) — 当前功能与设置说明
- [ARCHITECTURE.md](ARCHITECTURE.md) — 当前运行架构
- [HOOKS.md](HOOKS.md) — 当前适配入口与边界
- [CONTRIBUTING.md](CONTRIBUTING.md) — 开发约定
- [TODO.md](TODO.md) — 当前仍未完成的工作
- [CHANGELOG.md](CHANGELOG.md) — 版本变化
- [DIVIDER.md](DIVIDER.md) — 工作台分隔线说明
- [docs/release-signing.md](docs/release-signing.md) — Release 构建、隔离签名与安全边界

## 使用说明

LiquidDock 通过 LSPosed 调整桌面、系统界面和选定应用的显示行为。升级前备份配置，并保留可用的恢复方式。

LiquidDock 由社区独立维护。

## Credits

- [Compose Miuix](https://github.com/compose-miuix-ui/miuix) — MIUIX Compose 界面框架
- [PrismalAGSL](https://github.com/styropyr0/PrismalAGSL) — 设置界面的液态玻璃组件
- [AndroidX / Jetpack](https://source.android.com/docs/setup/about/licenses) — Android 基础界面组件
- [LSPosed / libxposed](https://github.com/LSPosed/LSPosed) — 模块 Hook API 与运行框架
- [HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) — HyperOS 模块开发参考
- [Prismal](https://github.com/styropyr0/Prismal) — 液态玻璃光学模型参考

## License

[GNU General Public License v3.0](LICENSE)
