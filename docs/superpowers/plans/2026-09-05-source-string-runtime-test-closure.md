# Source-String Runtime Test Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fully close the testing-architecture gap: no runtime behavior may be asserted by reading production source text; only explicit static R8/Gradle/Manifest/API/architecture contracts may inspect source.

**Architecture:** Replace filename-suffix guessing with a default-deny source-inspection gate. Mixed source contracts are split so static vendor/API boundaries remain static while ownership, freshness, animation, recovery and lifecycle decisions are driven by Android/Xposed-free production-used state/policy objects. Existing pure models are reused wherever they already express the behavior; new models are added only when the runtime still owns sequencing as raw fields/imperative branches.

**Tech Stack:** Java 17, JUnit 4, Android/AGP, GitHub Actions, existing LiquidDock package-private pure state/policy pattern.

**Spec:** `docs/superpowers/specs/2026-09-05-pure-runtime-state-tests-design.md`

## Global Constraints

- Do not add source-string runtime behavior tests.
- R8 / Gradle / AndroidManifest / explicit API or architecture bans may remain static.
- ownership / freshness / animation / recovery behavior must be tested through production-used pure state/policy inputs and outputs.
- Do not widen production visibility for tests; same-package package-private typed APIs are preferred.
- Do not add test-only mirror state machines.
- Producer rollover completion is not freshness; fresh rendered generation remains the reveal authority.
- Keep PR #109 on `refactor/pure-runtime-state-tests`; do not merge as part of this plan.

---

### Task 1: Make the test-policy gate default-deny

**Files:**
- Modify: `src/test/java/com/hellovoid/liquiddock/RuntimeBehaviorTestPolicyContractTest.java`

**Interfaces:**
- Consumes: repository test source tree.
- Produces: explicit source-inspection allowlist; every non-allowlisted test that references production Java source fails regardless of filename suffix.

- [ ] **Step 1: Change the gate first so the current branch turns RED.**

The gate must scan every `*.java` test. A test that references `src/main/java`, reads a known production Java file through `Files.readString/readAllBytes`, or reflectively opens same-package state/policy internals is rejected unless its class name is in the explicit static allowlist.

Initial static allowlist contains only audited structural/configuration classes such as:

```java
Set.of(
    "HookUtilArchitectureContractTest.java",
    "DockShadowArchitectureTest.java",
    "LauncherGlassStaticBoundaryTest.java",
    "R8ReleaseKeepContractTest.java",
    "PrismalModuleBoundaryContractTest.java"
)
```

Mixed runtime/static contracts are intentionally not allowlisted yet.

- [ ] **Step 2: Run `./gradlew testDebugUnitTest --stacktrace`.**

Expected: FAIL from `RuntimeBehaviorTestPolicyContractTest` with the concrete list of remaining unauthorized source-reading tests.

- [ ] **Step 3: Commit the RED gate.**

---

### Task 2: Remove source-string freshness contracts that already have pure models

**Files:**
- Modify or split: `LauncherWallpaperFreshnessHookContractTest.java`
- Modify or split: `LauncherWidgetTransitionWiringContractTest.java`
- Modify or split: `SystemUiHomeTransitionWiringContractTest.java`
- Extend typed tests where needed: `LauncherWallpaperContentStateTest.java`, `LauncherWidgetTransitionStateTest.java`, `LauncherGlassSceneControllerTest.java`, `SystemUiHomeEarlyRevealStateTest.java`

**Interfaces:**
- Reuse: `LauncherWallpaperContentState`, `LauncherWidgetTransitionState`, `LauncherGlassSceneController.StateMachine`, existing SystemUI pure policy/state.

- [ ] **Step 1: Keep only exact vendor class/method presence or forbidden-API checks that are genuinely static.**
- [ ] **Step 2: Delete source assertions about callback ordering, stale/fresh generation, reveal timing, polling/delay sequencing, or coordinator behavior.**
- [ ] **Step 3: Add missing typed input/output cases to the existing pure tests for any behavior not already covered.**
- [ ] **Step 4: Re-run focused tests and confirm GREEN.**

---

### Task 3: Replace Dock shadow source behavior contracts

**Files:**
- Delete or reduce to static-only: `DockVendorNativeShadowReuseContractTest.java`
- Extend: `DockShadowRuntimePolicyTest.java`
- Keep: `DockShadowArchitectureTest.java`

**Interfaces:**
- Reuse: `DockShadowRuntimePolicy` for geometry sync, vendor refresh, temporary overrides.

- [ ] **Step 1: Remove method slicing / source-order assertions for ownership and runtime refresh.**
- [ ] **Step 2: Preserve only explicit API bans in `DockShadowArchitectureTest`.**
- [ ] **Step 3: Add any missing runtime decision cases to `DockShadowRuntimePolicyTest` using booleans/enums only.**
- [ ] **Step 4: Run focused tests.**

---

### Task 4: Model fullscreen producer recovery as pure state

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/ZeroCopyProducerRecoveryState.java`
- Modify: `Miuix307PassBlurTextureView.java`
- Modify if needed: `Miuix307ZeroCopyRenderer.java`, `Miuix307MaterialPipeline.java`
- Replace: `WorkstationFullscreenProducerRecoveryContractTest.java` with typed `ZeroCopyProducerRecoveryStateTest.java` plus any narrowly static vendor hook/API boundary test.

**Interfaces:**

```java
final class ZeroCopyProducerRecoveryState {
    static final class Decision {
        final boolean recreateProducer;
        final boolean clearFrameworkBinding;
        final boolean clearConsumedFrame;
        final boolean clearFrameAvailable;
        final boolean clearActivationExhausted;
        final boolean requestBind;
    }

    Decision onRebindRequested();
    Decision onProducerRecreated();
    void onFreshFrameConsumed();
    boolean isRebindPending();
    boolean hasFreshFrame();
}
```

Production `Miuix307PassBlurTextureView` must consume these decisions; tests never inspect its source.

- [ ] **Step 1: Add RED typed tests for duplicate rebind suppression, stale-frame reset, producer recreation completion, and fresh-frame gating.**
- [ ] **Step 2: Verify RED because the pure production state does not exist.**
- [ ] **Step 3: Add the state and wire it into the actual producer rebind lifecycle.**
- [ ] **Step 4: Delete runtime source assertions from `WorkstationFullscreenProducerRecoveryContractTest`; retain only a narrow static vendor `Launcher.onResume` hook presence check if still valuable.**
- [ ] **Step 5: Run focused tests and full unit suite.**

---

### Task 5: Close remaining ownership/animation/recovery source contracts discovered by the new gate

**Files:**
- Audit all failures emitted by Task 1.
- Expected mixed candidates include widget/MAML background ownership, folder interaction/drag ownership, mirror reflection accounting, and workstation geometry contracts.

**Rules:**
- If behavior already has a pure state/policy test, remove the source assertion.
- If a test is an explicit forbidden API / architecture ownership boundary, move it to or retain it in an allowlisted static class.
- If runtime behavior has no pure seam, extract the smallest production-used Android-free policy/state and test real input/output.
- Do not allowlist a mixed file merely to silence the gate.

- [ ] **Step 1: Iterate on the concrete RED list until every remaining production-source reader is an audited static-only test.**
- [ ] **Step 2: Add each surviving static-only class to the explicit allowlist with a reason comment.**
- [ ] **Step 3: Run the gate and full unit suite.**

---

### Task 6: Final verification and PR closure

**Files:**
- Update: `CONTRIBUTING.md` only if the final gate wording needs clarification.
- Update PR #109 title/body.

- [ ] **Step 1: Run `./gradlew testDebugUnitTest --stacktrace`.**
- [ ] **Step 2: Run `./gradlew assembleDebug --stacktrace`.**
- [ ] **Step 3: Verify APK and source artifacts upload successfully.**
- [ ] **Step 4: Audit the PR diff for temporary workflows/scripts and unauthorized source-reading runtime tests.**
- [ ] **Step 5: Verify branch is not behind `main`.**
- [ ] **Step 6: Keep PR #109 Draft/open; do not merge.**
