# OS4 Original Background Edge Verification

Status: `CODE_AND_ONE_SHOT_GREEN_FORMAL_CI_PENDING`

Branch: `port/os4-original-background-edge`
PR: `#140`
Baseline: `a45651a97cb5f13594371e2c0d8c791c89a8a947`

## Device evidence that rejected Milestone 2

The previous renderer-local Bloom FBO implementation produced a giant opaque black mask extending beyond the glass component and hid the background. The captured device log still showed a live PassBlur/OES zero-copy path, a successful first EGL material draw, valid Prismal mapping, and mapping progression to FULL coverage. No target-GPU shader/program creation failure or framebuffer-incomplete error was observed in the supplied log.

The implementation root cause was identified in source: the existing Prismal backdrop blur shaders always emit alpha `1.0`, but Milestone 2 reused them to blur a transparent Bloom mask. Transparent black pixels therefore became opaque black before the local source-over composite.

More importantly, the extracted OS4 glass shader evidence showed that the visible soft edge is background-driven: SDF edge distance -> pseudo-normal -> screen-space backdrop reflection -> directional soft falloff -> brightness/luminance-dependent lift. Fixing only the Bloom alpha bug would preserve the wrong visual model.

## New architecture

This branch starts from the last device-proven volumetric-edge baseline before the failed local Bloom FBO work.

Visible edge flow:

```text
existing Prismal logical-resolution glass pass
  -> rounded-rect SDF edge distance
  -> centered finite-difference SDF gradient
  -> pseudo-3D edge normal normalize(vec3(gradient, 1))
  -> screen-space offset into the active backdrop texture
  -> nonlinear internal edge reflection mask
  -> directional main/opposite soft-light falloff
  -> multiply current background-derived glass color
  -> luminance-aware grayscale lift
  -> existing shape-derived alpha
```

There is no renderer-local Bloom mask/H/V/composite path on this branch.

## TDD evidence

### RED 1 — new original-model contract

API101 CI #4383:
- run `34209324580`
- job `102006294705`
- `25 tests completed, 4 failed`
- exactly the four new `PrismalOs4OriginalEdgeContractTest` methods failed:
  - original OS4 params/bindings absent;
  - background reflection semantics absent;
  - directional soft-light/luminance lift absent;
  - independent Bloom halo still present.
- prior Prismal tests did not add failures.

### Task 1 — parameter surface

One-shot run `34209582619` succeeded:
- exact patch applied;
- focused parameter/binding contract passed;
- production patch committed;
- temporary workflow/script deleted.

Added parameter dimensions:
- `os4EdgeWidthPx`
- `os4ReflectOffsetPx`
- `os4ReflectionStrength`
- `os4ReflectionLighten`
- `os4DirectionalAngleRange`
- `os4DirectionalIntensity`
- `os4DirectionalOppositeIntensity`

The non-zero defaults are implementation defaults, not claimed recovered vendor runtime values.

### RED 2 — corrected legacy edge contracts

The old `PrismalOpticalEdgeShaderTest` still encoded the rejected finite-width white/cool Bloom halo as desired behavior. Those tests were replaced before production implementation with contracts for the extracted model.

API101 CI #4387:
- run `34209793799`
- job `102007816113`
- `24 tests completed, 7 failed`
- all seven failures were the new/corrected original-model edge contracts;
- existing AA, volumetric edge, and unrelated Prismal tests remained green.

### Task 2 — background-driven shader

One-shot run `34210150109` succeeded:
1. exact shader patch application succeeded;
2. focused `PrismalOpticalEdgeShaderTest` + `PrismalOs4OriginalEdgeContractTest` passed;
3. full `./gradlew testDebugUnitTest assembleDebug --stacktrace` passed;
4. production shader patch committed;
5. temporary workflow/script self-deleted.

The implementation:
- keeps the device-proven volumetric thickness displacement;
- constructs the edge pseudo-normal from the centered raw SDF gradient and positive Z;
- samples the same active backdrop texture used by the glass at a normal-directed screen-space offset;
- mixes that reflection with an internal nonlinear edge mask and explicit reflection strength;
- computes independent main/opposite directional responses with angle-range falloff;
- applies light as multiplicative gain to the current backdrop-derived glass color;
- applies additional lift as a function of current luminance;
- removes `os4BloomRing` and `os4BloomColor` entirely;
- does not expand output opacity.

## Diff boundary

Compared with device-proven baseline `a45651a97cb5f13594371e2c0d8c791c89a8a947`, production changes are limited to:

- `prismal/src/main/java/com/hellovoid/prismal/PrismalOpticalEdgeShader.java`
- `prismal/src/main/java/com/hellovoid/prismal/PrismalParams.java`
- `prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java`

`PrismalRenderer` changes only add seven uniform bindings. No local Bloom FBO/texture/program path exists in this branch. No Launcher, PassBlur, OES, EGL, Recents, Workstation, or freshness source changes are part of this port.

## Remaining gates

1. Formal normal-user API101 CI on a descendant of the production commits.
2. Record exact artifact and APK SHA-256.
3. True-device GPU/visual validation on that exact APK.

Device success criteria:
- no black rectangle or component-external opaque region;
- wallpaper/content remains visible through the whole glass;
- edge reflection responds to the underlying background rather than reading as a constant white stroke;
- dark backgrounds receive a stronger soft lift than already-bright backgrounds;
- the useful volumetric edge thickness from the previous device-proven version remains;
- 50/75/100% backdrop capture scale does not materially change edge raster precision;
- no HOME/Recents/Workstation lifecycle regression.
