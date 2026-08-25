# Dock Runtime Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Dock customization, Dock stroke/shadows, divider customization, and workstation Dock customization stop mutating vendor Views immediately when disabled and restore LiquidDock-owned state where exact restoration is available.

**Architecture:** Add a process-local runtime state owner for non-glass visual switches. Hooks install inertly where safe and consult live state before mutation. Each owner captures the vendor state it changes and exposes an explicit teardown/refresh entry point used on true -> false transitions.

**Tech Stack:** Java 17, Android Views/Drawables/LayoutParams, libxposed API101 hooks, JUnit 4 source-contract tests, Gradle Android build.

**Spec:** `docs/superpowers/specs/2026-08-25-runtime-toggle-ownership-design.md`

## Global Constraints

- Do not fabricate native shadow/layout values that were not captured before mutation.
- Numeric style keys need not become instant hot reload unless existing code already refreshes them safely.
- Grid and resize-animation hook selection remain restart-bound.
- Do not change workstation mode detection semantics.

---

### Task 1: Add live non-glass visual switch state

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/VisualRuntimeState.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Test: `src/test/java/com/hellovoid/liquiddock/VisualRuntimeToggleContractTest.java`

**Interfaces:**
- Produces: `isDockCustomizationEnabled()`, `isDockStrokeEnabled()`, `isDockShadowEnabled()`, `isStrokeShadowEnabled()`, `isDividerEnabled()`, `isWorkstationDockEnabled()`.
- Produces teardown dispatch to `MainHook`, `DockStrokeRenderer`, `DockDividerHook`, and `WorkstationDockGeometryHook`.

- [ ] **Step 1: Write failing runtime-state contract**

Assert the state listener tracks `Dock.ENABLED`, `Dock.STROKE_ENABLED`, `Dock.SHADOW_ENABLED`, `Dock.STROKE_SHADOW`, `Divider.ENABLED`, and `Workstation.DOCK_CUSTOMIZATION`, and `ModuleMain` seeds initial values.

- [ ] **Step 2: Run focused test and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.VisualRuntimeToggleContractTest --stacktrace`

Expected: FAIL because `VisualRuntimeState` does not exist.

- [ ] **Step 3: Implement minimal runtime state**

Publish booleans before scheduling main-thread teardown. Keep listener registration centralized in `VisualRuntimeState.initialize(...)`.

- [ ] **Step 4: Run focused test and verify GREEN**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `fix: track dock visual toggles at runtime`.

### Task 2: Make Dock customization and whole-Dock shadow reversible

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/DockRuntimeOwnershipContractTest.java`

**Interfaces:**
- Consumes: `VisualRuntimeState.isDockCustomizationEnabled()` and `isDockShadowEnabled()`.
- Produces: `MainHook.onRuntimeDockCustomizationDisabled()` and `MainHook.onRuntimeDockShadowDisabled()`.

- [ ] **Step 1: Write failing Dock ownership contract**

Assert width/height/radius/spacing/blur hooks check live Dock customization state, native-shadow suppression passes vendor calls through when false, `syncDockShadow` checks both Dock customization and Dock shadow live state, and teardown removes/hides the LiquidDock shadow.

- [ ] **Step 2: Run focused test and verify RED**

Expected: FAIL on missing live guards and teardown methods.

- [ ] **Step 3: Implement minimal reversible behavior**

Guard every Hook mutation before rewriting arguments. Native-shadow suppression must call vendor code unchanged while Dock customization is false. Whole-Dock shadow teardown removes the owned shadow View from its parent when possible and clears the weak ref; do not guess a vendor shadow style. If a previously captured native shadow target is available, only stop suppressing future calls and let the vendor reapply its next lifecycle update.

- [ ] **Step 4: Run focused test and verify GREEN**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `fix: release dock customization ownership on toggle`.

### Task 3: Refresh/remove Dock stroke and stroke shadow immediately

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java`
- Test: `src/test/java/com/hellovoid/liquiddock/DockRuntimeOwnershipContractTest.java`

**Interfaces:**
- Consumes: `VisualRuntimeState.isDockStrokeEnabled()` and `isStrokeShadowEnabled()`.
- Produces: `DockStrokeRenderer.onRuntimeStrokeDisabled()` and `refreshInstalledFromCurrentConfig()` or equivalent exact refresh API.

- [ ] **Step 1: Extend failing contract**

Assert installed hosts can be enumerated and their foreground wrapper removed immediately when stroke is disabled; when only stroke shadow is disabled, the renderer refreshes style without removing the base stroke.

- [ ] **Step 2: Run focused test and verify RED**

Expected: FAIL.

- [ ] **Step 3: Implement renderer refresh/teardown**

For stroke false, restore each `StrokeDrawable.baseForeground()` and clear `INSTALLED`. For stroke-shadow false, reload current Dock config, force `strokeShadow=false` through live state, update installed styles/radii, and invalidate hosts.

- [ ] **Step 4: Run focused test and verify GREEN**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `fix: refresh dock stroke ownership on toggle`.

### Task 4: Restore divider vendor state on disable

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockDividerHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/DividerRuntimeOwnershipContractTest.java`

**Interfaces:**
- Consumes: `VisualRuntimeState.isDividerEnabled()`.
- Produces: `DockDividerHook.onRuntimeDividerDisabled()`.

- [ ] **Step 1: Write failing divider contract**

Assert the hook stores an original snapshot before first mutation, restores width/height/margins/background on disable, removes pending parent layout listeners, clears snapshots, and bind/deferred callbacks leave the vendor line untouched while live disabled.

- [ ] **Step 2: Run focused test and verify RED**

Expected: FAIL.

- [ ] **Step 3: Implement exact snapshot/restore**

Use a weak map keyed by divider View. Snapshot a copy of `MarginLayoutParams` plus the original background Drawable/reference before mutation. Teardown cancels deferred listeners using the stored parent where necessary, restores copied fields/background, requests layout, and clears ownership maps.

- [ ] **Step 4: Run focused test and verify GREEN**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `fix: restore divider state when disabled`.

### Task 5: Release workstation Dock width ownership

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/WorkstationDockGeometryHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/WorkstationRuntimeOwnershipContractTest.java`

**Interfaces:**
- Consumes: `VisualRuntimeState.isWorkstationDockEnabled()`.
- Produces: `WorkstationDockGeometryHook.onRuntimeCustomizationDisabled()`.

- [ ] **Step 1: Write failing workstation contract**

Assert bind/apply paths check live customization state and disabling calls `Binding.apply(false)` to restore width state and blocks workstation icon offset mutation.

- [ ] **Step 2: Run focused test and verify RED**

Expected: FAIL.

- [ ] **Step 3: Implement minimal release/gates**

Keep mode detection unchanged. Live false means geometry bindings restore normal width and future callbacks do not apply customization offsets even if workstation mode is active.

- [ ] **Step 4: Run focused test and verify GREEN**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `fix: release workstation dock ownership on toggle`.

### Task 6: Verify Dock ownership plan

- [ ] **Step 1: Run focused runtime ownership tests**

Run: `./gradlew testDebugUnitTest --tests '*VisualRuntimeToggleContractTest' --tests '*DockRuntimeOwnershipContractTest' --tests '*DividerRuntimeOwnershipContractTest' --tests '*WorkstationRuntimeOwnershipContractTest' --stacktrace`

Expected: PASS.

- [ ] **Step 2: Run full unit tests**

Run: `./gradlew testDebugUnitTest --stacktrace`

Expected: PASS.
