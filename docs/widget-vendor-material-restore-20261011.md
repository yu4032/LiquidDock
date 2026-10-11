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
