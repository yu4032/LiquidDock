# Recents Capsule Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply native HyperOS realtime glass blur to the Pad Recents Clear All capsule and Device Interconnect capsule without changing their vendor geometry, click behavior, or animations.

**Architecture:** Hook the stable `com.miui.home.recents.views.RecentsDecorations.findAndSetupViews` boundary after vendor inflation/setup. Resolve only the two stable resource IDs `recent_clear_all_task_container_for_pad` and `world_container`, then enable the existing `MiBlurBridge` pass-window backdrop blur on those exact native background owners. Keep the vendor drawable authoritative for tint/corners and clean blur state symmetrically on detach.

**Tech Stack:** Android Views, HyperOS hidden View blur APIs via existing `MiBlurBridge`, API101 HookUtil, JUnit source-contract tests.

**Spec:** OS3 Launcher 4.50.0.1204 decompilation (`RecentsDecorations`, `recent_clear_all_task_container_for_pad.xml`, `world_view_container.xml`).

## Global Constraints

- Branch from current `main`; never implement directly on `main`.
- No JADX/R8 obfuscated member names or hard-coded numeric `0x7f...` resource IDs.
- No reflective field crawling, no fixed delays, no click-listener replacement.
- Preserve vendor background drawable, Folme touch animation, visibility, and lifecycle authority.
- Fail closed: if the target ID or pass-window blur API is unavailable, leave the stock capsule unchanged.

---

### Task 1: Lock the semantic-hook contract

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherRecentsCapsuleGlassContractTest.java`

**Interfaces:**
- Consumes: existing `LauncherGlassRecentsHook`, `MiBlurBridge`.
- Produces: source contract for stable Recents target resolution and lifecycle-safe blur.

- [ ] Write a failing source-contract test requiring stable `RecentsDecorations.findAndSetupViews`, both resource names, `MiBlurBridge.applyPassWindowBlur`, detach cleanup, and prohibiting obfuscated/numeric hooks, background replacement, and `postDelayed`.
- [ ] Run `./gradlew testDebugUnitTest --tests '*LauncherRecentsCapsuleGlassContractTest'` and confirm it fails before implementation.

### Task 2: Implement native capsule backdrop glass

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherRecentsCapsuleGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassRecentsHook.java`

**Interfaces:**
- `LauncherRecentsCapsuleGlassHook.install(ClassLoader)` installs once.
- Existing `LauncherGlassRecentsHook.install(...)` delegates capsule installation after the Recents dispatcher hook setup.

- [ ] Hook `RecentsDecorations.findAndSetupViews`, proceed first, then resolve the two native capsule IDs with Android resources.
- [ ] Bind each resolved capsule exactly once with an attach-state listener; apply current `LiquidDockConfig.glass.blur` through `MiBlurBridge.applyPassWindowBlur` while attached and clear it on detach.
- [ ] Leave the stock background drawable and all listeners untouched; log bounded success/failure.
- [ ] Run the focused contract test and then `./gradlew testDebugUnitTest assembleDebug --stacktrace`.

### Task 3: CI verification

**Files:** none.

- [ ] Push the implementation branch / PR and verify GitHub Actions `Unit + debug assembly` succeeds.
- [ ] Inspect the diff for accidental unrelated Recents producer/lifecycle changes before handoff.
