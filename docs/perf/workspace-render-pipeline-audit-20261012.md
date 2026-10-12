# Workspace rendering pipeline audit — 2026-10-12

**Branch:** `test/workspace-app-home-capture-gates-20261012` (based on `main@8418994c`). **Status:** source audit + Perfetto instrumentation; Android-device GPU timing not available in CI. **No visual/runtime throttling change.**

## Map of the actual pipeline

1. **Vendor/Workspace ownership:** `MiuixLauncherStaticGlassHook` hooks Launcher icon/widget attach, page reconcile, scroll, widget RemoteViews. Existing nodes may be refreshed on HOME resume.
2. **Native HOME fences:** `LauncherGlassHomePresentationHook` observes `WindowElement` / `RectFSpringAnim`, arms `setHomeTransitionPendingForAll(true)` and releases on accepted terminal. `LauncherWidgetTransitionCoordinator` handles widget launch suppression, fresh return, and fade-in.
3. **Per-View glass nodes:** `LauncherGlassStaticNode` owns a material View, optical style, suppression, interaction/visibility animators, geometry capture, and node lifetime. `LauncherGlassVisibility` walks ancestor visibility/alpha, `LauncherGlassGeometry` calculates root-space rectangles. No Bitmap / PixelCopy / ScreenCapture.
4. **Root ownership:** `LauncherGlassSessionRegistry` shares a single `LauncherGlassSession` and one `LauncherGlassSceneController` per stable ViewRoot. Recents capsules may consume the **same** root producer.
5. **Presentation:** `LauncherGlassStaticLayer` is one root-wide transparent TextureView inserted as child 0 of the stable root. Its scene/motion fades set **View alpha only**; they do not automatically pause the producer. The TextureView itself is not translated with Workspace scroll; glass geometry is late-projected in root coordinates.
6. **UI per-pre-draw scan:** `LauncherGlassSession.syncSceneOnUiThreadInternal()` captures drag/static geometry, processes native motion force-refresh lease, reconciles producer/root and schedules output redraw when geometry changes. Geometry capture checks transformed corners and ancestor visibility per eligible node.
7. **Producer:** `RootPassBlurBackend` receives native PassBlur via OES SurfaceTexture, applies `PassBlurSourceFrameGate` FPS cap, *drains* rejected frames to avoid BufferQueue blockage, and normalizes accepted frames into a GPU texture.
8. **GL preparation:** `LauncherGlassSession.onFreshFrame()` prepares the downsampled Prismal backdrop from the normalized source. It passes the shared source frame to Recents independently and guards Workspace by generation, rotation, geometry and vendor coverage.
9. **Static redraw:** `renderStaticScene()` draws all eligible glass nodes to a native-sized RGBA8888 EGL window output (one TextureView), uses scissored individual glass shaders and swaps the full window surface.
10. **Independent redraw path:** `LauncherGlassOutputRenderState` merges pending STATIC/DRAG dirtiness, but a Widget fade/press, geometry motion or configuration change can request rendering from the **last prepared backdrop** without a new OES frame.
11. **Presentation/composition:** TextureView is sampled by HWUI / Launcher; actual SF composition ownership must be established using FrameTimeline + layer data. One TextureView does not necessarily imply one independent SF layer.

## Ranked findings and limits

### P0: Alpha/geometry-driven redraws are independent of capture

- `LauncherGlassStaticNode.animateVisibilityTo()` requests `requestStaticRedraw()` on every ValueAnimator update; `LauncherGlassVisibilityTransition` currently gives a full 450ms fade.
- `LauncherWidgetTransitionCoordinator` launches a fade-out and, after fresh HOME generation, a fade-in on the relevant widget. Other registered widget nodes remain in the same root-wide static scene.
- `LauncherGlassSession.drainOutputRenderWork()` can call `renderOutputs(true,false)` with a previously prepared backdrop, which still calls `renderStaticScene()` and EGL swap. Geometry-related changes also request this route.
- Hypothesis: the return jank contains GL swaps from the *geometry/fade* route even when source frame delivery stops. A pure source FPS cap would not eliminate them.
- **Do not delete user-visible fades yet.** Check matching gesture traces and compare Widget-return vs icon-launch App→HOME.

### P0: Source-driven render path always redraws eligible static glass

- Each valid `onFreshFrame()` calls `prepareBackdrop()` and `renderOutputs(true,true)`; `renderStaticScene()` clears a native-size target and traverses the full static snapshot. Per-node shader scissors bound shader work but not the underlying EGL window dimensions.
- At 3008×1880, one nominal RGBA8888 output buffer is about 21.57 MiB. This is a dimension estimate, not measured bus traffic.
- #349 and #350 reduced some shader math; #351 and #354 are independent unmerged candidates. #353 diagnoses actual EGL swap and stable-scene clip; its clip is disabled during motion.
- Hypothesis: full-screen clear/swap and HWUI consumption/BufferQueue pressure dominate the lengthy tail of StaticSwap under many widgets.

### P1: UI geometry and source freshness are different authorities

- `captureGeometry()` checks each node's ancestor chain, transforms local corners to global/root coordinates, and compares snapshots with 0.25px position/size tolerance. One root matrix inversion is already shared.
- `setHomeTransitionPending(true)` preserves presentation cache but does **not** imply zero producer frames or zero geometry redraws. `requestFreshBackdrop()` invalidates an actual source generation.
- Recents coverage can keep the shared source alive. Folder and rotation have stronger hard presentation/capture fences. Turning off all source updates just because Workspace glass is invisible would break Recents.
- The static TextureView is root anchored, not guaranteed to share child Workspace's zoom; assume neither cache-only zoom nor wallpaper-space identity without measured matrices.

### P1: Visibility only gates pixels, not GPU producer

- `LauncherGlassStaticLayer.setSceneVisible()` calls `setAlpha()`, not `setUpdatesEnabled(false)`.
- `LauncherGlassSceneController` tracks HOME/Recents/Folder/unlock/wallpaper; plain app foreground occlusion is not itself an explicit Workspace producer stop state in this scanned graph.
- Launcher window scheduling / producer delivery while another App is foreground must be measured; source code alone cannot establish that the producer is continuously active in the background.
- Adding a fade without an eligibility gate may add extra composition/update work rather than save GPU time.

### P2: Extra work / cache candidates requiring evidence

- Static nodes still iterate on each pre-draw. Existing code uses a shared root transform, reusable scratch arrays, geometry snapshots and `LauncherGlassOutputRenderState` coalescing; do not reintroduce broad geometry caches rolled back in #256.
- On `onFreshFrame()`, `renderOutputs(true,true)` is requested even if static/drag have no currently drawable nodes; any blank-output optimization requires an EGL surface-epoch/last-presented-content invariant and recovery tests.
- `RootPassBlurBackend` correctly drains rate-limited OES buffers; never drop source callbacks without `updateTexImage()`.
- #354 lazy native output texture/FBO may save one redundant RGBA storage allocation for direct EGL draws, but does not shrink the EGL window surface or reduce swaps.
- Wallpaper source freshness is not the same as root zoom/geometry. Reusing source textures needs strict content-generation and transform compatibility.

## Instrumentation implemented in this branch

Traces are gated by `Trace.isEnabled()`, without enabling synchronous `MainHook.debugLogging`:

- `LD.Workspace.SourceCallbacks`: cumulative OES callbacks observed by the backend during tracing.
- `LD.Workspace.SourceFramesGated`: cumulative callbacks drained without normalization because of the source FPS/generation gate.
- `LD.Workspace.SourceFramesNormalized`: cumulative validated source callbacks passed to normalization; not necessarily presented.
- `LD.Workspace.SourceLatch`: time on `SurfaceTexture.updateTexImage()` for rendered **and** gated frames.
- `LD.Workspace.Normalize`: GL normalization command submission for accepted source frames.
- `LD.Workspace.SourceFrame`: session rendering after all Workspace geometry/generation/presentation gates.
- `LD.Workspace.GeometryFrame`: independent geometry/alpha-driven output redraw from an existing backdrop.
- `LD.Workspace.StaticSwap`: full-root static EGL buffer exchange called from either route; nested span distinguishes source vs geometry.
- `LD.Workspace.Swap`: backend EGL swap for Workspace output surfaces (may include non-static drag output; do not sum it as extra static swaps).

JVM contract tests verify all trace points and preserve the source-gate drain path. Perfetto slices measure host wall-clock, **not GPU execution time**.

## Mandatory capture and decision

Use the same app/gesture and seven-Widget page, 165Hz, fixed orientation, after thermal warmup:

- A. main, glass ON; B. main, glass OFF; C. #358 trace APK glass ON. Repeat App→HOME via icon and via widget, Recents→HOME, App-covered idle, and Workspace scrolling 5-10 times each.
- Optional compare #353 separately for `EglSwap`, `StaticDraw`, root surface area / HWUI crop and `EGL_EXT_buffer_age` readout. Do not report #353's stable-scene crop as a fix for motion.
- FrameTimeline with per-gesture Launcher vs SF missed deadlines; `SourceFrame` vs `GeometryFrame` counts, nested `StaticSwap` frequency and P50/P90/P99, widget visibility fade interval, input callbacks vs normalized frames; hardware GPU busy/fences when available.

**Decision tree:**
1. Many `GeometryFrame→StaticSwap` during no `SourceFrame` ⇒ try a narrowly gated stale-backdrop geometry/alpha redraw policy on a separate branch (must not break Widget fades).
2. Many `SourceFrame→StaticSwap` during HOME motion ⇒ test generation-aware input/output coalescing while keeping wallpaper geometry/final frame authority.
3. Long swap wall times but short draw/normalize ⇒ investigate native BufferQueue + HWUI/SF ownership and GPU fences; do not claim shader bottleneck.
4. No static swap during app-covered interval ⇒ do not introduce redundant app coverage pause.
5. Only normal Widget fade causes heavy redo ⇒ benchmark native layer-level alpha vs fully static glass **without** altering all Widget visuals globally.

No new optimization is justified before a matched device trace. **Do not merge this diagnostic PR without CI + device profiling**, and do not adopt bitmap, PixelCopy, ScreenCapture, surface resizing, speculative buffer preservation, or fixed-time presentation freezes.
