# Widget-heavy App → HOME return: performance attribution

**Scope:** HyperOS 3 / Launcher 4.50, native HOME `WindowElement` spring and wallpaper zoom, Workspace static Prismal glass. Built from current `main` on 2026-10-11. **Profiling only, not an FPS fix.** Do not merge a dirty-node optimization without a real device trace: PR #256 tried broad dirty tracking and reverted it after Recents/folder/drag/pinch regressions.

## Symptom / competing explanations

A page containing many widgets drops frames when the user returns from an app; Workspace and wallpaper both scale. These can overlap:

1. Launcher main thread: vendor widget updates, measure/draw, input-to-HOME animation, and LiquidDock `OnPreDraw` static geometry work.
2. LiquidDock GL worker: every qualifying PassBlur source frame prepares the backdrop and draws all eligible static glass shapes to the full-root output, then `eglSwapBuffers`.
3. Native SurfaceFlinger/launcher compositor: scaling wallpaper/window surfaces, composition and back-pressure.

Do not infer which thread is at fault from the visible wallpaper zoom alone. In particular, a vendor `WindowElement` transform can run outside View-local geometry and frame timing.

## Trace lanes

Already in `main` on the **Launcher UI thread**:

- `LD.Workspace.SceneSync`, `DragNodes`, `StaticNodes`, `SourceReconcile`
- `LD.Workspace.StaticCandidates` and `StaticGeometryReads`

Added in this branch (only when `android.os.Trace.isEnabled()`, **debug-file logging off**):

- `LD.Workspace.WidgetGeometryReads`: how many captured static geometries are Widget nodes; not a GPU measurement.
- `LD.Workspace.SourceFrame` / `Backdrop` on the GL thread (previously gated on `MainHook.debugLogging`).
- `LD.Workspace.GeometryFrame`: geometry-triggered GL output render task (previously gated on debug-file logging).
- `LD.Workspace.StaticDraw`: GL target binding, clear and per-node Prismal draw submission. It is a *CPU/driver slice*, **not** the measured GPU shader duration.
- `LD.Workspace.StaticSwap`: `eglSwapBuffers` including any compositor back-pressure.
- `LD.Workspace.DrawnStaticNodes` / `DrawnWidgets`: glass nodes submitted in that static frame. These are not native Widget render counts.

The exact shader/GPU pipeline occupancy requires GPU/SurfaceFlinger and FrameTimeline views; a short `StaticDraw` slice does not prove that the GPU completed rapidly.

## Capture

Save this as `workspace_home_return.textproto` on your computer:

```textproto
buffers {
  size_kb: 65536
  fill_policy: RING_BUFFER
}
data_sources {
  config {
    name: "linux.ftrace"
    ftrace_config {
      atrace_categories: "gfx"
      atrace_categories: "view"
      atrace_apps: "com.miui.home"
    }
  }
}
data_sources {
  config {
    name: "android.surfaceflinger.frametimeline"
  }
}
duration_ms: 20000
```

Run from a normal Linux shell or Windows PowerShell:

```bash
adb push workspace_home_return.textproto /data/local/tmp/workspace_home_return.textproto
adb shell perfetto --txt -c /data/local/tmp/workspace_home_return.textproto -o /data/misc/perfetto-traces/workspace_home_return.perfetto-trace
adb pull /data/misc/perfetto-traces/workspace_home_return.perfetto-trace
```

On **Windows Git Bash**, prefix the `adb` calls with `MSYS_NO_PATHCONV=1` to avoid rewriting Android paths as `C:/Program...`. If the trace destination is denied, use a shell-accessible output directory or `adb shell su -c` with a valid quote boundary. Open the trace in https://ui.perfetto.dev/ and confirm `com.miui.home`, `LD.Workspace.*` lanes and FrameTimeline are present; an absent lane does not mean zero cost.

## A/B protocol

Keep panel refresh rate, resolution, wallpaper, Launcher layout and animations the same. Disable LiquidDock debug-file logging throughout.

- **A:** widget-heavy page, Workspace glass enabled. Open the same app, return HOME in the same way 5–10 times during the capture.
- **B:** same widget-heavy page, Workspace glass disabled (scope restarted if required); repeat identical returns.
- **C:** page with few/no widgets, Workspace glass enabled; repeat.
- **D (optional):** widget-heavy page with only widget-glass disabled but all other glass left unchanged; requires the existing GUI to expose that independent control and should not remove the native widgets.

Compare missed-present frames and frame deadlines on Launcher/SurfaceFlinger FrameTimeline, then correlate with the native return animation and the UI and GL trace slices. Collect at least two independent captures per condition; avoid switching apps/layout mid-run.

## Decision gates before optimization

- **Large `StaticNodes` slice and high `WidgetGeometryReads`** during late frames: investigate scoped widget geometry events / transform invalidation, preserving per-child visibility, HOME spring, scroll and proxy ownership. Do *not* restore PR #256's rolled-back blanket dirty cache.
- **Long `Backdrop`, `StaticDraw` or `StaticSwap` correlated with jank, while B is clean:** focus on Prismal shading / render scheduling, and separate expensive draw from swap back-pressure. Preserve source-to-wallpaper alignment and PassBlur freshness.
- **A and B both jank similarly, with small LiquidDock slices:** investigate vendor widget rendering and SurfaceFlinger wallpaper/workspace composition rather than changing LiquidDock glass.
- **Few-widget page fine, many-widget page bad even with glass disabled:** investigate widget RemoteViews/MAML drawing/layout over native HOME animation first.

No timing skip, capture freeze, PassBlur downgrade or automatic wallpaper zoom suppression was introduced by this diagnostic PR.
