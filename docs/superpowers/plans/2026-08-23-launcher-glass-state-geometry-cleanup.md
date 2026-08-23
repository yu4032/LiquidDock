# Launcher Glass State and Geometry Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make repository source equal the validated #3139 APK source, then converge Launcher glass lifecycle/state/geometry authority without changing validated producer behavior.

**Architecture:** Retain Controller-owned generation, Session-owned producer/Surface detection, wallpaper content generation, and rotation settle. Remove lifecycle callbacks that manufacture source invalidation, replace multiple static-node observers with one final geometry Snapshot comparison, and make ViewTreeObserver registration identity-aware.

**Tech Stack:** Android/Java, LSPosed hooks, MIUI/HyperOS Launcher vendor APIs, Gradle unit tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-23-launcher-glass-state-geometry-cleanup-design.md`

## Global Constraints

- Base is exactly `1351e4af4381be181c382ac4bce2975888228dab`.
- Materialize all four `ci/24bug-*.patch` patches before semantic refactoring.
- Do not rewrite endpoint rollover, rotation settle, wallpaper token/generation behavior, Layer2 `mIsDrawIcon`, Folder/Recents covered semantics, or Surface triple identity.
- `requestFreshBackdrop()` is reserved for real source invalidation.
- Controller remains the only scene-generation creator.
- Prefer behavior tests; do not add source-string deletion tests.

---

### Task 1: Materialize the validated CI source

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSceneController.java`
- Modify any additional source/test file targeted by the four patch files, exactly as specified by those patches.
- Modify: `.github/workflows/api101-build.yml`
- Delete: `ci/24bug-launcher-session-green.patch`
- Delete: `ci/24bug-scene-controller-green.patch`
- Delete: `ci/24bug-resize-pulse-order.patch`
- Delete: `ci/24bug-rotation-settle.patch`

**Interfaces:**
- Consumes: current repository source + four CI patches.
- Produces: source tree whose checked-in Java/test files are byte-equivalent in behavior to the files CI previously produced after `git apply`.

- [ ] **Step 1: Enumerate every path touched by all four patches.**

Read each unified diff and build the complete path set. Do not infer from patch filenames.

- [ ] **Step 2: Apply the four diffs in existing workflow order.**

Order must remain:

```text
24bug-launcher-session-green.patch
24bug-scene-controller-green.patch
24bug-resize-pulse-order.patch
24bug-rotation-settle.patch
```

- [ ] **Step 3: Remove the workflow materialization step and the four patch files.**

The test/assemble commands remain unchanged.

- [ ] **Step 4: Verify with Actions.**

Expected workflow commands:

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected: both pass with no `git apply` step.

- [ ] **Step 5: Commit.**

```bash
git commit -m "build: materialize validated launcher glass fixes"
```

### Task 2: Remove dead and shadow state

**Files:**
- Delete: `src/main/java/com/hellovoid/liquiddock/LauncherGlassGeometryStability.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassFramePolicy.java`
- Modify/delete corresponding unit tests.

**Interfaces:**
- Consumes: materialized validated source.
- Produces: identical visible behavior with one representation per state fact.

- [ ] **Step 1: Identify behavior tests that cover consumed-generation and covered-state transitions independently of deleted fields.**
- [ ] **Step 2: Run those tests as the baseline.**
- [ ] **Step 3: Remove `hasConsumedFrame`; replace all predicates/assignments with `consumedGeneration >= 0` semantics.**
- [ ] **Step 4: Remove `StateMachine.covered`; derive it from `state == COVERED`.**
- [ ] **Step 5: Delete `LauncherGlassGeometryStability` and its tests/callers.**
- [ ] **Step 6: Delete `requestBackdropRefresh()` and its test-only contract.**
- [ ] **Step 7: Run unit tests and commit.**

```bash
git commit -m "refactor: remove launcher glass shadow state"
```

### Task 3: Correct HOME lifecycle semantics

**Files:**
- Modify the hook containing `setAnimTargetVisibility` handling.
- Modify the Launcher `onResume` recovery hook.
- Remove the Mingou-specific recovery hook.
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSceneController.java` only where necessary to expose behavior already represented by VisualOwnerState/ProxyVisibility.
- Modify behavior tests such as `WorkspaceResumeRecoveryTest` / `AppReturnProxyGeometryTest` as appropriate.

**Interfaces:**
- Consumes: `LauncherGlassVisualOwnerState`, `LauncherGlassProxyVisibility`, current-page reconcile/pre-draw flow.
- Produces: ordinary VISIBLE/onResume events do not cause a fresh producer; an existing CLOSE_TO_HOME owner still terminates correctly.

- [ ] **Step 1: Write/retain a failing behavior test proving ordinary VISIBLE does not request HOME recovery while CLOSE_TO_HOME VISIBLE ends its proxy owner.**
- [ ] **Step 2: Run the focused test and confirm RED for the ordinary-VISIBLE recovery path.**
- [ ] **Step 3: Remove `scheduleWorkspaceRecoveryFromHost(..., "anim-target-visible")` from ordinary VISIBLE handling.**
- [ ] **Step 4: Remove Mingou recovery and replace `onResume` fresh-producer forcing with current-page reconcile/normal pre-draw only.**
- [ ] **Step 5: Run focused and full tests; commit.**

```bash
git commit -m "refactor: separate home visibility from source invalidation"
```

### Task 4: Converge static Workspace geometry to final Snapshot authority

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassGeometry.java` only if a reusable capture overload is needed.
- Delete: `LauncherGlassScrollMotionTracker.java`
- Delete: `LauncherGlassRootTransformTracker.java`
- Delete: `LauncherGlassEffectiveVisibilityTracker.java`
- Delete/merge corresponding tests.

**Interfaces:**
- Consumes: `LauncherGlassGeometry.Snapshot` final root-space geometry.
- Produces: one Snapshot capture and one previous-Snapshot comparison per static node per pre-draw; node-owned reusable Matrix/point buffers.

- [ ] **Step 1: Add/retain behavior tests showing ancestor scroll, ancestor/local transform, bounds, alpha, and visibility changes alter the final Snapshot.**
- [ ] **Step 2: Run focused tests.**
- [ ] **Step 3: Give each StaticNode reusable Matrix/point buffers and capture exactly one final Snapshot during pre-draw.**
- [ ] **Step 4: Compare the new Snapshot directly against the previous Snapshot; remove cached bounds/visibility/alpha/local Matrix and all tracker fields.**
- [ ] **Step 5: Delete the three tracker classes and tests whose behavior is now covered by Snapshot tests.**
- [ ] **Step 6: Run full tests and commit.**

```bash
git commit -m "refactor: make geometry snapshot the static node authority"
```

### Task 5: Make ViewTreeObserver registration identity-aware

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Test the observer ownership behavior at the nearest existing test seam.

**Interfaces:**
- Consumes: root `ViewTreeObserver` identity/liveness.
- Produces: listener re-registration only on first install, dead observer, or observer identity replacement.

- [ ] **Step 1: Add a behavior test for same-observer fresh recovery not causing a detach/attach cycle.**
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Change `recoverFreshBackdropOnUi()` to compare stored/current observer identity and liveness before re-registering.**
- [ ] **Step 4: Run focused/full tests and commit.**

```bash
git commit -m "refactor: retain stable launcher view tree observer"
```

### Task 6: Test subtraction and final regression audit

**Files:**
- Delete/merge obsolete helper/source-shape tests.
- Keep rotation, wallpaper, proxy ownership, Layer2, Folder/Recents semantic tests unchanged unless a compile-only reference removal is required.

**Interfaces:**
- Produces: behavior-oriented suite with no tests solely preserving deleted implementation details.

- [ ] **Step 1: Remove obsolete `ScrollMotionTrackerTest`, resume-recovery/source-shape contracts, and dead helper tests after their replacement behavior coverage is green.**
- [ ] **Step 2: Reduce `AppReturnProxyGeometryTest` to VisualOwnerState/ProxyVisibility behavior only.**
- [ ] **Step 3: Run the full workflow.**

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

- [ ] **Step 4: Diff-audit hard regression paths.**

Confirm no structural rewrite of rotation settle, endpoint rollover, wallpaper candidate/authoritative tokens, Surface triple identity, Layer2 `mIsDrawIcon`, or Folder/Recents semantic covered behavior.

- [ ] **Step 5: Commit final test cleanup and open a PR for review/hardware validation.**
