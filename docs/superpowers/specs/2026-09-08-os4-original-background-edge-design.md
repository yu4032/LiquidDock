# OS4 Original Background-Driven Edge Design

## Goal

Replace the failed Milestone 2 standalone Bloom-overlay interpretation with the background-driven edge-light model observed in the extracted OS4 glass shader, while preserving the device-proven volumetric edge/raster-density fixes.

## Evidence and failure analysis

The Milestone 2 device run produced a very large opaque black region extending outside the glass component. The runtime capture/mapping path itself remained alive: PassBlur/OES frames arrived, zero-copy remained active, Prismal mapping progressed from PARTIAL to FULL, and there was no shader-program or framebuffer creation failure in the captured log. The direct implementation bug is that Prismal's existing backdrop blur shaders output `alpha=1.0` and were reused on a transparent Bloom mask, turning transparent Bloom pixels into opaque black before source-over composite.

That alpha defect is not the only issue. The extracted OS4 glass shader shows that the visible soft edge is primarily background-driven rather than an independent white halo. Its core sequence is:

```text
shape SDF distance
  -> finite-difference edge normal
  -> edge depth within uShapeEdgePx
  -> screen-space reflected/refracted backdrop sample
  -> nonlinear edge reflection mask
  -> directional soft-light response
  -> multiplicative gain + luminance-aware grayscale lift
  -> existing glass alpha
```

Therefore fixing the local Bloom blur alpha alone would preserve the wrong visual architecture.

## Architecture

The implementation remains a single Prismal glass pass for the visible OS4-style edge. No renderer-local Bloom FBO is introduced on this branch.

The existing logical-resolution output remains unchanged. The backdrop texture may still originate from a downsampled/blurred physical capture, but the SDF, finite-difference edge normal, reflection offset, directional-light calculation, and final color gain are evaluated in the logical-resolution glass fragment pass.

### 1. SDF edge and normal

Use the existing rounded-rect SDF and the already device-proven logical-pixel derivative footprint. Compute a stable edge normal using centered samples around the current fragment. The SDF normal is authoritative inside the OS4 edge band and blends back to the existing Prismal optical normal away from the edge.

### 2. Edge depth

Resolve an explicit `u_os4EdgeWidthPx` with an automatic fallback when zero. Compute:

```glsl
float edgeT = clamp(edgeDist / max(edgeWidthPx, 1.0), 0.0, 1.0);
float edgeRemain = 1.0 - edgeT;
```

No finite-width halo is allowed outside the glass silhouette. Edge width controls the internal optical band, not output alpha expansion.

### 3. Background reflection sample

Offset the current screen/backdrop UV along the stable edge normal:

```glsl
vec2 reflectOffsetUv = edgeNormal
        * (2.0 * edgeNormalZ)
        * reflectOffsetPx
        * edgeRemain
        / u_resolution;
```

Sample the same background source used by the current glass (`u_blurredTexture` when enabled, otherwise `u_backgroundTexture`) and mix it into the refracted glass color using a nonlinear edge curve and explicit reflection strength.

This is not a second capture path and not a new texture owner.

### 4. Directional soft light

Build a pseudo-3D normal from the 2D SDF normal plus a positive Z component. Use the configured light direction and an angle-range falloff to compute main-side and opposite-side responses. The implementation may use a stable polynomial approximation or clamped trigonometric form, but it must preserve these semantics:

- main intensity and opposite intensity are independent;
- angle range controls softness/width of the directional response;
- light response is multiplied by the OS4 edge mask so it cannot illuminate the component interior or exterior independently.

### 5. Background-dependent lift

Do not add an independent white Bloom color. Apply the directional result to the current glass color:

```glsl
float gain = 1.0 + softLight;
color *= gain;
float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
float darkResponse = 1.0 - smoothstep(0.35, 0.92, luminance);
color += vec3(reflectionLighten * darkResponse * softLight);
```

The lift is intentionally stronger over dark backgrounds and weaker over already bright content.

### 6. Volumetric thickness

Keep the Milestone 1 volumetric thickness displacement that was visibly effective on device, but remove the independent finite-width white Bloom-ring contribution. Thickness and edge-light width remain separate concepts.

## Parameters

Add the following `PrismalParams` values and renderer uniforms:

- `os4EdgeWidthPx` — default `0`, meaning size-adaptive fallback;
- `os4ReflectOffsetPx` — default `0`, meaning thickness-derived fallback;
- `os4ReflectionStrength` — default `0.28`;
- `os4ReflectionLighten` — default `0.16`;
- `os4DirectionalAngleRange` — default `0.52`;
- `os4DirectionalIntensity` — default `0.42`;
- `os4DirectionalOppositeIntensity` — default `0.14`.

These are implementation defaults, not claimed extracted runtime values. They expose the original model dimensions without pretending that unknown vendor runtime parameter values were recovered.

## Explicit removals

This branch must not contain or execute:

- `PrismalBloomTarget` sizing for the visible edge;
- renderer-local Bloom mask/H/V/composite targets;
- any Bloom blur pass over a transparent mask;
- any independent white/cool edge RGB halo outside the background-driven glass color;
- any expansion of output alpha beyond the existing glass silhouette.

## Frozen boundaries

- no ScreenCapture / PixelCopy / bitmap fallback / CPU readback;
- no new SurfaceTexture, EGL context, PassBlur producer, freshness authority, or Launcher lifecycle owner;
- no changes to Recents/Workstation authority;
- no change to the proven physical-backdrop/logical-output split;
- no checked-in modification of upstream provenance GLSL; the change remains an explicit runtime shader adaptation.

## Verification

Automated contracts must prove:

1. OS4 edge parameters exist and are bound by `PrismalRenderer`;
2. the optical shader contains a backdrop reflection sample controlled by edge depth and reflection strength;
3. the shader contains independent main/opposite directional-light terms and an angle-range softness control;
4. the final highlight is implemented as gain/luminance-aware lift of existing glass color;
5. the old independent `os4BloomRing`/`os4BloomColor` contribution is absent;
6. output opacity remains the existing glass opacity and is not widened by the edge light;
7. full `testDebugUnitTest assembleDebug` succeeds.

The final gate remains a true-device visual test on the exact CI APK.