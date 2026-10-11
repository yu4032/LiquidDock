# LiquidDock

<p align="center">
  <strong>简体中文</strong> · <a href="./README_EN.md">English</a>
</p>

<p align="center">
  <a href="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml"><img alt="Build" src="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml/badge.svg"></a>
  <a href="https://github.com/yu4032/LiquidDock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/yu4032/LiquidDock"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/yu4032/LiquidDock"></a>
</p>

LiquidDock 是一款面向 HyperOS 3 平板的 LSPosed 模块，用于自定义桌面、Dock、多任务界面和液态玻璃外观。

<p align="center">
  <img width="3008" height="1880" alt="LiquidDock 桌面效果" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" />
</p>

## 功能

### 桌面布局

- 自由调整主屏幕网格，横屏支持 2–10 列、2–6 行；竖屏自动交换行列，并分别记忆图标位置。
- 分别设置横竖屏的水平距离、上下间距、行距和页面指示器位置，也可调整分屏布局偏移。
- 自定义桌面及 Dock 图标大小。多列小组件可跟随网格间距拉伸，或保持原尺寸居中。
- 缩小网格时检查已有的 4×2 小组件，避免保存无法容纳它们的布局。

### Dock 与工作台

- 调整 Dock 的宽高、位置、图标间距、圆角和背景模糊。
- 自定义 Dock 描边、方圆曲线、整体阴影、描边阴影及分隔线。
- 调整 Dock 尺寸变化动画，隐藏手机互联入口，或通过黑名单排除最近应用推荐中的指定应用。
- 工作台模式下可单独调整 Dock 图标位置、桌面水平偏移，以及所有应用页面在横竖屏下的间距。

### 液态玻璃

液态玻璃可分别用于 Dock、桌面图标、Dock 功能图标、小组件、大小文件夹、拖动中的桌面元素、快捷菜单和多任务操作按钮。

玻璃外观可调整模糊、厚度、折射、色散、颜色、透明度、光照、高光、阴影与圆角；图标和文件夹、小组件和大文件夹还可分别选择使用哪些高光效果。背景采样分辨率和渲染帧率也可以调节。

<p align="center">
  <img width="704" height="440" alt="LiquidDock 液态玻璃效果" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" />
</p>

### 小组件与文件夹

- 小组件可使用玻璃背景，并对部分深色内容进行适配。
- 在小组件部件管理中，按具体组件选择隐藏背景部件，或将文字、图片部件白化。隐藏规则支持独立备份。
- 小文件夹和大文件夹拥有各自的玻璃开关、尺寸和圆角设置。

### 多任务与系统界面

- 调整多任务背景壁纸的模糊强度，取消壁纸压暗，为「清除全部」和设备互联按钮添加玻璃。
- 在桌面和多任务界面隐藏手势小白条。
- 为桌面卸载、移除和二次确认弹窗，以及应用顶部的分屏、小窗菜单使用玻璃背景。
- 安全中心侧边栏支持玻璃外观；还可使用侧滑停靠手势呼出小 Dock，并调整第二段触发距离。

### 系统搜索与 Gboard

- **MIUI 系统搜索**：主界面玻璃背景，可单独调整颜色、模糊和其他光学参数。
- **Gboard**：悬浮键盘及相关工具栏玻璃，支持独立外观参数和悬浮键盘拖动手柄行为设置。

### 动画与设置

可调整工作区显隐、Dock 图标恢复、玻璃按压反馈、菜单退出及设置页面切换动画。多数已加载的玻璃外观设置可以即时生效。

设置界面提供 iOS 风格玻璃开关、JSON 配置导入与导出、恢复默认配置及作用域重启。
