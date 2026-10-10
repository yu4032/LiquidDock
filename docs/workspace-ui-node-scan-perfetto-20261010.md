# Workspace glass pre-draw scan: Perfetto sampling gate

**Status (2026-10-10): instrumentation on independent PR #321 passed [CI #38044043722](https://github.com/yu4032/LiquidDock/actions/runs/38044043722); device traces are not yet captured. This is not yet a speed-up.** Baseline `main@c25a4c92`; target `LauncherGlassSession.syncSceneOnUiThreadInternal` (Launcher Workspace glass). It does not touch Dock PassBlur producer, EGL output lifecycle, Prismal parameters or background wallpaper generation.

## Why measurement comes before a dirty-node cache

The Launcher root's `OnPreDrawListener` calls `syncSceneOnUiThread()` for each frame. Drag sinks already avoid geometry capture when unchanged; static nodes currently call `captureGeometry()` for every living node, including ancestor alpha/visibility and global-to-root matrix operations. The root scroll anchor and static-root transform are also recomputed. Separately, `RootPassBlurBackend.reconcileRoot()` runs after node processing.

Caching these geometry reads on the basis of `root.getWidth()/getHeight()` alone is **unsafe**: ancestors can change scale, opacity, translation or visibility with unchanged root bounds, while paging, drag/proxy, unlock and source root replacement have separate authorities.

The previous `[DC][WorkspacePerf][UI]` counter reported only total sync time under `MainHook.debugLogging`. That debug mode also synchronously appends to a file, making it a poor basis for a reliable frame-budget comparison.

## New Perfetto trace lanes (only if Android app tracing is active)

| Section/counter | Meaning |
|---|---|
| `LD.Workspace.SceneSync` | Whole UI-thread pre-draw sync, including source root reconciliation |
| `LD.Workspace.DragNodes` | Per-frame sink `syncFromMaterial` and any required drag geometry captures |
| `LD.Workspace.StaticNodes` | Workspace scroll-anchor read, global-to-root transform and static node geometry/visibility captures |
| `LD.Workspace.SourceReconcile` | `RootPassBlurBackend.reconcileRoot()` cost alone |
| `LD.Workspace.DragCandidates` / `DragGeometryReads` | Drag snapshot size vs actual expensive `captureGeometry` calls |
| `LD.Workspace.StaticCandidates` / `StaticGeometryReads` | Static snapshot size vs calls to `captureGeometry`; static reads only increment if root transform is valid |

The code checks `Trace.isEnabled()`; when no app trace is collected, it **does not emit per-frame slices, counters, logs or allocate counter arrays**. Scoped trace sections end in `finally`, including failures. Diagnostic mode is not required.

### Sample on a rooted or developer-enabled Android test device

Create a local file `workspace_scan.textproto`:

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

Then record, for example:

```bash
adb push workspace_scan.textproto /data/local/tmp/workspace_scan.textproto
adb shell perfetto --txt -c /data/local/tmp/workspace_scan.textproto \
  -o /data/misc/perfetto-traces/workspace_scan.perfetto-trace
adb pull /data/misc/perfetto-traces/workspace_scan.perfetto-trace
```

If the device denies access to the output directory, use a shell-accessible trace destination or an appropriate root shell. `perfetto` availability and output permissions depend on the ROM. Open the output in [Perfetto UI](https://ui.perfetto.dev/). Verify the `com.miui.home` process and `LD.Workspace.*` lanes actually appear; **an empty trace is not a zero-cost result**.

### Comparison protocol

1. Turn **off** LiquidDock debug-file logging during trace runs. Fix display refresh mode, resolution and Glass GUI configuration.
2. Capture three repeatable 20-second windows for a stationary home page (small vs large number of glass icons/widgets), Workspace paging/scroll, drag and unlock. Include a no-Workspace-glass baseline.
3. Inspect **p50/p95/p99** of `SceneSync` and the cost of `StaticNodes` separately; compare `StaticCandidates` to `StaticGeometryReads`. A high static read count in stable frames identifies the work being done, not necessarily the proven share of total jank.
4. Confirm every `SceneSync` that runs static scan also emits drag/static counters; `SourceReconcile` may be absent on rotation/replacement early-return frames.
5. Only after measurements, propose a narrowly scoped dirty-revision policy for a single node class with proven invalidation events, and test ancestor visibility, spring/unlock, rotation, widget translation, paging and proxy state. Do **not** add a timer-based poll or force a blanket no-change fast path.

### Interpretation and limits

Perfetto tracing itself has measurable overhead; use several runs and compare analogous captures, not traced FPS against untraced FPS. This patch gives stage attribution, **not** a claim that scanning was eliminated, nor a measured improvement. On-device A/B and regressions are required before any runtime cache.

## Additional source-level candidate (not changed in this PR)

`LauncherGlassStaticNode.captureGeometry()` calls `LauncherGlassBoundsPolicy.apply(...)` which returns a newly allocated `float[4]` on each call. The per-node `geometryPoints` buffer is already reused. A later, separately tested `applyInto` overload using a reusable 4-float buffer may reduce allocations without suppressing geometry updates; it must preserve finite-offset coercion, negative insets, collapsed-dimension 1-pixel normalization and repeated calls. This does **not** solve the O(N) scan itself and needs a measured allocation baseline.
