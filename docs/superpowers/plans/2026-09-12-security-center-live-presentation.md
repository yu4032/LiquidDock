# Security Center Live Presentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve Security Center's strict first-presentation safety barrier while allowing same-generation PassBlur frames to continuously repaint already-authorized Prismal outputs.

**Architecture:** Add one Android-free phase state that distinguishes first strict presentation, rebound strict presentation, and LIVE. Wire `SecurityCenterGlassSession` so only the first two presentations use serial/TextureView ownership barriers; later same-generation source frames reuse the existing Prismal draw path directly. Any new request, rebind/recovery, output mutation, or shutdown invalidates LIVE and falls back to the strict pipeline.

**Tech Stack:** Java, Android TextureView/EGL, existing `RootPassBlurBackend`, Prismal renderer, JUnit4, GitHub Actions API101 workflow.

**Spec:** `docs/superpowers/specs/2026-09-12-security-center-live-presentation-design.md`

## Global Constraints

- Work only on `fix/r8-security-center-self-reflection` / PR #148.
- No local Gradle/Javac/test/assemble/lint/emulator validation; formal validation only through `.github/workflows/api101-build.yml`.
- No `ScreenCapture`, `PixelCopy`, `Bitmap.createBitmap`, stale screenshot, CPU backdrop copy, or `postDelayed` in Security Center/root PassBlur code.
- No hardcoded vendor-obfuscated names in source, tests, docs, comments, or config tables.
- Preserve Game vendor-only routing and Launcher/Dock producer behavior.
- Fail closed on stale generation, detached root, missing sink/output, or producer recovery.

---

### Task 1: Replace the discarded demand-pulse RED with live-phase state tests

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterLivePresentationState.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterLivePresentationStateTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/RootPassBlurBackendBoundaryTest.java`

**Interfaces:**
- Produces: `void onStrictPresentation(long generation, boolean requestedFollowUp)`, `boolean isLive(long generation)`, `void invalidate()`.

- [ ] **Step 1: Write the failing state tests**

```java
@Test public void firstStrictPresentationNeverEntersLive() {
    SecurityCenterLivePresentationState state = new SecurityCenterLivePresentationState();
    state.onStrictPresentation(7L, true);
    assertFalse(state.isLive(7L));
}

@Test public void reboundStrictPresentationEntersSameGenerationLive() {
    SecurityCenterLivePresentationState state = new SecurityCenterLivePresentationState();
    state.onStrictPresentation(7L, true);
    state.onStrictPresentation(7L, false);
    assertTrue(state.isLive(7L));
    assertFalse(state.isLive(8L));
}

@Test public void differentGenerationOrInvalidationCannotInheritLive() {
    SecurityCenterLivePresentationState state = new SecurityCenterLivePresentationState();
    state.onStrictPresentation(7L, true);
    state.onStrictPresentation(8L, false);
    assertFalse(state.isLive(8L));
    state.onStrictPresentation(7L, false);
    assertFalse(state.isLive(7L));
    state.onStrictPresentation(9L, true);
    state.onStrictPresentation(9L, false);
    assertTrue(state.isLive(9L));
    state.invalidate();
    assertFalse(state.isLive(9L));
}
```

Remove `securityCenterFreshRequestsUseDemandPulseProducer`; retain the generic backend reflection/no-delay contracts.

- [ ] **Step 2: Run API101 and verify RED**

Expected: zero-copy audit passes; new state tests fail before implementation, with no unrelated failures.

- [ ] **Step 3: Implement the minimal Android-free state**

```java
final class SecurityCenterLivePresentationState {
    private long awaitingGeneration = -1L;
    private long liveGeneration = -1L;

    synchronized void onStrictPresentation(long generation, boolean requestedFollowUp) {
        if (generation < 0L) return;
        if (requestedFollowUp) {
            awaitingGeneration = generation;
            liveGeneration = -1L;
            return;
        }
        if (awaitingGeneration == generation) {
            liveGeneration = generation;
            awaitingGeneration = -1L;
        } else {
            invalidate();
        }
    }

    synchronized boolean isLive(long generation) {
        return generation >= 0L && generation == liveGeneration;
    }

    synchronized void invalidate() {
        awaitingGeneration = -1L;
        liveGeneration = -1L;
    }
}
```

- [ ] **Step 4: Run API101 and verify state tests pass**

Expected: all existing tests plus the new state tests pass before Session integration contracts are added.

- [ ] **Step 5: Commit**

Commit message: `feat(security-center): add live presentation phase state`.

### Task 2: Add RED integration contracts for strict-to-live rendering

**Files:**
- Modify: `src/test/java/com/hellovoid/liquiddock/SecurityCenterLauncherStylePresentationContractTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/SecurityCenterFramePipelineStateTest.java` only if a regression assertion is needed; do not alter pipeline semantics merely to satisfy LIVE.

**Interfaces:**
- Consumes: `SecurityCenterLivePresentationState` from Task 1.
- Requires Session helpers: `renderLiveFrame(...)` and `invalidateLivePresentation()` or equivalent explicit names.

- [ ] **Step 1: Add static contracts**

Require `SecurityCenterGlassSession` to:

```java
assertTrue(session.contains("SecurityCenterLivePresentationState"));
assertTrue(session.contains("livePresentation.isLive(frame.generation)"));
assertTrue(session.contains("renderLiveFrame("));
assertTrue(session.contains("livePresentation.onStrictPresentation("));
assertTrue(session.contains("livePresentation.invalidate()"));
assertTrue(session.contains("presentationBarrier.begin("));
assertTrue(session.contains("armPresentation("));
```

Also assert that LIVE rendering does not call `armPresentation` inside the `renderLiveFrame` helper and does not call `listener.onFrameRendered` from that helper.

- [ ] **Step 2: Run API101 and verify only the new integration contract fails**

Expected: one or a small, precisely scoped set of new contract failures; no compile failures and no old architecture failures.

- [ ] **Step 3: Commit RED**

Commit message: `test(security-center): require strict-to-live presentation handoff`.

### Task 3: Wire Session live rendering without changing RootPassBlur backend

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassSession.java`

**Interfaces:**
- Uses: `SecurityCenterLivePresentationState`.
- Keeps: `SecurityCenterFramePipelineState` and `SecurityCenterPresentationBarrier` unchanged as strict-presentation authorities.

- [ ] **Step 1: Add phase state and invalidation points**

Add:

```java
private final SecurityCenterLivePresentationState livePresentation =
        new SecurityCenterLivePresentationState();
```

Call `livePresentation.invalidate()` before accepting a new logical `FrameRequest`, before `requestSourceRebind`, before window-visibility producer recovery, on output attach/resize/detach mutation, and during shutdown/reset.

- [ ] **Step 2: Mark strict presentations**

After `framePipeline.onPresented(...)` is accepted and outputs remain current, call:

```java
livePresentation.onStrictPresentation(generation, presented.requestSource);
```

The first presentation requests the post-handoff source and cannot enter LIVE. The next accepted same-generation presentation with no requested follow-up enters LIVE.

- [ ] **Step 3: Add direct live render path**

At the beginning of `onFreshFrame`, after validating backend/frame and reading the latest `frameRequest`, branch to `renderLiveFrame(frame, request)` when `livePresentation.isLive(frame.generation)`.

`renderLiveFrame` must validate root dimensions, latest sink set, and current EGL outputs, then reuse:

```java
prismalRenderer.prepareBackdrop(...);
for (...) {
    prismalRenderer.beginGlassFrame();
    prismalRenderer.drawGlass(...);
    presentTarget(prismalRenderer.outputTexture(), geometry, output);
    sink.requestPresentationDraw();
}
```

Do not call `framePipeline.onFreshSource`, `presentationBarrier.begin`, `armPresentation`, or listener ownership callbacks in this helper.

- [ ] **Step 4: Keep fail-closed behavior**

If LIVE validation fails, invalidate LIVE and return; do not reveal a stale/new-generation frame. The next logical geometry/generation event must re-enter the strict path.

- [ ] **Step 5: Run API101**

Expected: zero-copy/no-delay audit, all unit tests, and assembleDebug pass.

- [ ] **Step 6: Commit**

Commit message: `fix(security-center): keep Prismal backdrop live after handoff`.

### Task 4: Final verification and artifact audit

**Files:** No source changes unless CI reveals a concrete failure.

- [ ] **Step 1: Verify current branch head**

Confirm PR #148 head equals the final implementation commit and no other branch was created or force-updated.

- [ ] **Step 2: Inspect final API101 logs**

Require zero-copy/no-delay audit success, all tests success, `assembleDebug` success, and artifact uploads success.

- [ ] **Step 3: Download artifacts and scan source artifact**

Verify zero occurrences in Security Center/root PassBlur sources for `ScreenCapture`, `Bitmap.createBitmap`, `PixelCopy`, and `postDelayed(`. Verify `SecurityCenterLivePresentationState`, strict barrier calls, and the live render helper are present.

- [ ] **Step 4: Report device acceptance signals**

Expected logs after the strict two-presentation startup should continue to show repeated same-generation source/render activity while the Sidebar remains open, without repeated ownership handoffs. A new generation/rebind must visibly return to the strict `submitted -> TextureView ACK -> presented` sequence before LIVE resumes.
