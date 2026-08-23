# Workspace Wallpaper Content Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add event-driven wallpaper content freshness to Workspace glass so same-geometry wallpaper replacements refresh the cached GPU backdrop without continuous producer updates.

**Architecture:** A new Android-free wallpaper content state machine owns generation/coalescing semantics. A new HyperOS wallpaper hook translates vendor events into candidate/authoritative refresh tokens. `LauncherGlassSceneController` routes tokens to the correct root and `LauncherGlassSession` associates producer pulses/consumed frames with wallpaper content generations while preserving the existing scene generation and pause-after-frame behavior.

**Tech Stack:** Java 17, Android/Xposed-style `HookUtil`, HyperOS 4.50 Launcher reflective hooks, SurfaceTexture/OES/PassBlur/GLES, JUnit host-side contract tests, Gradle.

**Spec:** `docs/superpowers/specs/2026-08-23-workspace-wallpaper-content-generation-design.md`

## Global Constraints

- GPU-only: no PixelCopy, ImageReader, Bitmap readback, glReadPixels, screen recording, or CPU wallpaper capture.
- Keep Workspace producer demand-driven and paused after a consumed fresh frame.
- No fixed-delay timers or polling loops.
- Do not conflate wallpaper content changes with Surface/geometry generation changes.
- Preserve current App→HOME, rotation, Surface generation, producer endpoint rollover, Dock, folder, widget, and icon behavior.
- Stale events/frames from an older wallpaper content generation must not commit a newer generation.

---

### Task 1: Wallpaper Content State Machine

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherWallpaperContentState.java`
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherWallpaperContentStateTest.java`

**Interfaces:**
- Produces: `long onWallpaperChanged()`, `Pulse onCandidateBoundary(long generation)`, `Pulse onAuthoritativeBoundary(long generation)`, `boolean onFrameCommitted(long generation, boolean authoritative)`, `long generation()`, `long committedGeneration()`.
- `Pulse` contains `generation` and `authoritative` and has an explicit no-op representation.

- [ ] **Step 1: Write failing state-machine tests** covering monotonic generations, candidate coalescing, authoritative-after-candidate, duplicate authoritative coalescing, and stale event/frame rejection.
- [ ] **Step 2: Run `./gradlew testDebugUnitTest --tests '*LauncherWallpaperContentStateTest' --stacktrace` and verify RED because the production type does not exist.**
- [ ] **Step 3: Implement the minimal Android-free state machine.**
- [ ] **Step 4: Re-run the focused tests and verify GREEN.**
- [ ] **Step 5: Commit state machine + tests.**

### Task 2: Scene/Session Wallpaper Token Plumbing

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSceneController.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherWallpaperBackdropGenerationContractTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/LauncherGlassSceneControllerTest.java`

**Interfaces:**
- Consumes: `LauncherWallpaperContentState.Pulse`.
- Produces: `LauncherGlassSceneController.requestWallpaperFreshForRoot(View root, long generation, boolean authoritative)` and `LauncherGlassSession.requestWallpaperBackdrop(long generation, boolean authoritative)`.

- [ ] **Step 1: Write failing contracts** proving wallpaper refresh has a distinct content-generation path, does not call `onGenerationInvalidated()`, and does not depend on producer geometry changes.
- [ ] **Step 2: Run focused tests and verify RED.**
- [ ] **Step 3: Add separate wallpaper requested/consumed generation fields to the session, invalidate only backdrop/frame freshness for wallpaper pulses, and keep scene visibility unchanged.**
- [ ] **Step 4: Associate consumed OES frames with the requested wallpaper generation and preserve `pauseUpdates(binding)` after consumption.**
- [ ] **Step 5: Run focused tests and existing scene/session lifecycle contracts; verify GREEN.**
- [ ] **Step 6: Commit token plumbing.**

### Task 3: HyperOS 4.50 Wallpaper Event Hook

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherWallpaperFreshnessHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherWallpaperFreshnessHookContractTest.java`

**Interfaces:**
- Consumes: `ClassLoader`, current `Workspace` root, `LauncherWallpaperContentState`.
- Produces: candidate and authoritative calls to `LauncherGlassSceneController.requestWallpaperFreshForRoot(...)`.

- [ ] **Step 1: Write failing source/behavior contracts** requiring hooks for `DesktopWallpaperManager`, `Workspace.onWallpaperColorChanged`, `onWallpaperFirstFrameRendered`, and `onDrawFrameEnd`, while forbidding `ACTION_WALLPAPER_CHANGED`, fixed-delay posts, and continuous Workspace producer mode.
- [ ] **Step 2: Run focused tests and verify RED.**
- [ ] **Step 3: Implement reflective/version-tolerant hook installation and root routing; duplicate callbacks must be coalesced by the state machine.**
- [ ] **Step 4: Install the hook from the existing MiuiX pipeline without changing Dock behavior.**
- [ ] **Step 5: Run focused tests and verify GREEN.**
- [ ] **Step 6: Commit vendor hook integration.**

### Task 4: Full Regression and Build Verification

**Files:**
- No production expansion beyond Tasks 1–3 unless a failing existing contract exposes a real compatibility issue.

- [ ] **Step 1: Run `./gradlew testDebugUnitTest --stacktrace`.**
- [ ] **Step 2: Run `./gradlew assembleDebug --stacktrace`.**
- [ ] **Step 3: Verify the source contains no PixelCopy/ImageReader/Bitmap readback/glReadPixels additions and no timer/polling workaround.**
- [ ] **Step 4: Verify Workspace still pauses PassBlur after consuming a fresh frame and Dock continuous-on-bind policy is unchanged.**
- [ ] **Step 5: Open a Draft PR against `main` and let standard PR CI test the synthetic merge tree.**
- [ ] **Step 6: Download and validate the CI APK/source artifacts; keep PR Draft until target-device wallpaper replacement validation.**
