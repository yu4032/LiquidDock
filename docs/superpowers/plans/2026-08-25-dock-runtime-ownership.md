# Dock Runtime Ownership Implementation Plan

**Goal:** Make Dock customization, Dock stroke/shadows, and divider customization stop mutating vendor Views immediately when disabled and restore LiquidDock-owned state where exact restoration is available.

**Architecture:** `VisualRuntimeState` owns the runtime-safe non-glass visual switches. Hooks install inertly where safe and consult live state before mutation. Each owner exposes explicit teardown/refresh behavior for true -> false transitions. Workstation customization remains restart-bound because it spans Dock width, icon offsets, grid and All Apps geometry.

**Spec:** `docs/superpowers/specs/2026-08-25-runtime-toggle-ownership-design.md`

## Global Constraints

- Publish new runtime booleans before main-thread teardown so queued callbacks observe false immediately.
- Do not fabricate native shadow/layout values that were not captured before mutation.
- Numeric/unit/mode style keys need not become instant hot reload.
- Grid, Dock resize-animation hook selection, and Workstation customization remain restart-bound.
- Do not change workstation mode detection semantics.

## Task 1: Live non-glass visual state

Files:
- `src/main/java/com/hellovoid/liquiddock/VisualRuntimeState.java`
- `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- `src/test/java/com/hellovoid/liquiddock/VisualRuntimeToggleContractTest.java`

Runtime state tracks:
- `Dock.ENABLED`
- `Dock.STROKE_ENABLED`
- `Dock.SHADOW_ENABLED`
- `Dock.STROKE_SHADOW`
- `Divider.ENABLED`

It deliberately does not track `Workstation.DOCK_CUSTOMIZATION`.

Disable transitions dispatch to:
- `MainHook.onRuntimeDockCustomizationDisabled()`
- `DockStrokeRenderer.onRuntimeStrokeDisabled()`
- `MainHook.onRuntimeDockShadowDisabled()`
- `DockStrokeRenderer.refreshInstalledFromCurrentConfig()`
- `DockDividerHook.onRuntimeDividerDisabled()`

Status: completed and regression-tested RED -> GREEN.

## Task 2: Dock customization and whole-Dock shadow

Files:
- `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- `src/test/java/com/hellovoid/liquiddock/DockRuntimeOwnershipContractTest.java`

Behavior:
- width/height/radius/spacing/blur mutations consult live Dock customization state;
- native shadow suppression passes MIUI calls through unchanged when live customization is false;
- LiquidDock-owned whole-Dock shadow is removed on disable and cannot be resurrected by `syncAll`;
- unknown native shadow parameters are not guessed or replayed.

Status: completed and regression-tested RED -> GREEN.

## Task 3: Dock stroke and stroke shadow

Files:
- `src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java`
- `src/test/java/com/hellovoid/liquiddock/DockRuntimeOwnershipContractTest.java`

Behavior:
- disabling stroke restores each saved base foreground and clears installed ownership;
- disabling only stroke shadow refreshes installed styles without removing the base stroke;
- Dock customization false removes only LiquidDock's radius delta, not an independently enabled stroke.

Status: completed and regression-tested RED -> GREEN.

## Task 4: Divider exact restore

Files:
- `src/main/java/com/hellovoid/liquiddock/DockDividerHook.java`
- `src/test/java/com/hellovoid/liquiddock/DividerRuntimeOwnershipContractTest.java`

Behavior:
- snapshot width, height, four margins and original background before first mutation;
- track deferred parent/listener pairs;
- on disable, cancel pending listeners and restore every claimed divider exactly;
- bind/deferred callbacks become inert once live state is false.

Status: completed and regression-tested RED -> GREEN.

## Task 5: Workstation boundary

`Workstation.DOCK_CUSTOMIZATION` is intentionally restart-bound. No `isWorkstationDockEnabled()` runtime state or partial teardown is implemented.

Reason: the feature owns multiple structural paths (Dock width, Dock icon offsets, grid and All Apps geometry). Restoring only one subset at runtime would leave a mixed vendor state.

The settings UI must communicate `重启桌面生效` / launcher restart for this switch.

## Verification

Required before merge:

- runtime ownership contract tests pass;
- full `testDebugUnitTest` passes;
- `assembleDebug` passes;
- workflow artifact uploads succeed;
- PR diff confirms Workstation is not present in `VisualRuntimeState` and no unrelated behavior was changed.
