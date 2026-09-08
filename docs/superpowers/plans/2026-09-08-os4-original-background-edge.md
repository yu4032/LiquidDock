# OS4 Original Background Edge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the failed standalone Bloom overlay with the extracted OS4-style background-reflection and directional soft-light edge model while retaining the device-proven volumetric edge and logical-resolution raster path.

**Architecture:** Keep all visible edge optics in the existing Prismal glass fragment pass. The edge uses the rounded-rect SDF, centered finite-difference normal, current backdrop textures, a nonlinear reflection mask, directional main/opposite soft light, and luminance-aware color lift. No local Bloom FBO, blur, or independent white halo is used.

**Tech Stack:** Java 17, Android OpenGL ES 2.0, GLSL ES 1.00 + `GL_OES_standard_derivatives`, JUnit 4, Gradle 9.6.1, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-08-os4-original-background-edge-design.md`

## Global Constraints

- No ScreenCapture / PixelCopy / bitmap fallback / CPU readback.
- No new SurfaceTexture, EGL context, PassBlur producer, freshness authority, or Launcher lifecycle owner.
- No Recents/Workstation authority changes.
- Preserve the physical-backdrop/logical-output split proven on device.
- Do not expand glass output alpha for the edge highlight.
- Do not reintroduce renderer-local Bloom mask/H/V/composite targets.
- Vendor runtime parameter values that were not recovered must be labeled as implementation defaults.

---

### Task 1: Formalize original OS4 edge controls

**Files:**
- Modify: `prismal/src/main/java/com/hellovoid/prismal/PrismalParams.java`
- Modify: `prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java`
- Test: `prismal/src/test/java/com/hellovoid/prismal/PrismalOs4OriginalEdgeContractTest.java`

**Interfaces:**
- Produces immutable `PrismalParams` fields and builder defaults:
  - `os4EdgeWidthPx`
  - `os4ReflectOffsetPx`
  - `os4ReflectionStrength`
  - `os4ReflectionLighten`
  - `os4DirectionalAngleRange`
  - `os4DirectionalIntensity`
  - `os4DirectionalOppositeIntensity`
- Produces matching GLSL uniforms bound by `PrismalRenderer.renderGlassNode(...)`.

- [ ] **Step 1: Write RED contract**

Create `PrismalOs4OriginalEdgeContractTest` that reflects over `PrismalParams` and its builder, asserts all seven fields exist, and reads `PrismalRenderer.java` to require exact uniform bindings:

```java
assertTrue(renderer.contains("uniform1f(\"u_os4EdgeWidthPx\", p.os4EdgeWidthPx)"));
assertTrue(renderer.contains("uniform1f(\"u_os4ReflectOffsetPx\", p.os4ReflectOffsetPx)"));
assertTrue(renderer.contains("uniform1f(\"u_os4ReflectionStrength\", p.os4ReflectionStrength)"));
assertTrue(renderer.contains("uniform1f(\"u_os4ReflectionLighten\", p.os4ReflectionLighten)"));
assertTrue(renderer.contains("uniform1f(\"u_os4DirectionalAngleRange\", p.os4DirectionalAngleRange)"));
assertTrue(renderer.contains("uniform1f(\"u_os4DirectionalIntensity\", p.os4DirectionalIntensity)"));
assertTrue(renderer.contains("uniform1f(\"u_os4DirectionalOppositeIntensity\", p.os4DirectionalOppositeIntensity)"));
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :prismal:testDebugUnitTest --tests 'com.hellovoid.prismal.PrismalOs4OriginalEdgeContractTest' --stacktrace
```

Expected: test compile/runtime failures only because the new parameters/bindings are absent.

- [ ] **Step 3: Implement minimal parameter surface**

Add immutable fields, constructor assignments, builder fields, and renderer bindings with defaults:

```java
public float os4EdgeWidthPx = 0f;
public float os4ReflectOffsetPx = 0f;
public float os4ReflectionStrength = 0.28f;
public float os4ReflectionLighten = 0.16f;
public float os4DirectionalAngleRange = 0.52f;
public float os4DirectionalIntensity = 0.42f;
public float os4DirectionalOppositeIntensity = 0.14f;
```

- [ ] **Step 4: Run focused GREEN**

Use the Step 2 command; expected PASS.

- [ ] **Step 5: Commit**

```text
feat: expose original OS4 edge controls
```

---

### Task 2: Replace independent Bloom with background-driven edge optics

**Files:**
- Modify: `prismal/src/main/java/com/hellovoid/prismal/PrismalOpticalEdgeShader.java`
- Modify: `prismal/src/test/java/com/hellovoid/prismal/PrismalOpticalEdgeShaderTest.java`
- Test: `prismal/src/test/java/com/hellovoid/prismal/PrismalOs4OriginalEdgeContractTest.java`

**Interfaces:**
- Consumes the seven uniforms from Task 1 plus existing `u_backgroundTexture`, `u_blurredTexture`, `u_useBlurredTexture`, `u_resolution`, and `u_lightDir`.
- Removes the Milestone 1 independent `os4BloomRing/os4BloomColor` contribution.
- Preserves existing volumetric thickness displacement and logical-pixel SDF normal stabilization.

- [ ] **Step 1: Extend RED shader contracts**

Require the patched shader source to contain all of:

```text
u_os4EdgeWidthPx
u_os4ReflectOffsetPx
u_os4ReflectionStrength
u_os4ReflectionLighten
u_os4DirectionalAngleRange
u_os4DirectionalIntensity
u_os4DirectionalOppositeIntensity
os4ReflectUv
texture2D(u_blurredTexture, os4ReflectUv)
texture2D(u_backgroundTexture, os4ReflectUv)
os4DirectionalMain
os4DirectionalOpposite
os4DirectionalGain
os4DarkResponse
```

Require it to reject the old independent-halo path:

```java
assertFalse(shader.contains("os4BloomRing"));
assertFalse(shader.contains("os4BloomColor"));
```

Also require the final opacity expression to remain shape-derived and not reference directional-light or reflection strength values.

- [ ] **Step 2: Run RED**

```bash
./gradlew :prismal:testDebugUnitTest --tests 'com.hellovoid.prismal.PrismalOpticalEdgeShaderTest' --tests 'com.hellovoid.prismal.PrismalOs4OriginalEdgeContractTest' --stacktrace
```

Expected: failures for missing original-model uniforms/light-gain semantics and for the still-present independent Bloom ring.

- [ ] **Step 3: Add OS4 uniform block and edge-depth resolution**

Insert uniforms after fragment precision. Resolve automatic geometry values using the device-proven Milestone 1 relationship:

```glsl
float os4EdgePx = u_os4EdgeWidthPx > 0.0
        ? u_os4EdgeWidthPx : clamp(minDim * 0.060, 6.0, 18.0);
float os4ReflectOffsetPx = u_os4ReflectOffsetPx > 0.0
        ? u_os4ReflectOffsetPx
        : clamp(max(u_glassThickness, os4EdgePx + 6.0) * 0.38, 4.0, 14.0);
float os4EdgeT = clamp(edgeDist / max(os4EdgePx, 1.0), 0.0, 1.0);
float os4EdgeRemain = 1.0 - os4EdgeT;
```

- [ ] **Step 4: Implement backdrop reflection mix**

Use the stabilized SDF normal already produced by the Milestone 1 shader. Build a pseudo Z component from edge depth and calculate the screen-space offset:

```glsl
float os4NormalZ = sqrt(max(0.08, 1.0 - clamp(dot(opticalEdgeNormal, opticalEdgeNormal) * 0.35, 0.0, 0.92)));
vec2 os4ReflectUvOffset = opticalEdgeNormal
        * (2.0 * os4NormalZ)
        * os4ReflectOffsetPx
        * os4EdgeRemain
        / u_resolution;
vec2 os4ReflectUv = clamp(uvCenter + os4ReflectUvOffset, vec2(0.0), vec2(1.0));
```

Sample the same active backdrop source and mix with a `sqrt + smootherstep` edge mask derived from `os4EdgeT` and `u_os4ReflectionStrength`.

- [ ] **Step 5: Implement directional soft light**

Construct a normalized pseudo-3D normal and light vector. Compute main and opposite terms independently, map alignment to an angle-like falloff controlled by `u_os4DirectionalAngleRange`, and multiply both by the internal edge mask. The code must name the final terms `os4DirectionalMain` and `os4DirectionalOpposite` for contract visibility.

- [ ] **Step 6: Implement background-dependent gain/lift**

Use:

```glsl
float os4SoftLight = (os4DirectionalMain + os4DirectionalOpposite) * os4EdgeMask;
float os4DirectionalGain = 1.0 + smoothstep(0.0, 1.0, os4SoftLight);
color *= os4DirectionalGain;
float os4Luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
float os4DarkResponse = 1.0 - smoothstep(0.35, 0.92, os4Luma);
color += vec3(u_os4ReflectionLighten * os4DarkResponse
        * max(os4DirectionalGain - 1.0, 0.0));
```

Remove the old `os4BloomRing/os4BloomColor` block completely.

- [ ] **Step 7: Run focused GREEN**

Use Step 2 command; expected PASS.

- [ ] **Step 8: Commit**

```text
feat: port background-driven OS4 edge light
```

---

### Task 3: Full regression and CI artifact

**Files:**
- Create/modify: `docs/superpowers/verification/2026-09-08-os4-original-background-edge.md`
- Update the new PR body with exact evidence.

**Interfaces:**
- No new runtime interface.

- [ ] **Step 1: Run full suite**

```bash
./gradlew testDebugUnitTest assembleDebug --stacktrace
```

Expected: all Prismal and app tests PASS; debug APK assembles.

- [ ] **Step 2: Verify exact branch head through `API101 migration build`**

Record workflow run ID, job ID, duration, actionable task counts, and artifact IDs.

- [ ] **Step 3: Compute artifact hashes**

Download `LiquidDock-api101-debug`, record artifact ZIP SHA-256 and extracted APK SHA-256.

- [ ] **Step 4: Keep PR Draft for true-device gate**

The device gate must verify:

- no black rectangle or component-external opaque region;
- background remains visible through the entire glass;
- edge reflection visibly follows underlying wallpaper/content rather than appearing as a constant white stroke;
- dark backgrounds receive more soft lift than bright backgrounds;
- edge thickness remains comparable to the device-proven Milestone 1 result;
- 50/75/100% capture scale no longer changes edge raster precision materially;
- no HOME/Recents/Workstation lifecycle regression.