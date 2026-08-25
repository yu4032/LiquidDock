# Glass Edge Stroke Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add independently configurable Fill-Diff and standard strokes to every Prismal glass component.

**Architecture:** Store glass-stroke settings beside glass optics, convert them into portable `PrismalParams`, and upload them as uniforms in the shared renderer. A shader transformer injects one SDF-based stroke calculation into both the shared Prismal shader and the retained MIUI 307 shader source.

**Tech Stack:** Android Java/Kotlin, Jetpack Compose, OpenGL ES 2.0 GLSL, JUnit 4, Gradle.

**Spec:** `docs/superpowers/specs/2026-08-25-glass-edge-stroke-design.md`

## Global Constraints

- Glass edge stroke settings must not read or mutate Dock background stroke settings.
- Cover Dock background, Dock icons, workspace icons, folders, widgets, large folders, and workstation equivalents.
- Default enabled mode is Fill-Diff with 1dp width and a low-alpha white color.
- Do not add an overlay View, RenderNode, derivative extension, shadow, gradient, per-component switch, or corner-radius setting.
- Keep implementation changes uncommitted until device acceptance.

---

### Task 1: Configuration and settings page

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/config/PresetManager.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java`
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Modify: `src/main/res/values/strings.xml`
- Modify: `src/main/res/values-zh-rCN/strings.xml`
- Test: `src/test/java/com/hellovoid/liquiddock/GlassEdgeStrokeConfigContractTest.java`

**Interfaces:**
- Produces: `ConfigSchema.GlassStroke` keys and immutable `LiquidDockConfig.GlassStroke` values: `enabled`, `fillDiff`, `fillDiffWidth`, `standardWidth`, `red`, `green`, `blue`, `alpha`.

- [ ] Write a failing contract test asserting the eight independent keys, their defaults/ranges, preset entries, runtime reads, `Page.GlassStroke`, `parentPage(Page.GlassStroke) == Page.Stroke`, and the arrow entry in `StrokePage`.
- [ ] Run `./gradlew :testDebugUnitTest --tests com.hellovoid.liquiddock.GlassEdgeStrokeConfigContractTest` and verify it fails because `ConfigSchema.GlassStroke` and `Page.GlassStroke` are absent.
- [ ] Add keys `glass_stroke_enabled`, `glass_stroke_fill_diff`, `glass_stroke_fill_diff_width`, `glass_stroke_standard_width`, `glass_stroke_red`, `glass_stroke_green`, `glass_stroke_blue`, and `glass_stroke_alpha`; use enabled/Fill-Diff defaults, 1dp widths, RGB 255, and alpha 64.
- [ ] Add a “玻璃边缘描边” child page with total switch, mutually exclusive mode controls, active-mode width, RGB sliders, and opacity slider; keep the current Dock controls on the parent page.
- [ ] Re-run the focused test and verify it passes.

### Task 2: Portable parameter chain

**Files:**
- Modify: `prismal/src/main/java/com/hellovoid/prismal/PrismalParams.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PrismalAdapter.java`
- Modify: `prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java`
- Test: `src/test/java/com/hellovoid/liquiddock/GlassEdgeStrokeParameterContractTest.java`

**Interfaces:**
- Consumes: `LiquidDockConfig.GlassStroke`.
- Produces: `PrismalParams.strokeEnabled`, `strokeFillDiff`, `strokeFillDiffWidthPx`, `strokeStandardWidthPx`, `strokeR/G/B/A` and uniforms with matching `u_glassStroke*` names.

- [ ] Write a failing test that creates runtime material parameters and asserts all eight values survive conversion to `PrismalParams`; also assert the renderer uploads every uniform.
- [ ] Run the focused test and verify it fails on the first absent field.
- [ ] Extend the immutable parameter/builder types, convert dp widths using display density in the existing material factory, copy fields in the adapter, and upload booleans as integer uniforms plus widths/colors as floats.
- [ ] Re-run the focused test and verify it passes.

### Task 3: Two SDF stroke modes

**Files:**
- Replace: `prismal/src/main/java/com/hellovoid/prismal/PrismalEdgeAntialiasShader.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PrismalShader.java`
- Replace: `prismal/src/test/java/com/hellovoid/prismal/PrismalEdgeAntialiasShaderTest.java`

**Interfaces:**
- Consumes: the eight `u_glassStroke*` uniforms and existing `distMask`/`edgeDist` SDF values.
- Produces: `glassStrokeMask`; Fill-Diff subtracts a fully excluded inner contour, while standard mode creates a boundary-centered ring.

- [ ] Replace the temporary band-width test with failing behavioral transformation tests: disabled stroke leaves color unchanged; Fill-Diff contains outer-minus-inner coverage; standard mode uses half-width around the boundary; both apply a fixed 0.75px coverage feather.
- [ ] Run `./gradlew :prismal:testDebugUnitTest --tests com.hellovoid.prismal.PrismalEdgeAntialiasShaderTest` and verify failure against the temporary `rimAaPx` transformer.
- [ ] Inject uniform declarations after `u_rimStrength`, calculate coverage after all Prismal highlights, mix `u_glassStrokeColor.rgb` over `color` using `glassStrokeMask * u_glassStrokeColor.a`, and remove the `rimAaPx`/`bandR = max(...)` workaround.
- [ ] Apply the same transformer to both shared and retained MIUI 307 shader strings, fail fast if either insertion anchor is missing or duplicated.
- [ ] Re-run the shader tests and relevant Prismal parity tests; verify they pass.

### Task 4: Integration verification and device install

**Files:**
- Verify all modified production and test files from Tasks 1–3.

**Interfaces:**
- Produces: installable Debug APK for user acceptance.

- [ ] Run focused configuration, parameter-chain, shader, GUI-navigation, and Prismal parity tests.
- [ ] Run `git diff --check` and inspect the full diff for accidental Dock stroke coupling or unrelated tracked changes.
- [ ] Run `./gradlew :assembleDebug` and verify exit code 0.
- [ ] Install `/home/zhaoyu/betterdock/build/outputs/apk/debug/LiquidDock-debug.apk` with `adb install -r` and verify package `com.hellovoid.liquiddock` is present.
- [ ] Report the two selectable modes and ask the user to test icon corners, straight edges, animations, folders, widgets, Dock, and workstation mode. Do not commit implementation changes before acceptance.
