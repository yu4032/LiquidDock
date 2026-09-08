# OS4 Bloom Overlay Milestone 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parameterize the device-proven OS4 volumetric edge model and move Bloom diffusion into a renderer-owned local high-resolution FBO/blur/composite path.

**Architecture:** Keep volumetric refraction/reflection in the existing Prismal glass pass. Add explicit OS4 parameters to `PrismalParams`, a pure local-target sizing helper, and dedicated lightweight Bloom mask/composite shaders; `PrismalRenderer` reuses one local mask/H/V target set per glass node and composites the blurred result back into its existing transparent logical output texture.

**Tech Stack:** Java 17, Android OpenGL ES 2.0, GLSL ES 1.00, JUnit 4, Gradle 9.6.1, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-08-os4-bloom-overlay-design.md`

## Global Constraints

- No ScreenCapture, PixelCopy, Bitmap fallback, or CPU readback.
- No new SurfaceTexture, EGL context, producer authority, Launcher lifecycle owner, or freshness state machine.
- Existing physical-backdrop/logical-output split remains unchanged.
- Checked-in upstream Prismal GLSL remains the provenance baseline.
- Bloom scratch targets are renderer-owned and local to glass bounds plus bounded padding.
- Existing backdrop blur remains 0.5× physical backdrop density.
- No settings UI and no vendor/native Bloom cache in this milestone.

---

### Task 1: Formal OS4 parameters and glass-pass uniforms

**Files:**
- Modify: `prismal/src/main/java/com/hellovoid/prismal/PrismalParams.java`
- Modify: `prismal/src/main/java/com/hellovoid/prismal/PrismalOpticalEdgeShader.java`
- Modify: `prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java`
- Test: `prismal/src/test/java/com/hellovoid/prismal/PrismalOpticalEdgeShaderTest.java`
- Create: `prismal/src/test/java/com/hellovoid/prismal/PrismalOs4ParamsContractTest.java`

**Interfaces:**
- Produces `PrismalParams` fields: `os4EdgeWidthPx`, `os4ThicknessPx`, `os4ReflectOffsetPx`, `os4BloomWidthPx`, `os4BloomIntensity`, `os4BloomBlurRadiusPx`, `os4BloomEnabled`.
- Produces shader uniforms: `u_os4EdgeWidthPx`, `u_os4ThicknessPx`, `u_os4ReflectOffsetPx`, `u_os4BloomWidthPx`, `u_os4BloomIntensity`, `u_os4BloomInMainPass`.

- [ ] **Step 1: Write RED parameter/shader contracts**

Add tests that require the seven `PrismalParams` fields and builder fields, require OS4 shader uniforms, reject direct hardcoded declarations such as `float os4EdgePx = clamp(...)`, and require runtime renderer bindings including `u_os4BloomInMainPass`.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```bash
./gradlew :prismal:testDebugUnitTest --tests 'com.hellovoid.prismal.PrismalOs4ParamsContractTest' --tests 'com.hellovoid.prismal.PrismalOpticalEdgeShaderTest' --stacktrace
```

Expected: failures only for missing OS4 public parameters/uniform consumption/runtime binding.

- [ ] **Step 3: Implement minimal parameterization**

Use finite builder defaults with `0f` meaning automatic size for the pixel-width parameters so the Milestone 1 size-dependent visual remains the default:

```java
public float os4EdgeWidthPx = 0f;
public float os4ThicknessPx = 0f;
public float os4ReflectOffsetPx = 0f;
public float os4BloomWidthPx = 0f;
public float os4BloomIntensity = 0.62f;
public float os4BloomBlurRadiusPx = 5f;
public boolean os4BloomEnabled = true;
```

In GLSL resolve auto values explicitly:

```glsl
float os4EdgePx = u_os4EdgeWidthPx > 0.0
        ? u_os4EdgeWidthPx : clamp(minDim * 0.060, 6.0, 18.0);
float os4ThicknessPx = u_os4ThicknessPx > 0.0
        ? u_os4ThicknessPx : max(u_glassThickness, os4EdgePx + 6.0);
float os4ReflectOffsetPx = u_os4ReflectOffsetPx > 0.0
        ? u_os4ReflectOffsetPx : clamp(os4ThicknessPx * 0.38, 4.0, 14.0);
float os4BloomEdgePx = u_os4BloomWidthPx > 0.0
        ? u_os4BloomWidthPx : clamp(minDim * 0.090, 9.0, 28.0);
```

Gate the compatibility in-main Bloom contribution with `u_os4BloomInMainPass * u_os4BloomIntensity`. Runtime binds `u_os4BloomInMainPass=0` when local Bloom is enabled, else `1` so the old analytic fallback remains available.

- [ ] **Step 4: Run focused tests and verify GREEN**

Use the Step 2 command; expected PASS.

- [ ] **Step 5: Commit**

Commit message:

```text
feat: parameterize OS4 prismal edge model
```

---

### Task 2: Pure local Bloom target sizing

**Files:**
- Create: `prismal/src/main/java/com/hellovoid/prismal/PrismalBloomTarget.java`
- Create: `prismal/src/test/java/com/hellovoid/prismal/PrismalBloomTargetTest.java`

**Interfaces:**
- Produces `static PrismalBloomTarget.Spec plan(float glassWidth, float glassHeight, float bloomWidthPx, float blurRadiusPx, int outputWidth, int outputHeight)`.
- `Spec` exposes immutable `width`, `height`, `paddingPx`.

- [ ] **Step 1: Write RED sizing tests**

Require:
- target width/height derive from glass dimensions plus two-sided padding;
- padding includes Bloom half-width plus at least a 3-sigma blur spread;
- dimensions are at least 1 and no larger than logical output;
- non-finite/negative width controls sanitize to zero/finite fallback;
- larger Bloom/blur values monotonically increase target size until clamped.

- [ ] **Step 2: Run test and verify RED**

```bash
./gradlew :prismal:testDebugUnitTest --tests 'com.hellovoid.prismal.PrismalBloomTargetTest' --stacktrace
```

Expected: compile failure because `PrismalBloomTarget` does not exist.

- [ ] **Step 3: Implement pure planner**

Use:

```java
float safeBloom = finitePositive(bloomWidthPx);
float safeBlur = finitePositive(blurRadiusPx);
float padding = (safeBloom * 0.5f) + safeBlur * 3f + 2f;
int width = clampInt((int) Math.ceil(Math.max(1f, glassWidth) + 2f * padding), 1, outputWidth);
int height = clampInt((int) Math.ceil(Math.max(1f, glassHeight) + 2f * padding), 1, outputHeight);
```

If `bloomWidthPx <= 0`, the renderer resolves the automatic Bloom width before calling this planner; the planner itself receives an actual width.

- [ ] **Step 4: Run test and verify GREEN**

Use Step 2 command; expected PASS.

- [ ] **Step 5: Commit**

```text
feat: add local bloom target planner
```

---

### Task 3: Dedicated Bloom GLSL sources

**Files:**
- Create: `prismal/src/main/java/com/hellovoid/prismal/PrismalBloomShaderSources.java`
- Create: `prismal/src/test/java/com/hellovoid/prismal/PrismalBloomShaderSourcesTest.java`

**Interfaces:**
- Produces package-private constants `MASK_VERTEX`, `MASK_FRAGMENT`, `COMPOSITE_VERTEX`, `COMPOSITE_FRAGMENT`.
- Mask uniforms: `u_targetSize`, `u_glassSize`, `u_cornerRadii`, `u_bloomWidthPx`, `u_bloomIntensity`, `u_lightDir`.
- Composite uniforms: `uTexture`, `u_resolution`, `u_centerPx`, `u_targetSize`.

- [ ] **Step 1: Write RED shader-source contracts**

Require the mask shader to contain rounded-rect SDF, expanded outer coverage, contracted inner coverage, explicit subtraction/`outer - inner`, finite-difference SDF normal, main/opposite/ambient directional terms, and no sampler/backdrop dependency. Require composite shader to position the local target in logical output coordinates.

- [ ] **Step 2: Run test and verify RED**

```bash
./gradlew :prismal:testDebugUnitTest --tests 'com.hellovoid.prismal.PrismalBloomShaderSourcesTest' --stacktrace
```

Expected: compile failure because the source class does not exist.

- [ ] **Step 3: Implement minimal ES2 shaders**

The mask shader renders a full local quad, converts local UV into centered pixel coordinates, evaluates the same per-corner rounded rectangle convention as Prismal, builds:

```glsl
float outer = 1.0 - smoothstep(-aa, aa, sd - halfWidth);
float inner = 1.0 - smoothstep(-aa, aa, sd + halfWidth);
float ring = clamp(outer - inner, 0.0, 1.0);
```

Compute finite-difference SDF normal at ±1 local pixel and directional response:

```glsl
float facing = dot(edgeNormal, normalize(u_lightDir + vec2(1e-5)));
float mainLight = pow(max(facing, 0.0), 1.45) * 0.95;
float oppositeLight = pow(max(-facing, 0.0), 1.10) * 0.34;
float ambientLight = 0.16;
```

Output premultiplied Bloom color and alpha.

- [ ] **Step 4: Run test and verify GREEN**

Use Step 2 command; expected PASS.

- [ ] **Step 5: Commit**

```text
feat: add local OS4 bloom shaders
```

---

### Task 4: Renderer-owned local Bloom FBO, blur, and positioned composite

**Files:**
- Modify: `prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java`
- Modify: `prismal/src/test/java/com/hellovoid/prismal/PrismalBatchRendererContractTest.java`
- Create: `prismal/src/test/java/com/hellovoid/prismal/PrismalBloomRendererContractTest.java`

**Interfaces:**
- Consumes `PrismalBloomTarget.plan(...)` and `PrismalBloomShaderSources`.
- Adds renderer-owned programs: `bloomMaskProgram`, `bloomCompositeProgram`.
- Adds reusable scratch textures/FBOs: mask, blur-H, blur-V.
- Adds private `renderBloomOverlay(PrismalGeometry g, PrismalParams p, float opacity)`.

- [ ] **Step 1: Write RED renderer contracts**

Require:
- local Bloom programs and three local targets exist;
- target sizing calls `PrismalBloomTarget.plan` using actual resolved Bloom width and blur radius;
- no second full-logical-output Bloom target allocation;
- local mask draw happens after main glass draw;
- H/V blur uses explicit local width/height;
- composite binds `outputFramebuffer`, restores logical output viewport, positions with `g.centerX` and `height - g.centerY`;
- `p.os4BloomEnabled` bypasses the whole local path;
- `releaseTargets()` deletes all Bloom FBO/textures;
- `close()` deletes Bloom programs;
- existing backdrop blur remains `BLUR_FBO_SCALE = 0.5f`.

- [ ] **Step 2: Run RED renderer tests**

```bash
./gradlew :prismal:testDebugUnitTest --tests 'com.hellovoid.prismal.PrismalBloomRendererContractTest' --tests 'com.hellovoid.prismal.PrismalBatchRendererContractTest' --stacktrace
```

Expected: failures for missing local Bloom renderer path only.

- [ ] **Step 3: Generalize blur helper without changing backdrop semantics**

Change the internal blur pass signature to:

```java
private void renderBlurPass(int program, int inputTexture, int framebuffer,
                            int targetWidth, int targetHeight, float sigma)
```

Backdrop calls pass existing `blurWidth/blurHeight`; Bloom calls pass its local target dimensions.

- [ ] **Step 4: Implement Bloom target lifecycle**

Resolve actual automatic widths in Java using the same Milestone 1 formulas from the glass shader based on `min(g.glassWidth, g.glassHeight)`. Call `PrismalBloomTarget.plan`, resize local scratch targets only when required dimensions change, and reuse them sequentially across nodes.

- [ ] **Step 5: Implement mask → blur H/V → composite**

Render the mask into the local mask FBO at full local resolution, blur H/V at full local resolution using `sigma=max(os4BloomBlurRadiusPx, 0.5f)`, then bind `outputFramebuffer` and composite the vertical-blur texture with source-over premultiplied blending.

Composite center must use GL bottom-left coordinates:

```java
float centerGlY = height - g.centerY;
```

Target dimensions include padding, so the positioned quad naturally covers the halo region without a full-screen target.

- [ ] **Step 6: Run focused renderer tests and verify GREEN**

Use Step 2 command; expected PASS.

- [ ] **Step 7: Commit**

```text
feat: render OS4 bloom in local prismal target
```

---

### Task 5: Full regression, artifact, and device gate

**Files:**
- Modify: `docs/superpowers/specs/2026-09-08-os4-bloom-overlay-design.md` only if implementation evidence requires factual status notes.
- Modify PR #139 body with exact CI/artifact/device-gate status.

**Interfaces:**
- No new production interface.

- [ ] **Step 1: Run full local/CI-equivalent suite**

```bash
./gradlew testDebugUnitTest assembleDebug --stacktrace
```

Expected: all app + Prismal tests PASS and APK assembles.

- [ ] **Step 2: Verify GitHub Actions on exact production head**

Confirm `API101 migration build` success and record run ID/job ID/build duration/actionable task counts.

- [ ] **Step 3: Record artifact hashes**

Download `LiquidDock-api101-debug`, compute ZIP and extracted APK SHA-256, and record them in PR #139.

- [ ] **Step 4: Keep PR Draft for device gate**

Do not merge or mark Ready. Device test must compare the exact APK at 50%/75%/100% backdrop density and check for clipping rectangles, corner discontinuities, edge softness, regressions, and preservation of the volumetric edge.
