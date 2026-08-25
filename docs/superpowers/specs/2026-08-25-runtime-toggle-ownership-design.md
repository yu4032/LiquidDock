# Runtime Toggle Ownership Design

Date: 2026-08-25

## Goal

Eliminate stale visual ownership after a LiquidDock feature switch is turned off. A component-level visual switch must stop already-installed hooks and delayed callbacks from mutating vendor views, and any visual state owned by LiquidDock must be restored immediately where restoration is safe and well-defined.

This change does not attempt to make every configuration key hot-reloadable. Structural layout or hook-selection settings remain restart-bound unless they have a complete reversible runtime path.

## Scope

### Live visual ownership switches

The following switches are process-local live state while Launcher is running:

- `liquid_icon_glass`
- `liquid_widget_glass`
- `liquid_small_folder_glass`
- `liquid_large_folder_glass`
- `dock_customization`
- `dock_stroke`
- `dock_shadow`
- `stroke_shadow`
- `dock_divider_enabled`

Every permanent hook or delayed callback that can mutate a vendor view for one of these features must consult the current live state immediately before mutation.

### Restart-bound structural settings

The following remain restart-bound in this change:

- home grid enable/disable and grid geometry
- widget grid adaptation
- Dock resize-animation hook selection
- `workstation_dock_customization` as a whole
- structural workstation layout parameters that alter Dock width, icon offsets, grid or All Apps geometry
- debug logging, which is already documented as restart-bound

`workstation_dock_customization` is deliberately not half-hot-toggled. It spans several separately installed geometry and offset hooks; releasing only Dock width while leaving icon/grid/All Apps behavior active would create mixed vendor state.

The settings UI states the restart boundary for these structural switches rather than implying immediate teardown.

## Runtime State

Use two process-local runtime-state owners:

- `GlassRuntimeState` for live glass component ownership;
- `VisualRuntimeState` for live non-glass visual ownership (`Dock`, stroke/shadows, divider).

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

### Widget glass

Keep the existing live behavior: release widget fallback material ownership, dispose widget nodes, remove stale observers, and gate RemoteViews/MAML/reconcile/scheduled bind paths.

### Small and large folder glass

Track the two folder variants independently. Disabling one variant restores only views owned by that variant:

- restore `ImageView` alpha or saved background;
- restore large-folder drawable Paint alpha when applicable;
- restore any claimed folder cover;
- dispose the corresponding static node;
- stop startup recovery/attach callbacks from reclaiming the disabled variant.

Global folder-open coverage must not hide native materials belonging to a disabled variant.

### Dock customization and shadows

Dock customization live state gates every installed hook that changes normal-mode Dock geometry/material or suppresses the native Dock shadow.

When Dock customization is disabled:

- stop modifying vendor width/height/radius/spacing/blur inputs;
- remove LiquidDock-owned whole-Dock shadow;
- stop suppressing future native shadow calls;
- restore only native state that LiquidDock explicitly saved.

If exact vendor shadow/geometry state was not captured, do not fabricate parameters. The hook becomes inert and future vendor lifecycle calls pass through unchanged.

`dock_shadow` controls the independent whole-Dock shadow only. Disabling it removes the LiquidDock shadow immediately.

`stroke_shadow` affects stroke renderer style only and must not keep a stale shadow once false.

### Dock stroke

Disabling Dock stroke immediately restores each installed host's saved base foreground and clears stroke ownership. Disabling only stroke shadow refreshes installed stroke styles without discarding the base stroke.

### Divider

Before the first LiquidDock mutation of a divider View, capture its original width, height, four margins, and background Drawable. When divider customization becomes disabled:

- cancel pending deferred geometry work;
- restore the captured layout/background state for every live claimed divider;
- future binds and already-queued layout callbacks leave the vendor view untouched.

Re-enabling may claim the current divider again from its then-current vendor state.

## Hook Installation Strategy

For features that must support false -> true without restarting Launcher, install lightweight inert hooks whenever the parent subsystem is available, then gate behavior with live state. Do not condition hook installation solely on the component's startup boolean.

Where hook selection or layout semantics are structural, keep the setting restart-bound rather than installing only a partially reversible runtime path.

## Delayed Work Safety

All delayed ownership paths (`post`, `postDelayed`, `postOnAnimation`, recovery loops, RemoteViews/MAML callbacks, drag proxy frames, workspace reconciliation, deferred divider geometry) re-check the relevant live state at execution time. Teardown alone is not sufficient.

## Testing

Use RED -> GREEN regression tests before implementation. Tests cover:

- runtime listeners track the intended live visual switches;
- component hooks read live state rather than only startup config;
- disabling icon glass prevents later workspace/Dock/drag callbacks from reclaiming icon glass;
- disabling each folder variant restores its own material state and prevents recovery callbacks from reclaiming it;
- disabling Dock customization stops native-shadow suppression and Dock mutations;
- disabling Dock stroke removes installed foreground ownership;
- disabling Dock shadow prevents stale shadow resurrection;
- disabling divider restores captured vendor layout/background and cancels deferred work;
- Workstation customization is absent from live runtime state;
- structural Grid/widget-adaptation/resize-animation/Workstation switches are explicitly restart-bound in UI.

Final verification runs the complete `testDebugUnitTest` and `assembleDebug` workflow before merge.

## Non-goals

- Hot-reloading every numeric or unit/mode setting.
- Making the home-grid transition live.
- Partially hot-toggling Workstation customization.
- Reconstructing unknown vendor visual state from guessed constants.
- Refactoring unrelated glass rendering or Prismal code.
