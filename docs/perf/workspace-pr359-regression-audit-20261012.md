# PR #359 regression isolation — Widget-heavy App enter / HOME return

Date: 2026-10-12. User-reported device observation: **PR #359 is visibly less smooth than latest main**, and entering/exiting apps from a Widget-heavy workspace saturates CPU and nearly saturates GPU. This supersedes the previous theoretical benefit statements about #359. No device trace is attached to this document.

## Confirmed implementation errors or unnecessary work

**1. Split freshness generations at native HOME spring start (#359, P0).** `LauncherGlassSceneController.StateMachine.onAppReturnPresentationStarted()` increments its generation, but `LauncherGlassSession.sceneGeneration` is not invalidated until `session.requestFreshBackdrop()`. That later call is intentionally blocked while `homeTransitionPending` is true. Thus the controller declares prior HOME frames stale while the GL session continues accepting them against its older generation. `LauncherGlassSession.onFreshFrame()` can therefore repeatedly run `prepareBackdrop()` and full-root `renderOutputs(true, true)` during a transition even though those frames cannot satisfy the controller's new freshness generation. This is a *specific internal contract mismatch*, not a measured percentage of jank.

**2. Redundant App-return source work (#359, P0).** `onResume()` invalidates a generation and queues a delayed `requestFreshBackdrop()`; the accepted native HOME spring invalidates again, and its terminal requests another fresh frame. If the native spring begins after the two native frame opportunities, the earlier PassBlur/Prismal render is thrown away. Queued source callbacks still have OES/normalization cost even if the UI ultimately ignores the old frame.

**3. Dynamic fragment branching (#359, P0 performance risk).** `PrismalZeroContributionShader` introduces conditional `reflW > 0.0`, `os4ReflectionWeight > 0.0` and `os4EdgeMask > 0.0` scopes around texture sampling and directional math. These may reduce scalar work but add per-fragment diverging branches on mobile GPUs and can produce worse throughput in Widget-heavy scenes. Fallback is for GL compile failure only, not for shaders that compile but run slowly. It has no established speedup and is deliberately absent from recovery PR #360.

**4. Freshness pause/resume becomes bursty (#359, P1).** A new Launcher `onStop` source pause and `onResume` reactivation creates state transitions and source-frame warmups absent from main. Native Launcher return animations and Recents have their own coverage boundaries. The work may bunch up at App→HOME, versus main's already-established producer cadence.

**5. Pre-draw/GL duplicate work (#359, P1).** The new `renderStaticScene` performs an extra linear scan of all static nodes before the existing loop, solely to detect empty output; with many visible Widgets this scan almost always returns true and cannot skip the full-screen clear/EGL swap. Main still uses one root-wide RGBA TextureView and each active Widget fade updates that surface. Alpha hiding is not a GPU pause. No evidence yet attributes saturation exclusively to this scan or to Widget fades.

## Recovery strategy

- **Do not merge or publish PR #359 as a performance fix.** The physical regression outweighs speculative wins and unit-test success.
- **PR #360 is new from main:** GPU shaders, output FBO policy, source OES/PassBlur lifecycle, native HOME spring, Widget fade, EGL clear/swap and performance gates stay *exactly main*.
- #360 retains only (a) idempotent immutable-glass-config acquisition, (b) stable-root one-shot bootstrap, (c) skipping unchanged, already-owned Widget vendor material discovery and provider subtree traversal on HOME resume.
- Dedicated vendor `updateAppWidget` and MAML content-root replacement hooks still own actual provider updates.
- Use the same 7+ Widget page and app routes to compare main and #360; only if #360 retains baseline smoothness should an isolated GL/Shader experiment proceed.

## Verification limits

A high CPU/GPU percentage does not identify which process/thread or GPU queue saturated. Trace FrameTimeline, main thread, RenderThread, producer callbacks and eglSwapBuffers over identical gestures before attributing measured percentages. Existing static audit/CI can verify code consistency, not 165 Hz device performance.
