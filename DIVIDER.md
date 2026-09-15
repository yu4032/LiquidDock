# LiquidDock Workstation Divider

本文档描述当前 `main` / **v2.4.1** 的 `DockDividerHook` 行为。

Divider 是 Workstation/Laptop 组合适配中的一个**独立、可逆视觉 owner**。它可以 live disable/restore，但工作台整体仍包含 Dock、Grid、All Apps、Recents、PassBlur 和 normal-layout restore，因此不能把 Divider 的热切换能力理解为完整 Workstation structure 可热卸载。

Divider 与 Workspace PassBlur 的 local render resolution / FPS gate 无关：质量控制不会改变 Divider View geometry、snapshot 或 restore 语义。

## 1. 参数与存储语义

| 参数 | Persisted range | Runtime meaning |
| --- | ---: | --- |
| Divider | on/off | live visual ownership gate |
| width | 0 ~ 160 | 历史 raw `0.1 dp`，runtime 除以 10 |
| height scale | 0 ~ 100 | parent height 百分比 |
| Y offset | −80 ~ 80 | 历史 raw `0.1 dp`，runtime 除以 10 |
| R / G / B | 0 ~ 255 | background color |
| alpha | 0 ~ 255 | background alpha |

Divider width / Y offset 是历史 direct integer contract，不等同于普通 `ConfigKey.StorageMode.DP_TENTHS` sidecar。不要为了统一存储格式擅自迁移它们。

`LiquidDockConfig.Divider` 会在 runtime snapshot 中把历史值归一化成真实 dp / percent / color。

## 2. Hook / geometry boundary

`DockDividerHook` 从 Workstation Dock line holder/bind 生命周期取得真实 divider View，在 parent geometry 有效后应用：

- width；
- height；
- Y/top offset；
- RGBA background。

如果 parent height 尚未有效，必须等待后续 layout/pre-draw；不能拿 divider 自身高度伪造 parent geometry。

## 3. Ownership lifecycle

第一次修改一个 divider View 前，LiquidDock capture `OriginalState`。runtime disable 的顺序保持：

```text
VisualRuntimeState publishes dividerEnabled=false
        ↓
pending callbacks observe disabled
        ↓
remove pending pre-draw listener
        ↓
restore layout + background
        ↓
requestLayout()
        ↓
release ownership snapshot
```

这样 queued callback 不能在 teardown 后再次把 View 改回自定义状态。

## 4. OriginalState snapshot

当前 snapshot 包含：

- `LayoutParams.width`；
- `LayoutParams.height`；
- left/top/right/bottom margins；
- original background Drawable。

snapshot 按 View 弱引用持有，不延长 Launcher View lifetime。

### Drawable alias protection

只保存：

```java
Drawable original = view.getBackground();
```

不够安全，因为后续 background mutation 可能原地改变同一 drawable 实例。

当前实现优先通过 `Drawable.ConstantState.newDrawable(resources).mutate()` 生成独立 snapshot；无法复制时才退化为原引用。因此常见 `ColorDrawable` 可以恢复真实 vendor 颜色，而不是恢复被 LiquidDock 自己污染后的对象。

## 5. Disable / re-enable

关闭后会释放该 View 的 snapshot。以后再次启用时，第一次 mutation 会重新捕获**当时**的 vendor state，而不是永远回到进程启动时的旧值。

这对主题变化、Workstation 重建和 holder rebind 很重要。

## 6. 与其他 Workstation owner 的关系

Divider 不拥有：

- Workstation Dock width；
- Dock icon top/bottom offset；
- Dock icon glass radius；
- Workspace grid offset；
- All Apps offsets/spacing；
- PassBlur producer；
- Recents recovery；
- normal-layout backup/restore。

这些由各自 Hook/policy/session 管理。修改 Divider 时不要通过全局 Workstation restore 顺便重写其它 owner。

## 7. Regression checklist

修改 `DockDividerHook` 时至少验证：

- 初次 bind 后 geometry 正确；
- parent height 为 0 时 defer，而不是使用错误 fallback；
- disable 精确恢复 width/height/margins/background；
- pending callback 在 disable 后不能重新 mutation；
- Drawable snapshot 不受 alias 污染；
- re-enable 重新 capture 新 vendor state；
- 普通模式不受影响；
- Workstation 进出与 holder replacement 不保留 stale snapshot。

CI 基线：

```bash
./gradlew testDebugUnitTest assembleDebug --stacktrace
```

真机 Workstation lifecycle 仍需要设备验证；CI 只验证代码/contract，不证明 vendor runtime 时序。
