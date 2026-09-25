# LiquidDock Hook / Listener Map

本文档记录当前 `main` / **v2.5.0** 的主要 Hook、listener、reflection 和 runtime ownership 边界。

当前 Xposed scope：

```text
com.miui.home
com.android.systemui
com.miui.securitycenter
com.google.android.inputmethod.latin
com.android.quicksearchbox
```

## 1. ModuleMain process routing

`ModuleMain.onPackageReady()` 按 package/process 分流：

| Package / process | 当前职责 |
| --- | --- |
| `com.miui.home` | Launcher / Dock / Grid / Workstation / Recents / Launcher glass |
| `com.android.systemui` | HOME/keyguard timing + optional app-caption menu glass |
| `com.miui.securitycenter:ui` | Security Center sidebar glass |
| `com.google.android.inputmethod.latin` | Gboard floating/toolbar glass |
| `com.android.quicksearchbox` | MIUI Search main-background glass |

其他同包非目标进程不应误装 feature hooks。

---

## 2. Launcher bootstrap

Launcher package ready 后依次完成：

- legacy/current config migration；
- `LiquidDockConfig` snapshot；
- animation/runtime state init；
- Dock mirror shortcut；
- native Dock shadow bridge；
- Launcher 4.50 icon-size hook；
- Dock functional-icon registry；
- `MainHook`；
- Grid split hooks；
- drag/folder/shortcut/dialog/static glass；
- Dock icon animation；
- Recents / HOME / wallpaper integration。

判断 Launcher 当前安装图时，应同时看 `ModuleMain` 与 `MainHook`，不能只看其中一个。

---

## 3. MainHook

`MainHook.install()` 当前是 composition root，安装顺序由 `LauncherInstallSequence` 约束。

主要阶段：

1. `WorkstationModeHook`；
2. config load；
3. Dock/Workstation foundation；
4. Grid；
5. Dock shadow ownership；
6. supported glass owner；
7. fallback Dock customization（仅 glass owner 未接管时）。

`MainHook` 不再保存主要 Workstation mutable state，仅保留短期 compatibility facade 和进程日志。

---

## 4. Dock hooks

当前 Dock 相关入口主要包括：

- `DockMirrorShortcutHook`；
- `DockNativeShadowBridge`；
- `DockStrokeRenderer`；
- `WorkstationDockCustomizationHook`；
- `WorkstationDockGeometryHook`；
- `DockResizeAnimationHook`；
- `DockDividerHook`；
- `DockShadowOwnership`；
- `Miuix307MaterialPipeline`；
- `DockBottomGeometryHook`；
- `DockIconAnimationGlassHook`；
- `DockGlassDropRefreshHook`。

### Dock spacing

支持路径会 Hook Launcher 自己加载的 RecyclerView layout/decoration 签名。这里存在跨 ClassLoader 类名约束，相关 keep rule 不能随意删除。

### Vendor material ownership

只有在 LiquidDock 已经建立有效 replacement owner 后，才允许压制 vendor visual。release 时恢复的是实际 snapshot/observed state，而不是猜测默认值。

---

## 5. Home Grid hooks

当前 Grid 已拆为多个明确 owner：

- `HomeGridProfileOverlayHook`；
- `HomeGridOrientationMemoryHook`；
- `HomeGridMutationCaptureHook`；
- `HomeGridDeviceConfigCountHook`；
- `HomeGridHorizontalCenteringHook`；
- `HomeGridVerticalBoundsHook`；
- `HomeGridCellGeometryHook`；
- `HomeGridFolderAlignmentHook`；
- `HomeGridPageIndicatorHook`；
- `HomeGridRotationRefreshHook`；
- `WorkspaceDropRuleHook`；
- `HomeGridDragBoundsHook`。

`HomeGridHook` 主要负责组合和少量 profile transform。

禁止把 MIUI occupancy matrix 替换成 LiquidDock 自己维护的 matrix。

---

## 6. Launcher 4.50 icon-size hook

`Launcher450IconSizeHook` 根据实际 measure/layout domain 调整目标图标尺寸。

作用域与普通 All Apps/Search 分离，避免修改共享 GridConfig 后把不相关页面一起缩放。

`Launcher450DockFunctionalIconRegistry` 同时为“仅 Dock 功能图标玻璃”提供稳定分类。

---

## 7. Static Workspace glass

`MiuixLauncherStaticGlassHook` 发现静态 Launcher hosts，并把它们注册到共享 scene/session。

常见 host：

- ShortcutIcon；
- LauncherAppWidgetHostView；
- MAML widget host；
- folder material host。

相关 listener/hook 负责：

- attach/detach；
- layout/geometry；
- vendor material reconciliation；
- page change；
- app-transition visibility；
- widget content update。

Static node 的 redraw 不是 backdrop freshness authority。

---

## 8. Folder glass

`MiuixFolderGlassHook` 负责小/大文件夹 material lifecycle。

Folder open/close、drag、press、preview/content geometry 与 vendor background suppression 分离管理。

关闭某一 folder type 时只释放该类型 visual ownership。

---

## 9. Live drag hooks

`MiuixLauncherDragOverlayHook` 的重要入口包括：

- drag source prewarm；
- real DragView add/remove；
- per-frame geometry sync；
- visual mirror；
- overlay attach/detach。

真实 DragView 保持 drag/drop authority。

禁止恢复 frozen drag-start screenshot 方案。

---

## 10. ShortcutMenu hooks

`MiuixShortcutMenuGlassHook` 当前覆盖三类 pre-show 入口：

- Workspace touch；
- `DockContainerView.dispatchTouchEventFromHome()`；
- `DockContainerView.dispatchTouchEvent()`。

后两者分别覆盖桌面转发 Dock 与非桌面直接拉出的 Dock。

长按成立后通过稳定菜单 lifecycle rendezvous 到 `ShortcutPopupGlassCoordinator`。

关键行为：

- early source 可跨系统 drag 的 `ACTION_CANCEL` 保留；
- Dock owner 不要求严格同一个 View instance；
- popup 已接受后不重复替换有效 source；
- dismiss 先快速 fade，再等真实 detach release。

`ShortcutMenuDarkModeController` 负责文字和图标视觉适配，不改变菜单业务行为。

---

## 11. Launcher dialogs

`LauncherUninstallDialogGlassHook` 以：

```text
com.miui.home.launcher.uninstall.BaseUninstallDialog
```

的构造生命周期为主入口，并限制到：

- DeleteDialog；
- RemoveDialog；
- SecondConfirmDialog。

`UninstallController.showDialog()` 只用于必要的 preloaded DeleteDialog cache invalidation，不是 glass ownership 主入口。

### Native night bridge

`LauncherDialogNativeNightBridge` Hook MIUIX AlertController 的构造器。

当前发现策略：

- 枚举目标进程 AlertController constructors；
- 匹配 `Context + Dialog + Window` 参数语义；
- **不再通过 `androidx.appcompat.app.AppCompatDialog` class-string 查找目标类型**。

这是 R8-safe contract。不要退回类名字符串方案。

### Dialog hierarchy

`LauncherDialogGlassCoordinator` 等待真实 Dialog hierarchy/layout 完成后绑定，并使用实际 dialog parent panel / dim view 管理 replacement 与 restore。

---

## 12. Recents hooks

`LauncherGlassRecentsHook` 负责：

- Recents show；
- Recents hide；
- Workspace covered authority；
- return-to-HOME recovery；
- wallpaper-settle authority；
- Workstation producer recovery；
- Recents capsule install。

`LauncherRecentsCapsuleGlassHook` 通过 `RecentsDecorations` 生命周期寻找两个稳定资源：

- `recent_clear_all_task_container_for_pad`；
- `world_container`。

`RecentsBackgroundBlurHook` 独立控制 Recents 背景壁纸模糊。

---

## 13. Wallpaper hooks

`LauncherWallpaperFreshnessHook` 绑定 Launcher 自己的 wallpaper update transaction。

不要用：

- framework wallpaper broadcast；
- 普通 View redraw；
- 固定时间等待；

替代当前已验证的 Launcher wallpaper-content lifecycle。

Wallpaper change 和 Recents return settle 是不同 authority。

---

## 14. HOME / keyguard timing

SystemUI：

- `SystemUiKeyguardGoneSource`；
- `SystemUiHomeTransitionSource`。

Launcher：

- `SystemUiKeyguardGoneRuntime`；
- `SystemUiHomeTransitionRuntime`；
- `LauncherGlassHomePresentationHook`。

SystemUI source 只发布时序，不直接管理 Launcher Workspace session。

---

## 15. SystemUI app-caption menu

可选路径：

- `SystemUiHandleMenuSurfaceAnimationAuthority`；
- `SystemUiHandleMenuGlassHook`；
- `SystemUiHandleMenuPrismalSession`；
- `SystemUiHandleMenuGlassOutputView`。

当前 Hook 两类入口：

1. Xiaomi WMShell caption menu window；
2. AOSP/WMShell `desktop_mode_window_decor_handle_menu` layout。

它保持原生按钮/交互和 Surface 动画，只接管支持的背景 presentation。

callback 异常必须被隔离，不能传播到 SystemUI 主流程。

---

## 16. Security Center

只有 `com.miui.securitycenter:ui` 安装。

主要 bootstrap：

- `SecurityCenterPassBlurContinuousAuthority.install()`；
- `SecurityCenterVendorMaterialState.install()`；
- `SecurityCenterSourceAuthorityHook.install()`；
- `SecurityCenterGlassHook.install()`。

### Semantic resolver

`SecurityCenterSemanticContractResolver` 允许：

- stable class relations；
- method signatures；
- public semantic getters；
- resources；
- unique structural relations。

不允许：

- versionCode whitelist；
- obfuscated member-name table；
- “找到第一个差不多的” fallback。

ambiguity 必须 reject。

---

## 17. Gboard

`ThirdPartyGlassAdapterRegistry` 对 Gboard 安装：

- `GboardPassBlurContinuousAuthority`；
- `GboardFloatingGlassHook`；
- `GboardHandwritingCapsuleGlassHook`。

Floating keyboard 主要从稳定 KeyboardHolder/layout 关系识别。

Handwriting/companion toolbar 通过稳定 widget class hierarchy 和 geometry gate 识别。

`GboardFloatingHandlePolicy` 独立控制底部 handle drag 后是否自动 resize。

---

## 18. MIUI Searchbox

对 `com.android.quicksearchbox` 安装：

- `MiuiSearchboxPassBlurContinuousAuthority`；
- `MiuiSearchboxGlassHook`。

目标限制为 SearchActivity 主背景，不泛化到整个进程任意 blur View。

Activity resume / attach 时会重新请求 freshness。

---

## 19. Reflection policy

### LiquidDock-owned code

禁止为了访问自己的 private member 使用：

```java
Class.forName("com.hellovoid.liquiddock...")
getDeclaredField("...")
getDeclaredMethod("...")
```

应增加 typed/package-private API。

### Vendor code

Vendor/private reflection 可以存在，但必须有明确 semantic contract 和 failure behavior。

### R8 / class-string

特别警惕“模块自己也打包同名类 + 字符串交给 vendor ClassLoader”的组合。

真实历史故障包括：

- RecyclerView signature 名称被 R8 改写；
- AppCompatDialog 字符串被改写成 `v9`。

优先通过参数/继承/资源关系发现目标；只有确实需要保留二进制名时才增加 targeted keepnames。

---

## 20. Listener / callback cleanup

任何 feature-owned listener 都必须有对称 cleanup：

- attach state；
- layout change；
- global layout；
- pre-draw；
- Surface callback；
- native animation listener。

release 顺序应先阻止新 callback，再恢复 vendor presentation，最后释放 output/source。

---

## 21. Verification

最低 CI：

```bash
./gradlew testDebugUnitTest assembleDebug --stacktrace
```

Debug 与 Release 都启用 optimization，因此 debug CI 也是 R8 regression gate。

涉及 vendor 私有生命周期的修改仍需要真机日志验证。
