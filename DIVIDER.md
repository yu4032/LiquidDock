# LiquidDock Workstation Divider

本文档描述当前 `main` / **v2.5.1** 的工作台 Dock 分隔线行为。

Divider 是独立的视觉设置，不拥有 Workstation Dock 长度、图标位置、All Apps 布局或玻璃 source。

## 1. 用户设置

设置页提供：

| 设置 | 当前范围 | 说明 |
| --- | ---: | --- |
| 自定义 Dock 分隔线 | 开/关 | 独立视觉开关 |
| 分隔线宽度 | 0–160 | UI 显示单位为 `dp×10`，运行时除以 10 |
| 高度比例 | 0–100 | 相对 parent 高度百分比 |
| 垂直偏移 | −80–80 | UI 显示单位为 `dp×10`，运行时除以 10 |
| 红 / 绿 / 蓝 | 0–255 | 背景颜色 |
| 透明度 | 0–255 | 背景 alpha |

宽度和垂直偏移沿用历史 raw tenths-of-dp 存储格式。例如值 `10` 表示运行时 `1.0 dp`。

它们不是普通 `DP_TENTHS` sidecar key。这个存储约定用于保持旧配置和 JSON 兼容，不能直接改成新的通用小数格式。

## 2. 启用条件

Divider 的实际显示同时受：

- LiquidDock 总开关；
- Divider runtime gate；
- 当前配置中的 Divider enabled；

控制。

存在旧 Divider 参数但没有显式开关的历史配置会经过兼容逻辑保持原有行为。

## 3. Geometry

`DockDividerHook` 从真实 divider View 和 parent layout 获取几何。

应用顺序包括：

- width；
- 基于 parent 高度计算 height；
- Y/top offset；
- RGBA background。

如果 parent 高度尚未有效，会等待后续 layout/pre-draw，而不是使用 divider 自身高度伪造结果。

## 4. Restore ownership

第一次修改 divider View 前会保存 `DividerSnapshot`。

snapshot 包含：

- width；
- height；
- margins；
- background。

关闭 Divider 或 owner 释放时：

```text
runtime gate -> disabled
        ↓
pending callback sees disabled
        ↓
remove pending listener
        ↓
restore saved layout/background
        ↓
requestLayout
        ↓
release snapshot
```

这样已经排队的 callback 不会在关闭后再次写入自定义状态。

## 5. Drawable snapshot

背景不是简单保存同一个 Drawable 引用。

对于可复制的 Drawable，会优先创建独立 snapshot，避免 LiquidDock 后续 mutation 同时污染“原始背景”。

只有无法复制时才退化为原引用。

## 6. Re-enable

释放时会丢弃旧 snapshot。

以后重新启用 Divider 时，会以**当时最新的 vendor state**作为新的恢复基线，而不是永久恢复到进程启动时的旧状态。

这对以下场景很重要：

- 主题变化；
- Workstation 重新创建；
- holder/rebind；
- 横竖屏切换。

## 7. 与其他 Workstation 设置的边界

Divider 不拥有：

- Workstation Dock width；
- Dock icon top/bottom spacing；
- Dock icon glass radius；
- Workspace grid offset；
- All Apps spacing；
- Recents recovery；
- wallpaper freshness；
- normal-layout backup。

修改 Divider 时不要触发全局 Workstation restore。

## 8. Regression checklist

修改 `DockDividerHook` 或 Divider config 时至少检查：

- 首次 bind geometry；
- parent height 为 0 时 defer；
- width/height/margins/background 精确恢复；
- disable 后 pending callback 不重新写入；
- background snapshot 不被 alias mutation；
- re-enable 重新捕获 vendor state；
- 普通模式不受影响；
- Workstation enter/exit；
- holder replacement；
- 旧 JSON 中 raw tenths-of-dp 值仍能正确导入。

最低 CI：

```bash
./gradlew testDebugUnitTest assembleDebug --stacktrace
```

真实 Workstation View 生命周期仍需要目标设备验证。
