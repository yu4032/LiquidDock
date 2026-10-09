# Live configuration capability audit (2026-10-09)

Scope: `feat/live-glass-config`, API 101 Remote Preferences. A change being persisted or included in `LiveGlassConfigState.isLiveKey()` does **not** establish that an installed Hook, scene geometry, or an already-open material instance will actually react. Distinguish **live on an installed owner**, **applies at next UI event/creation**, and **requires scope restart**.

## Inventory

- `ConfigSchema.Glass`: **97** declared keys (the earlier 103 count mistakenly included the following six `ConfigSchema.Gboard` keys).
- Of the GUI's **70** directly referenced Glass schema keys, **67** enter the Launcher optics subscription and the two shortcut-popup toggles have their own independent live subscription. `WALLPAPER_FLICKER_FIX` is owned by a separate lazy-installed watcher in the `system` process. A subscribed key alone does not establish full runtime behavioral coverage.
- `ConfigSchema.LauncherHighlight`: 18 keys subscribed.
- `ConfigSchema.Recents`: 2 keys subscribed.
- Five `ConfigSchema.Animation` timing keys subscribed in the Launcher process.

## Verified wiring by static code analysis (not a device performance test)

| UI/config family | Actual runtime path | Live guarantee / limitation |
| --- | --- | --- |
| Prismal optics (blur, IOR, displacement, dome, chromatic dispersion, highlights, tint, shadows, color, normals) | Remote prefs → frame-coalesced `LiveGlassConfigState` → `Miuix307ZeroCopyRenderer` / root `LauncherGlassSessionRegistry` / `ShortcutPopupGlassCoordinator` | Live for already-connected **Launcher/Dock** material owners. Popup reuses its frozen normalized texture for blur updates; does not recapture a visible menu. Other application processes are not covered by this Launcher listener. |
| Workspace capture density / renderer FPS | `LauncherGlassSession.applyLiveGlassConfig` → `RootPassBlurBackend.setQuality`, fresh scene request | Live for Launcher root sessions; 50–100% resolution now affects backdrop only (optical edges native). Already-frozen Shortcut popup retains its capture density/FPS until next opening. |
| Icon/widget/folder glass size and corner radius | `LauncherGlassStaticNode.componentStyle/captureGeometry` | Current node config is replaced; geometry-invalidating `postInvalidateOnAnimation` sent only when size/rounding/enabled state differs. |
| Icon/functional Dock icon/recents capsule/widget and folder glass toggles | `GlassRuntimeState` transition callbacks | Disable/release paths exist; icon policy and observed widget re-enable paths exist. **Not** a blanket promise to resurrect uninstalled Hooks or all previously disposed folder nodes. |
| Shortcut popup optical params and highlight profile | `ShortcutPopupGlassCoordinator.onLiveGlassConfigChanged` → session update | Live on an **already-open** popup. Newly prepared popups also use the latest config even if the installation closure captured old config. |
| Shortcut popup glass enable and dark text | `MiuixShortcutMenuGlassHook.onLivePreferences`, `ShortcutMenuDarkModeController` | Install hooks once while Launcher glass is enabled, gate each event on live booleans. Active dark text restores the original vendor colors on disable. Active glass session is released on disable; **enable while popup already visible** applies on next popup to avoid self-sampling. |
| Wallpaper flicker fix | `WallpaperClientCompositionHook.initialize` in `system` process | Remote listener enables/disables guarded SurfaceControl hook; installation is lazy on first opt-in. A failed installation cannot be repaired by preference change alone. System-process behavior requires device verification. |
| Uninstall-dialog rendering/dark mode/dim controls | Uninstall dialog Hook reads config when a relevant event occurs | Typically **next dialog**, not an immediate update of an already-open dialog. Not in the Launcher material fanout. |
| Recents blur strength and wallpaper dimming | `RecentsBackgroundBlurHook` volatile params | Live on subsequent Recents transitions where installed interceptors run; existing vendor animation is not replayed. |
| Launcher animation durations (workspace visibility, icon reveal, press in/out, shortcut popup fade) | `AnimationRuntimeState.configure` | Updated without reinstalling hooks. Ongoing animations may finish at their original duration; next animation uses new values. |
| Security Center exit animation, Dock resize animation, app settings-page animation | Distinct consumers, not included in Launcher timing subscription | No universal cross-process live guarantee. |
| Dock stroke, shadow and divider, mirrored shortcut, Dock frame-sync | `VisualRuntimeState`, `DockStrokeRenderer`, `DockNativeShadowBridge`, `DockDividerHook` | Boolean transitions and Dock stroke geometry/color/shadow/divider settings now refresh; decimal `_tenths` sidecars are honored. Full Dock size/spacing/blur-radius overrides still depend on separate vendor geometry/Hook paths and are not fully live. |
| Grid, icon size, rotation profile and workstation layout | `HomeGridHook`, `WorkstationDockGeometryHook`, `WorkstationDockCustomizationHook` | Workstation visible Dock width and icon vertical offset now update from live prefs using stored native-width baseline and observed RecyclerView decor insets. Grid topology/rotation memory and other workstation geometry still require dedicated layout validation or scope restart. |
| SystemUI gesture-handle visibility | Independent `GestureHandleRuntimeState` listener | Dedicated live boolean in SystemUI where hook installed, not Launcher material listener. |
| Security Center glass enable | `SecurityCenterGlassRuntimeState`, `ExternalGlassLiveConfigState`, `SecurityCenterGlassSession` | Dedicated live boolean plus active optical replay from a cached source frame through the existing presentation state machine. Capture-quality changes apply at next session; device evidence required. |
| Gboard, Baidu and other third-party glass | `ExternalGlassLiveConfigState`, `GboardFloatingGlassCoordinator`, `MiuiSearchboxGlassHook` | Gboard and MIUI Searchbox process-local listeners update active sessions and their own tint/blur override keys. Frozen captures preserve the retained backdrop, and mode/quality changes use appropriate later capture/session ownership. **Baidu and unsupported packages still have no live observer**. |
| Bulk import/reset | Remote Preferences plus `GlassRuntimeState` / `VisualRuntimeState` / `LiveGlassConfigState` | Null-key bulk callbacks now reconcile selected Launcher visual booleans and optics. Other runtime owners need separate review. |

## Correctness constraints

1. Never call a setting *fully live* only because the preference listener receives its key: Hook-time registration is a separate responsibility.
2. Never restart/rebind the EGL context or SurfaceFlinger PassBlur producer just to change a uniform.
3. Geometry changes must invalidate geometry capture, while optical-only changes should avoid a full Launcher root layout scan.
4. Already-open Shortcut popups keep their background frozen; blur can be recomputed from the retained normalized FBO. Capture scale changes apply to the *next* popup.
5. A full glass teardown can release owners whose Hook was never installed or whose material view has been disposed; re-enable is not universally guaranteed without new UI lifecycle events.
6. API 101 cross-process preference delivery, true zero-frames-late GUI updates and performance remain **device validation** requirements, not conclusions from JVM CI.

## Workspace scale/offset regression guard

`WorkspaceFrameGeometryPolicy` verifies that the fresh PassBlur frame's **logical root dimensions and config rotation** agree with the **UI-owned Launcher root** and the **full-root TextureView EGL output**. `LauncherGlassSession.onFreshFrame` no longer overwrites the UI geometry from an async source callback. Mismatched frames are rejected with `[WorkspaceGeometry] stale frame rejected` logging, followed by bounded recapture. This guards against old-size optics being rendered into a new-size surface (perceived zoom/shift), but does not prove that every compositor-level transform or SurfaceTexture crop mismatch is fixed on device.

## Remaining non-live/structural boundaries

- Grid row/column switches, orientation profile transforms, workspace geometry and widget squeeze are **transactional layout changes**, not safe uniform-only updates. No blind hot replacement of captured `HomeGridInstallConfig`; preserve the existing GUI/widget overflow fence and validated system relayout.
- Ordinary Dock legacy fallback width/height/spacing/blur radius and Dock bottom offset install-time geometry remain outside this live path; do not pretend they can safely rerun the install-time Hook.
- A completely disabled master/Glass feature can prevent original Hook installation. Re-enabling then requires a process scope restart unless a dedicated inert-at-install Hook exists.
- A newly enabled SystemUI menu's one-time Hook is installed from the scope bootstrap; state-driven render and preference notification are separate checks. Package process watchers require API101 device confirmation.
- Uninstall dialog and unsupported third-party materials still use next-dialog/next-session behavior when they cannot safely reuse live-owned render state.

## Suggested validation matrix

- In GUI, adjust `BLUR`, `IOR`, `LENS_REFRACTION`, `OS4_EDGE_WIDTH_PX`, `TINT_ALPHA`, `LauncherHighlight.SPECULAR`, including decimal `_tenths` paths; check Dock, workspace icon, folder and an open Shortcut popup.
- Set Workspace capture density 50/75/100 and frame cap 0/60/165 during movement. Verify native contour edges remain stable, and there is no PassBlur producer rebind.
- Change icon/widget/folder size and corner radius while nodes are visible; ensure root geometry is updated once, with no optical-only root invalidations.
- Disable/enable widget, icon and folder glass; test **both** initially-on and initially-off installs and lifecycle return from folder/Recents.
- Change Recents blur/dim, stroke/shadow/divider settings; test scene entry and reset/import (including preference-clear callbacks).
- Check effects in Gboard/Security Center/SystemUI separately; do not infer their behavior from the Launcher-only listener.

Draft PR only; do not merge until user/device verification.
