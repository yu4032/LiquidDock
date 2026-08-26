# LiquidDock Workstation Divider

本文档描述当前 `main` / **v2.1.1** 的 `DockDividerHook` 行为。

Divider 属于 Workstation/Laptop Dock 的实验性适配部分；参数可以使用，但工作台整体仍未视为完全支持。

## 1. 参数语义

| 参数 | 持久化范围 | 运行时语义 |
|---|---:|---|
| Divider 开关 | 开/关 | live visual toggle |
| width | 0 ~ 160 | 历史 raw `0.1 dp`；运行时除以 10 |
| height scale | 0 ~ 100 | 相对父容器高度百分比 |
| Y offset | −80 ~ 80 | 历史 raw `0.1 dp`；运行时除以 10 |
| R / G / B | 0 ~ 255 | Divider background color |
| Alpha | 0 ~ 255 | Divider alpha |

Divider 的 width / Y offset 历史存储语义与普通 Dock dimension key 不同，不要统一成普通 `DP_TENTHS` sidecar 规则。

## 2. Hook 边界

`DockDividerHook` 从 Workstation Dock line holder/bind 生命周期取得 divider View，并在 View 已拥有有效父布局后应用：

- width；
- height；
- top/Y offset；
- RGBA background。

当父容器高度尚未有效时，不使用 divider 自身高度作为错误 fallback，而是等待后续 layout/pre-draw 再计算。

## 3. Runtime ownership

v2.1.1 起 Divider 开关使用 `VisualRuntimeState`。

关闭顺序：

1. 先发布 `dividerEnabled=false`；
2. 已排队的 geometry callback 执行时先看到 disabled；
3. 取消 pending pre-draw listener；
4. 恢复原始 layout/background；
5. `requestLayout()`；
6. 清除 ownership snapshot。

因此关闭后不会出现 queued callback 再次把 Divider 改回 LiquidDock 样式的问题。

## 4. OriginalState snapshot

第一次修改某个 divider View 前，保存：

- `LayoutParams.width`；
- `LayoutParams.height`；
- `MarginLayoutParams.leftMargin`；
- `topMargin`；
- `rightMargin`；
- `bottomMargin`；
- 原始 background Drawable。

snapshot 按 View 弱引用持有，不延长 Launcher View 生命周期。

### Drawable alias 防护

不能只保存：

```java
Drawable original = view.getBackground();
```

因为后续 `setBackgroundColor()` 可能原地修改同一个 `ColorDrawable`，导致“原始 background 引用”已经被污染。

当前实现优先通过 `Drawable.ConstantState.newDrawable(resources).mutate()` 创建独立 snapshot；无法复制时才退化为原引用。

这保证常见 `ColorDrawable` 能恢复真正的 vendor 原色。

## 5. Disable / re-enable

关闭 Divider 后会删除该 View 的 snapshot ownership。

以后重新开启时，第一次 mutation 会重新捕获**当时**的 vendor layout/background，而不是永远恢复到进程启动时的旧状态。

这很重要，因为 HyperOS 可能在主题、Workstation 切换或重新 bind 时更换 Divider View 或 background。

## 6. 与 Workstation 的关系

Divider 自身视觉开关可以 live disable/restore，但 Workstation composite customization 仍是 restart-bound。

不要把“Divider 可即时恢复”理解为整个工作台 Dock 都支持完整热切换。工作台同时涉及：

- Dock width；
- icon offset；
- Grid / All Apps offset；
- producer lifecycle；
- Recents；
- normal-layout backup/restore。

## 7. 回归要求

修改 `DockDividerHook` 时至少验证：

- 初次 bind 后 geometry 正确；
- parent height 为 0 时会延迟而不是使用错误 fallback；
- disable 精确恢复 width/height/margins/background；
- pending callback 在 disable 后不能重新修改 View；
- background snapshot 不受 `setBackgroundColor()` alias 污染；
- re-enable 会重新捕获新的 vendor state；
- 普通模式不受影响。

CI 基线：

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```
