# Pure Runtime State Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace in-scope source-string runtime behavior contracts with production-used Android-free state/policy objects tested through real inputs and outputs, while preserving legitimate static R8/Gradle/Manifest/API-ban tests.

**Architecture:** Keep Android/Xposed classes as effect executors, but move runtime decisions for Dock ownership, glass teardown, Dock shadow animation/ownership, unlock recovery, and Workstation Recents recovery into package-private pure Java state/policy classes. Reuse `LauncherGlassSceneController.StateMachine`, `LauncherWidgetTransitionState`, and `DockIconAnimationState` for freshness/animation instead of duplicating those semantics. Static source inspection remains only for declarative wiring or explicit forbidden-API/architecture rules.

**Tech Stack:** Java, JUnit 4 host unit tests, Android Gradle plugin, GitHub Actions (`testDebugUnitTest`, `assembleDebug`).

**Spec:** `docs/superpowers/specs/2026-09-05-pure-runtime-state-tests-design.md`

## Global Constraints

- Do not add source-string tests for runtime ownership, freshness, animation, recovery, teardown ordering, callback timing, or lifecycle sequencing.
- Static source/config inspection remains allowed for R8, Gradle/module wiring, Manifest/Xposed scope, and explicit API/architecture bans.
- Every new pure state/policy class must be used by production runtime code; no test-only shadow model.
- A producer rollover callback means endpoint replacement completed at its defined boundary; it is never a fresh-frame event.
- `LauncherGlassSceneController.StateMachine` remains the authority for scene generation freshness and reveal.
- Existing intended visual/runtime behavior must be preserved unless the approved spec explicitly strengthens fail-closed recovery semantics.
- Use RED -> GREEN for each task and run the focused host unit test before the complete suite.

---

## File Structure

New focused production files:

- `src/main/java/com/hellovoid/liquiddock/VisualRuntimeTransitionPolicy.java` — pure before/after Dock visual ownership transition planner.
- `src/main/java/com/hellovoid/liquiddock/GlassRuntimeTransitionPolicy.java` — pure glass/component ownership release planner.
- `src/main/java/com/hellovoid/liquiddock/DockShadowRuntimePolicy.java` — pure Dock shadow geometry/override/refresh decisions.
- `src/main/java/com/hellovoid/liquiddock/UnlockCaptureRecoveryState.java` — pure fail-closed unlock producer-recovery state machine.
- `src/main/java/com/hellovoid/liquiddock/WorkstationRecentsRecoveryPolicy.java` — pure decision for whether HOME may uncover after Workstation producer rollover.

New/rewritten behavior tests:

- `src/test/java/com/hellovoid/liquiddock/VisualRuntimeTransitionPolicyTest.java`
- `src/test/java/com/hellovoid/liquiddock/GlassRuntimeTransitionPolicyTest.java`
- `src/test/java/com/hellovoid/liquiddock/DockShadowRuntimePolicyTest.java`
- `src/test/java/com/hellovoid/liquiddock/UnlockCaptureRecoveryStateTest.java`
- `src/test/java/com/hellovoid/liquiddock/WorkstationRecentsRecoveryPolicyTest.java`
- direct-call rewrites of `LauncherGlassSceneControllerTest.java`, `SystemUiHomeEarlyRevealStateTest.java`, `LauncherWidgetTransitionStateTest.java`, and `DockIconAnimationStateTest.java`.

Static-only tests retained or narrowed:

- `R8ReleaseKeepContractTest.java`
- `HookUtilArchitectureContractTest.java`
- Manifest/Xposed scope/module tests already present.
- a narrow `LauncherGlassStaticBoundaryTest.java` for SystemUI scope, forbidden capture APIs, and project-owned reflection/API bans only.
- a narrow `DockShadowArchitectureTest.java` for forbidden second-shadow-owner/HotSeats-alpha APIs only.

Deleted after replacement:

- `DockRuntimeOwnershipContractTest.java`
- `GlassRuntimeDisableContractTest.java`
- `DockShadowAnimationRegressionTest.java`
- runtime-behavior portions of `LauncherUnlockCaptureBoundaryContractTest.java`
- `WorkstationStaticLayerRecentsRecoveryContractTest.java`

---

### Task 1: Dock visual ownership transition policy

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/VisualRuntimeTransitionPolicy.java`
- Create: `src/test/java/com/hellovoid/liquiddock/VisualRuntimeTransitionPolicyTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/VisualRuntimeState.java`
- Delete after GREEN: `src/test/java/com/hellovoid/liquiddock/DockRuntimeOwnershipContractTest.java`

**Interfaces:**
- Produces `VisualRuntimeTransitionPolicy.Snapshot` with effective booleans: `dockCustomization`, `stroke`, `dockShadow`, `strokeShadow`, `divider`, `mirrorHidden`.
- Produces `VisualRuntimeTransitionPolicy.Transition plan(Snapshot before, Snapshot after)`.
- `Transition` exposes booleans: `dockCustomizationDisabled`, `strokeDisabled`, `strokeEnabled`, `dockShadowDisabled`, `dockShadowEnabled`, `strokeShadowChanged`, `dividerDisabled`, `mirrorVisibilityChanged`.

- [ ] **Step 1: Write failing pure transition tests**

```java
@Test public void disablingCoreReleasesEveryEffectiveDockOwner() {
    Snapshot before = new Snapshot(true, true, true, true, true, true);
    Snapshot after = new Snapshot(false, false, false, false, false, false);
    Transition t = VisualRuntimeTransitionPolicy.plan(before, after);
    assertTrue(t.dockCustomizationDisabled);
    assertTrue(t.strokeDisabled);
    assertTrue(t.dockShadowDisabled);
    assertTrue(t.strokeShadowChanged);
    assertTrue(t.dividerDisabled);
    assertTrue(t.mirrorVisibilityChanged);
    assertFalse(t.strokeEnabled);
    assertFalse(t.dockShadowEnabled);
}

@Test public void enablingOnlyStrokeDoesNotClaimDockShadowOwnership() {
    Snapshot before = new Snapshot(true, false, false, false, true, false);
    Snapshot after = new Snapshot(true, true, false, false, true, false);
    Transition t = VisualRuntimeTransitionPolicy.plan(before, after);
    assertTrue(t.strokeEnabled);
    assertFalse(t.dockShadowEnabled);
    assertFalse(t.dockCustomizationDisabled);
}
```

- [ ] **Step 2: Run RED test**

Run: `./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.VisualRuntimeTransitionPolicyTest --stacktrace`
Expected: FAIL because `VisualRuntimeTransitionPolicy` does not exist.

- [ ] **Step 3: Implement the pure planner**

```java
final class VisualRuntimeTransitionPolicy {
    static final class Snapshot {
        final boolean dockCustomization, stroke, dockShadow, strokeShadow, divider, mirrorHidden;
        Snapshot(boolean dockCustomization, boolean stroke, boolean dockShadow,
                 boolean strokeShadow, boolean divider, boolean mirrorHidden) { ... }
    }

    static final class Transition {
        final boolean dockCustomizationDisabled;
        final boolean strokeDisabled, strokeEnabled;
        final boolean dockShadowDisabled, dockShadowEnabled;
        final boolean strokeShadowChanged;
        final boolean dividerDisabled;
        final boolean mirrorVisibilityChanged;
        ...
    }

    static Transition plan(Snapshot before, Snapshot after) {
        return new Transition(
                before.dockCustomization && !after.dockCustomization,
                before.stroke && !after.stroke,
                !before.stroke && after.stroke,
                before.dockShadow && !after.dockShadow,
                !before.dockShadow && after.dockShadow,
                before.strokeShadow != after.strokeShadow,
                before.divider && !after.divider,
                before.mirrorHidden != after.mirrorHidden);
    }
}
```

- [ ] **Step 4: Wire `VisualRuntimeState.apply(...)` to the planner**

Capture `before` from current effective getters, publish the new raw booleans, capture `after`, call `plan(before, after)`, then execute the existing main-thread callbacks from the returned transition flags. Preserve style-key refresh handling outside this planner because it is not an ownership state transition.

- [ ] **Step 5: Run GREEN focused tests and existing runtime-state tests**

Run: `./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.VisualRuntimeTransitionPolicyTest --stacktrace`
Expected: PASS.

- [ ] **Step 6: Delete the source-string ownership behavior test**

Delete `DockRuntimeOwnershipContractTest.java`; static shadow API bans move in Task 3 rather than remaining mixed with runtime ownership assertions.

- [ ] **Step 7: Commit**

Commit message: `refactor: model dock runtime ownership transitions`

---

### Task 2: Glass ownership and teardown transition policy

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/GlassRuntimeTransitionPolicy.java`
- Create: `src/test/java/com/hellovoid/liquiddock/GlassRuntimeTransitionPolicyTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java`
- Delete after GREEN: `src/test/java/com/hellovoid/liquiddock/GlassRuntimeDisableContractTest.java`

**Interfaces:**
- `Snapshot(enabled, icon, widget, widgetDarkContent, smallFolder, largeFolder)` contains effective states.
- `Transition` contains `fullTeardown`, `iconRelease`, `widgetRelease`, `widgetDarkContentChanged`, `nextWidgetDarkContent`, `smallFolderRelease`, `largeFolderRelease`.
- `fullTeardown` dominates all component release flags.

- [ ] **Step 1: Write RED tests for full teardown dominance and component-only changes**

```java
@Test public void fullDisableProducesOneDominantTeardown() {
    Snapshot before = new Snapshot(true, true, true, true, true, true);
    Snapshot after = new Snapshot(false, false, false, false, false, false);
    Transition t = GlassRuntimeTransitionPolicy.plan(before, after);
    assertTrue(t.fullTeardown);
    assertFalse(t.iconRelease);
    assertFalse(t.widgetRelease);
    assertFalse(t.smallFolderRelease);
    assertFalse(t.largeFolderRelease);
}

@Test public void darkContentToggleCarriesTheNewEffectiveValue() {
    Snapshot before = new Snapshot(true, true, true, true, true, true);
    Snapshot after = new Snapshot(true, true, true, false, true, true);
    Transition t = GlassRuntimeTransitionPolicy.plan(before, after);
    assertTrue(t.widgetDarkContentChanged);
    assertFalse(t.nextWidgetDarkContent);
    assertFalse(t.fullTeardown);
}
```

- [ ] **Step 2: Run RED test**

Run: `./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.GlassRuntimeTransitionPolicyTest --stacktrace`
Expected: FAIL because the planner does not exist.

- [ ] **Step 3: Implement planner and production wiring**

Use the same before/publish/after pattern as Task 1. In `GlassRuntimeState.apply(...)`, if `transition.fullTeardown` execute the existing full teardown callback and return. Otherwise execute only the component actions signaled by the transition.

- [ ] **Step 4: Run GREEN tests**

Run: `./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.GlassRuntimeTransitionPolicyTest --stacktrace`
Expected: PASS.

- [ ] **Step 5: Remove `GlassRuntimeDisableContractTest`**

Its runtime source-string assertions are replaced by the planner tests. Do not recreate listener/callback-order assertions as source-string tests.

- [ ] **Step 6: Commit**

Commit message: `refactor: model glass runtime teardown transitions`

---

### Task 3: Dock shadow animation/ownership policy and static API bans

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/DockShadowRuntimePolicy.java`
- Create: `src/test/java/com/hellovoid/liquiddock/DockShadowRuntimePolicyTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/DockShadowArchitectureTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Delete: `src/test/java/com/hellovoid/liquiddock/DockShadowAnimationRegressionTest.java`

**Interfaces:**

```java
final class DockShadowRuntimePolicy {
    enum GeometrySync { REMEMBER_ONLY, SYNC_CONFIG }
    static GeometrySync geometrySync(boolean workstationMode, boolean animationActive);
    static boolean shouldRefreshVendorShadow(boolean workstationMode, boolean dockCustomizationEnabled);
    static boolean shouldApplyTemporaryOverrides(boolean workstationMode,
                                                 boolean dockCustomizationEnabled,
                                                 boolean hasConfig);
}
```

- [ ] **Step 1: Write RED behavior tests**

```java
@Test public void geometryIsPassiveDuringAnimationAndWorkstation() {
    assertEquals(REMEMBER_ONLY, geometrySync(false, true));
    assertEquals(REMEMBER_ONLY, geometrySync(true, false));
    assertEquals(SYNC_CONFIG, geometrySync(false, false));
}

@Test public void temporaryOverridesRequireSingleLiquidDockOwnershipWindow() {
    assertTrue(shouldApplyTemporaryOverrides(false, true, true));
    assertFalse(shouldApplyTemporaryOverrides(true, true, true));
    assertFalse(shouldApplyTemporaryOverrides(false, false, true));
    assertFalse(shouldApplyTemporaryOverrides(false, true, false));
}
```

- [ ] **Step 2: Run RED test**

Run: `./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.DockShadowRuntimePolicyTest --stacktrace`
Expected: FAIL because the policy does not exist.

- [ ] **Step 3: Implement and wire the policy**

`MainHook.syncAll(...)` always remembers `oldBg`, then calls `geometrySync(workstationMode, animating(bg))`; only `SYNC_CONFIG` loads/publishes Dock shadow config. `pushConfiguredHotSeatsShadow(...)` uses `shouldApplyTemporaryOverrides(...)`. Runtime enable/disable shadow refresh uses `shouldRefreshVendorShadow(...)`.

- [ ] **Step 4: Keep only true static architecture bans**

`DockShadowArchitectureTest` may source-inspect `MainHook.java` only for forbidden second-owner/alpha APIs:

```java
assertFalse(main.contains("shadowViewRef"));
assertFalse(main.contains("makeDockShadow("));
assertFalse(main.contains("ensureShadowBelowBackground("));
assertFalse(main.contains("overrideViewAlpha("));
assertFalse(main.contains("nativeShadowInternalCall"));
assertFalse(main.contains("captureVendorDockShadow"));
```

Do not assert method ordering, `syncAll` contents, vendor callback ordering, `scope.close()` placement, or animation behavior from source text.

- [ ] **Step 5: Run focused GREEN tests**

Run both `DockShadowRuntimePolicyTest` and `DockShadowArchitectureTest`; expected PASS.

- [ ] **Step 6: Delete `DockShadowAnimationRegressionTest.java` and commit**

Commit message: `refactor: test dock shadow behavior through pure policy`

---

### Task 4: Fail-closed unlock capture recovery state

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/UnlockCaptureRecoveryState.java`
- Create: `src/test/java/com/hellovoid/liquiddock/UnlockCaptureRecoveryStateTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassHomePresentationHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSessionRegistry.java`

**Interfaces:**

```java
final class UnlockCaptureRecoveryState {
    static final class Decision {
        final boolean suspendProducers;
        final boolean requestRollover;
        final boolean releaseBarrier;
        final long serial;
    }

    Decision onPrepare();
    Decision onSystemUiGoneFinished();
    Decision onRolloverFinished(long serial, boolean success);
    boolean isBlocked();
}
```

`LauncherGlassSessionRegistry.prepareUnlockCaptureReturn` changes from success-only `Runnable` completion to a package-private callback:

```java
interface RolloverCompletion { void onComplete(boolean success); }
static void prepareUnlockCaptureReturn(RolloverCompletion completion);
```

The registry invokes completion exactly once after all live sessions have either completed rollover or failed/rejected; it passes `false` when any failed. Empty-session recovery succeeds immediately.

- [ ] **Step 1: Write RED state-machine tests**

```java
@Test public void prepareArmsCaptureAndSuspendsOnce() {
    UnlockCaptureRecoveryState s = new UnlockCaptureRecoveryState();
    Decision d = s.onPrepare();
    assertTrue(s.isBlocked());
    assertTrue(d.suspendProducers);
    assertFalse(d.releaseBarrier);
}

@Test public void rolloverCompletionIsNotFreshness() {
    UnlockCaptureRecoveryState s = new UnlockCaptureRecoveryState();
    s.onPrepare();
    Decision request = s.onSystemUiGoneFinished();
    assertTrue(request.requestRollover);
    Decision rolled = s.onRolloverFinished(request.serial, true);
    assertTrue(rolled.releaseBarrier);
    assertFalse(s.isBlocked());
    // No scene-visibility assertion exists here: freshness is owned by SceneController.StateMachine.
}

@Test public void rejectionRemainsFailClosedAndStaleCompletionCannotRelease() {
    UnlockCaptureRecoveryState s = new UnlockCaptureRecoveryState();
    Decision first = s.onPrepare();
    Decision request = s.onSystemUiGoneFinished();
    s.onRolloverFinished(request.serial, false);
    assertTrue(s.isBlocked());
    assertFalse(s.onRolloverFinished(first.serial - 1L, true).releaseBarrier);
    assertTrue(s.isBlocked());
}
```

Also cover the PREPARE-skipped failsafe: `onSystemUiGoneFinished()` from IDLE must return both `suspendProducers=true` and `requestRollover=true` for a new serial.

- [ ] **Step 2: Run RED test**

Run: `./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.UnlockCaptureRecoveryStateTest --stacktrace`
Expected: FAIL because state machine does not exist.

- [ ] **Step 3: Implement the state machine**

Use phases `IDLE`, `ARMED`, `ROLLOVER_PENDING`, `FAILED`. A matching successful rollover completion releases only the capture barrier; failure stays `FAILED`/blocked until a new PREPARE starts a new serial.

- [ ] **Step 4: Wire production unlock flow**

Replace `unlockTransitionArmed`, `unlockTransitionSerial`, and `unlockReleaseScheduledSerial` with one `UnlockCaptureRecoveryState`. Execute returned decisions in small adapter helpers:

- `suspendProducers` -> `LauncherGlassSceneController.setUnlockTransitionPendingForAll(true)` then `LauncherGlassSessionRegistry.suspendForUnlockCapture()`;
- `requestRollover` -> `prepareUnlockCaptureReturn(success -> state.onRolloverFinished(serial, success))`;
- `releaseBarrier` -> `LauncherGlassSceneController.setUnlockTransitionPendingForAll(false)`.

Do not call `onFreshFrameReady` from rollover completion.

- [ ] **Step 5: Run GREEN focused tests**

Run `UnlockCaptureRecoveryStateTest`; expected PASS.

- [ ] **Step 6: Commit**

Commit message: `refactor: model unlock capture recovery state`

---

### Task 5: Workstation Recents recovery policy + scene freshness composition

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/WorkstationRecentsRecoveryPolicy.java`
- Create: `src/test/java/com/hellovoid/liquiddock/WorkstationRecentsRecoveryPolicyTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSessionRegistry.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassRecentsHook.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/LauncherGlassSceneControllerTest.java`
- Delete: `src/test/java/com/hellovoid/liquiddock/WorkstationStaticLayerRecentsRecoveryContractTest.java`

**Interfaces:**

```java
final class WorkstationRecentsRecoveryPolicy {
    static final class Decision {
        final boolean requestRollover;
        final boolean allowUncover;
    }
    static Decision onRecentsReturn(boolean workstationMode, boolean rolloverAccepted);
}
```

`LauncherGlassSessionRegistry.prepareWorkstationRecentsReturn()` changes to `static synchronized boolean ...`; it returns `true` when no rollover is required or every live session accepted `rebindProducer()`, and `false` when any live Workstation session rejects/throws.

- [ ] **Step 1: Write RED policy tests**

```java
@Test public void normalModeUncoversWithoutRollover() {
    Decision d = onRecentsReturn(false, false);
    assertFalse(d.requestRollover);
    assertTrue(d.allowUncover);
}

@Test public void workstationRejectStaysFailClosed() {
    Decision d = onRecentsReturn(true, false);
    assertTrue(d.requestRollover);
    assertFalse(d.allowUncover);
}

@Test public void workstationAcceptedRolloverAllowsFreshnessRecoveryButNotVisibilityByItself() {
    Decision d = onRecentsReturn(true, true);
    assertTrue(d.requestRollover);
    assertTrue(d.allowUncover);
}
```

- [ ] **Step 2: Run RED policy test**

Expected: FAIL because policy does not exist.

- [ ] **Step 3: Implement registry success aggregation and Recents hook wiring**

On `onRecentViewHide`, call the registry only in Workstation mode, pass the boolean into the policy, and if `allowUncover` is false keep Recents coverage/presentation blocking active and log the fail-closed recovery. If allowed, preserve the existing wallpaper-settle barrier and uncover sequence.

- [ ] **Step 4: Add direct scene freshness composition tests**

In `LauncherGlassSceneControllerTest`, directly instantiate `LauncherGlassSceneController.StateMachine` and verify:

```java
state.onRootReady();
state.onBootstrapReconciled();
long visibleGeneration = state.generation();
state.onFreshFrameReady(visibleGeneration);
assertTrue(state.isLayerVisible());
state.setCovered(true);
state.setCovered(false);
long recoveryGeneration = state.generation();
assertFalse(state.isLayerVisible());
state.onFreshFrameReady(visibleGeneration); // stale
assertFalse(state.isLayerVisible());
state.onFreshFrameReady(recoveryGeneration); // matching fresh frame
assertTrue(state.isLayerVisible());
```

This is the recovery reveal proof. The Workstation policy only determines whether recovery may proceed; it does not itself reveal the scene.

- [ ] **Step 5: Run GREEN tests and delete old source-order contract**

Run `WorkstationRecentsRecoveryPolicyTest` and `LauncherGlassSceneControllerTest`; expected PASS. Delete `WorkstationStaticLayerRecentsRecoveryContractTest.java`.

- [ ] **Step 6: Commit**

Commit message: `refactor: model workstation recents recovery policy`

---

### Task 6: Split unlock static boundaries from runtime behavior and direct-call existing state tests

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherGlassStaticBoundaryTest.java`
- Delete: `src/test/java/com/hellovoid/liquiddock/LauncherUnlockCaptureBoundaryContractTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/LauncherGlassSceneControllerTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/SystemUiHomeEarlyRevealStateTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/LauncherWidgetTransitionStateTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/DockIconAnimationStateTest.java`

**Interfaces:**
- Tests directly instantiate package-private classes in `com.hellovoid.liquiddock`.
- No reflection is needed for package-private constructors/methods/fields used by these tests.

- [ ] **Step 1: Create the narrow static boundary test**

Allowed assertions only:

- Xposed scope contains `com.miui.home` and `com.android.systemui`;
- SystemUI observer code does not use `ScreenCapture`, `captureDisplay`, `SurfaceControl`, or `SetPassBlurSurface`;
- Registry does not use `HookUtil` to reflect into `LauncherGlassSession` (or rely on the existing `HookUtilArchitectureContractTest` and omit duplicate assertions).

Do not assert callback ordering, PREPARE sequencing, fresh-frame sequencing, method body substrings, or source index ordering.

- [ ] **Step 2: Convert scene/widget tests to direct calls**

Examples:

```java
LauncherGlassSceneController.StateMachine state =
        new LauncherGlassSceneController.StateMachine();
state.onRootReady();
state.onFreshFrameReady(state.generation());
assertTrue(state.isLayerVisible());
```

```java
LauncherWidgetTransitionState state = new LauncherWidgetTransitionState();
state.beginReturnWaitingFresh(42L);
assertFalse(state.onFreshFrame(41L));
assertTrue(state.onFreshFrame(42L));
```

- [ ] **Step 3: Convert Dock animation test helpers to direct package access**

Replace reflective `observeChanged/sample/readFloat/readBoolean` helpers with:

```java
assertTrue(state.observeProxyFrame(icon, 0.90f, 1_100L));
DockIconAnimationState.Sample sample = state.sample(icon, 1_190L);
assertEquals(0.75f, sample.opacity, 0.0001f);
assertTrue(sample.fading);
```

- [ ] **Step 4: Delete the mixed unlock source-string contract and run tests**

Run the four direct state-test classes plus `LauncherGlassStaticBoundaryTest`; expected PASS.

- [ ] **Step 5: Commit**

Commit message: `test: use direct runtime state inputs and outputs`

---

### Task 7: Enforce the testing boundary and final audit

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/RuntimeBehaviorTestPolicyContractTest.java`
- Modify: `CONTRIBUTING.md`

**Interfaces:**
- Structural test policy gate is intentionally static because forbidden test style is itself a source/API rule.

- [ ] **Step 1: Add a narrow RED/green policy gate**

Walk only test Java filenames that match runtime-behavior categories (`*StateTest.java`, `*PolicyTest.java`, `*RecoveryTest.java`, `*OwnershipTest.java`, `*AnimationTest.java`) and assert they do not contain `Files.readString(`, `source.contains(`, or `indexOf(` used for production source sequencing. Exclude this gate itself.

Do not globally ban `Files.readString`, because R8/Gradle/Manifest/API-ban tests are allowed and historical Grid/UI source contracts are out of scope.

- [ ] **Step 2: Update `CONTRIBUTING.md`**

Add explicit rules:

- runtime ownership/freshness/animation/recovery -> production-used Android-free state/policy + real input/output tests;
- R8/Gradle/Manifest/API bans -> static inspection allowed;
- source strings must not be used to prove runtime call order, lifecycle sequencing, callback timing, teardown, or reveal freshness.

- [ ] **Step 3: Run complete unit tests**

Run: `./gradlew testDebugUnitTest --stacktrace`
Expected: PASS.

- [ ] **Step 4: Run build**

Run: `./gradlew assembleDebug --stacktrace`
Expected: PASS.

- [ ] **Step 5: Audit remaining source-string tests**

Search `src/test/java` for `Files.readString` and `source.contains`. For every remaining hit, classify it as one of:

- R8/keep rule;
- Gradle/module/dependency;
- Manifest/Xposed scope;
- API/architecture ban;
- persisted schema/declarative completeness;
- deferred Grid/UI/resource/renderer implementation contract outside this PR.

There must be no remaining in-scope ownership/freshness/animation/recovery runtime assertion based on source text.

- [ ] **Step 6: Final PR verification**

Open Draft PR to `main`; ensure base is the current main containing `b6e3caa227a6291de879085f395c2fb16b893994` or later. Run PR CI and require `testDebugUnitTest`, `assembleDebug`, APK artifact, and source artifact success. Review changed filenames for unrelated runtime changes, then squash merge only after final exact-head CI is green.

- [ ] **Step 7: Commit**

Commit message: `test: enforce pure runtime behavior contracts`
