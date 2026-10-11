# Widget-dense App → HOME return — Perfetto study and safe empty-frame optimization

**Date:** 2026-10-11. **Base main:** `5eb30db`. **Branch:** `perf/workspace-geometry-swap-coalesce-20261011`. **Status:** experiment / CI, not yet real-device tested. Keep draft until visual and matched-trace checks.

## Observed signals

The user provided two Perfetto traces, `多组件玻璃开.perfetto-trace` (~5.425s of ftrace events) and `多组件玻璃关.perfetto-trace` (~6.808s). They cover **different interactions and durations**; comparisons below are descriptive, not controlled causal estimates. FrameTimeline data was decoded from Android `FrameTimelineEvent` (`TracePacket` field 76), with actual SurfaceFrame start/end cookies paired.

| Measure | Widget glass ON | Widget glass OFF |
|---|---:|---:|
| `LD.Workspace.SourceFrame` | 336 | 185 |
| `LD.Workspace.GeometryFrame` | 193 | 0 |
| `LD.Workspace.StaticSwap` | 529 | 186 |
| `DrawnWidgets` typical nonzero | 7 | 0 |
| `StaticNodes` p50 | 0.236ms | 0.040ms |
| `StaticSwap` p50 / p90 | 1.446 / 9.061ms | 0.367 / 2.445ms |
| Pairs of successive static swaps starting <6.0606ms apart | 182 | 5 |
| Launcher actual SurfaceFrames (all Launcher layers) | 670 | 604 |
| Launcher `PRESENT_ON_TIME` | 63 | 170 |
| Launcher `JANK_BUFFER_STUFFING` flag | 309 | 218 |
| SurfaceFlinger actual DisplayFrames | 802 | 818 |
| SurfaceFlinger `PRESENT_ON_TIME` | 218 | 363 |

Caveats: `eglSwapBuffers` slices measure CPU wait and submission, **not direct GPU shader duration**. The FrameTimeline `BUFFER_STUFFING` bit means buffers were queued; it does not by itself identify which producer caused the delay. Earlier `StaticSwap` p90=80ms was an analysis error; correct ON p90 is 9.061ms.

## Candidate rejected: delaying geometry-only swaps

Naive one-refresh bounded geometry delays looked promising given 193 additional geometry frames; an offline replay of the real timestamps disproved a sufficiently good coalescing ratio:

- ~169/193 geometry frames would be delayed.
- Only ~42/169 would see the next source frame arrive within the delay.
- Most geometry updates would become later without removing a swap. The additional delay risks visible Widget/WALLPAPER phase lag.

The experimental delay code was **fully reverted from the final branch diff**, rather than kept as an unproven patch. Producer cadence, native HOME zoom, PassBlur freshness, geometry updates, and wallpaper token ownership must remain unchanged.

## Safer measurable target: transparent static output

The same trace contains repeated output buffers where **zero Widget/static geometry was actually drawn**:

- ON: 100 zero-node swaps across **six** contiguous runs; 94 of them could theoretically be avoided if their EGL Surface remained unchanged between frames.
- OFF: 186 zero-node swaps in **one** run; up to 185 could theoretically be avoided.
- These numbers are upper bounds from recorded traces, **not real FPS savings or a confirmed result from this patch**.

When there is no drawable glass geometry, the renderer still clears the entire output to transparent and `eglSwapBuffers` on every source callback. The previously presented transparent buffer is already sufficient until a node acquires geometry again.

Patch: a tiny GL-thread `LauncherGlassEmptyOutputPolicy` records when a successful fully transparent static output has been presented on a given EGL Surface. The first empty presentation still clears and swaps (including after previously nonempty nodes), and source/geometry-driven rendering resumes immediately once any node has geometry. Subsequent empty frames skip the static clear/swap. Reset this state on static Surface attachment, detachment, or resize. Failed/unswapped clears never set the blank state.

The policy intentionally does **not** skip `prepareBackdrop()` in `onFreshFrame` or drop source-frame freshness acknowledgments. It never changes active Widget glass rendering or visual fidelity: 7-node animation frames continue to draw exactly as before. Thus the patch targets unnecessary work while a scene is empty; it is **not yet a complete fix for jank with 7 visible widgets**.

## Device acceptance gates before merge

1. Glass off and glass on, same page: confirm previously visible Widget backgrounds still restore properly (the separate MAML ownership PR #341 has not been incorporated).
2. With Widget glass enabled: open and close an app repeatedly; confirm no lingering glass after widgets disappear, no one-frame stale opaque image, and no missing first glass frame when widgets return.
3. Test home/workspace zoom, Recents→HOME, moving between pages, widget add/remove and fade, widget material reload, rotation, SurfaceTexture detach/attach/resize, folder/popup and scrolling. No Sprite/bitmap workaround or native wallpaper alteration.
4. Collect matched Perfetto runs with FrameTimeline and `LD.Workspace.StaticSwap`; in empty periods, swap count should reduce. **Do not declare heavy-widget improvement solely from this.**
5. Re-run dense Widget return profiling focusing on long `StaticSwap` and `BUFFER_STUFFING` when `DrawnWidgets=7`; if those remain, optimize active-geometry presentation/compositor only after extra measurement.

CI tests are pure state contracts. They cannot prove glass visual correctness, absence of wallpaper drift, or improved end-user FPS.
