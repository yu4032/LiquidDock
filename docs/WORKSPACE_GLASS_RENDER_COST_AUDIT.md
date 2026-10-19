# Workspace glass render-cost audit — 2026-10-19

## Ownership and scope

LiquidDock glass-on nodes (Dock, icon glass, static widgets, folders, popup
glass and other Prismal sessions) use the **same in-pass GPU cast shadow**
recipe `PrismalEdgeDecoration`, inherited from the global/per-scope optical
material settings. A node's glass toggle must be ON for its renderer to exist.
For the Dock specifically, the native MiShadow write is replaced with zero
alpha **only after** the bound zero-copy Prismal host is active, and refreshed
on activation, teardown and permanent detach. When OFF/unavailable, vendor
MiShadow is restored with its existing non-glass customization behavior.

The `OUTER_SHADOW_ENABLED` option still controls whether the GPU shadow is
drawn; this change **does not silently enable disabled user shadow settings**.

## Asymptotic cost (code inspection; no measured frame timings)

Let `N` be visible glass nodes in the static workspace, `A_i` their
rendered/scissored pixel area including optical shadow guard, `F=W*H` the
full static output pixel count, `P` the number of newly prepared backdrop
pixels, and `E_i` the thin optical shoulder area of node i.

- Workspace CPU scene draw: **O(N)** loops, one GL node draw per visible node;
  previously each node also requested `root.getResources()`/display density and
  did a concurrent map lookup for its shadow recipe. These frame constants are
  now resolved **once**, O(1) total. Invisible nodes (alpha=0) do not draw.
- GPU scene: **O(F + sum(A_i * C_i))** when the output FBO is cleared and all
  glass nodes are shaded. Even if nodes are scissored, a full-surface clear and
  EGL composition still affect fill/bandwidth.
- Reconstructed macOS27 experimental fragment: `O(1)` arithmetic per fragment,
  but with large constant cost. Up to **7 texture samples** for edge chromatic
  dispersion (old path 3), plus distance blur-fill/graded backdrop reads in
  the meniscus; full flat interior has 1 center sample (after `cfb6b7fd`).
  The contour's normal/erfc/gradient math also incurs ALU on each visible
  affected pixel.
- Producer normalization + Gaussian blur: **O(P)** per prepared backdrop frame;
  `P` is controlled by capture quality and blur FBO density, separate from
  the native-resolution transparent output.

The render loop is **not O(N²)** in node count; the practical bottleneck is
expected to be fragment cost, total visible glass pixels, overdraw, output
clear/compose bandwidth and synchronization. Heavy pages with multiple large
widgets have `sum(A_i)` near full-screen even at modest `N`.

## Confirmed code optimizations in this patch

1. Batch the three immutable per-scope shadow recipes and display density once
   per `renderStaticScene` call, not per node.
2. Skip GLSL draws for alpha-zero nodes; already-cleared output correctly
   removes their prior images.
3. Keep current source capture and PassBlur frame-synchronization logic
   untouched; avoid speculative changes to EGL swap or wallpaper animation.
4. No new GPU texture, Surface, FBO or pass was added to unify cast shadows.

## Follow-up hardware profiling

Use the existing debug performance counters and, when possible, Perfetto GPU,
SurfaceFlinger and CPU slices to compare the same many-widget workspace page
with glass-on shadow ON/OFF and 27 full-shader versus compatibility shader.
Record node count, output resolution, backdrop preparation time, scene GPU
submission/swap time, frame deadline miss rate and GPU utilization.
165Hz has a **6.06ms** frame budget; runtime resource usage cannot be
predicted accurately from complexity formulas alone.

**Caveat**: the native Dock TextureView currently has its original viewport.
Prismal external shadow pixels outside that viewport may be clipped on
straight edges even though the same in-shader material is used. Expanding
the host surface/overscan requires an independent geometry/mapping change and
is intentionally not smuggled into this ownership-only patch.
