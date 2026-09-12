# Security Center Launcher-Style Glass Sinks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Security Center custom glass fully own native material and follow the exact vendor View animation transforms through per-node sinks.

**Architecture:** Retain one root-wide `SecurityCenterGlassSession` and one `RootPassBlurBackend`, but replace the union-sized output with multiple `SecurityCenterGlassSinkView` outputs attached beside each material peer. The vendor View hierarchy owns animation; sinks mirror peer-local transforms and inherit ancestor Folme transforms naturally.

**Tech Stack:** Android View/TextureView, EGL/GLES20, existing Prismal renderer, RootPassBlurBackend, JUnit source-contract tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-10-security-center-launcher-style-sinks-design.md`

## Global Constraints

- Preserve root SurfaceControl → SetPassBlurSurface → SurfaceTexture/external OES → normalization → Prismal zero-copy path.
- Exactly one `RootPassBlurBackend` per Security Center root.
- No `ScreenCapture`, `Bitmap`, `PixelCopy`, CPU screenshot path, fixed delay, stale fallback, quality downgrade or second producer.
- Do not hook Folme/MIUIX animation implementation classes.
- Keep semantic discovery fail-closed; no version-specific obfuscated class literals.
- Device-side ADB verification is waived by user.

---

### Task 1: Lock the two runtime regressions with RED contracts

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterLauncherStylePresentationContractTest.java`

**Interfaces:**
- Consumes: current `SecurityCenterVendorMaterialBridge`, `SecurityCenterGlassOutputView`, `SecurityCenterGlassSession`, `SecurityCenterGlassCoordinator` source.
- Produces: failing assertions describing TurboLayout ownership and Launcher-style per-node sink requirements.

- [ ] **Step 1: Write the failing source-contract tests**

Require `claimCustom` to reset/clear TurboLayout itself; require a `SecurityCenterGlassSinkView` with peer-local `x/y`, pivot, scale, rotation, alpha and visibility mirroring; require the coordinator/session to use multiple sink outputs rather than a single union `SecurityCenterGlassOutputView`; forbid animation-time geometry-driven `setLayoutParams` resizing in the sink sync path.

- [ ] **Step 2: Run CI to verify RED**

Run through the branch workflow: `./gradlew testDebugUnitTest assembleDebug --stacktrace`.
Expected: the new contract test fails while existing tests continue to compile.

- [ ] **Step 3: Commit RED only**

Commit message: `test: lock Security Center launcher-style presentation contract`.

### Task 2: Expand TurboLayout material ownership

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialBridge.java`
- Test: `src/test/java/com/hellovoid/liquiddock/SecurityCenterLauncherStylePresentationContractTest.java`

**Interfaces:**
- Consumes: validated `finalBackground()` restore method and existing hidden View material reset APIs.
- Produces: `claimCustom(Object turboLayout, View dockLayout, View boxMaterialView, View allAppsLayout)` that clears TurboLayout when it is a View and preserves existing restore behavior.

- [ ] **Step 1: Minimal production change**

Before clearing Dock, validate/cast `turboLayout` to `View`, reset its MIUI material, clear pass-window blur, and clear its drawable background only if the restore path can reconstruct it through `finalBackground()`.

- [ ] **Step 2: Run targeted/full tests**

Expected: Turbo ownership assertion turns green; presentation assertions remain RED.

- [ ] **Step 3: Commit ownership fix**

Commit message: `fix: fully claim Security Center TurboLayout material`.

### Task 3: Introduce peer-bound Security Center glass sinks

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassSinkView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java`
- Remove after migration: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassOutputView.java`
- Test: `src/test/java/com/hellovoid/liquiddock/SecurityCenterLauncherStylePresentationContractTest.java`

**Interfaces:**
- Produces: `SecurityCenterGlassSinkView.attachBefore(View material, SecurityCenterGlassSession session)` and `syncFromMaterial()`.
- `syncFromMaterial()` copies `x`, `y`, pivot, scale, rotation, alpha and visibility from the peer while keeping local width/height based on peer base dimensions.
- Sink owns one TextureView `Surface` and delegates attach/resize/detach to the shared session with sink identity.

- [ ] **Step 1: Implement sink peer binding**

Attach immediately before the material peer in the same `ViewGroup`. Keep width/height equal to `material.getWidth()/getHeight()`; only update size when the peer's actual layout dimensions change, never from animated global bounds. Copy transforms exactly like `LauncherGlassSinkView.syncFromMaterial()`.

- [ ] **Step 2: Coordinator creates/disposes sinks per node**

Maintain weak references for Dock, optional Box and optional All Apps sinks. Reconcile sinks whenever `bindAssistant`, `updateAllAppsLayout`, root bind, transition pre-draw, or panel close changes the material set. Dispose stale sinks synchronously before losing ownership.

- [ ] **Step 3: Keep transition pre-draw as transform sync authority**

During vendor transforming state, call `syncFromMaterial()` for every live sink each pre-draw. Do not synthesize or start a custom animator.

- [ ] **Step 4: Run tests**

Expected: sink transform and no-animation-resize assertions turn green.

- [ ] **Step 5: Commit sink migration**

Commit message: `refactor: follow Security Center vendor animation with per-node sinks`.

### Task 4: Make the shared session render multiple sink surfaces

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassSession.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassFrameGeometry.java` only if node lookup identity is needed.
- Test: `src/test/java/com/hellovoid/liquiddock/SecurityCenterLauncherStylePresentationContractTest.java`
- Test: existing `SecurityCenterGlassFrameGeometryTest.java`, `RootPassBlurBackendBoundaryTest.java`.

**Interfaces:**
- `attachOutput(SecurityCenterGlassSinkView sink, Surface surface, int width, int height)`.
- `resizeOutput(SecurityCenterGlassSinkView sink, int width, int height)`.
- `detachOutput(SecurityCenterGlassSinkView sink, Surface surface)`.
- One session-level `RootPassBlurBackend`; map sink identity to EGL window surface.

- [ ] **Step 1: Replace singleton OutputState with sink-keyed output states**

Use a render-thread-only `WeakHashMap<SecurityCenterGlassSinkView, OutputState>`. Create/destroy EGL window surfaces independently while retaining the single shared source backend and Prismal renderer.

- [ ] **Step 2: Render node-local glass into each sink**

For each current frame node with a live sink, prepare the shared backdrop once, draw only that node's geometry, and composite the node crop to that sink's EGL surface. Do not use a union presentation TextureView.

- [ ] **Step 3: Preserve fresh-generation authorization**

Notify the coordinator only after every required current-generation sink output has been rendered successfully; stale generation callbacks remain ignored.

- [ ] **Step 4: Run full tests**

Expected: all new and existing tests pass.

- [ ] **Step 5: Commit multi-output session**

Commit message: `refactor: render Security Center nodes through shared multi-output session`.

### Task 5: Verify architecture and CI

**Files:**
- Update tests only if needed to reflect intentional class removal/name migration; do not weaken assertions.

**Interfaces:**
- Consumes: completed presentation architecture.
- Produces: CI-green branch with no duplicate Release verification.

- [ ] **Step 1: Run zero-copy audit and full debug CI**

Expected workflow steps: zero-copy audit → unit/debug assembly → artifacts; no release verification.

- [ ] **Step 2: Review diff against the approved constraints**

Confirm no Folme hooks, no delay/debounce, no second `RootPassBlurBackend`, no CPU capture path, and no version-specific obfuscated class literals.

- [ ] **Step 3: Confirm source/decompile contract**

Ensure TurboLayout ownership and peer hierarchy assumptions remain compatible with the two known Security Center decompiles in the handoff artifact.

- [ ] **Step 4: Record verification boundary**

Report CI/source-contract result as verified; keep device runtime behavior explicitly unverified because ADB/device execution is waived.
