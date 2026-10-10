# Dock PassBlur pre-draw stage attribution — Perfetto

**Status:** Instrumentation-only change on `perf/dock-predraw-stage-tracing-20261010`, [CI #38047653519](https://github.com/yu4032/LiquidDock/actions/runs/38047653519) successful; on-device profiling outstanding. No geometry cache, redraw throttling or claimed frame-rate improvement. Source baseline: merged `main@089c76b2` (P1-A Workspace bounds optimization already accepted).

## What this measures

`Miuix307PassBlurTextureView.installGeometryObserver()` receives root `OnPreDrawListener` callbacks and must preserve both producer/root authority and the Dock-to-PassBlur UV mapping. These stages execute even on stable frames before equality checks:

| Perfetto app slice | Work represented |
|---|---|
| `LD.Dock.GeometryPreDraw` | Total Dock `OnPreDraw` geometry pass |
| `LD.Dock.ProducerGeometry` | Producer root/size/rotation comparison and any safe in-place update/rebind decision |
| `LD.Dock.ReadSurfaceGeometry` | `readSurfaceGeometry(materialHost)`, including cached reflective ViewRoot/Surface checks |
| `LD.Dock.BackdropMapping` | Visible dock/screen coordinates, sampling insets, two UV mappings, scene fingerprint and publication |
| `LD.Dock.UiSceneFingerprint` | `DockGlassCompositor.refreshUiSceneIfNeeded()` alone; captures the per-icon scene fingerprint pass |

Trace scopes are emitted **only when `android.os.Trace.isEnabled()`**, scoped with `try/finally` and require no `MainHook.debugLogging` file writes. There are no new polling timers, snapshots, frame pumps or producer API changes. The scene-only render decision is intentionally still evaluated even if the backdrop mapping is unchanged.

## Device capture

Prepare `dock_predraw.textproto`:

```textproto
buffers {
  size_kb: 65536
  fill_policy: RING_BUFFER
}
data_sources {
  config {
    name: "linux.ftrace"
    ftrace_config {
      atrace_categories: "view"
      atrace_categories: "gfx"
      atrace_apps: "com.miui.home"
    }
  }
}
duration_ms: 20000
```

Example capture (ROM/path permissions may require root):

```bash
adb push dock_predraw.textproto /data/local/tmp/dock_predraw.textproto
adb shell perfetto --txt -c /data/local/tmp/dock_predraw.textproto \
  -o /data/misc/perfetto-traces/dock_predraw.perfetto-trace
adb pull /data/misc/perfetto-traces/dock_predraw.perfetto-trace
```

View the trace at [Perfetto UI](https://ui.perfetto.dev/) and filter to the `com.miui.home` process. Confirm all relevant `LD.Dock.*` slices appear; a missing slice does not prove that its stage costs nothing.

## Compare on the same build/settings

1. Disable LiquidDock debug-file logging. Keep display mode, glass quality, scale, launcher state and dock icons fixed.
2. Capture several windows of stable Dock, icon/dock resizing, Workspace paging, app→Recents→home (wallpaper transform), orientation changes and workstation mode if used.
3. Compare p50/p95/p99 of `GeometryPreDraw` and its two top-level child sections, then break down `ReadSurfaceGeometry` versus `UiSceneFingerprint`. Check worst-frame attribution and frame continuity, not only averages.
4. Verify `ReadSurfaceGeometry` remains responsive to producer root replacement/rotation; `UiSceneFingerprint` must run when hotseat geometry changes even when producer UV rect remains unchanged.
5. If source/root reflection dominates, design a *complete* invalidation contract first (root replacement, mWinFrameInScreen, rotation, width/height, vendor rebind). If fingerprint dominates, profile candidate count and seek reliable dirty revisions in `DockGlassCompositor`. Do **not** introduce fixed-interval polling.
6. Compare the same workload with tracing disabled to understand instrumentation overhead. A working trace is not an FPS fix.

**Safety:** Preserve force-refresh frame-sync lease, wallpaper zoom completion and output-root mapping, surface rebind/rotation settle, overscan validity, fresh producer barriers and the shared Prismal shader. No ScreenCapture/PixelCopy/Bitmap or extra recording layers.
