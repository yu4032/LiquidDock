# Widget-dense App → HOME return — frame pacing experiment

**Date:** 2026-10-11. **Branch:** `perf/workspace-geometry-swap-coalesce-20261011`. **Status:** experimental / no on-device validation. Based on main `5eb30db`. Do not merge until repeatable A/B trace and wallpaper visual checks.

## Evidence from the two supplied traces

Captured by the user: `多组件玻璃开.perfetto-trace` (about **5.425 seconds** of available ftrace events) and `多组件玻璃关.perfetto-trace` (about **6.808 seconds**). The traces are **different interactive sequences**, not randomized matched-window trials; do not equate differences with causal improvement percentages.

```
                          glass ON     glass OFF
LD.Workspace.SourceFrame      336          185
LD.Workspace.GeometryFrame    193            0
LD.Workspace.StaticSwap       529          186
LD.Workspace.DrawnWidgets   7 usual         0
LD.Workspace.StaticNodes
  p50 (milliseconds)         0.236        0.040
LD.Workspace.StaticSwap
  p50 / p90 (ms)          1.446/9.061  0.367/2.445
StaticSwap intervals
  < 6.0606 milliseconds      182            5
```

The data show `529 = 336 + 193` output opportunities when widget glass is on. These are **separate source-triggered and geometry-triggered presentations**, not definitive proof that 193 frames were identical. During animation, a geometry-only frame can be essential, even if there is no new background.

FrameTimeline actual SurfaceFrame classification, grouped by `com.miui.home/...Launcher#` (both Launcher layers combined for the OFF trace):

```
                                      glass ON  glass OFF
Launcher actual frames                   670       604
Launcher PRESENT_ON_TIME                  63       170
Launcher JANK_BUFFER_STUFFING bit set    309       218
SurfaceFlinger actual display frames    802       818
SF display PRESENT_ON_TIME               218       363
```

`JANK_BUFFER_STUFFING` is a FrameTimeline bit flag, not proof of which producer initiated a missed deadline. The separate `Floating Dock` layer shows related lateness. The GL `StaticSwap` slice times wall-clock `eglSwapBuffers` (driver/compositor back-pressure may be counted there), **not** directly the shader execution time. Corrected previous misreport: ON StaticSwap p90 ~9.06ms, **not 80.17ms**.

## Experimental change

Only when work consists of **Workspace STATIC geometry** (no DRAG), there is no active drag output, a static EGL output already exists, and the last **completed static swap** happened less than one active display period ago:

1. Keep geometry work pending, post a GL delayed callback for the remaining frame period (whole milliseconds rounded upward).
2. Allow a native PassBlur source callback to run immediately and consume that geometry work, as already supported by `LauncherGlassOutputRenderState.consumeForSourceRender()`.
3. If no PassBlur source arrived, the delayed callback must still submit the latest geometry frame. Source processing and Recents output are never delayed.
4. Do not pace first presentation after output creation/resizing; reset the submission timestamp when the output surface changes.
5. Leave Prismal SDF/refraction/highlight shader, backdrop capture quality, geometry transforms, wallpaper token and HOME transition ownership unchanged.

**Intended** reduction: duplicate consecutive *geometry-only* swaps within one refresh interval, without introducing dirty-scene caching. This is not a new source-frame cadence rule.

**Known limitations:** without FrameTimeline-vsync alignment, an `eglSwapBuffers` wall-clock completion is an imperfect proxy for the display present phase; `Handler.postDelayed` may add up to ~1ms round-up and scheduling jitter. A geometrical update during a source drought can become ~one refresh interval later. If this shows any visual phase lag or poor input responsiveness, revert the experiment. In particular the prior #256 dirty-cache changes were reverted after device regressions, so do not re-introduce them.

## Mandatory on-device acceptance gates

- Same widget-heavy page, same app return path and wallpaper, 5–10 App→HOME gestures; **baseline PR #340** instrumentation vs this experimental branch, panel 165Hz, animations unmodified.
- Confirm counters: `LD.Workspace.SourceFrame`, `GeometryFrame`, `StaticSwap` and actual SurfaceFlinger/Launcher FrameTimeline PresentType/JankType. Goal: fewer closely spaced static swaps **without** more Launcher missed deadlines or worse input latency.
- Also repeat glass-off and few-widget control. These are independent captures, so compare across repeated matched runs, not percentages from the initial pair above.
- Ensure visual continuity of HOME/workspace scaling, wallpaper zoom ending, Recents→HOME, rapid back navigation, folder opening/closing, Workspace page scrolling, widget fade and position, landscape/portrait, root rebind and service restart.
- Confirm advanced-material Widget original background restored after toggling glass (separate fix in PR #341); this experiment does not modify the native material ownership chain.
- Observe whether the expected static presentation fell through while no source arrived; it must be delivered no later than one refresh-period pacing window plus platform scheduling overhead.
- Cancel/revert the experiment if the GL worker's delayed task creates input starvation, if any visible geometry trails the native Launcher surface, or if FrameTimeline fails to improve.

CI only verifies compilation and pure state-machine/pacing rules; it cannot establish visual correctness or an FPS improvement.
