# Widget vendor background restore: ownership audit (2026-10-11)

## Report

On a page containing many widgets, toggling LiquidDock widget glass off may leave the original vendor backdrop invisible, especially MAML/advanced-material widgets. Two attached Perfetto traces (`多组件玻璃关` and `多组件玻璃开`) separately show Widget geometry scan/draw load but cannot reveal the MAML variable's hidden runtime state; visual restore needs on-device verification.

## Source-verified defect

`LauncherGlassVendorMaterialSuppressor.claimWidgetMaterial()` changes a MAML host's `enable_background_blur` to `1.0`, but `releaseWidgetMaterial()` previously wrote `0.0` regardless of the value originally provided by Launcher. It also calls opaque vendor blur setters without a symmetric state snapshot. Thus a hard-coded zero is not a restoration.

`MamlView.initMamlview` may replace `mRoot` asynchronously, and `LauncherWidgetComponentSelectionExecutor` / `LauncherMamlBackgroundRuleExecutor` hide ScreenElements under separate reversible claims. Restore must use the **same root** that was claimed; other roots must not receive an old snapshot.

## Patch

- Before mutating the vendor material, sample `getVariableNumber("enable_background_blur")` from a **loaded** MAML root. Keep its numeric value in `OwnedValueState<Double>`; repeated claims preserve the first real snapshot.
- When `initMamlview` supplies a new root, refresh that root's own baseline, rather than pretending that it owns the old root's variable.
- On release, restore the actual prior numeric value only if the same root is still installed and the LiquidDock-owned value `1.0` remains applied. Never guess a missing getter's value or restore a saved value into an unrelated root.
- Do not apply loaded-root MAML hide rules until an actual Workspace widget glass node has claimed the host.
- After MAML element `mShow` and vendor variable restoration, request its normal `requestUpdate()` redraw if attached.

There is **no** blind `setMaMlBlur(true)`, hard-coded blur radius or blanket native blur-mode rewrite: undocumented vendor blur setters still require precise getter/ownership evidence. The patch improves the reproducible variable handoff but cannot guarantee full native blur state reconstruction on every advanced material.

## Device acceptance

1. Reproduce on the same MAML advanced-material widget without root restarts: glass on → off → on → off. Check provider background and content after each transition.
2. Repeat immediately after MAML root reload, widget color/theme refresh, opening an app and returning HOME, and switching between pages.
3. Repeat a standard RemoteViews widget with tagged `android.R.id.widget_frame` and a manual hide/whitening rule, checking original Drawable identity and element visibility.
4. Check that disabling glass while vendor changes the variable concurrently does not overwrite newer vendor state.
5. Verify that MAML/clock native fallback background is **not** visible while glass is on, and reappears correctly while glass is off. Confirm no additional UI/RenderThread jank.
6. Capture launcher logs for `[DC][StaticGlassHook]`, `[DC][GlassRuntime]`, `[MamlWidgetBg]` if restoration still fails; retrieve `enable_background_blur` before/after through device-side reflection to identify remaining unowned native blur state.

This is a **source-evidenced restoration fix**, not a real-device verified resolution. Leave PR draft until a repeatable MAML device test passes. The performance instrumentation remains separate in PR #340; do not merge the issues or reduce background quality to hide either bug.

## Follow-up: App → HOME still loses the native material (2026-10-11)

**Device report invalidated the previous acceptance assumption:** PR #341's first MAML-variable-only fix did **not** restore the original widget backdrop after returning from an app. The PR remains draft, unmerged.

Source inspection identified an additional destructive native-material path. `MiBlurBridge.clearContentBlur(host)` called on every widget glass claim invokes `clearPassWindowBlur()`, which forces view blur mode, background blur mode, background blur radius and background blend list to zero/empty. The same claim path used several vendor boolean/radius setters without storing original state. Releasing the widget later restored none of this state; restoring MAML `enable_background_blur` alone was insufficient.

The revised widget-only claim preserves native compositor blur configuration. It reads the original `getPassWindowBlurEnabled` flag when supported, gates this output off **without clearing native modes/radius/blend**, and on release restores the original enabled state only if the LiquidDock-owned disabled flag is still applied. Repeated claims keep the original snapshot. Unsupported native getters fail open for the vendor state instead of overwriting values LiquidDock cannot reverse. Other consumers of `MiBlurBridge.clearContentBlur()` are unaffected.

**Outstanding proof:** source evidence supports the loss-of-state mechanism but **does not establish that it is the only cause**. Validate (a) MAML/advanced material on/off and App → HOME, (b) original native material quality and radius when glass is off, (c) no stock backdrop through glass when glass is on, (d) RemoteViews provider updates, (e) cold Launcher restart before A/B because the prior APK already cleared native material parameters irreversibly in the live process. If A/B still fails, collect Launcher runtime MAML variable / pass gate before and after `onResume` and `updateColor`, and look for `LauncherWidgetTransitionCoordinator` fresh-scene gating rather than broadening speculative state restoration.
