# LiquidDock Hook / Listener Map

本文档记录当前 `main` / **v2.4.1** 的主要 libxposed Hook、Android listener、vendor reflection 与 runtime ownership 边界,它描述的是当前生产代码。

当前 Xposed scope：

```text
com.miui.home
com.android.systemui
com.miui.securitycenter
```

实际进程策略：Launcher 承载主要功能；SystemUI 只提供 HOME/keyguard 时序；Security Center 仅 `com.miui.securitycenter:ui` 进入对应初始化。

---

## 1. API101 entry point

### `ModuleMain.onModuleLoaded`

- 初始化 `Api101Bridge`；
- 保存当前 process name；
- 记录 framework/API diagnostic。

### `ModuleMain.onPackageReady`

按 package/process 分流：

| 进程 | 安装内容 |
| --- | --- |
| `com.android.systemui` | `SystemUiKeyguardGoneSource`, `SystemUiHomeTransitionSource` |
| `com.miui.securitycenter:ui` | Security Center PassBlur authority、vendor material interception、source authority、semantic glass bootstrap |
| `com.miui.home` | migration、runtime state、`MainHook`、Launcher/Dock/Grid/Glass/Recents/transition hooks |

Security Center 不执行 Launcher migration，也不调用 `MainHook.install()`。

---

## 2. Launcher / Dock bootstrap

`MainHook.install(classLoader)` 仍负责一部分 Dock / Workstation / Grid composition。

主要边界包括：

- Workstation mode detection / width / layout backup；
- `DockStrokeRenderer` native hook；
- Workstation Dock / Divider / Grid-related hooks；
- `HomeGridHook`；
- whole-Dock shadow ownership；
- 307+ `Miuix307MaterialPipeline`；
- 不支持 307 zero-copy pipeline 时 fail closed，不恢复旧 screenshot glass。

`ModuleMain` 另外独立安装更多已拆出的 feature hooks，因此判断当前 composition 时必须同时看 `ModuleMain` 和 `MainHook`，不能只看后者。

---

## 3. HyperOS 307+ Dock material pipeline

`Miuix307MaterialPipeline` 支持：

- `HotSeatsListContentMiuiXBlurBackground`；
- `HotSeatsListContentBlurBackground2`（themed path）。

主要 Hook：

| Authority | Hook / callback | 作用 |
| --- | --- | --- |
| `Launcher` | `setupViews()` | 找到当前 HotSeats/background/workspace，建立 glass binding |
| `HotSeats` | `onAttachedToWindow()` | background 替换/主题切换后的 recovery |
| `HotSeats` | `setMingouStaticDockSnapshotMode(boolean)` | 镜像 vendor static-Dock snapshot power authority |
| fallback | `setMingouStaticDockLiveBlurVisible(boolean)` | snapshot authority 不存在时的 live-blur power fallback |
| `Launcher` | `onResume()` | Workstation fullscreen 返回时 producer recovery |
| `BlurUtilities` | `setBackgroundBlur(View, int, ...)` | LiquidDock owning path 下抑制 vendor post-composition blur |
| supported Dock backgrounds | width/height/radius setters | normal-Dock geometry + glass/stroke/shadow 同步 |

`LauncherWallpaperFreshnessHook` 也由该 pipeline 安装。

### Dock spacing

307 主路径与备用 `MainHook` 路径都 Hook：

- `HotSeatsListContentLayoutManager$OffsetDecoration.getItemOffsets(...)`；
- `HotSeatsListContentLayoutManager.updateBackgroundView(...)`。

精确签名需要 Launcher ClassLoader 解析的 `RecyclerView` / `RecyclerView$State`。这两个名称由 targeted R8 `-keepnames` 保护。

---

## 4. Launcher 4.50 icon-size hooks

`Launcher450IconSizeHook` 用 `ThreadLocal<MeasureDomain>` 把 `GridConfig` getter override 限制在真实测量事务中。

主要 Hook：

| 目标 | 方法 | Domain |
| --- | --- | --- |
| `ShortcutIcon` | `onMeasure()` | Workspace / Dock / folder content / Workstation apps |
| `FolderIcon1x1` | `onMeasure()` | Workspace small folder |
| `FolderIconPreviewContainer1X1` | `onMeasure()` | small-folder preview independent remeasure |
| `ShortcutIcon` / `BaseProgressShortcutIcon` | `scaleDownToFolder(boolean)` | folder transition sizing |
| `GridConfig` | `getIconSize()` | only when a measure domain is active |

Dock 的 `getDockIconWidth()` 保持 vendor authority，不随自定义 icon size 改写。Launcher 原生以固定 Dock slot 承载 `ShortcutIcon`，再通过 `(measuredWidth - iconSize) / 2` 居中图标；这样缩放只改变视觉尺寸，不改变 item 槽中心，也保持 `HotSeatsListContentLayoutManager` 的 `mViewWidths` 与实际 child 测量一致。

共享 `GridConfig` 不被修改，因此普通 All Apps / Search 不会因为全局 mutation 被缩放。

---

## 5. Static Launcher glass

`MiuixLauncherStaticGlassHook` discovers static hosts and binds them to the shared root session.

### Host classes

- `ShortcutIcon`;
- `LauncherAppWidgetHostView`;
- `MaMlHostView`.

### Lifecycle hooks

| Target | Hook | Purpose |
| --- | --- | --- |
| host constructors | constructor hooks | observe/bind new icon/widget hosts |
| `LauncherAppWidgetHostView` | `updateAppWidget(RemoteViews)` | re-claim ownership after RemoteViews rebuild |
| `MaMlHostView` | `onResume()`, `updateColor(int)` | re-run MAML material ownership after async updates |
| `Workspace` | `setCurrentScreenInner(int)` | reconcile selected page after vendor commits page identity |
| `Launcher` | `onResume()` | reconcile current Workspace page/geometry |
| `ShortcutIcon` | `setAnimTargetVisibility(int)` | app-launch proxy ownership end boundary |
| `FloatingIconView2` | `update(...)` | real close-to-home proxy geometry/visibility |
| `FloatingIconLayer2` | `update(...)` | rotation-aware SurfaceControl proxy geometry/visibility |

### Static drag suppression

For material classes exposing `onDragContainerBgAnimAlpha(boolean, boolean)`, a class-scoped hook mirrors vendor drag ownership into the matching `LauncherGlassStaticNode`.

---

## 6. Live drag overlay

`MiuixLauncherDragOverlayHook` keeps MIUI DragView authoritative.

### Hook points

- `DragController.createDragView(...)`: source prewarm only for normal single drag;
- framework `ViewGroup.onViewAdded(View)`: begin when a real `DragView` enters the real DragContainer;
- framework `ViewGroup.onViewRemoved(View)`: end/release when that DragView leaves.

Metadata classification uses vendor View/tag/field structure once at drag begin. The per-frame path does not repeatedly reflect classification metadata.

Actual presentation ownership:

- upper non-touchable app-panel window;
- live `LauncherGlassDragOverlay` output;
- direct-draw `LauncherDragVisualMirror`;
- MIUI DragView remains geometry and drag/drop logic authority.

---

## 7. Folder glass

`MiuixFolderGlassHook` owns small/large folder material integration.

Representative boundaries include:

- `FolderIcon1x1` / larger folder host construction/bind;
- folder open/close lifecycle;
- folder blur/material helper interception;
- vendor folder covered-state dispatch;
- drag/press ownership;
- large-folder draw/material suppression.

Runtime small/large gates are independent; callbacks must re-check the matching component state before reclaiming material.

---

## 8. Shortcut popup

`MiuixShortcutMenuGlassHook` has two independently configured behaviors under global Liquid Glass.

### Popup glass

| Target | Method | Purpose |
| --- | --- | --- |
| `ShortcutMenuLayer` | `setRequestingItemInfo(ItemInfo)` | prewarm/cancel popup source around request ownership |
| `ShortcutMenu` | `show()` | original first, then bind real popup content |
| `ShortcutMenu` | `dismiss(EditStateChangeReason)` | begin fast custom fade, then normal detach cleanup |

`mDecorView` / `mPopupView` are vendor fields. `getContentView()` is called on the actual popup object. The glass session itself uses typed LiquidDock APIs.

### Dark-mode content

`ShortcutMenuDarkModeController` is listener-based, not an Xposed method hook:

- recursively applies white text;
- caches near-black drawable classification;
- listens through `ViewTreeObserver.OnGlobalLayoutListener` for dynamically added menu content;
- removes the listener on detach.

No project-owned reflection is used.

---

## 9. Dock icon animation and HOME presentation

`DockIconAnimationGlassHook` and `MiuixLauncherStaticGlassHook` cooperate with MIUI's floating icon owner so Dock/Workspace static nodes do not overlap launch/return proxies.

`LauncherGlassHomePresentationHook` consumes typed SystemUI protocols and Launcher state to coordinate HOME presentation. `SystemUiKeyguardGoneRuntime` and `SystemUiHomeTransitionRuntime` are timing inputs, not glass producers.

---

## 10. Recents and wallpaper

### `LauncherGlassRecentsHook`

Uses semantic Recents dispatcher boundaries:

- `onRecentViewShow()` -> mark Workspace glass covered;
- `onRecentViewHide()` -> prepare return/recovery, then release covered authority.

Workstation return may roll over a retired PassBlur producer even when the Java Surface still appears usable.

### `LauncherWallpaperFreshnessHook`

Tracks vendor wallpaper lifecycle and advances wallpaper-content authority. It is not a generic redraw hook and must not be replaced by `invalidate()`-based freshness.

### `RecentsBackgroundBlurHook`

Separately scales Launcher Recents background blur. This is not part of PassBlur backdrop selection.

---

## 11. Grid hooks

Current grid composition is split across legacy `HomeGridHook` plus newer production hooks/policies:

- `HomeGridProfileOverlayHook`;
- `HomeGridOrientationMemoryHook`;
- `HomeGridMutationCaptureHook`;
- `HomeGridDeviceConfigCountHook`;
- `HomeGridHorizontalCenteringHook`;
- `HomeGridVerticalBoundsHook`;
- `WorkspaceDropRuleHook`;
- `HomeGridDragBoundsHook`.

The split is incomplete; `HomeGridHook` still owns several runtime concerns.

Grid hooks may change geometry/drop legality but must leave MIUI occupancy/placement authority intact.

---

## 12. Workstation / Divider

Workstation hooks cover Dock width, icon offsets, Workspace offset, All Apps offsets/spacing, Divider, producer recovery and normal-layout restore.

`DockDividerHook` snapshots the original View state before first mutation and restores it on live disable. Parent-height-dependent geometry may defer to a layout/pre-draw boundary rather than inventing a fallback height.

See [DIVIDER.md](DIVIDER.md).

---

## 13. Security Center bootstrap

Security Center uses a capability/semantic resolver, not a fixed obfuscated-member map.

### Package/process gate

```text
package: com.miui.securitycenter
process: com.miui.securitycenter:ui
```

### Stable bootstrap anchor

`DockWindowManagerService.onCreate()` is hooked first so LiquidDock gets the real Context/resources/package metadata before validating feature hooks.

### Semantic contract validation

`SecurityCenterSemanticContractResolver` resolves:

- `TurboLayout` configure signature;
- wrapper-to-Turbo relation;
- manager terminal-cleanup relation;
- assistant-type setter/getter discriminator;
- named semantic getters;
- All Apps motion helper by constructor/method structure;
- sidebar AIDL implementation/lifecycle;
- required resource capabilities.

It rejects ambiguous matches. VersionCode is logged only as diagnostic evidence.

### Current assistant/carrier types

`SecurityCenterGlassCoordinator` supports:

- Game type 1;
- Video type 3;
- Global Dock type 4;
- All Apps carrier attached under the current Turbo root.

### Continuous PassBlur authority

`SecurityCenterPassBlurContinuousAuthority` hooks hidden `SurfaceControl.Transaction` methods:

- `SetPassBlurSurface(SurfaceControl, Surface)`;
- `setUpdateTextureFlag(SurfaceControl, boolean, float)`.

While LiquidDock owns a root, later vendor attempts to steal the output Surface or alter its update contract are rewritten back to the active claim.

### Vendor material state

`SecurityCenterVendorMaterialState` hooks stable `View` material APIs such as background, pass-window blur, Mi blur/blend, bloom and shadow setters. It records vendor writes before/while a carrier is claimed, suppresses conflicting on-screen changes during ownership, then replays the latest recorded state on release.

This avoids depending on guessed private restore helpers.

---

## 14. Runtime states

### Launcher Glass

`GlassRuntimeState` distributes live component changes. Disable order is publish false -> release ownership. Asynchronous callbacks must check current component state again before mutating presentation.

### Dock visual state

`VisualRuntimeState` drives reversible Dock customization/stroke/shadow/Divider behavior.

### Security Center

`SecurityCenterGlassRuntimeState` is process-local and has its own owner/release lifecycle.

---

## 15. Reflection and R8 boundaries

### Allowed

- Android framework private APIs;
- HyperOS Launcher/SystemUI/Security Center private classes or vendor objects;
- semantic structural reflection when the vendor contract requires it.

### Forbidden pattern

Do not access LiquidDock-owned fields/methods by string reflection. R8 is free to rename those members. Use typed package-private APIs.

### Cross-ClassLoader string hazard

Even a vendor-targeted `Class.forName(name, false, launcherClassLoader)` can be broken when `name` also denotes a class bundled by LiquidDock. R8 may adapt the string to the module's obfuscated binary name. Current Dock spacing therefore protects:

```text
androidx.recyclerview.widget.RecyclerView
androidx.recyclerview.widget.RecyclerView$State
```

with targeted `-keepnames`.

---

## 16. Maintenance rules

New hooks should preserve these constraints:

- a concrete vendor/system authority must justify the hook point;
- every visual owner needs an explicit release/restore path;
- stale callbacks re-check live state/generation;
- bind success does not mean fresh content;
- no screenshot fallback for active Liquid Glass;
- do not hide incompatibility behind broad R8 keep rules;
- Security Center contracts should prefer semantic/structural capabilities over obfuscated member names;
- tests for runtime behavior should call production-used typed state/policy objects rather than prove behavior by source-string ordering.
