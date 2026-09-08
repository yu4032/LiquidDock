# OS4 Local Bloom Overlay — Milestone 2 Design

## Status

Approved continuation of the device-proven Milestone 1 edge model on branch `port/os4-volumetric-edge-model`.

Milestone 1 proved on-device that the OS4-inspired edge model visibly moves LiquidDock away from a thin white rim toward a thicker optical edge. Milestone 2 turns the temporary shader constants into renderer parameters and moves Bloom softening into a local high-resolution render target.

## Goals

1. Promote OS4 edge controls into `PrismalParams` so the model is explicit and tunable.
2. Keep volumetric refraction and edge reflection in the existing Prismal glass pass.
3. Remove the final OS4 Bloom contribution from the main glass fragment at runtime.
4. Render Bloom into a local FBO sized only to the glass bounds plus blur padding.
5. Blur that local target at full local density, then composite it back into `outputFramebuffer` at the glass position.
6. Preserve the proven physical-backdrop/logical-output split from Milestone 1 and PR #138.
7. Preserve all existing PassBlur/OES/freshness/Launcher ownership boundaries.

## Non-goals

- No ScreenCapture, PixelCopy, Bitmap fallback, or CPU readback.
- No new SurfaceTexture, EGL context, or producer authority.
- No new cache invalidation tied to Launcher lifecycle.
- No native processor or vendor Bloom cache port in this milestone.
- No settings UI yet; defaults should reproduce the Milestone 1 visual direction.
- No change to upstream checked-in Prismal GLSL provenance file.

## Alternatives considered

### A. Keep Bloom inside the main Prismal fragment

Smallest code change, but Bloom remains inseparable from the main glass pass and cannot gain an independent blur radius without making the whole material pass more expensive. Rejected because it does not create the desired performance boundary.

### B. Add a second analytic Bloom draw directly into the full output FBO

This separates the shader logic and only rasterizes a local quad, but a soft Bloom still needs either many analytic taps or no true spatial diffusion. It also makes later caching awkward. Useful as a fallback, but not the target architecture.

### C. Local Bloom mask FBO + local separable blur + positioned composite

Chosen. It gives Bloom an independent resolution/cost domain, keeps high-frequency edge detail local, reuses one renderer-owned scratch target across nodes, and does not create new Launcher or backdrop ownership.

## Public parameter model

Add the following immutable fields to `PrismalParams` and matching builder defaults:

- `os4EdgeWidthPx` — width of the volumetric edge region.
- `os4ThicknessPx` — optical material thickness used by edge refraction.
- `os4ReflectOffsetPx` — second reflection sample offset along the stabilized SDF normal.
- `os4BloomWidthPx` — finite geometric ring width before blur.
- `os4BloomIntensity` — Bloom color/intensity multiplier.
- `os4BloomBlurRadiusPx` — local blur sigma/radius control.
- `os4BloomEnabled` — renderer-level Bloom pass gate.

Defaults must match Milestone 1 closely enough that enabling Milestone 2 does not intentionally redesign the appearance.

The old `highlightWidth` remains for upstream Prismal rim/highlight controls. It must not become the source of the OS4 thickness or Bloom width.

## Main glass shader changes

`PrismalOpticalEdgeShader` will replace hardcoded Milestone 1 constants with uniforms:

- `u_os4EdgeWidthPx`
- `u_os4ThicknessPx`
- `u_os4ReflectOffsetPx`
- `u_os4BloomWidthPx`
- `u_os4BloomIntensity`
- `u_os4BloomInMainPass`

The volumetric edge and reflection sample remain in the glass fragment.

The existing analytic Bloom block stays available as a compatibility/debug path but is multiplied/gated by `u_os4BloomInMainPass`. Runtime `PrismalRenderer` sets it to `0` once the local Bloom pass is enabled. Focused shader tests can still assert the model text without requiring the local renderer.

## Local Bloom target

`PrismalRenderer` owns one reusable set of scratch objects:

- Bloom mask texture/FBO.
- Horizontal blur texture/FBO.
- Vertical blur texture/FBO.

For each glass node, target dimensions are derived from:

`ceil(glassWidth + 2 * padding)` × `ceil(glassHeight + 2 * padding)`

where `padding` is based on `os4BloomWidthPx + blur spread`, bounded to sane values and clamped to the logical output dimensions.

Targets are resized only when the required local dimensions change. Multiple glass nodes reuse the same target sequentially.

## Bloom mask shader

A dedicated lightweight ES 2.0 fragment shader computes:

1. local rounded-rectangle SDF using the node's four radii;
2. anti-aliased expanded outer coverage;
3. anti-aliased contracted inner coverage;
4. `ring = outer - inner`;
5. stabilized local SDF normal by finite differences;
6. main/opposite/ambient directional response from `lightDir`;
7. OS4 nonlinear cross-section shaping;
8. premultiplied Bloom color/alpha for the local target.

It must not sample the backdrop and must not depend on PassBlur/OES textures.

## Local blur

Reuse the existing Prismal separable blur programs, but generalize the internal blur helper so it accepts explicit target width/height rather than implicitly using the backdrop blur dimensions.

Backdrop blur behavior and scale remain unchanged.

Bloom blur runs at the local target's full pixel dimensions. This is the key cost boundary: only the padded glass rectangle pays for high-resolution Bloom diffusion.

## Composite back to Prismal output

A small positioned-quad composite program maps the local Bloom texture back into `outputFramebuffer` using:

- logical output resolution;
- glass center in GL bottom-left coordinates;
- local Bloom target width/height.

The composite uses premultiplied source-over semantics so Bloom is represented correctly in the transparent Prismal output texture and remains compatible with the existing outer compositor.

The Bloom pass executes immediately after each main glass node draw. Multi-node batch behavior therefore remains deterministic and does not require a separate scene graph or deferred Bloom list.

## Alpha behavior

Milestone 1 deliberately avoided expanding the glass silhouette with the analytic Bloom block. Milestone 2 may produce a soft optical halo in the Bloom overlay, but only inside the bounded local target and only through the existing transparent Prismal output texture.

The halo must not alter the main glass SDF/opacity calculation. Disabling `os4BloomEnabled` must return exactly to the main glass result without the local overlay.

## Error handling

- Invalid or non-finite public OS4 parameters are sanitized in renderer-side calculations similarly to existing opacity/blur controls.
- Local target dimensions are clamped to at least 1×1 and at most logical output dimensions.
- FBO completeness remains fail-fast through the existing framebuffer creation contract.
- No fallback path is added if Bloom target creation or shader compilation fails; Prismal renderer fails closed as it already does for GL program/FBO errors.

## Testing

TDD contracts will cover:

1. `PrismalParams` exposes all OS4 fields with finite defaults independent from `highlightWidth`.
2. `PrismalOpticalEdgeShader` consumes OS4 uniforms instead of hardcoded Milestone 1 constants.
3. Runtime renderer binds all OS4 main-pass uniforms and disables in-main-pass Bloom when local Bloom is active.
4. Renderer owns local Bloom mask + H/V blur targets, not a second full logical-output target.
5. Local target sizing is based on glass dimensions + bounded padding.
6. Bloom mask uses outer-minus-inner ring construction and directional main/opposite/ambient lighting.
7. Existing backdrop blur helper is generalized without changing the 0.5× backdrop blur scale semantics.
8. Local Bloom composite maps using logical output resolution and glass center.
9. `os4BloomEnabled=false` bypasses the local Bloom sequence.
10. Existing Prismal and application contract tests remain green.

## Device acceptance

After CI green, test the exact APK on the same device used for Milestone 1.

Acceptance questions:

- Does the thicker optical edge remain visually present?
- Is Bloom softer and less like a painted white band?
- At 50%/75% backdrop density, does Bloom retain full local edge detail?
- Is there no visible rectangular local-target boundary or clipped blur halo?
- Are corners continuous in portrait/landscape and through normal Dock scene changes?
- Is there no new glass freeze, stale backdrop, or Recents/Launcher lifecycle regression?

Milestone 2 is not complete until the device visual gate passes.