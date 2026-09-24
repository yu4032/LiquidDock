# LiquidDock Architecture

本文档描述当前 `main` / **v2.4.1** 的生产架构。历史 1.x ScreenCapture 方案、早期 307 实验设计和 `docs/superpowers/*` 中的阶段性计划不属于当前 runtime contract。

当前主要边界：

```text
Launcher:       HyperOS 3.0.307+ / com.miui.home release-4.50.x.x
SystemUI:       HOME / keyguard timing authority only
SecurityCenter: com.miui.securitycenter:ui, semantic capability gate
Hook API:       libxposed API 101
Renderer:       MiuiX PassBlur + OES/GLES + Prismal
Build:          minSdk 33 / target+compileSdk 37 / JDK 17 / R8 debug+release
```

---

## 1. Process composition

`ModuleMain` is the process-level composition root.

### `com.miui.home`

Launcher is the primary runtime. Startup performs migrations, reads the immutable `LiquidDockConfig` snapshot, initializes runtime visual state, installs `MainHook`, then installs the current Launcher-specific feature hooks.

Important independently composed modules include:

- `Launcher450IconSizeHook`;
- `MiuixLauncherDragOverlayHook`;
- `MiuixFolderGlassHook`;
- `MiuixShortcutMenuGlassHook`;
- `MiuixLauncherStaticGlassHook`;
- `DockIconAnimationGlassHook`;
- `LauncherGlassRecentsHook`;
- `LauncherGlassHomePresentationHook`;
- `DockGlassDropRefreshHook`;
- `RecentsBackgroundBlurHook`;
- Home-grid profile/orientation/mutation/centering/bounds/drop hooks.

`MainHook` remains a large secondary composition owner for Dock, Workstation and some Grid state. Reducing that ownership is still an active TODO.

### `com.android.systemui`

SystemUI installs only `SystemUiKeyguardGoneSource` and `SystemUiHomeTransitionSource`. It publishes transition timing into the corresponding protocol/runtime boundary. It does not own Launcher glass rendering, PassBlur output, or Workspace geometry.

### `com.miui.securitycenter:ui`

The Xposed scope contains package `com.miui.securitycenter`, but `SecurityCenterProcessPolicy` requires the exact `:ui` process.

Before feature-specific semantic hooks are installed, `ModuleMain` requires:

1. `SecurityCenterPassBlurContinuousAuthority`;
2. `SecurityCenterVendorMaterialState`;
3. optional `SecurityCenterSourceAuthorityHook`;
4. `SecurityCenterGlassHook` bootstrap.

If required PassBlur/material interception cannot be established, Security Center glass fails closed.

---

## 2. Configuration architecture

```text
Compose settings / SharedPreferences
        ↓
LegacyConfigMigration / ConfigMigration
        ↓
ConfigSchema + ConfigCodec + PresetManager
        ↓
libxposed Remote Preferences
        ↓
ConfigReader
        ↓
LiquidDockConfig immutable snapshot
        ↓
runtime states / hooks / sessions / renderers
```

`ConfigSchema` is the single persisted-key registry and owns:

- type;
- UI default;
- runtime fallback;
- export default;
- min/max;
- storage mode;
- export policy.

A historical key may remain in the schema for backup/import compatibility without remaining an active rendering control.

### Runtime state

`GlassRuntimeState` owns live Launcher component gates such as global glass, icon, functional Dock icon, widget, widget dark content and folder types.

`VisualRuntimeState` owns reversible Dock visual gates such as Dock customization, stroke, shadow and Divider.

`SecurityCenterGlassRuntimeState` is separate from Launcher state.

The general disable invariant is:

```text
publish disabled
    ↓
queued callbacks observe disabled
    ↓
teardown/release owned presentation
    ↓
restore saved vendor state where available
```

Structural hook installation is not generally reversible at runtime.

---

## 3. PassBlur domain model

Native source binding is explicit through `PassBlurBindRequest`; the domain is not inferred from an arbitrary View hierarchy.

Current domains:

- `LAUNCHER_WORKSPACE`;
- `DRAG_OVERLAY`;
- `DOCK`;
- `SECURITY_CENTER`.

Each request carries its authoritative host/root, requested scale, domain-derived native scale, and optional exclusions.

For Launcher Workspace, local render quality and native spatial mapping are deliberately separated. Native PassBlur remains at authoritative scale while local FBO size may be reduced after OES normalization.

---

## 4. GPU zero-copy source pipeline

The active Liquid Glass backdrop path is:

```text
HyperOS PassBlur producer
        ↓
caller-owned Surface
        ↓
SurfaceTexture / GL_TEXTURE_EXTERNAL_OES
        ↓
GPU normalization + overscan
        ↓
Prismal renderer
        ↓
output Surface / TextureView-backed consumer
```

`RootPassBlurBackend` centralizes native producer/OES/source-freshness/EGL-source lifecycle used by root-based consumers. Hidden ViewRoot/SurfaceControl interaction is isolated at the bridge/bind boundary instead of leaking into feature policy.

Core invariants:

- no active glass fallback to ScreenCapture, PixelCopy or CPU backdrop readback;
- a successful bind/rebind is not evidence of fresh content;
- `SurfaceTexture.updateTexImage()` drain and expensive Prismal/output rendering are separate operations;
- render FPS limiting cannot create BufferQueue backpressure;
- a new scene generation bypasses ordinary render throttling until freshness is satisfied;
- output visibility is not a substitute for source freshness.

The shortcut-menu dark-mode icon classifier does create a tiny temporary Bitmap for drawable color classification. That code is outside the PassBlur/backdrop pipeline and is not a capture backend.

---

## 5. Dock architecture

HyperOS 3.0.307+ uses `Miuix307MaterialPipeline` for the supported HotSeats background implementations:

- `HotSeatsListContentMiuiXBlurBackground`;
- themed `HotSeatsListContentBlurBackground2`.

The live vendor background remains the geometry/lifecycle shell. LiquidDock composes its own optical output while suppressing incompatible vendor compositor blur only where ownership is established.

The pipeline installs:

- vendor blur suppression;
- normal-Dock customization compatibility;
- HotSeats attach recovery;
- Workstation resume producer recovery;
- vendor static-Dock snapshot power tracking;
- wallpaper freshness tracking;
- geometry hooks for supported background classes.

### Static HOME power behavior

HyperOS can switch HotSeats to a static Dock snapshot. LiquidDock mirrors that vendor authority and may disable producer updates while the vendor static snapshot owns idle HOME presentation. This is event-driven; it is not a fixed timer capture loop.

### Dock spacing

Spacing modifies both item offsets and Dock background width. The exact hook signature requires Launcher-loaded AndroidX `RecyclerView` classes. Because LiquidDock also packages AndroidX, R8 must preserve the binary names used across this ClassLoader boundary; see the R8 section below.

### Stroke and shadow

`DockStrokeRenderer` owns foreground stroke. Whole-Dock shadow is a separate owner. Restore is allowed only for state LiquidDock actually saved; unknown vendor parameters are not synthesized.

---

## 6. Launcher root-wide static glass

`MiuixLauncherStaticGlassHook` discovers/maintains static hosts and connects them to one shared Launcher session.

Current primary hosts:

- `ShortcutIcon`;
- `LauncherAppWidgetHostView`;
- `MaMlHostView`;
- folder material paths managed by `MiuixFolderGlassHook`.

Key components:

- `LauncherGlassSession` — Launcher-specific source/output and projection owner;
- `RootPassBlurBackend` — producer/OES/freshness/EGL-source lifecycle;
- `LauncherGlassSessionRegistry` — stable-root registry;
- `LauncherGlassSceneController` — scene visibility/freshness/static presentation state;
- `LauncherGlassStaticNode` — static icon/widget/folder node;
- `LauncherGlassVendorMaterialSuppressor` and component-specific ownership code — reversible vendor-material handoff.

Static nodes share backdrop authority but keep independent node geometry/presentation state.

### Page and resume reconciliation

Workspace page changes and Launcher `onResume()` schedule current-page reconciliation. These operations may refresh geometry and discover hosts, but they do not invent source freshness. Wallpaper, surface and transition authorities remain separate.

---

## 7. App launch / return-home visual ownership

MIUI's floating icon proxy is the presentation authority during app transitions.

LiquidDock observes final geometry/visibility from:

- `FloatingIconView2`;
- `FloatingIconLayer2`.

When the proxy owns the icon, the corresponding static glass node remains hidden. Static ownership is restored only when the vendor proxy lifecycle permits it. This avoids drawing a static glass icon under or over the MIUI morph target.

`SystemUiHomeTransitionRuntime` and `SystemUiKeyguardGoneRuntime` provide timing authority for HOME/keyguard transitions, while `LauncherGlassHomePresentationHook` connects those signals to Launcher presentation policy.

---

## 8. Live DragView architecture

Workspace drag uses a dedicated live overlay, not a frozen screenshot.

Lifecycle:

```text
DragController.createDragView
    -> prewarm live source for normal single drag
ViewGroup.onViewAdded(real DragView in DragContainer)
    -> resolve source kind/style
    -> create live upper overlay
    -> wait for glass output + visual mirror + fresh backdrop
    -> suppress vendor presentation
per-frame geometry sync
ViewGroup.onViewRemoved(real DragView)
    -> restore vendor presentation
    -> release overlay
    -> release static-node drag suppression
```

Important ownership split:

- MIUI DragView remains drag/drop logic and moving-geometry authority;
- `LauncherDragSourceOverlay` is a non-touchable application-panel presentation surface;
- `LauncherDragVisualMirror` calls `dragView.draw(canvas)` directly, with no bitmap copy;
- the live glass source samples the Launcher below the upper overlay;
- static source metadata selects icon/widget/folder optics but does not drive moving geometry.

`LauncherLiveDragSessionBridge` now calls typed `LauncherGlassSession` APIs; it must not reflect project-owned private fields.

---

## 9. Shortcut popup architecture

Shortcut popup glass is intentionally independent from the ordinary static Workspace compositor
and from the root-export `RootPassBlurBackend` path.

`MiuixShortcutMenuGlassHook`:

- reads popup-glass and dark-mode options at process installation;
- calls the vendor `ShortcutMenu.show()` first and resolves the real `mPopupView.getContentView()`;
- attaches `ShortcutPopupHwuiGlassEffect` directly to that content RenderNode;
- leaves popup scale/translation/alpha animation entirely under HyperOS ownership;
- releases the effect only at the real popup detach boundary.

`ShortcutPopupHwuiGlassEffect` uses `RuntimeShader -> RenderEffect` and Xiaomi's
`View.setBackdropRenderEffect(RenderEffect)` bridge. Pass-window backdrop acquisition stays inside
the Launcher ViewRoot/HWUI composition, so there is no extra TextureView/SurfaceFlinger output layer
that can be sampled back into the next PassBlur frame.

### Dark-mode content adapter

`ShortcutMenuDarkModeController` uses Android typed View APIs only:

- text and TextView compound drawables -> white;
- standalone ImageView drawable -> classify once, tint only near-black/neutral line art;
- colorful icons -> untouched;
- result cache -> weak drawable identity;
- dynamic descendants -> re-evaluated from `OnGlobalLayoutListener` without re-scanning cached drawables.

This adapter does not use LiquidDock self-reflection or cross-ClassLoader class-name lookup.

---

## 10. Widget ownership and dark content

RemoteViews can recreate internal widget frames, so `LauncherAppWidgetHostView.updateAppWidget()` re-runs normal binding/ownership reconciliation. MAML also reconciles after relevant lifecycle/color updates.

Widget features are split into three concerns:

1. glass material ownership;
2. dark-content adaptation;
3. background-component hiding.

Component hiding uses discovered, explicit selectors and reversible mutations. It is not a general-purpose script engine.

---

## 11. Folder architecture

Small and large folders have separate runtime gates and independent size/corner styles. Folder open/close, drag suppression and vendor material suppression are coordinated so disabling one folder class releases only that class.

Launcher 4.50 icon scaling has a separate measure-domain path for `FolderIcon1x1` and `FolderIconPreviewContainer1X1`, preventing later preview remeasure from reverting the configured scale.

---

## 12. Freshness model

LiquidDock separates multiple kinds of generation/authority instead of treating every invalidate as new content:

- scene generation;
- wallpaper content generation;
- producer/source endpoint generation;
- material/carrier epoch where required;
- geometry state.

A fresh output is authorized only when the relevant source generation has actually produced and rendered a matching frame.

### Recents

`LauncherGlassRecentsHook` follows the semantic Recents dispatcher. Recents show marks Workspace glass covered. Recents hide performs the required mode-specific producer preparation/recovery before the covered state is released.

### Workstation producer recovery

Workstation can leave the Launcher Java Surface alive while the old PassBlur BufferQueue endpoint is retired. Recovery therefore operates on producer endpoint lifecycle, not only `View.isAttachedToWindow()` or Surface identity.

### Wallpaper

Wallpaper lifecycle owns wallpaper-content freshness. Geometry reconciliation alone cannot advance wallpaper generation.

### Rotation / root replacement

An old endpoint/root cannot authorize presentation for the replacement. The replacement must bind and publish fresh content before reveal.

---

## 13. Grid ownership

Grid profile logic has been split into several production-used policies/hooks, including orientation memory, profile overlay, mutation capture, device-config count, horizontal centering, vertical bounds, drop legality and drag bounds.

`HomeGridHook` still retains significant runtime ownership and is an active refactoring target.

Non-negotiable rule: MIUI remains placement/occupancy authority. LiquidDock does not infer or rewrite occupied matrices by hooking `addOccupied()` / `transformToHVArray()`.

---

## 14. Workstation / Laptop

Workstation remains an experimental composite path because it spans:

- Dock geometry;
- Dock icon offsets and glass radius;
- Workspace grid offset;
- All Apps geometry;
- Divider;
- producer lifetime;
- Recents recovery;
- wallpaper/rotation freshness;
- normal-layout backup/restore.

Individual visual owners may support live release, but the composite structure remains restart-bound until a complete reversible restore path exists.

---

## 15. Security Center architecture

Security Center no longer uses a single exact versionCode or obfuscated member names as the compatibility contract.

### Activation

`SecurityCenterGlassHook` first hooks `DockWindowManagerService.onCreate()` to obtain a real Context, then validates a semantic contract.

`SecurityCenterSemanticContractResolver` requires a unique combination of:

- `TurboLayout` configure signature/relations;
- wrapper-to-Turbo relation;
- terminal-cleanup manager relation;
- assistant-type round-trip discriminator;
- stable semantic getters (`getDockLayout`, `getAppsLayout`, `getBoxView`, `getGameTurboLayout`, `getMainView`, `getVideoBoxViewAdapter`);
- required resource capabilities;
- one structurally valid All Apps motion helper;
- one sidebar AIDL implementation/lifecycle contract.

Missing or ambiguous capability rejects activation. The observed package version is logged for diagnostics, not treated as the compatibility authority.

### Root/session vs material/carrier

`SecurityCenterGlassCoordinator` owns one root-wide session by root identity. Material/carrier identity is tracked separately with a material epoch.

Supported assistant types are:

- Game = 1;
- Video = 3;
- Global Dock = 4;
- All Apps as a carrier attached to the current Global Dock/Turbo root.

### PassBlur continuous authority

Security Center can reuse the same root SurfaceControl while its vendor material pipeline tries to rebind PassBlur output or change update flags/scale. `SecurityCenterPassBlurContinuousAuthority` claims the LiquidDock Surface/scale for an active root and rewrites later conflicting vendor transactions until real unbind/release.

### Vendor material state

`SecurityCenterVendorMaterialState` hooks stable Android View material APIs before sidebar construction. It records vendor intent. While a carrier is LiquidDock-owned, later vendor writes are recorded but suppressed on-screen. Release replays the latest observed vendor state instead of invoking guessed private restore helpers.

The policy is fail closed: if reliable material interception, source binding or semantic validation is unavailable, LiquidDock does not partially claim the sidebar.

---

## 16. R8 and reflection architecture

Debug and release are both optimized by R8.

### Project-owned code

LiquidDock classes must use typed APIs between one another. String reflection against project-owned private fields/methods is a correctness bug because R8 may rename or inline them.

`runtime-reflection.keep` intentionally does **not** keep whole LiquidDock subsystems merely to support self-reflection.

### Vendor/framework code

Reflection is allowed at Android/HyperOS private boundaries where no public/typed build-time API exists. Optional vendor calls use explicit success/failure semantics; invariant-required calls must fail visibly rather than silently returning null.

### Cross-ClassLoader names

A special hazard exists when a type also exists inside LiquidDock but its binary name is passed to the Launcher ClassLoader. R8 can adapt that reflective string to LiquidDock's obfuscated name. Current targeted protection:

```proguard
-keepnames class androidx.recyclerview.widget.RecyclerView
-keepnames class androidx.recyclerview.widget.RecyclerView$State
```

This protects the Dock spacing method signature without retaining the whole AndroidX dependency tree.

`liquiddock.keep` separately keeps the libxposed entry point and the small SystemUI timing protocol island required by framework/Xposed loading.

---

## 17. Testing architecture

Runtime ownership/freshness/animation/recovery behavior should be exercised through production-used typed state/policy objects.

`RuntimeBehaviorTestPolicyContractTest` default-denies new tests that read production source text. Static source inspection is reserved for audited architecture/API/keep-rule contracts. The legacy source-reader debt list is explicit and may only shrink.

CI runs:

```bash
./gradlew testDebugUnitTest assembleDebug --stacktrace
```

and separately audits Security Center/root PassBlur sources against forbidden screenshot-era APIs.
