# Live configuration capability audit (2026-10-09)

Scope: `feat/live-glass-config`, API 101 Remote Preferences. A change being persisted or included in `LiveGlassConfigState.isLiveKey()` does **not** establish that an installed Hook, scene geometry, or an already-open material instance will actually react. Distinguish **live on an installed owner**, **applies at next UI event/creation**, and **requires scope restart**.

## Inventory

- `ConfigSchema.Glass`: **97** declared keys (the earlier 103 count mistakenly included the following six `ConfigSchema.Gboard` keys).
- Of the GUI's **70** directly referenced Glass schema keys, **67** are in the Launcher LiveGlass subscription. The other three are `WALLPAPER_FLICKER_FIX`, `SHORTCUT_POPUP_GLASS`, and `SHORTCUT_POPUP_DARK_TEXT`. Inclusion in the 67 only verifies notification routing, not full behavioral support.
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
| Shortcut popup glass enable and dark text | `MiuixShortcutMenuGlassHook.install` captures booleans and conditionally installs early capture/dismiss Hooks | **Not live**; restart Launcher scope for changes to installed-Hook features. |
| Wallpaper flicker fix | Separate installed vendor Hook authority | **Not routed live**; restart relevant scope. |
| Uninstall-dialog rendering/dark mode/dim controls | Uninstall dialog Hook reads config when a relevant event occurs | Typically **next dialog**, not an immediate update of an already-open dialog. Not in the Launcher material fanout. |
| Recents blur strength and wallpaper dimming | `RecentsBackgroundBlurHook` volatile params | Live on subsequent Recents transitions where installed interceptors run; existing vendor animation is not replayed. |
| Launcher animation durations (workspace visibility, icon reveal, press in/out, shortcut popup fade) | `AnimationRuntimeState.configure` | Updated without reinstalling hooks. Ongoing animations may finish at their original duration; next animation uses new values. |
| Security Center exit animation, Dock resize animation, app settings-page animation | Distinct consumers, not included in Launcher timing subscription | No universal cross-process live guarantee. |
| Dock stroke, shadow and divider enable, mirrored shortcut, Dock frame-sync | `VisualRuntimeState` | Boolean runtime transitions handled. Shadow/stroke style updates include selected refresh callbacks; **not all Dock dimensions, color fields, or divider dimensions** are subscribed. |
| Grid, icon size, rotation profile and workstation layout | Grid/layout Hook install, profile transformation and workspace lifecycle | Structural changes are **not** handled by LiveGlass and must follow their own validated relayout/restart paths. Never claim instant safe grid changes. |
| SystemUI gesture-handle visibility | Independent `GestureHandleRuntimeState` listener | Dedicated live boolean in SystemUI where hook installed, not Launcher material listener. |
| Security Center glass enable | Independent `SecurityCenterGlassRuntimeState` listener | Dedicated live boolean; optical settings not proven live in that process. |
| Gboard, Baidu and other third-party glass | Package-specific Hook and config instances | Not covered by Launcher LiveGlass fanout. May require the associated app/scope restart. |
| Bulk import/reset | Remote Preferences plus `GlassRuntimeState` / `VisualRuntimeState` / `LiveGlassConfigState` | Null-key bulk callbacks now reconcile selected Launcher visual booleans and optics. Other runtime owners need separate review. |

## Correctness constraints

1. Never call a setting *fully live* only because the preference listener receives its key: Hook-time registration is a separate responsibility.
2. Never restart/rebind the EGL context or SurfaceFlinger PassBlur producer just to change a uniform.
3. Geometry changes must invalidate geometry capture, while optical-only changes should avoid a full Launcher root layout scan.
4. Already-open Shortcut popups keep their background frozen; blur can be recomputed from the retained normalized FBO. Capture scale changes apply to the *next* popup.
5. A full glass teardown can release owners whose Hook was never installed or whose material view has been disposed; re-enable is not universally guaranteed without new UI lifecycle events.
6. API 101 cross-process preference delivery, true zero-frames-late GUI updates and performance remain **device validation** requirements, not conclusions from JVM CI.

## Suggested validation matrix

- In GUI, adjust `BLUR`, `IOR`, `LENS_REFRACTION`, `OS4_EDGE_WIDTH_PX`, `TINT_ALPHA`, `LauncherHighlight.SPECULAR`, including decimal `_tenths` paths; check Dock, workspace icon, folder and an open Shortcut popup.
- Set Workspace capture density 50/75/100 and frame cap 0/60/165 during movement. Verify native contour edges remain stable, and there is no PassBlur producer rebind.
- Change icon/widget/folder size and corner radius while nodes are visible; ensure root geometry is updated once, with no optical-only root invalidations.
- Disable/enable widget, icon and folder glass; test **both** initially-on and initially-off installs and lifecycle return from folder/Recents.
- Change Recents blur/dim, stroke/shadow/divider settings; test scene entry and reset/import (including preference-clear callbacks).
- Check effects in Gboard/Security Center/SystemUI separately; do not infer their behavior from the Launcher-only listener.

Draft PR only; do not merge until user/device verification.
