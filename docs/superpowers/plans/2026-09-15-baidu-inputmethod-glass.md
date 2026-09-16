# Baidu Input Method Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe Prismal/PassBlur glass background replacement for structurally supported Baidu Input Method keyboard layouts.

**Architecture:** Hook the stable `com.content.input_mi.ImeService` lifecycle, resolve stable runtime keyboard-region/container views, insert a TextureView glass sink below keyboard content, and hide stock background only after a presented frame. Use a dedicated PassBlur domain and fail closed for unsupported self-drawing layouts.

**Tech Stack:** Android View/TextureView, libxposed API 101 HookUtil, RootPassBlurBackend, PrismalRenderer, JUnit contract tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-15-baidu-inputmethod-glass-design.md`

## Global Constraints

- Package is `com.baidu.input_mi`.
- Stable service hook is `com.content.input_mi.ImeService`.
- Do not use obfuscated vendor class/member names as hook contracts.
- Do not use hard-coded resource IDs, fixed delays, PixelCopy, ScreenCapture, or Bitmap capture.
- Hide stock visuals only after the first TextureView-presented frame.
- Unsupported layouts retain stock behavior.

---

### Task 1: Add the static safety contract

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/BaiduInputMethodGlassContractTest.java`

**Interfaces:**
- Consumes: existing source tree and Gboard architecture conventions.
- Produces: static regression contract for package scope, stable-service hook, dedicated PassBlur domain, zero-copy pipeline, no obfuscated names/resource IDs/fixed delays.

- [ ] **Step 1:** Add the contract test with assertions for `com.baidu.input_mi`, `com.content.input_mi.ImeService`, `BAIDU_INPUTMETHOD`, `PassBlurBindRequest.baiduInputMethod`, `TextureView`, `onSurfaceTextureUpdated`, `OnPreDrawListener`, and absence of `postDelayed`, `PixelCopy`, `ScreenCapture`, `Bitmap.createBitmap`, `0x7f`, `xb9`, `ch9`.
- [ ] **Step 2:** Run the test and verify RED because Baidu production files do not exist yet.
- [ ] **Step 3:** Do not change the test during implementation unless the design itself changes.

### Task 2: Add package/domain wiring

**Files:**
- Modify: `src/main/resources/META-INF/xposed/scope.list`
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/PassBlurDomain.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/PassBlurBindRequest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurBridge.java`
- Create: `src/main/java/com/hellovoid/liquiddock/BaiduInputMethodPassBlurContinuousAuthority.java`

**Interfaces:**
- Produces: `PassBlurDomain.BAIDU_INPUTMETHOD`, `PassBlurBindRequest.baiduInputMethod(View)`, Baidu-specific continuous producer claim/release.

- [ ] **Step 1:** Add Baidu package scope and ModuleMain branch.
- [ ] **Step 2:** Add dedicated PassBlur domain/bind request.
- [ ] **Step 3:** Mirror the proven Gboard continuous-authority semantics with Baidu-specific claim ownership.
- [ ] **Step 4:** Update `Miuix307PassBlurBridge` to claim/release the Baidu authority only for `BAIDU_INPUTMETHOD`.

### Task 3: Add stable runtime structure resolution

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/BaiduInputMethodStructureResolver.java`
- Create: `src/main/java/com/hellovoid/liquiddock/BaiduInputMethodGlassHook.java`

**Interfaces:**
- Produces: `Structure` containing authoritative input view, sink host, content view, stock background view and runtime geometry checks.

- [ ] **Step 1:** Hook `ImeService.setInputView(View)` and `ImeService.onWindowShown()` only.
- [ ] **Step 2:** Resolve stable keyboard classes by semantic names (`KeyboardRegion`, `KeyboardContainer`, `InputView`) from descendants; do not inspect fields or vendor resource IDs.
- [ ] **Step 3:** Require a distinct background authority and compact/floating geometry before activation.
- [ ] **Step 4:** Return null for direct self-drawing layouts that cannot safely expose a distinct stock background.

### Task 4: Add glass sink/session/coordinator

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/BaiduInputMethodGlassGeometry.java`
- Create: `src/main/java/com/hellovoid/liquiddock/BaiduInputMethodGlassView.java`
- Create: `src/main/java/com/hellovoid/liquiddock/BaiduInputMethodGlassSession.java`
- Create: `src/main/java/com/hellovoid/liquiddock/BaiduInputMethodGlassCoordinator.java`

**Interfaces:**
- Consumes: `Structure`, `LiquidDockConfig.Glass`, `PassBlurBindRequest.baiduInputMethod(View)`.
- Produces: continuous zero-copy glass surface with first-present stock handoff and fail-closed teardown.

- [ ] **Step 1:** Capture root/sink crop geometry without resource IDs.
- [ ] **Step 2:** Add TextureView sink callbacks and first-present signal.
- [ ] **Step 3:** Clone only the minimal proven Gboard zero-copy session logic, switching to Baidu domain.
- [ ] **Step 4:** Insert sink below content, track layout/pre-draw, suppress stock background after first present, restore on release/failure.

### Task 5: Verify

**Files:**
- Test: `src/test/java/com/hellovoid/liquiddock/BaiduInputMethodGlassContractTest.java`

- [ ] **Step 1:** Run the Baidu contract test; expect PASS.
- [ ] **Step 2:** Run existing Gboard glass contract tests; expect PASS.
- [ ] **Step 3:** Run the repository test/build workflow in CI.
- [ ] **Step 4:** Inspect diff for forbidden obfuscated hooks, resource IDs, fixed delays, or CPU capture.
