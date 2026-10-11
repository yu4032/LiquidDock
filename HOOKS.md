# LiquidDock Hook / Listener Map

本文档记录 LiquidDock 的主要 Hook、listener、reflection 和 runtime ownership 边界（原逐项核验于 2026-10-03）。**2026-10-10 源码基线为 `main` / `versionName=2.6.4`**；此次只修正版本标识，未重新完成全部 Hook 的设备级核验。新增或更改的时序以当前源码、契约测试和真实设备记录为准；见 [TODO.md](TODO.md) 与 [可维护性审计](docs/maintainability-audit-20261010.md)。

当前 Xposed scope：

```text
com.miui.home
com.android.systemui
com.miui.securitycenter
com.google.android.inputmethod.latin
com.android.quicksearchbox
com.android.settings
system
```

## 1. ModuleMain process routing

`ModuleMain.onPackageReady()` 按 package/process 分流：

| Package / process | 当前职责 |
| --- | --- |
| `com.miui.home` | Launcher / Dock / Grid / Workstation / Recents / Launcher glass |
| `com.android.systemui` | keyguard timing + optional app-caption menu glass |
| `com.miui.securitycenter:ui` | Security Center sidebar glass |
| `com.google.android.inputmethod.latin` | Gboard floating/toolbar glass |
| `com.android.quicksearchbox` | MIUI Search main-background glass |
| `com.android.settings` | Opt-in genuine PrismalAGSL switch with GPU-native scene sampling (default off) |
| `system` | 配置启用时安装 `WallpaperClientCompositionHook`，支持可选壁纸 GPU 渲染 |

`com.android.settings` 为 **默认关闭** 的真实 PrismalAGSL 组件替换试验。仅在总开关、Settings 子开关及版本检查通过后，为三种 MIUIX 开关安装 Hook。原生控件继续处理状态、设置项验证、持久化与无障碍；Prismal 组件接管可见界面和触摸，一个 Settings 窗口共享一个 GPU RenderNode 场景采样源。

其他同包非目标进程不应误装 feature hooks。

---

## 2. Launcher bootstrap

Launcher package ready 后依次完成：

- 读取当前 Remote Preferences（不执行旧配置迁移）；
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

自由网格支持横屏 2–10 列、2–6 行，竖屏交换维度。`HomeGridSqueezePlannerHook` 仅绑定匹配的 HOME `land_grid` / `vertical_grid` 控制器，不修改 All Apps 控制器。当前 main 通过接口代理把 1×1 图标交给系统 Pad 规划器，多格项交给通用矩形规划器；后者注册循环与事务保护。

Dock 最近应用过滤入口为 `DockRecentAppBlacklistHook`。进程日志门控由 `ModuleMain` 初始化，Launcher 调试状态同步到 `Api101Bridge`。

当前 Grid 已拆为多个明确 owner：

- `HomeGridCellGeometryHook`；
- `HomeGridFolderAlignmentHook`；
- `HomeGridPageIndicatorHook`；
- `HomeGridRotationRefreshHook`；
- `WorkspaceDropRuleHook`；

`HomeGridHook` 负责自由网格组合与旋转 transform。

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

HOME 与 keyguard 现在使用不同 authority：

- HOME：`LauncherGlassHomePresentationHook` 直接观察 Launcher 4.50 的 `WindowElement + RectFSpringAnim` 原生生命周期；
- HOME barrier 只在真实 spring start 或已运行 spring 的 `runningAnimUpdate` 进入 `CLOSE_TO_HOME(_CENTER)` 后建立；
- spring end / cancel 只记录物理动画状态；当前 HOME owner 进入 `WindowElement.finishTransition(...)` 后，真正的 release 统一等对应 `onFinishCompleted()`，merge 不会提前放行；
- keyguard 仍由 `SystemUiKeyguardGoneSource` → `SystemUiKeyguardGoneRuntime` 提供跨进程时序。

HOME 不再依赖 SystemUI 自行重建的 transition START/FINISH token 生命周期。

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

## 18a. Settings PrismalAGSL component replacement and GPU sampling

**Opt-in, default OFF**, strictly scoped to `com.android.settings` on `OS3.0.310.0.WAOCNXM`.

Failure provenance: the original ComposeView overlay lacked a lifecycle owner; after adding one, Settings crashed because Prismal AGSL assets had not been initialized. A temporary hand-drawn Canvas imitation was stable, but did not match Prismal's visual or gesture behavior.

Current architecture:
- The MIUIX `SlidingButton`, `SlidingSwitch` and `SlidingSwitchCompat` objects stay in their original parents as invisible preference controllers. Removing those Java instances would break Settings' stored Preference references and persistence. Their visual and touch surface is replaced with the **real** `GuiOnTouchPrismalToggle`, not an imitation shader.
- `SystemSettingsPrismalBootstrap` explicitly binds PrismalAGSL shader assets from the LiquidDock APK before attaching the Compose component. R8 keep rules retain its reflective asset manager bridge.
- `SystemSettingsComposeLifecycleBridge` supplies Activity-callback-backed lifecycle/saved state where native Settings has none, without setting owner tags on Settings' original root.
- `SystemSettingsNativeBackdrop` records native Settings drawing commands into **one GPU RenderNode per window** and clips to glass geometry plus optical overscan. It implements `PrismalBackdrop` with per-thumb `positionInWindow` alignment. The injected glass wrapper skips its own children during native recording, preventing feedback. On scroll or movement, the recording is refreshed (25ms throttle). No Bitmap, PixelCopy, ScreenCapture or CPU readback.
- The Prismal callback delegates any requested switch to the original clickable Settings Preference row (or native CompoundButton) so validation and persistent state remain owned by Settings; `setChecked` hooks observe but never substitute native work.
- Transactional attach failures restore stock opacity and remove partial overlays. Listeners, GPU records, and lifecycle bridges are released on window teardown.

The design is **not device-accepted**. Check startup/class loader compatibility, initial GPU sampling, correct glass refraction and moving-thumb coordinates, no recursive capture, per-frame GPU cost, preference veto, click/drag, scroll/rebind, accessibility, Activity recreation and safe restoration. Do not merge until verified.

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
