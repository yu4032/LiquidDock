# Restart-Bound Settings UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make settings that cannot be safely hot-toggled clearly communicate that Launcher restart is required, while leaving live visual ownership switches immediate.

**Architecture:** Keep runtime semantics unchanged for structural settings. Update Compose summaries/contract tests only where the underlying hook installation or grid structure is restart-bound.

**Tech Stack:** Kotlin Compose settings UI, Android resources where needed, JUnit 4 source-contract tests.

**Spec:** `docs/superpowers/specs/2026-08-25-runtime-toggle-ownership-design.md`

## Global Constraints

- Do not mark live visual switches as restart-bound.
- Do not implement live 6x4/8x4 grid transition.
- Do not change settings keys/defaults/export format.

---

### Task 1: Mark structural toggles restart-bound

**Files:**
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Test: `src/test/java/com/hellovoid/liquiddock/RestartBoundSettingsContractTest.java`

**Interfaces:**
- UI only; no runtime API changes.

- [ ] **Step 1: Write failing UI contract**

Assert the Compose UI uses summaries containing `重启桌面生效` for home-grid enable/disable, widget adaptation where exposed, Dock resize-animation hook selection, and structural workstation customization/geometry entry points. Assert Debug logging keeps its existing restart wording. Assert icon/widget/folder glass summaries do not gain restart wording.

- [ ] **Step 2: Run focused test and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.RestartBoundSettingsContractTest --stacktrace`

Expected: FAIL because structural summaries are currently ambiguous.

- [ ] **Step 3: Update summaries only**

Append concise `重启桌面生效` wording to affected structural Boolean settings and any section header/summary necessary to avoid repeating it on every numeric field. Keep visual ownership switches immediate and unchanged.

- [ ] **Step 4: Run focused test and verify GREEN**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `docs: mark structural settings restart-bound`.

### Task 2: Final verification

- [ ] **Step 1: Run all runtime-toggle contract tests**

Run: `./gradlew testDebugUnitTest --tests '*RuntimeToggle*' --tests '*RuntimeOwnership*' --tests '*RestartBoundSettingsContractTest' --stacktrace`

Expected: PASS.

- [ ] **Step 2: Run complete unit suite**

Run: `./gradlew testDebugUnitTest --stacktrace`

Expected: PASS with zero failures.

- [ ] **Step 3: Build APK**

Run: `./gradlew assembleDebug --stacktrace`

Expected: BUILD SUCCESSFUL.
