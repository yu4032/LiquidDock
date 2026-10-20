# Workspace static-scene Perfetto baseline

The remaining P0 performance issue is a static-node scan on every Launcher root pre-draw.
Do not replace it with a timer or a root-size-only dirty gate. Property animations,
ancestor transforms, icon proxies, widget rebinds, workspace scroll projection,
rotation/unlock and Recents can all change geometry without a root layout.

The present patch adds **offline reporting only**. Runtime geometry, source freshness,
frame pacing, GL ownership and capture contracts are entirely unchanged.

## Capture

Disable LiquidDock file debug logging. Keep resolution, refresh rate, launcher version,
number of icons/widgets, and glass settings fixed throughout comparisons.

Save `workspace_scan.textproto` on your computer:

```textproto
buffers { size_kb: 65536 fill_policy: RING_BUFFER }
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

```sh
adb push workspace_scan.textproto /data/local/tmp/workspace_scan.textproto
adb shell perfetto --txt -c /data/local/tmp/workspace_scan.textproto -o /data/misc/perfetto-traces/workspace_scan.perfetto-trace
adb pull /data/misc/perfetto-traces/workspace_scan.perfetto-trace
python -m pip install perfetto
python scripts/workspace_trace_report.py workspace_scan.perfetto-trace
```

Capture **three repeats** each for a stable home page (few and many widgets),
Workspace paging, drag, app-to-HOME animation and unlock. Trace collection itself
changes timings; compare like with like. Inspect `com.miui.home` in
https://ui.perfetto.dev/ and verify `LD.Workspace.*` slices.

The report prints p50/p95/p99 in **microseconds** and per-frame counters.
`StaticGeometryReads` counts the full `captureGeometry()` calls, not node
allocations or GPU frames. A ratio close to 100% on idle frames shows potential
wasted scanning but does **not** establish a frame-time improvement.

For a controlled A/B after implementing a specific fast path:

```sh
python scripts/workspace_trace_report.py candidate.perfetto-trace --reference baseline.perfetto-trace
```

Do not enable a dirty-node skip until the trace demonstrates a material cost
and the proposed invalidation policy covers **view and ancestor transform changes,
layout, attach/detach, visibility, live config, native HOME spring, proxy updates,
widget changes and Workspace scroll**. Missing slices/counters are invalid evidence,
not zero work.

Existing technical rationale: [workspace-ui-node-scan-perfetto-20261010.md](workspace-ui-node-scan-perfetto-20261010.md).
