# Security Center Material-Lifetime Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor Security Center glass ownership so the actual vendor material carrier Views (`o0`, `y1`, `w`) own sink lifetime while one root-wide PassBlur session survives subtree replacement.

**Architecture:** Keep `SecurityCenterGlassSession`/`RootPassBlurBackend` root-wide. Replace scene/page lifecycle inference with material-subtree epochs and carrier identity replacement. A fresh `TurboLayout.V(...)` binding refresh replaces stale Dock/Game/Video carrier sinks; All Apps is driven by the actual `w` carrier lifecycle.

**Tech Stack:** Android Java, LSPosed/API101 hooks, MIUI private View material APIs, TextureView/SurfaceTexture, EGL/OpenGL, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-12-security-center-material-lifetime-design.md`

## Global Constraints

- Work only on `codex/security-center-timing-pipeline`; do not modify `main`.
- Do not add fixed delays, polling timers, or guessed animation timing.
- Do not change the root-wide PassBlur producer/output authority in this refactor.
- LiquidDock's caller-owned Security Center producer remains full-size; do not apply vendor `0.25` geometry scale.
- CI remains agile device-validation: build APK + zero-copy audit; stale scene-model unit tests are deferred until device acceptance.

---

### Task 1: Make configure bindings explicit material-subtree epochs

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/SecurityCenterEarlyPrepareHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeState.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java`

**Interfaces:**
- Consumes: semantic `TurboLayout.V(...)` configure hook and existing resolved Dock/Game/Video material getters.
- Produces: `bindAssistant(View turbo, View dock, View box, int type, long subtreeEpoch)` or equivalent epoch-bearing carrier refresh.

- [ ] **Step 1: Add an epoch source to each successful configure binding**

Use a monotonic counter in `SecurityCenterEarlyPrepareHook`; each invocation of the resolved configure method allocates a new epoch before arming readiness. Carry that epoch through `PendingPrepare` and the runtime bind call.

- [ ] **Step 2: Teach runtime/coordinator bind to distinguish root reuse from subtree replacement**

On the same `TurboLayout`, if Dock or Box carrier identity differs, dispose stale sinks, reset All Apps carrier, force a fresh custom handoff, but keep the root `SecurityCenterGlassSession` alive.

- [ ] **Step 3: Log carrier identities and epoch**

Emit one bind log containing epoch plus identity hashes of Turbo/Dock/Box so device logs can prove second-pull material replacement.

- [ ] **Step 4: Commit**

Commit message: `refactor: bind Security Center glass by material subtree`

---

### Task 2: Replace page-scene lifecycle with carrier registry semantics

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassFrameGeometry.java`

**Interfaces:**
- Consumes: current Dock/Box/Apps material refs and their sink readiness.
- Produces: current carrier composition without synthetic `DOCK/ALL_APPS/TRANSITIONING` ownership decisions.

- [ ] **Step 1: Remove All Apps settle/transition state from frame authority**

Stop using `SecurityCenterAllAppsSettleState` and `SecurityCenterGlassSceneState` to decide whether Apps may be composed. Keep only a current generation/serial sufficient for frame freshness and presentation ACK validation.

- [ ] **Step 2: Compose directly from attached material carriers**

`captureFrame()` requires Dock; includes Box if current Box material is attached/visible/presentation-ready; includes Apps if current Apps material is attached/visible/presentation-ready. Do not suppress Game solely because Apps exists.

- [ ] **Step 3: Force a fresh handoff when carrier set changes**

When Box/Apps carrier identity or membership changes, mark the current handoff pending and request a fresh frame. The next TextureView presentation ACK calls `claimCustom()` with the exact current carrier set.

- [ ] **Step 4: Commit**

Commit message: `refactor: drive Security Center composition from material carriers`

---

### Task 3: Make All Apps lifecycle follow `w` attach/remove only

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java`

**Interfaces:**
- Consumes: resolved All Apps motion helper only as a discovery point for the actual `w` View.
- Produces: `attachAllAppsCarrier(turbo, apps)` / `removeAllAppsCarrier(turbo, apps)` behavior driven by actual View lifetime.

- [ ] **Step 1: Stop translating motion-helper callbacks into synthetic transition scenes**

The attach hook records the actual `w` carrier after vendor attach. Dismiss hooks no longer create a transition generation.

- [ ] **Step 2: Observe actual `w` detach/removal**

Attach a View attach-state listener to the current Apps carrier. On detach, if identity still matches, retire only the Apps sink/ref and request a fresh Dock/Box composition.

- [ ] **Step 3: Keep Game inner-page transitions invisible to glass lifecycle**

Do not add hooks to `z1` or main/second/third containers. The Game carrier remains `y1` for the whole toolbox material subtree epoch.

- [ ] **Step 4: Commit**

Commit message: `refactor: inherit Security Center All Apps material lifetime`

---

### Task 4: CI/device validation and architecture cleanup

**Files:**
- Modify only if required for compilation: stale references to removed scene/settle methods.
- Keep: `.github/workflows/api101-build.yml` agile build behavior.

**Interfaces:**
- Produces: installable debug APK and logs proving material-subtree replacement.

- [ ] **Step 1: Trigger existing PR CI**

Expected build command remains `./gradlew assembleDebug --stacktrace` plus the zero-copy audit already present in workflow.

- [ ] **Step 2: Inspect compile/zero-copy failures**

Fix only issues caused by this refactor. Do not restore stale scene-model behavior merely to satisfy old tests.

- [ ] **Step 3: Device log acceptance markers**

Require logs showing distinct subtree epochs and distinct Dock/Game carrier identities on first vs second pull; All Apps carrier attach/detach must not recreate the root session.

- [ ] **Step 4: Final commit if CI-only fixes are needed**

Commit message: `fix: complete Security Center material-lifetime refactor`
