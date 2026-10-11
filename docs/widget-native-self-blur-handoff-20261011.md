# Widget native self blur handoff (2026-10-11)

## Regression after #341

The first fix in #341 restored native widget backgrounds after glass OFF / App → HOME, and was confirmed on device. After its merge, enabling widget glass no longer consistently hid the vendor **advanced material**. The two observations must be preserved together; the old destructive `MiBlurBridge.clearContentBlur(host)` is **not** an acceptable fix.

## Two distinct compositor channels

- `setPassWindowBlurEnabled`: the reversible pass-window gate already owned by #341. It preserves native blur modes, blend colors and radius.
- `View#setMiSelfBlur(int, ArrayList)`: a separate self/content-blur channel that the old `clearContentBlur` used to clear. #341 intentionally stopped invoking it, allowing this native channel to remain drawn beneath/over LiquidDock's widget glass.

The original code additionally tried opaque vendor methods `setMaMlBlur`, `setViewBlur`, and `setBlurIfNeed`; their state is not snapshotted, so they are **not** blindly re-enabled here.

## Scoped non-destructive experiment

`LauncherWidgetNativeSelfBlurGate` hooks only the native `View#setMiSelfBlur(int, ArrayList)` setter once in the Launcher process, before binding widget hosts.

- Vendor calls outside LiquidDock ownership pass through unchanged; the latest requested self-blur radius and color list are retained under weak view keys.
- On actual Workspace widget glass claim, the exact host self-blur output is set to radius 0 only if a real vendor setter baseline was observed.
- While the glass owns a widget, later vendor setter writes (e.g. during `MaMlHostView.onResume/updateColor`) update the saved vendor intent but submit radius 0 instead.
- On glass release, the latest saved vendor parameters are applied, not guessed. The hook's own calls are distinguished with a thread-local marker.
- Missing native method, missing vendor baseline and setter failure are fail-open: do not corrupt the user's native material for a visual guess. No broad alpha/visibility mutation, no bitmap capture, no `clearContentBlur`, and no changes to folder/dock suppression.
- A native source that does **not** call `setMiSelfBlur` may remain visible: this implementation cannot be claimed universal without device traces.

## Acceptance — device required

1. Install CI APK and restart `com.miui.home` **once** so the observer sees material setters from fresh host initialization.
2. Widget glass ON: check native advanced material is hidden and LiquidDock optics/foreground content remain correct (including MAML and RemoteViews widgets).
3. Widget glass OFF: check original native background / blur radius / tint returns immediately.
4. App → HOME while glass OFF, App → HOME while glass ON, then ON→OFF and OFF→ON multiple times; native background must not vanish or double-stack.
5. Re-run after theme change, widget-provider update, root reload and page rotation. Inspect `[DC][WidgetSelfBlur]` logs for `claim`, `restore`, and `claim without observed native baseline`. A missing baseline means the native material is not being suppressed by this gate.
6. Check CPU/RenderThread frame costs in widget-heavy scenes. The setter hook performs no work per draw/frame, only when vendor changes self blur or a widget is claimed/released.

This change is isolated from PR #340's Perfetto performance work. Keep the new PR draft until an ON/OFF+App→HOME cycle is confirmed.
