# Continuous Glass Corners Implementation Plan

> Execution: inline, following the user's instruction to start. Use executing-plans and TDD.

**Goal:** Apply a consistent continuous corner contour to every Prismal glass surface.
**Architecture:** Shared GLSL transformation in the existing optical shader adapter;
shared CPU contour used by the glass Dock's shadow and foreground border.
**Tech Stack:** Java, GLSL ES 1.00, Android Path, JUnit, Gradle.
**Spec:** docs/superpowers/specs/2026-10-25-continuous-glass-corners-design.md

## Global Constraints
- No new dependencies or settings. Keep component sizes and radii.
- Preserve native-blur Dock behavior. Attribute upstream MIT code.
- Do not alter unrelated work in the original checkout.

## Review Focus
- Zero radii stay rectangular and finite.
- Full-radius squares stay circular; long Dock flanks keep smooth shoulders.
- Unequal corner radii select the same corner for coverage and refraction.
- CPU border/shadow contour agrees with the shader.
- Existing shader transformations and fallback programs still compile.

### Task 1: Continuous geometry and shader
- [x] Write failing geometry and shader integration tests; run them.
- [x] Implement shared continuous geometry and GLSL transformation through PrismalOpticalEdgeShader.
- [x] Run Prismal tests and compile/render GLSL with software GLES if available.

### Task 2: Dock alignment and verification
- [x] Match glass-host outline and optional stroke to the shared geometry.
- [x] Add upstream attribution and produce a contour comparison image.
- [x] Run full unit suite, assembleDebug, diff review and report device-verification limits.

## Execution notes
- User authorized inline implementation with “开始”; retain the named local branch for device testing.
- Coverage and lens distance use each corner's radius; body depth retains a shared radius
  to avoid a center-axis brightness seam on asymmetric surfaces.
- Legacy smoothing now controls height-normal sample spacing rather than changing the silhouette.
- Preserve existing Dock border style controls, with wording explaining the fixed glass silhouette.
- Independent review identified the asymmetric-depth seam and misleading native-only wording;
  both were corrected. GPU test reproduced the seam before the fix and passed afterward.
