# Launcher 4.50 widget native material handoff (2026-10-11)

## Status

**PR #345 first implementation was falsified on device**: hooking `setMiSelfBlur` passed CI but did not hide native widget advanced material. This implementation has been removed, not retained as dead code. This revised PR is unverified on device until the user reports otherwise.

## Source evidence, not API-name guessing

The decompiled Launcher 4.50.0.1204 source in the user's `yu4032/hyperos-analysis` repository identifies the actual widget native blur owner:

- `query/os3-launcher-pad-4.50.0.1204/sources/com/miui/home/launcher/common/BlurUtilities.java`
  - `setWidgetOrMaMlBlur(View)` calls `setViewBlur(view, 1)`, `setWidgetBackgroundBlendColors(view)`, and `setBlurRadius(view, R.dimen.shortcut_menu_layer_blur_radius)`.
  - `setViewBlur(view, int)` invokes **`View.setMiViewBlurMode(int)`**, not `View.setMiSelfBlur`.
  - `setWidgetBlurIfSupported(View)` checks `isWidgetBlurSupported()` and `supportWidgetBackgroundBlur(view)` before calling `setWidgetOrMaMlBlur`.
  - `setMaMlBlurIfSupported(MamlView, boolean)` sets the same native mode and writes `enable_background_blur=1.0` only when eligibility permits.
- `query/os3-launcher-pad-4.50.0.1204/sources/com/miui/home/launcher/LauncherAppWidgetHostView.java`
  - `onAttachedToWindow()` calls `BlurUtilities.setWidgetBlurIfSupported(this)`.
  - `onDetachedFromWindow()` calls `BlurUtilities.clearWidgetBlurIfSupported(this)`.
  - `updateAppWidget()` calls `setBlurIfNeed(child)`, which clears the tagged RemoteViews fallback background when native widget blur is supported. This drawable is independently owned/restored by #341.

## Revised isolated handoff

- Install the native `View.setMiViewBlurMode(int)` interceptor **before** widget host binding; only already-claimed Workspace Widget hosts are affected. Other Views pass through verbatim.
- Claim only when both native Launcher predicates are true: `BlurUtilities.isWidgetBlurSupported()` and `BlurUtilities.supportWidgetBackgroundBlur(host)`.
- While glass is active, set the host's native view-blur **mode to 0**. If Launcher reissues a mode setting, preserve its latest requested mode and apply 0 while owned. **Do not clear native background blur mode, radius or blend colors.**
- On release, first cease interception. If Launcher reissued a setter while owned, apply its latest requested mode; otherwise, call the proper **Launcher** setup entry (`setWidgetBlurIfSupported` for RemoteViews, `setMaMlBlurIfSupported` for MAML). Do this **before** restoring #341's MAML variable and PassWindow gate, since vendor MAML setup writes the background variable.
- Unavailable methods and ineligible widgets fail open without modifying the native material. Native blur that does not use this verified path remains an explicit limitation.
- No blanket alpha/view visibility, no bitmap/screen capture, no frame-by-frame hook, no changes to Dock/folder material or performance PR #340.

## Device validation

Install the revised PR #345 CI artifact, restart `com.miui.home` once, then check:
1. Widget glass **ON**: the native blur plate is hidden, with LiquidDock glass and widget foreground contents still visible.
2. Widget glass **OFF**: original native advanced material returns correctly.
3. App → HOME with widget glass OFF and ON, then repeated toggles. The original background must **never disappear** after returning from an app.
4. Repeat for a MIUI MAML clock/weather widget and a standard RemoteViews widget, including a provider update/root reload.
5. If still visible, capture `[DC][WidgetViewBlur]`, `[DC][WidgetVendor]`, and Launcher `BlurUtilities` / `Launcher.Widget` logs. Distinguish `claim muted=true`, `release vendor-rebind=true`, and absence of claims (widget eligibility not met).

This patch has no on-device success claim until the user verifies both ON and OFF behavior.
