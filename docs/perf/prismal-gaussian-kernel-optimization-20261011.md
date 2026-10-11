# Prismal Gaussian backdrop cost — exact-kernel optimization

2026-10-11; fresh `main` branch. Independent from the empty-frame PR #343 and the constant-light PR #344. Requires independent device benchmarking and visual acceptance; no automatic merge.

## Measured context

The supplied widget-heavy Perfetto shows seven Prismal Widget nodes in the active period; EGL `StaticSwap` p90 is ~9.81ms while widgets are drawn. `Backdrop` CPU slices are usually much shorter, **but GL execution is asynchronous**, and work submitted during blur can be charged to later `eglSwapBuffers`. This change addresses potentially expensive GPU shader texture fetch and fragment-weight evaluation rather than Java or root geometry.

## Existing model (kept)

Two separable blur passes over a 31-tap Gaussian, radius 15, normalized by the weight sum, with `sigma=max(blurRadiusPx * physicalScale * 0.5,0.5)`. Horizontal input is **full-density source texture**, while horizontal output and vertical input share the same half-density grid; original horizontal texel offsets use **1/blurWidth** rather than **1/renderWidth**. We must keep this legacy sampling behavior for image parity.

## Change

- Compute the exact 31-tap normalized Gaussian weights **once on the GL thread per sigma change**, upload in ES2 float-array uniforms. This removes 31 transcendental `exp()` weight evaluations from **each fragment** in both blur passes.
- Horizontal: preserve all **31 original sample positions** because adjacent tap positions are *two input texels apart*; merging taps here is **not** equivalent.
- Vertical: preserve the weighted sum with **17 actual texture fetches** (center, 7 positive+negative fractional paired offsets, positive+negative tail tap 15) instead of 31, via the existing `GL_LINEAR` interpolation at matching input/output resolution. Mathematical equivalence holds even at clamped edges when sampled inputs are linearly filtered.
- Keep upstream `PrismalShaderSources.BLUR_H/BLUR_V` unchanged; build dedicated optimized ES2 fragments in an isolated internal helper.
- If either optimized shader fails compilation/linking, revert both blur passes to the original upstream programs. No downgrade in radius, buffer scale, texture format, final glass raster resolution, optical highlights or animation FPS.

## CPU unit tests and hardware gates

Pure JUnit tests compare 17-fetch GL_LINEAR interpolation against all 31 original weighted tap samples over varied sigma, random samples and clamped edges (error budget 2e-6), validate normalization and count texture fetches. CI tests cannot actually compile/run Android GLES shaders, so validate device startup and no `Prismal shader compile failed` logs. Compare repeated matched 7-Widget App→HOME at 165Hz, `StaticDraw/StaticSwap`, SourceFrame, CPU/GPU counters where available and FrameTimeline `BUFFER_STUFFING`; also compare Dock, folders, drag, blur radius minimum/maximum, portrait/landscape, center and screen edge pixel quality.

No claims of actual FPS improvement before device validation; do not combine PR #344 before measuring each separately.
