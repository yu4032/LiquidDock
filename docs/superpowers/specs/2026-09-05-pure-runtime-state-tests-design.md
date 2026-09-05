# Pure Runtime State Tests Design

Date: 2026-09-05

Status: Design approved in chat; implementation not started

Base: `main@b6e3caa227a6291de879085f395c2fb16b893994`

## 1. Problem

LiquidDock currently mixes two testing styles for runtime behavior:

1. pure input/output tests over Android-free state objects such as `LauncherWidgetTransitionState`, `DockIconAnimationState`, and `LauncherGlassSceneController.StateMachine`;
2. source-string runtime contracts that read production Java files and assert implementation text, call order, or method names with `Files.readString(...).contains(...)`.

The second style is brittle for runtime semantics. It proves that certain source text exists, not that ownership, freshness, animation, or recovery behavior is correct under real state transitions. Refactors can break those tests without changing behavior, while behavior regressions can still pass if the expected strings remain.

## 2. Goal

Stop adding source-string tests for runtime behavior and migrate the existing ownership / freshness / animation / recovery contracts in scope to pure state-machine tests with real inputs and observable outputs.

Static source/config tests remain appropriate only when the source/config shape itself is the contract.

## 3. Static-test allowlist

Source/config inspection may continue for:

- R8 / keep-rule requirements and prohibitions;
- Gradle/module/dependency wiring;
- Android Manifest / Xposed scope declarations;
- API bans and architectural bans where source usage itself is forbidden, such as legacy `HookUtil.invoke*` or project-owned classes using `HookUtil` to reflect into other LiquidDock classes;
- persisted preference/schema registration or other declarative-resource completeness rules where no runtime state machine is the subject.

Static tests in this allowlist must not be used to prove runtime sequencing, ownership handoff, animation progress, freshness barriers, teardown/recovery behavior, or callback timing.

## 4. Runtime behavior domains in scope

This change migrates source-string runtime contracts for these four domains:

### 4.1 Ownership

Examples:

- live Dock customization/stroke/shadow enable-disable transitions;
- glass feature ownership release when core/component switches change;
- avoiding multiple lifecycle authorities for one visual resource.

Runtime ownership decisions should be represented by an Android-free transition model that takes previous and next effective feature states and returns explicit actions/effects.

### 4.2 Freshness

Existing pure scene/widget state machines are the reference pattern.

Freshness tests must exercise generation/state transitions directly:

- stale generations do not reveal;
- matching fresh generations do reveal;
- covered/uncovered scenes wait for fresh content;
- early reveal semantics remain explicit where intentionally supported.

No runtime freshness requirement may be asserted by searching for `onFreshFrameReady`, `applyLayerVisibility`, callback order strings, or similar implementation text.

### 4.3 Animation

Animation behavior must be tested through pure state inputs such as animation begin/end, proxy progress, current time, and ownership state, then assert opacity/fading/side-effect decisions.

The existing `DockIconAnimationState` style is preferred over checking that particular methods are or are not called inside `MainHook.java`.

### 4.4 Recovery

Workstation Recents return and unlock producer recovery must become explicit state transitions rather than source-order assertions.

The model must distinguish at least:

- recovery requested;
- producer rollover accepted/rejected;
- scene generation invalidated;
- waiting for matching fresh frame;
- fresh frame accepted;
- reveal authorized;
- failure remains fail-closed.

A producer rollover callback means endpoint replacement completed at the defined boundary; it must not be modeled as a fresh rendered frame.

## 5. Architecture

### 5.1 Pure state objects

Introduce or extract small package-private Android-free state/policy classes where runtime decisions are currently embedded in Android/Xposed classes.

Preferred shape:

```java
final class RuntimeXState {
    Result onEvent(Event event);
    Snapshot snapshot();
}
```

or, for simple enable/disable policy:

```java
static Transition plan(Snapshot before, Snapshot after);
```

Results must expose decisions directly rather than requiring tests to inspect logs or implementation text.

### 5.2 Runtime adapters

Android/Xposed classes remain responsible for executing effects:

- posting to main/render handlers;
- invoking vendor methods;
- attaching/removing Drawables/Views;
- shutting down GPU sessions;
- issuing producer rebinds.

They consume decisions from pure state/policy objects. Tests target the pure object for behavioral correctness; Android integration remains covered by compilation and existing non-source-string tests where available.

### 5.3 No test-only shadow implementation

The pure state machine must be used by production runtime code. Do not create a duplicate test-only model that merely restates expected behavior while production keeps separate logic.

## 6. Concrete migration targets

### 6.1 `DockRuntimeOwnershipContractTest`

Remove source-string behavior assertions.

Extract effective Dock visual transition planning from `VisualRuntimeState.apply(...)` into a pure policy/state object. Test real before/after snapshots and emitted actions such as:

- dock customization disabled;
- stroke disabled/enabled;
- dock shadow disabled/enabled;
- stroke-shadow changed;
- divider disabled;
- mirror visibility changed.

Vendor native-shadow implementation-shape prohibitions should only remain static if they are true API/ownership bans. Behavior such as live-disable ownership must move to pure transition tests.

### 6.2 `GlassRuntimeDisableContractTest`

Replace teardown source-string assertions with a pure glass runtime transition planner.

Inputs include previous and next effective core/glass/component flags. Outputs enumerate ownership-release actions:

- full glass teardown;
- icon release;
- widget release;
- widget dark-content change;
- small-folder release;
- large-folder release.

Full teardown must dominate component-specific releases for the same transition.

### 6.3 `DockShadowAnimationRegressionTest`

Behavioral animation assertions move to pure animation/ownership policy tests using actual animation-active/settled inputs and expected shadow-sync decisions.

Static bans against creating a second standalone shadow owner may remain only if expressed as an architectural/API prohibition, not as a proxy for animation behavior.

### 6.4 `LauncherUnlockCaptureBoundaryContractTest`

Split structural and runtime concerns.

Allowed static checks may retain:

- SystemUI package/scope declaration;
- forbidden capture APIs;
- project-owned reflection/API bans.

Move runtime sequencing to pure recovery/freshness state tests:

- PREPARE arms fail-closed capture blocking;
- SystemUI gone-finished requests producer rollover;
- rejected rollover does not release barrier;
- successful rollover completion alone does not authorize scene reveal;
- only matching fresh frame authorizes reveal.

### 6.5 `WorkstationStaticLayerRecentsRecoveryContractTest`

Replace source ordering and method-name checks with the same or a shared pure recovery state machine.

Test:

- workstation Recents exit requests rollover before recovery can reveal;
- non-workstation/folder coverage does not request Workstation rollover;
- rollover acceptance moves to waiting-fresh, not visible;
- stale frame remains hidden;
- matching fresh frame authorizes reveal;
- rollover rejection remains fail-closed.

The existing typed Session API prohibition against Registry self-reflection remains a static architectural/API-ban test.

### 6.6 Existing pure state tests

`LauncherWidgetTransitionStateTest`, `LauncherGlassSceneControllerTest`, `SystemUiHomeEarlyRevealStateTest`, and similar tests should call package-private state classes directly where Java package access permits. Remove reflection-based invocation where practical; the behavior must be expressed as typed inputs/outputs.

## 7. Out of scope

This PR does not attempt a repository-wide deletion of every historical source-string test.

Specifically deferred unless they overlap the four runtime domains above:

- Grid/layout implementation contracts;
- UI/resource mapping contracts;
- renderer implementation-shape tests unrelated to ownership/freshness/animation/recovery;
- broad historical cleanup of old design-plan snippets.

No new source-string runtime behavior test may be added while this deferred work remains.

## 8. Test policy gate

Add one structural test/documented rule that prevents future source-string runtime behavior contracts without banning legitimate static tests.

The gate should focus on test intent/location/naming or a narrow allowlist, not naïvely ban all `Files.readString` usage. R8/Gradle/Manifest/API-ban tests must continue to work.

`CONTRIBUTING.md` should state:

- runtime ownership/freshness/animation/recovery => pure production state machine + real input/output tests;
- R8/Gradle/Manifest/API bans => static inspection allowed;
- do not use source strings to prove runtime call ordering or lifecycle behavior.

## 9. TDD sequence

1. inventory the in-scope source-string runtime assertions and classify each as static-allowed or runtime-behavior;
2. add failing pure-state tests for one domain at a time using intended production-facing state APIs;
3. extract the smallest production state/policy needed to make those tests real;
4. switch runtime adapters to consume that state/policy;
5. delete the replaced source-string runtime assertions;
6. repeat for ownership, glass teardown, animation/shadow ownership, unlock recovery, and Workstation recovery;
7. convert existing reflection-driven pure state tests to direct typed package-private calls where practical;
8. add the narrow static test-policy gate and CONTRIBUTING rule;
9. run full `testDebugUnitTest` and `assembleDebug`;
10. audit remaining `Files.readString` tests and verify every remaining use is static/declarative or outside the explicitly scoped runtime domains.

## 10. Acceptance criteria

Complete only when:

- no new source-string runtime behavior tests are introduced;
- the in-scope ownership/freshness/animation/recovery source-string assertions are removed or reduced to legitimate static bans;
- production code uses the extracted pure state/policy objects rather than duplicate test-only logic;
- ownership transitions are tested with real before/after state and emitted actions;
- glass teardown transitions are tested with real inputs/outputs;
- animation decisions are tested through real progress/time/state inputs;
- unlock and Workstation recovery are tested through real recovery/freshness transitions;
- rollover completion is not treated as a fresh-frame event;
- stale generations remain unable to reveal;
- legitimate R8/Gradle/Manifest/API-ban static tests remain intact;
- `CONTRIBUTING.md` documents the boundary;
- full unit tests pass;
- `assembleDebug` passes;
- final diff contains no unrelated runtime refactor.
