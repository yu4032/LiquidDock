# Prismal zero-contribution GPU work audit (2026-10-11)

Base: `main` `48ca068e` (includes #349 and #350). Draft only; **on-device visual and Perfetto validation still required**.

## Confirmed code paths

1. **Shared transmitted/reflected Prismal layer.** In the final generated shader, `reflShell` becomes *exactly zero* well inside large Widget surfaces, so `reflW` becomes zero. Previously `reflUv`, normalized reflected direction and an additional `texture2D` still executed, followed by `mix(color, reflSample, 0.0)`. New branch: compute UV and sample only when `reflW > 0.0`. A nonzero weight of any size is still processed (no approximation epsilon).
2. **OS4 glass reflection.** `os4ReflectionWeight = clamp(os4EdgeMask * max(strength,0),0,1)`. This is exactly zero outside the narrow edge band and whenever reflection is disabled. Previously the shader sampled a blurred/refraction texture regardless. Move the weight before the sample, skip UV + sampling only when it is zero.
3. **OS4 directional lighting.** Outside the edge band, both the additive color and the normal-debug highlight `os4SoftLight` are exactly zero. Keep the scalar defined as zero in the enclosing scope and branch around the two `acos`, dot products, luminance and directional arithmetic only when `os4EdgeMask > 0.0`. OS4 normal debug still reads the scalar.
4. **Dead expressions.** Under the single-edge refraction override, upstream `edgePunch` and `Fr` are unused; the configurable `edgeFalloffPower/dropLens` is also calculated but no longer consumed by `baseOffset`. Remove these local expressions in the final generated shader, not upstream. Real GPUs probably dead-code-eliminate some/all of them already; this is source hygiene, not a claimed performance gain.

## Existing disconnected parameter — keep separate

`PrismalSingleEdgeShader` replaces the upstream Snell/parallax/bulge term with `baseOffset = (dLens*lensDir)/u_resolution`. The `dropLens` intermediate and `u_edgeRefractionFalloff` no longer affect this final output. Do not silently alter `dLens` or remove the UI/configuration key under a performance-only patch. Follow-up requires a separate visual/functional decision and device tests.

## Limits and acceptance

- GLSL compiler may already omit unused locals; driver behavior for dynamically uniform-less branching and implicit texture LOD can vary. No guaranteed GPU throughput increase.
- Preserve all nonzero weights, original samplers, OS4 SDF masks, refraction/highlight formulas, widget geometry, blur resolution, wallpaper transitions and cadence.
- Unit tests inspect final assembled shader transformations and the zero-contribution identity; **they do not replace GLES runtime compilation**.
- Compare a 7-Widget page at 165Hz with repeated matched App→HOME and Recents→HOME gestures. Track `LD.Workspace.StaticDraw`, `StaticSwap`, and FrameTimeline buffer stuffing; check rounded corners, edge reflection, normal-debug light direction, dark/light wallpapers, Dock, folders, and high/zero reflection strength.
- If GLES compilation, visual parity, or actual performance regresses, revert this independent PR; do not merge solely because CI is green.
