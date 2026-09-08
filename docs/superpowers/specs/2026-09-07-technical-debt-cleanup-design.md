# LiquidDock Technical-Debt Cleanup Design

**Date:** 2026-09-07  
**Baseline:** `main` at `be1b4ba77955fe0ebebec67362a09643b69634bd`  
**Scope:** Current v2.2.1 / HyperOS 3.0.307+ / Launcher 4.50 / MiuiX PassBlur + OES/GLES zero-copy mainline only.

## 1. Problem statement

LiquidDock has already removed the highest-risk 1.x screenshot-era architecture from the current mainline. The remaining debt is primarily ownership debt created while zero-copy glass, Workstation, custom grid, Widget adaptation, and runtime toggles were added quickly across successive fixes.

The cleanup therefore must not be treated as a general rewrite. The objective is to reduce hidden mutable ownership and timing assumptions while preserving already validated runtime semantics.

The active debt clusters are:

1. `MainHook` still owns Workstation feature-level mutable state and delayed initialization/recheck behavior.
2. `WidgetGridSizing` still owns global mutable enable state, while Widget classification and supported span rules remain partly hard-coded inside `HomeGridHook`.
3. `HomeGridHook` still owns several independent runtime responsibilities despite existing helper hooks/policies.
4. `LauncherGlassSession` / `Miuix307PassBlurTextureView` / Prismal still require a resource-owner audit before any EGL/OES extraction.
5. Bundled Widget background-rule failures silently degrade to an empty ruleset.
6. `RuntimeBehaviorTestPolicyContractTest.LEGACY_SOURCE_DEBT` still contains 13 grandfathered source-reader tests.
7. CI/tooling and user-visible string cleanup remain lower-risk hygiene work.

## 2. Non-goals

This cleanup does **not**:

- restore or redesign ScreenCapture / PixelCopy / bitmap readback;
- replace the current Workstation Recents recovery with another episode state machine;
- add a second fresh-frame authority beside scene/wallpaper generation;
- create a large shared `GlassEngine` before resource ownership is proven identical;
- change Dock/Workspace visual parameters or optical output;
- change MIUI occupancy / placement authority;
- convert restart-bound structural hooks into hot-uninstall behavior;
- add a user-programmable Widget rule DSL;
- expand supported Widget spans as part of the refactor.

## 3. Invariants that must survive every phase

### 3.1 Zero-copy and freshness

- MiuiX PassBlur remains the only backdrop source for current Liquid Glass.
- Native PassBlur scale stays `1.0` for authoritative spatial mapping.
- Local render-scale optimization occurs only after OES normalization.
- FPS limiting never prevents `SurfaceTexture.updateTexImage()` drain.
- Recents/HOME/rotation/unlock reveal still requires the existing scene/wallpaper generation plus fresh OES frame barrier.
- Rebind success never means fresh content.
- SystemUI remains the unlock-to-HOME release authority.

### 3.2 Workstation

- Duplicate or non-covered `onRecentViewHide()` must not trigger producer rollover.
- `workstationBindEpoch` continues to reject stale queued bind completion.
- Workstation recovery stays Workstation-only and fail-closed.
- Structural Workstation customization remains restart-bound.
- Normal-mode layout restore must remain exact for state that LiquidDock actually captured.

### 3.3 Grid / Widget

- Widget adaptation changes allocation/frame only.
- MIUI continues to own placement and occupied matrices.
- `addOccupied()` / `transformToHVArray()` remain untouched.
- Orientation-specific placement memory and lazy/off-screen preparation remain unchanged.
- Supported Widget specs remain exactly `1×1`, `2×1`, `2×2`, `4×2` during the cleanup.

### 3.4 Runtime toggles

- Runtime disable publishes `false` before ownership teardown.
- Queued asynchronous callbacks must re-check current live state or generation before mutation.
- Unknown vendor state is never reconstructed or guessed.

## 4. Proposed architecture

The cleanup uses an incremental ownership-migration strategy.

### 4.1 Workstation ownership

Introduce a single `WorkstationModeController` as the owner of Workstation mode state and mode-transition generation.

It owns:

- current Workstation mode;
- whether a vendor callback has confirmed the mode;
- one monotonic generation used to invalidate delayed initialization/recheck work;
- normal-layout backup/restore state that exists solely because of Workstation transitions;
- Workstation-specific transition cancellation/reconciliation.

`MainHook` remains the composition root: it constructs the controller, installs it, and passes stable collaborators/config. It no longer exposes or mutates feature-level Workstation flags or maps directly.

The existing 2-second re-query may remain temporarily as a vendor-initialization fallback, but it must be generation-protected and cancellable. The cleanup does not replace one timing assumption with another arbitrary delay.

### 4.2 Widget classification and sizing

Split current Widget responsibilities into three units:

- `WidgetClassifier`: determines whether an `ItemInfo` represents a Widget. It first uses the current `ItemInfo.isWidget()` vendor path and falls back to the known `itemType` values 4/5/19.
- `WidgetSpecRegistry`: owns the supported span set. Initial registry contents are exactly 1×1, 2×1, 2×2, 4×2.
- `WidgetGridSizing`: pure geometry/allocation only. It receives an explicit adaptation-enabled decision from the caller and contains no static mutable config.

`HomeGridHook` consumes these units rather than embedding vendor classification and span policy in its main control flow.

### 4.3 `HomeGridHook` decomposition

Do not split rotation/refresh first. The safe migration order is:

1. Widget adaptation ownership;
2. page-indicator ownership;
3. folder-alignment ownership;
4. remaining cell-geometry ownership;
5. rotation/refresh last.

Each extraction must move real Hook installation/runtime ownership, not merely introduce another helper while leaving control flow in `HomeGridHook`.

### 4.4 Launcher glass resource ownership

Phase 1 performs no broad EGL/OES extraction.

Before changing `LauncherGlassSession`, `Miuix307PassBlurTextureView`, or Prismal resource lifecycles, create an owner graph that records for each resource:

- creator;
- thread/context requirement;
- bind/rebind owner;
- generation it belongs to;
- release trigger;
- whether Dock and Launcher lifetimes are truly equivalent.

Only resources with identical ownership semantics should be moved to a shared primitive. Differences caused by Dock vs Launcher lifecycle remain separate even if helper code looks similar.

### 4.5 Widget bundled-rule diagnostics

`WidgetBackgroundRuleEngine.loadBundled()` should distinguish:

- optional parser hardening failures that can continue with an empty result;
- failure to load/parse the required bundled compatibility resource.

Required bundled-rule degradation gets one structured, one-shot diagnostic. The runtime still fails safely; this change exists to make silent compatibility regressions observable without log spam.

### 4.6 Test debt

Runtime behavior tests must increasingly drive typed production state/policy. The `LEGACY_SOURCE_DEBT` list is a burn-down list, not a permanent allowlist.

Each migrated test is classified as one of:

1. legitimate static architecture/API contract -> move to the audited static allowlist if needed;
2. runtime behavior -> replace source inspection with typed state/policy tests consumed by production;
3. obsolete -> delete the test and remove the debt entry.

No new entry may be added to `LEGACY_SOURCE_DEBT`.

## 5. Phasing

### Phase 1 — deterministic ownership cleanup

Implement:

- `WorkstationModeController` and generation-protected delayed recheck;
- `WidgetClassifier`;
- `WidgetSpecRegistry`;
- stateless `WidgetGridSizing`;
- first `HomeGridHook` Widget-ownership extraction;
- one-shot bundled Widget-rule diagnostics;
- associated typed unit/contract tests.

This phase intentionally avoids producer/EGL/OES lifecycle changes.

### Phase 2 — `MainHook` and `HomeGridHook` shrink

After Phase 1 is green on CI and device Workstation/Grid smoke tests:

- move remaining Workstation feature state out of `MainHook`;
- migrate page indicator and folder alignment out of `HomeGridHook`;
- migrate low-risk Dock feature installation/ownership out of `MainHook` where a real owner already exists.

### Phase 3 — glass owner graph and minimal resource extraction

First produce an audited owner graph. Then extract only proven-common lifecycle primitives. No large `GlassEngine` is allowed as a design shortcut.

### Phase 4 — engineering hygiene

- burn down `LEGACY_SOURCE_DEBT`;
- update CI action versions / Node compatibility / artifact retention settings;
- resolve the MAML varargs warning semantically;
- migrate user-visible hard-coded strings to resources.

## 6. Verification gates

### CI gate

Every implementation task must pass:

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Changes touching reflection/R8 boundaries additionally require the existing release/keep-rule contract tests.

### Device gate for Phase 1

At minimum verify:

- normal Launcher startup;
- enter Workstation -> exit -> enter again quickly;
- Workstation state callback arriving before the delayed fallback;
- delayed fallback becoming stale after a newer transition;
- normal layout backup/restore;
- HOME -> Recents -> HOME repeatedly;
- Recents-adjacent rotation;
- 1×1 / 2×1 / 2×2 / 4×2 Widgets in both orientations;
- Widget adaptation disabled leaves MIUI frames untouched;
- bundled Widget rule resource is active and a forced test failure produces only one diagnostic;
- normal mode without Workstation has no regression.

## 7. Success criteria

Phase 1 is complete when all of the following are true:

- `MainHook` no longer owns the Workstation mode-confirmation flag or naked delayed recheck logic;
- the delayed Workstation fallback cannot mutate state after its generation is stale;
- `WidgetGridSizing` has no static mutable enable flag;
- `HomeGridHook` no longer contains direct `itemType == 4 || itemType == 5 || itemType == 19` classification;
- supported Widget spans come from `WidgetSpecRegistry`;
- Widget rule bundled-resource failure is observable once without changing fail-safe behavior;
- existing zero-copy, freshness, Recents recovery, Grid placement, and runtime-toggle invariants remain unchanged;
- CI is green and the Phase 1 device matrix has been recorded.

## 8. Documentation authority

After this design lands:

- `ARCHITECTURE.md` describes current runtime ownership, not future design;
- `HOOKS.md` describes current Hook/recovery boundaries;
- `FEATURES.md` describes user-visible behavior only;
- `CONTRIBUTING.md` carries maintenance invariants;
- `TODO.md` is the active debt/priority ledger;
- this spec explains the target ownership model;
- the corresponding implementation plan provides task-by-task execution steps.
