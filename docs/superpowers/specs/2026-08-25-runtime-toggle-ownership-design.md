# Runtime Toggle Ownership Design

Date: 2026-08-25

## Goal

Eliminate stale visual ownership after a LiquidDock feature switch is turned off. A component-level switch must stop already-installed hooks and delayed callbacks from mutating vendor views, and any visual state owned by LiquidDock must be restored immediately where restoration is safe and well-defined.

This change does not attempt to make every configuration key hot-reloadable. Structural layout hooks remain restart-bound unless they already have a complete reversible runtime path.

## Scope

### Live visual ownership switches

The following switches become process-local live state while Launcher is running:

- `liquid_icon_glass`
- `liquid_widget_glass`
- `liquid_small_folder_glass`
- `liquid_large_folder_glass`
- `dock_customization`
- `dock_stroke`
- `dock_shadow`
- `stroke_shadow`
- `dock_divider_enabled`
- `workstation_dock_customization`

Every permanent hook or delayed callback that can mutate a vendor view for one of these features must consult the current live state immediately before mutation.

### Restart-bound structural settings

The following remain restart-bound in this change:

- home grid enable/disable and grid geometry
- widget grid adaptation
- Dock resize-animation hook selection
- structural workstation layout parameters that alter grid/cell geometry
- debug logging, which is already documented as restart-bound

The settings UI should state the restart boundary for structural switches rather than implying immediate teardown.

## Runtime State

Extend the existing process-local runtime state pattern instead of creating independent preference listeners in each hook. The runtime state owns the latest booleans and listens for the corresponding `ConfigSchema` keys.

State transition ordering is important:

1. publish the new boolean first;
2. schedule teardown on the main thread;
3. delayed callbacks observe the new false value and become inert;
4. teardown restores current owned views and removes per-view ownership records/listeners where appropriate.

This prevents a callback queued before the preference change from reclaiming a view after teardown.

## Component Semantics

### Icon glass

When disabled:

- dispose workspace icon static nodes;
- unregister Dock icon glass candidates and clear animation ownership;
- prevent drag/launch overlay callbacks from starting or updating icon glass;
- leave vendor icon content untouched.

When enabled during the same process, existing installed inert hooks may reconcile visible workspace/Dock icons using the existing workspace reconciliation path.

### Widget glass

Keep the already-implemented live behavior: release widget fallback material ownership, dispose widget nodes, remove stale observers, and gate RemoteViews/MAML/reconcile/scheduled bind paths.

### Small and large folder glass

Track the two folder variants independently. Disabling one variant restores only views owned by that variant:

- restore `ImageView` alpha or saved background;
- restore large-folder drawable Paint alpha when applicable;
- restore any claimed folder cover;
- dispose the corresponding static node;
- stop startup recovery/attach callbacks from reclaiming the disabled variant.

Global folder-open coverage must not hide native materials belonging to a disabled variant.

### Dock customization and shadows

Dock customization live state gates every hook that changes Dock geometry/material or suppresses the native Dock shadow.

When Dock customization is disabled:

- stop modifying vendor width/height/radius/spacing/blur inputs;
- remove/hide LiquidDock-owned whole-Dock shadow;
- stop suppressing future native shadow calls;
- restore any native shadow state that LiquidDock explicitly saved. If no exact vendor shadow state was captured, stop suppressing and let the next vendor lifecycle call reapply its own state rather than fabricating parameters.

`dock_shadow` controls the independent whole-Dock shadow only. Disabling it removes or hides the LiquidDock shadow immediately.

`stroke_shadow` affects the stroke renderer style only and must not keep a stale shadow once false.

### Dock stroke

`DockStrokeRenderer` already supports removing its installed foreground wrapper when `strokeEnabled` is false. Add an immediate refresh/teardown path for all currently installed hosts when the live switch changes, while retaining its periodic config refresh for numeric style changes.

### Divider

Before the first LiquidDock mutation of a divider View, capture its original layout parameters and background state. When divider customization becomes disabled:

- cancel pending deferred geometry work;
- restore the original width, height, margins, and background for every live claimed divider;
- future binds with the switch disabled leave the vendor view untouched.

Re-enabling may claim the current divider again from its then-current vendor state.

### Workstation Dock customization

The switch gates workstation-only Dock ownership. Disabling it during the same process restores live workstation Dock width ownership and prevents further workstation customization hooks from applying offsets. Structural workstation grid/all-apps parameters remain restart-bound.

## Hook Installation Strategy

For features that must support false -> true without restarting Launcher, install lightweight inert hooks whenever the parent subsystem is available, then gate behavior with live state. Do not condition hook installation solely on the component's startup boolean.

Where a hook exists only because of a structural mode whose installation changes method semantics globally, keep it restart-bound rather than installing it inertly.

## Delayed Work Safety

All delayed paths (`post`, `postDelayed`, `postOnAnimation`, recovery loops, RemoteViews/MAML callbacks, drag proxy frames, workspace reconciliation) must re-check the relevant live state at execution time. Teardown alone is not sufficient.

## Testing

Use RED -> GREEN regression tests before implementation. Tests should cover:

- runtime listener tracks every live visual switch;
- component hooks read live state rather than only startup config;
- disabling icon glass prevents later workspace/Dock/drag callbacks from reclaiming icon glass;
- disabling each folder variant restores its own material state and prevents recovery callbacks from reclaiming it;
- disabling Dock customization stops native-shadow suppression and Dock mutations;
- disabling Dock stroke removes installed foreground ownership;
- disabling Dock shadow removes/hides the LiquidDock shadow;
- disabling divider restores captured vendor layout/background and cancels deferred work;
- workstation Dock customization releases its width ownership;
- structural Grid/widget-adaptation/resize-animation switches remain explicitly restart-bound in UI/contract tests.

Final verification must run the complete `testDebugUnitTest` and `assembleDebug` workflow before merge.

## Non-goals

- Hot-reloading every numeric style parameter.
- Making the 6x4/8x4 grid transition live.
- Reconstructing unknown vendor visual state from guessed constants.
- Refactoring unrelated glass rendering or Prismal code.