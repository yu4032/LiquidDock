# App ↔ HOME Widget glass: cache-versus-live-capture test (2026-10-12)

Status: **diagnostic only**. This PR does not pause a producer, change alpha, throttle frames, or alter shaders. JVM tests cover state-machine semantics; they cannot measure physical GL work.

## What source inspection establishes

- A HOME presentation fence calls `onPresentationStarted()` without invalidating the cached layer or requiring a fresh generation.
- `isLayerVisible()` can remain true while the native HOME scale animation transforms its parent. This does not prove that the cached texture was not refreshed.
- When `onFreshFrame()` is called with a valid source generation, the normal path invokes `prepareBackdrop()`, `renderOutputs(true, true)`, `renderStaticScene()`, and an EGL swap if a static output surface exists.
- Recents can retain cached presentation while a shared Recents capsule consumer keeps the source alive. Folder is a hard cover and has different semantics.
- Setting a TextureView alpha to zero is NOT a producer/render stop condition.

## Hypotheses to separate

H0: App-to-HOME frames are mostly a cached TextureView transformed by Launcher, with a single/new source refresh near the final wallpaper state.

H1: During App-to-HOME scaling, the source continues delivering fresh frames, and each consumed frame triggers full static shading and fullscreen EGL buffer swapping.

H2: Real-time frames occur, but most of the observed jank comes from BufferQueue/HWUI/SF backpressure rather than shader processing.

## Required A/B/C device runs (165 Hz, 3008×1880)

Use identical widgets, brightness, refresh rate, orientation and Launcher, and repeat each route 5-10 times. Capture with the same Perfetto configuration; first settle on HOME, then open an App for 5 seconds, then HOME, then Recents→HOME. Include a clock timestamp or marker for the gesture boundaries.

A. main, glass ON, 7 widgets (baseline).
B. main, glass OFF, same 7 widgets (native widgets/animation control).
C. PR #353 diagnostic APK, glass ON, same 7 widgets (EGL swap / clear / area counters). #353 is NOT merged here; its diagnostics must be captured in a separate build. Do not assume a combined branch.

Optional D. PR #354 lazy output allocation: test separately for VRAM pressure. Do not conflate this with draw/swap savings.
Optional E. Few widgets, glass ON, otherwise the same.

Measure per gesture, not raw whole-trace event totals:
- launcher FrameTimeline expected/actual vsync, jank flags, missed deadlines; render-thread workload;
- `LD.Workspace.SourceFrame`, `Backdrop`, `StaticDraw`, `StaticSwap`, `EglSwap`, `SurfaceClear` where instrumented;
- total producer frame count, glass static output swaps, draw counts, maximum and P50/P90/P99 swap durations;
- identify actual TextureView/HWUI and SF composition ownership (do not interpret one TextureView as an independent SF layer automatically);
- compare GPU busy/frequency and widget count if available.

## Decision rules

- **Cache-only animation confirmed** only if no corresponding fresh source frames and static EGL swaps are observed during the animation window, not just because the cached layer is visible.
- **Live-render load confirmed** if source events AND static draws/swaps overlap the animation and scale with its frames.
- **Swap/backpressure primary suspect** if excessive StaticSwap/EglSwap wall times and FrameTimeline buffer-stuffing coincide while static draw and Java static-node slices are short; this is correlation until GPU fences and composition owners corroborate.
- **Do not infer speedup** from alpha animation or a successful unit test.

## Next experiment, contingent on measurements

If many live swaps during an App-covered interval are confirmed, implement an explicit **Workspace-only output eligibility gate** on a new independent branch. Keep shared Recents and Drag consumers alive, retain the most recent correct glass frame, and require fresh wallpaper/geometry before showing a nonmatching cached texture. Avoid fixed-delay timers and alpha-only “optimizations.”

If swaps occur mainly during native HOME motion, prefer a controlled A/B for output coalescing keyed to wallpaper/geometry generation; do not freeze zoomed wallpaper without correct texture/coordinate synchronization. First prove the source and final state fences.

No ScreenCapture, PixelCopy, Bitmap, resolution reduction, EGL damage, or buffer-preservation trick is introduced here.
