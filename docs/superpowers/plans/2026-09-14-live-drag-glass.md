> [!NOTE]
> **Historical record / 历史记录。** 本文件记录其日期对应的计划、设计或验证快照，不维护为当前实现契约。当前行为请以 [README](../../../README.md)、[FEATURES](../../../FEATURES.md)、[ARCHITECTURE](../../../ARCHITECTURE.md)、[HOOKS](../../../HOOKS.md) 和 [TODO](../../../TODO.md) 为准。

# Live Drag Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the frozen drag backdrop in PR #175 with a continuously sampled upper-window drag glass path that excludes its own visual mirror.

**Architecture:** Keep MIUI DragView in Launcher for logic only, mirror its visual into an excluded `TYPE_APPLICATION_PANEL`, and render LiquidDock glass in that same upper window from a continuously updating PassBlur source. Remove all drag snapshot/freeze gates.

**Tech Stack:** Android View/TextureView, Xposed-style hooks, HyperOS Launcher 4.50 reverse-engineered lifecycle, SurfaceTexture/OES, EGL/GLES2, PassBlur, Prismal.

**Spec:** `docs/superpowers/specs/2026-09-14-live-drag-glass-design.md`

## Global Constraints

- No Bitmap, PixelCopy, ScreenCapture, CPU readback, or texture re-upload.
- No fixed delay for capture or teardown.
- Fail closed to the vendor DragView visual.
- Keep the small-folder preview scaling fix intact.
- PassBlur source remains live for the full drag.

---

### Task 1: Lock the live-drag architecture with RED contracts

**Files:**
- Modify: `src/test/java/com/hellovoid/liquiddock/FolderScaleAndDragBackdropContractTest.java`

**Interfaces:**
- Consumes: current `LauncherGlassDragOverlay`, `LauncherDragSourceOverlay`, `LauncherGlassSession`, `MiuixLauncherDragOverlayHook`.
- Produces: contracts forbidding drag freeze/snapshot code and requiring an upper-window visual mirror plus continuous source updates.

- [ ] **Step 1: Write failing tests** asserting drag code does not call `freezeAfterNextFreshFrame`, does not use clean-capture alpha gating, creates a visual mirror hosted by the drag overlay window, keeps producer updates enabled, and publishes mirror/glass geometry from the Choreographer frame.
- [ ] **Step 2: Run CI and verify RED** with only the new live-drag contracts failing.
- [ ] **Step 3: Commit the RED tests.**

### Task 2: Turn the drag source overlay into the complete upper presentation window

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherDragSourceOverlay.java`
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherDragVisualMirror.java`

**Interfaces:**
- Consumes: Launcher window token, authoritative DragView transform/content.
- Produces: an upper window root exposing a host for glass output and a non-interactive visual mirror.

- [ ] **Step 1: Add mirror-host API to `LauncherDragSourceOverlay`** so the overlay owns both glass and dragged-object visual children.
- [ ] **Step 2: Implement `LauncherDragVisualMirror`** using GPU/View drawing of the existing DragView content without bitmap readback; it must be non-touchable/non-focusable and update bounds/scale/rotation/alpha from the authoritative DragView each frame.
- [ ] **Step 3: Add fail-closed lifecycle** so mirror/output teardown restores vendor visibility before window disposal.
- [ ] **Step 4: Run targeted tests and commit.**

### Task 3: Remove frozen-backdrop drag semantics and keep PassBlur live

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassDragOverlay.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherDragOverlayHook.java`

**Interfaces:**
- Consumes: live `PassBlurBindRequest.dragOverlay(sourceOverlay)` session.
- Produces: continuous backdrop rendering plus per-frame upper-window glass/mirror geometry.

- [ ] **Step 1: Delete drag clean-capture state** (`cleanCapture*`, `freezeAfterNextFreshFrame` calls, `launcher-drag-frozen` behavior) from the drag path while leaving any non-drag session functionality untouched.
- [ ] **Step 2: Keep producer updates enabled** throughout drag and request/accept fresh PassBlur frames continuously using the existing configured source FPS.
- [ ] **Step 3: Move the drag glass sink into the upper overlay root** instead of hosting it as a sibling under Launcher.
- [ ] **Step 4: In each Choreographer frame, read DragView transform once and publish it to both mirror and glass geometry.**
- [ ] **Step 5: Hide the original DragView visual only after the upper mirror and glass path are presentation-ready; restore it on any failure/end.**
- [ ] **Step 6: Run targeted tests and commit.**

### Task 4: Verify vendor lifecycle and failure recovery

**Files:**
- Modify as needed: `src/test/java/com/hellovoid/liquiddock/FolderScaleAndDragBackdropContractTest.java`
- Modify as needed: runtime tests covering drag lifecycle.

**Interfaces:**
- Consumes: drag start/end hooks and overlay lifecycle.
- Produces: guarantees that vendor DragView never stays hidden after failure/end and no overlay window survives drag teardown.

- [ ] **Step 1: Add regression tests** for init failure, producer failure, drag end, root detach, and repeated drag start/end.
- [ ] **Step 2: Run full unit suite.**
- [ ] **Step 3: Run zero-copy audit and `assembleDebug`.**
- [ ] **Step 4: Commit any lifecycle fixes.**

### Task 5: Final clean-head verification

**Files:**
- No production changes unless verification reveals a defect.

**Interfaces:**
- Produces: CI artifact suitable for device testing.

- [ ] **Step 1: Confirm PR head contains no temporary patch workflow.**
- [ ] **Step 2: Run the normal `API101 migration build` on the clean head.**
- [ ] **Step 3: Verify zero-copy audit, unit tests, debug assembly/R8, and artifact upload all succeed.**
- [ ] **Step 4: Report head SHA, workflow run, artifact ID and SHA-256; do not claim device correctness before user testing.**
