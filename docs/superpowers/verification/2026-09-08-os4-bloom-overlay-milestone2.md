# OS4 Bloom Overlay Milestone 2 Verification

Status: `TASK4_ONE_SHOT_GREEN_FORMAL_CI_PENDING`

Branch: `port/os4-volumetric-edge-model`
PR: `#139`

## Scope boundary

Milestone 2 changes Prismal optics/rendering only. It does not add or replace Launcher, PassBlur, SurfaceTexture/OES, EGL-context, Recents, or freshness ownership.

Bloom flow:

```text
Prismal main glass raster (logical output)
  -> renderer-local rounded-rect Bloom mask
  -> renderer-local horizontal blur
  -> renderer-local vertical blur
  -> positioned composite into existing Prismal outputFramebuffer
```

The Bloom mask has no backdrop sampler. Scratch targets are sized from glass bounds plus bounded Bloom/blur padding and clamped to the existing logical output dimensions.

## Task evidence

### Task 1 — OS4 parameterization

GREEN.

- RED: CI #4364 — missing OS4 `PrismalParams` fields.
- GREEN: CI #4370 — full `testDebugUnitTest assembleDebug` succeeded.
- Runtime bindings include `u_os4EdgeWidthPx`, `u_os4ThicknessPx`, `u_os4ReflectOffsetPx`, `u_os4BloomWidthPx`, `u_os4BloomIntensity`, and `u_os4BloomInMainPass`.
- Zero-valued geometric width controls preserve the Milestone 1 adaptive defaults.

### Task 2 — local Bloom target planner

GREEN.

- RED: CI #4371 — `PrismalBloomTarget` absent.
- GREEN: CI #4372 — full workflow succeeded.
- Planner padding is Bloom half-width + 3 sigma blur spread + 2 px guard, with finite sanitization and output-dimension clamp.

### Task 3 — dedicated Bloom shader sources

GREEN.

- RED: CI #4373 — `PrismalBloomShaderSources` absent.
- Intermediate CI #4374 exposed one over-specific source-string test; implementation already passed Java compilation and the remaining Prismal tests.
- Test fixed to verify semantic per-corner radii flow instead of literal `u_cornerRadii.x/y/z/w` spelling.
- GREEN: CI #4375 succeeded.
- Bloom mask is procedural rounded-rect/SDF only and contains no backdrop sampler.

### Task 4 — renderer-local FBO / blur / composite

One-shot production verification GREEN; formal branch-head CI pending.

RED evidence:

- CI #4376: 39 Prismal tests, exactly the six new renderer-local Bloom contracts failed; prior tests remained green.
- CI #4377: 40 Prismal tests, exactly seven Milestone 2 RED contracts failed: the six renderer contracts plus the new premultiplied-to-straight composite contract; the prior 33 Prismal tests remained green.

Implementation commit:

```text
8ab3f78b1528463316f908a0f268b6063a2c8895
feat: render OS4 bloom in local FBO
```

One-shot verification run:

```text
run 34207085840 — Milestone2 task4 one-shot — SUCCESS
```

The run successfully completed all of:

1. exact Task 4 patch application;
2. `./gradlew testDebugUnitTest assembleDebug --stacktrace`;
3. production commit;
4. deletion of the temporary patch script/workflow.

Net production change in the one-shot commit is limited to:

- `prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java`
- `prismal/src/main/java/com/hellovoid/prismal/PrismalBloomShaderSources.java`

The temporary one-shot workflow/script are absent from the resulting branch head.

## Alpha convention

`PrismalBloomShaderSources.MASK_FRAGMENT` emits premultiplied color so H/V blur interpolates RGB and alpha without transparent-color fringes.

LiquidDock's existing consumer composites the final Prismal texture with straight-alpha source-over (`SRC_ALPHA`, `ONE_MINUS_SRC_ALPHA`). Therefore the local Bloom composite samples the blurred premultiplied texture and converts it back to straight RGB before writing into the existing Prismal output texture:

```glsl
vec4 bloom = texture2D(uTexture, vUv);
vec3 straightRgb = bloom.a > 1e-5 ? bloom.rgb / bloom.a : vec3(0.0);
gl_FragColor = vec4(straightRgb, bloom.a);
```

This keeps the new Bloom path compatible with the existing Prismal-output/TextureView composition convention rather than introducing a second alpha convention.

## Compatibility details

- `os4BloomEnabled=true`: compatibility Bloom is disabled in the main glass pass and the local Bloom path runs.
- `os4BloomEnabled=false`: local Bloom is bypassed and the existing analytic in-main Bloom fallback remains available.
- `os4BloomWidthPx=0`: not disabled; Java resolves the same Milestone 1 adaptive default `clamp(minGlassDim * 0.090, 9, 28)` before planning the local target.
- Bloom scratch allocations are renderer-owned and grow/reuse across sequential glass nodes; they are not full-output Bloom targets unless the bounded planner itself is clamped by an unusually large glass/halo.
- `releaseTargets()` releases Bloom textures/FBOs; `close()` also deletes Bloom programs.

## Remaining gates

1. Formal `API101 migration build` must pass on a normal user-authored descendant of production commit `8ab3f78b`.
2. Record final debug artifact and exact APK SHA-256 from that run.
3. True-device visual/GPU gate on that exact APK. CI does not prove target-GPU GLSL compilation or visual quality.

Device checks should specifically cover:

- no shader/program creation failure on the target GPU;
- no rectangular clipping around the local Bloom target;
- no dark fringe from double alpha multiplication;
- continuous corner Bloom around all four radii;
- preserved volumetric edge thickness from Milestone 1;
- visually useful Bloom softness/intensity;
- normal HOME and relevant Workstation/Recents transitions remain behaviorally unchanged.

PR #139 remains Draft until the device gate is completed.
