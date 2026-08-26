# LiquidDock 2.1 Hook 点总览

本文档记录当前 `main` / **v2.1.1** 安装的主要 libxposed Hook、Android listener、反射边界和 GPU 生命周期。

当前注入范围：

```text
com.miui.home
```

旧的 ScreenCapture / SystemUI freeform capture / bitmap readback Hook 不属于当前主线。

## 1. 入口与安装边界

API 101 入口为 `ModuleMain`。Launcher 进程启动时主要完成：

1. legacy preference migration；
2. 读取 `LiquidDockConfig` snapshot；
3. 初始化 `GlassRuntimeState` / `VisualRuntimeState` 等 live state；
4. `MainHook.install(classLoader)`；
5. 安装 Grid / Dock / Workstation / Recents / Liquid Glass 等模块。

完整 master-switch 启停仍属于 restart-bound：运行中关闭可以释放已接管的视觉 ownership，但不能安全撤销所有安装期结构 Hook。

## 2. Dock 与 MiuiX zero-copy glass

### Material owner

主要支持 HyperOS 3.0.307+ 的 Dock background / HotSeats material，包括 MiuiX blur background 路径。

### 主要 Hook

| 目标 | 方法/边界 | 作用 |
|---|---|---|
| `Launcher` | `setupViews()` | 解析 HotSeats / Workspace / Dock background，建立或刷新 glass 绑定 |
| `Launcher` | `onResume()` | HOME/Workstation 恢复与 geometry/session reconcile |
| `HotSeats` | attach / snapshot / live-blur 状态方法 | 同步 Dock producer 与 vendor material 生命周期 |
| Dock background | width / height / radius 更新 | 同步 Dock geometry、stroke、shadow 与 glass scene |
| `BlurUtilities` | `setBackgroundBlur(...)` | 在 live Dock customization/glass ownership 下接管对应 blur 参数 |
| Dock layout manager | item offsets / background view update | 普通 Dock spacing、背景宽度补偿与工作台 icon offset |

### PassBlur 隐藏 API

当前 zero-copy 路径会通过反射访问 HyperOS SurfaceControl / PassBlur 私有接口，把 vendor backdrop 输出接到 LiquidDock 的 producer `Surface`，再由 `SurfaceTexture` / OES 输入 GPU renderer。

典型数据流：

```text
MiuiX PassBlur
  -> Surface
  -> SurfaceTexture / GL_TEXTURE_EXTERNAL_OES
  -> normalization / overscan
  -> Prismal renderer
  -> Dock / Launcher output
```

## 3. Launcher-wide static glass

`MiuixLauncherStaticGlassHook` 负责图标和部分 Widget 的 host 发现与重新绑定。

| 目标 | 方法/边界 | 作用 |
|---|---|---|
| `ShortcutIcon` | constructor / visual lifecycle | 注册 icon host、恢复 static node |
| `LauncherAppWidgetHostView` | constructor | 注册 Widget host |
| 同上 | `updateAppWidget(RemoteViews)` | RemoteViews 更新后重新声明 Widget material ownership |
| `MaMlHostView` | constructor | 注册 MAML Widget |
| 同上 | `onResume()` / `updateColor(int)` | MAML 异步生命周期后重新 reconcile |
| `Workspace` | current-page/layout 变化 | 扫描当前页并恢复静态节点 |
| `Launcher` | `onResume()` | HOME resume 后 reconcile 当前 Workspace |

这些静态对象共享 root-wide `LauncherGlassSession`，不是每个 View 独立维护 producer。

## 4. 文件夹 glass

`MiuixFolderGlassHook` 负责小/大文件夹的 material ownership。

| 目标 | 方法/边界 | 作用 |
|---|---|---|
| `FolderIcon1x1` / `FolderIcon2x2` | constructor / bind | 注册 small/large folder host |
| `BlurUtilities` | folder blur 方法 | live-enabled 时接管 vendor folder blur；disabled 时透传 |
| `FolderStatusServiceImpl` | folder open/close dispatch | 维护 folder covered 状态 |
| `FolderIcon` / `Folder` | open/close 生命周期 | 隐藏/恢复桌面文件夹 glass |
| `FolderIcon` | touch | 传递 interaction/press 信息 |
| 大文件夹 draw 路径 | `drawChild(...)` 等 | 在 glass ownership 下抑制/恢复原生 drawable material |

v2.1.1 起 small/large folder 使用独立 live gate；关闭后只释放对应类型，不影响其它 glass。

## 5. 拖拽与 launch proxy

`MiuixLauncherDragOverlayHook` 管理拖拽期间的移动 glass node。

典型边界：

- DragView 加入容器 -> 创建移动节点；
- DragView 移除 -> 释放节点；
- vendor 原位置 material 的 drag alpha/lifecycle -> 抑制或恢复静态节点；
- callback 执行前再次检查 icon/widget/folder 对应 live state。

Dock / Workspace app launch 的 floating icon proxy geometry 也会用于隐藏原静态 icon glass，并在动画结束后按配置时长恢复。

## 6. Recents scene

`LauncherGlassRecentsHook` 使用 HyperOS semantic Recents dispatcher：

| 目标 | 方法 | 作用 |
|---|---|---|
| `RecentsServiceDispatcher` | `onRecentViewShow()` | 将共享 Workspace static layer 标记为 Recents covered |
| 同上 | `onRecentViewHide()` | 返回 HOME 前执行 Workstation producer recovery，再解除 covered |

### Workstation recovery

工作台可能复用同一个仍 valid 的 Launcher Surface，但旧 PassBlur BufferQueue producer 已停止回调。

当前返回路径：

```text
onRecentViewHide
  -> LauncherGlassSessionRegistry.prepareWorkstationRecentsReturn()
  -> shared producer rollover/rebind
  -> LauncherGlassSceneController.setRecentsCoveredForAll(false)
  -> 等待 fresh OES frame
  -> static layer reveal
```

不会直接强制显示旧 frame。

## 7. Wallpaper freshness

`LauncherWallpaperFreshnessHook` 使用 Launcher wallpaper lifecycle 维护 wallpaper generation。其作用不是简单 invalidate，而是给 shared glass backdrop 一个内容新鲜度权威。

常见边界包括 wallpaper changed、first-frame、draw completion 等 vendor callback。

## 8. Runtime visual ownership

### Glass

`GlassRuntimeState` 监听：

- global glass；
- icon glass；
- widget glass；
- small-folder glass；
- large-folder glass。

true -> false 时先发布 flag，再 dispatch teardown；已排队 callback 因此无法在释放后重新 claim。

### Dock visuals

`VisualRuntimeState` 监听：

- Dock customization；
- Dock stroke；
- Dock shadow；
- stroke-shadow gate；
- Divider；
- Squircle / Fill-Diff renderer refresh。

对应 teardown 包括：

- `MainHook.onRuntimeDockCustomizationDisabled()`；
- `DockStrokeRenderer.onRuntimeStrokeDisabled()`；
- `MainHook.onRuntimeDockShadowDisabled()`；
- `DockStrokeRenderer.refreshInstalledFromCurrentConfig()`；
- `DockDividerHook.onRuntimeDividerDisabled()`。

## 9. Divider

`DockDividerHook` 主要从 Workstation Dock line holder/bind 生命周期取得 divider View。

首次修改前 snapshot：

- width；
- height；
- margins；
- background Drawable 副本。

disable 时：

- 取消 pending pre-draw geometry listener；
- 恢复 layout params；
- 恢复 background；
- `requestLayout()`；
- 释放 snapshot ownership。

详见 [DIVIDER.md](DIVIDER.md)。

## 10. Native Dock stroke / shadow

`DockStrokeRenderer` 使用 foreground Drawable 持有描边，不再使用旧 overlay 作为默认实现。

- stroke disable -> 恢复原 foreground；
- Squircle / Fill-Diff -> 主动 refresh 已安装 renderer；
- whole-Dock shadow 是独立 owner；
- Dock customization 关闭后未来 vendor shadow 调用不再被继续抑制；
- 未保存的 MIUI 原生 shadow 参数不会被构造。

## 11. Workstation / Laptop

工作台状态由 Launcher laptop/workstation vendor state 及对应 controller/hook 组合判断。

当前相关 Hook 覆盖：

- Workstation Dock width；
- icon top/bottom offset；
- Workspace grid offset；
- All Apps offset；
- Divider；
- producer lifecycle；
- Recents recovery；
- 普通布局 backup/restore。

整体仍属于实验性适配，结构配置保持 restart-bound。

## 12. Grid

`HomeGridHook` 仍覆盖：

- cell count；
- orientation-specific geometry；
- Widget frame adaptation；
- page indicator；
- folder alignment；
- rotation / refresh；
- lazy/off-screen page preparation。

`WorkspaceDropRuleHook` 只扩展 custom-grid 的合法坐标判定，不接管 MIUI occupancy matrix / placement。

## 13. 多任务背景模糊

`RecentsBackgroundBlurHook` 继续针对 Launcher 自身 Recents blur 方法缩放背景模糊强度。它与 Liquid Glass backdrop producer 是独立功能，不参与 zero-copy capture/source selection。

## 14. 维护原则

新增 Hook 时优先保持：

- vendor state ownership 明确；
- runtime callback 可撤销；
- stale async callback 必须重新检查 live state；
- producer/content freshness 不依赖普通 redraw；
- 不恢复 1.x ScreenCapture 或 bitmap pipeline。