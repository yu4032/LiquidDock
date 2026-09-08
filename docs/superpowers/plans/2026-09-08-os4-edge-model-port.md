# OS4 Edge Model Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the OS4 volumetric glass-edge model into LiquidDock Prismal so rounded glass edges have real optical thickness and a finite-width Bloom ring instead of a thin highlight line.

**Architecture:** Build on the proven raster-domain split from PR #138 head `306115a65413acef2a2986488aaf8a88ccca4f1b`. Keep source normalization/blur downsampled and the final glass raster in logical resolution. Add the OS4 model in two bounded layers: (1) volumetric edge refraction/reflection inside the existing Prismal glass fragment; (2) a finite-width SDF Bloom ring composited inside the same logical-resolution output pass for milestone 1. Do not add a separate Bloom FBO/cache until the visual model is proven on-device.

**Tech Stack:** Android Java, OpenGL ES 3 fragment shaders patched at runtime by Prismal, JUnit contract tests, GitHub Actions `./gradlew testDebugUnitTest assembleDebug --stacktrace`.

**Spec:** Source-derived behavior is constrained by the supplied OS4 extraction `glass_original_extracted.tar.gz`, especially `mi_glass_v13_f16.gles300.frag`, `mi_dynamic_bloom_stroke.gles300.frag`, `mi_bloom_stroke.gles300.frag`, and recovered Bloom parameter setter symbols.

## Global Constraints

- Preserve PassBlur -> Surface/SurfaceTexture/OES zero-copy source ownership.
- Preserve existing Launcher scene/freshness authority and Recents recovery behavior.
- Preserve the iteration-2 render-domain split: backdrop/blur may be 50/75/100%; procedural glass output stays logical resolution.
- Do not add ScreenCapture, PixelCopy, bitmap fallback, or a second producer authority.
- Milestone 1 does not add a new Bloom FBO/cache; it proves the optical model first.
- Do not silently reuse `u_highlightWidth` as all OS4 concepts; edge thickness and Bloom-ring width are separate model quantities.
- Keep upstream checked-in Prismal GLSL as provenance baseline; changes go through the existing runtime shader-patch layer unless renderer state is strictly required.

---

### Task 1: Freeze OS4 Edge-Model Contracts

**Files:**
- Modify: `prismal/src/test/java/com/hellovoid/prismal/PrismalOpticalEdgeShaderTest.java`

**Interfaces:**
- Consumes: current `PrismalOpticalEdgeShader.apply(String)` runtime patch.
- Produces: contract requirements for `os4ThicknessPx`, `os4ReflectOffsetPx`, finite-width outer-minus-inner Bloom ring, central-difference edge normal reuse, and directional main/opposite edge lighting.

- [ ] **Step 1: Write failing contracts**
  - Assert the patched shader contains separate logical-pixel thickness and reflection-offset quantities.
  - Assert refraction displacement scales with thickness across the edge field rather than only highlight width.
  - Assert reflection samples use a normal-directed offset that fades toward the inner edge.
  - Assert Bloom coverage is defined as a finite-width ring using separate outer and inner boundaries.
  - Assert Bloom lighting supports main and opposite edge-facing response.

- [ ] **Step 2: Run CI RED**

Run: `./gradlew :prismal:testDebugUnitTest --stacktrace`

Expected: only the newly added OS4-model contracts fail; existing Prismal contracts remain green.

---

### Task 2: Port Volumetric Edge Refraction and Reflection

**Files:**
- Modify: `prismal/src/main/java/com/hellovoid/prismal/PrismalOpticalEdgeShader.java`
- Test: `prismal/src/test/java/com/hellovoid/prismal/PrismalOpticalEdgeShaderTest.java`

**Interfaces:**
- Consumes: existing SDF distance `distMask`, stabilized edge normal, backdrop UV/sample path, logical pixel coordinates.
- Produces: `os4ThicknessPx`, `os4ReflectOffsetPx`, `edgeDepth`, thickness-driven refracted UV, normal-directed reflected UV.

- [ ] **Step 1: Add model constants derived from existing user-visible width**
  - Derive bounded logical-pixel defaults locally in shader code so milestone 1 introduces no persisted setting.
  - Keep the quantities distinct from the existing highlight-width control.

- [ ] **Step 2: Add OS4-style edge depth**
  - Convert signed distance near the glass boundary into an inside-edge depth ratio.
  - Preserve full interior behavior outside the finite edge region.

- [ ] **Step 3: Add thickness-driven refraction displacement**
  - Use the stabilized SDF normal and current refraction direction.
  - Scale displacement approximately like OS4: interpolate between `(thickness-edgeWidth)*2` and `thickness*2` over edge depth.

- [ ] **Step 4: Add normal-directed reflection offset**
  - Offset the reflected/backdrop sample by edge normal multiplied by `os4ReflectOffsetPx` and an inner-edge fade.
  - Blend it through existing reflection strength rather than replacing base glass color.

- [ ] **Step 5: Run Prismal unit tests**

Run: `./gradlew :prismal:testDebugUnitTest --stacktrace`

Expected: Task-2 contracts pass; no existing optical-edge contract regresses.

---

### Task 3: Port Finite-Width Dynamic Bloom Ring

**Files:**
- Modify: `prismal/src/main/java/com/hellovoid/prismal/PrismalOpticalEdgeShader.java`
- Test: `prismal/src/test/java/com/hellovoid/prismal/PrismalOpticalEdgeShaderTest.java`

**Interfaces:**
- Consumes: signed SDF distance, central-difference normal, logical raster footprint.
- Produces: `os4BloomRing`, main/opposite directional light response, ambient floor, finite-width thick edge contribution.

- [ ] **Step 1: Implement outer-minus-inner ring coverage**
  - Define independent outer and inner SDF transitions in logical pixels.
  - Compute `ring = outerCoverage * (1 - innerCoverage)`.
  - Reuse derivative/physical-footprint AA only for transition smoothness, not for ring width.

- [ ] **Step 2: Implement OS4-inspired edge curve**
  - Reuse the already proven 0.85 saturation threshold / sqrt shoulder / smootherstep / power shaping for the ring cross-section.

- [ ] **Step 3: Add directional main/opposite light**
  - Compute edge-facing light from stabilized normal.
  - Keep main and opposite intensities separate in shader-local milestone constants.
  - Include a small ambient edge floor so unlit sides still read as glass volume rather than disappearing.

- [ ] **Step 4: Composite Bloom ring after base optical glass**
  - Add the ring as a premultiplied-style brightening contribution in the existing final logical-resolution glass fragment.
  - Do not alter silhouette alpha ownership or make the ring expand the actual hit/clip geometry.

- [ ] **Step 5: Run Prismal unit tests**

Run: `./gradlew :prismal:testDebugUnitTest --stacktrace`

Expected: all OS4 edge-model contracts and existing tests pass.

---

### Task 4: Full CI and Device Build

**Files:**
- Update PR description only after CI evidence exists.

**Interfaces:**
- Consumes: Tasks 1-3 implementation.
- Produces: installable debug APK and exact hashes for device A/B.

- [ ] **Step 1: Run full CI**

Run: `./gradlew testDebugUnitTest assembleDebug --stacktrace`

Expected: full test suite and `assembleDebug` pass.

- [ ] **Step 2: Verify net diff**
  - Confirm no PassBlur/OES/Launcher freshness/Recents ownership file changed.
  - Confirm milestone 1 changes are limited to Prismal shader runtime patch/tests plus this plan.

- [ ] **Step 3: Publish CI artifact metadata**
  - Record feature head, workflow run, artifact ID, ZIP SHA-256, APK SHA-256.
  - Keep PR Draft with `DEVICE VISUAL A/B PENDING`.

- [ ] **Step 4: Device acceptance target**
  - Rounded corners must show a visibly thicker glass edge, not merely a wider white line.
  - The edge should retain detail at 50/75/100% backdrop quality.
  - Interior refraction near the boundary should visibly bend/shift more than the center.
  - Main/opposite lighting should produce asymmetric edge volume without black seams or alpha halos.
