# Stage Manager Workspace Foundation Design

## Status

Approved direction for the first implementation stage of a macOS/iPadOS-style Stage Manager area on the Launcher workspace.

## Goal

Keep Launcher internally on LiquidDock's existing 8×4 physical grid, reserve columns 0 and 1 as a persistent left-side stage region, and keep columns 2–7 as the normal 6×4 desktop area. The stage region must remain fixed while Workspace pages scroll and must not participate in Launcher item occupancy.

## Scope

This spec covers only the workspace foundation required by Stage Manager:

1. Reserve the first two columns of the existing 8×4 home grid from ordinary icon, folder, and widget placement.
2. Preserve the existing 8×4 cell size, gaps, coordinate arrays, rotation handling, and widget sizing.
3. Add a fixed Stage Manager overlay host whose bounds are derived from columns 0–1 of the same 8×4 geometry.
4. Keep the overlay outside every `CellLayout`, so it does not move with individual pages and does not create `ItemInfo` or Launcher database records.
5. Define lifecycle and visibility rules for HOME/Workspace attachment, rotation, workstation mode, and feature disable.

The following are intentionally deferred to a later spec:

- recent-task acquisition;
- task snapshot/live thumbnail rendering;
- task launch/switch/close gestures;
- visual card styling and animation;
- multi-window/freeform integration.

## Existing Authorities

### Grid geometry

`HomeGridHook` remains the sole authority for custom home-grid geometry. The physical grid stays 8×4. This feature must not translate the entire `Workspace` View and must not introduce a second set of cell coordinates.

The stage bounds are derived from the established 8×4 `CellLayout` geometry:

- stage start: logical column 0;
- stage end: logical column 2;
- desktop start: logical column 2;
- desktop end: logical column 8.

The implementation may derive the left stage width from `mXs`, `mCellWidth`, and `mWidthGap`, but must not mutate those arrays solely for Stage Manager.

### Placement legality

`HomeGridDropLegalityPolicy` remains the pure legality authority consumed by `WorkspaceDropRuleHook` and orientation/restore logic. Stage reservation is expressed as an additional policy constraint rather than a View translation.

For an ordinary workspace item with `(cellX, spanX)`, placement is legal only when its occupied horizontal range is entirely inside columns 2–7:

```text
cellX >= 2
cellX + spanX <= 8
```

Existing 2×2 macroblock constraints remain in force after this stage-reservation constraint.

### Native occupancy

Launcher still owns the real occupancy matrix and collision resolution. LiquidDock only marks the reserved columns as illegal placement targets through its legality policy and corresponding hooks. The Stage Manager overlay itself never creates an occupied Launcher cell.

## Stage Overlay Host

Create one Launcher-root/Workspace-level overlay host, not one host per `CellLayout` page.

Properties:

- fixed in screen/workspace-root coordinates;
- bounds correspond to logical columns 0–1 of the current 8×4 geometry;
- does not scroll horizontally with Workspace pages;
- does not intercept touch while empty in this foundation stage;
- no `ItemInfo`, database row, drag target, or Launcher occupancy entry;
- recreated/rebound only when the Launcher/Workspace authority changes;
- geometry refreshed after valid Workspace layout and after rotation/configuration changes.

The host must be a sibling/overlay of the scrolling page content. It must not be inserted into a `CellLayout`, because page-local ancestry would make it scroll with the page and pollute layout/drag assumptions.

## Geometry Contract

The overlay uses the same 8×4 grid as the desktop rather than a hand-tuned dp width.

Given a valid `CellLayout` with:

- `mXs[0]` = first column x;
- `mXs[2]` = third column x;
- `mCellWidth` = current cell width;

preferred stage horizontal bounds are:

```text
left  = mXs[0]
right = mXs[2]
```

This includes the gap between columns 1 and 2 in the same way Launcher already defines its grid. If `mXs[2]` is unavailable during early layout, the host remains non-presentable until stable geometry is available; no fixed-delay fallback is allowed.

Vertical bounds initially follow the workspace content region rather than the full display. Stage-card visual insets are a later concern.

## Orientation and Mode Rules

### Landscape

Stage reservation is enabled when the selected home profile is 8×4 and the Stage Manager foundation feature is enabled.

### Portrait

The first implementation keeps the same logical reservation semantics only if the active home profile remains 8×4 in portrait. The overlay host may be hidden in portrait until a separate portrait UI is designed, but ordinary placement must not silently reinterpret saved stage-reserved coordinates.

### Workstation/Laptop mode

Existing workstation-specific Workspace and All Apps geometry remains authoritative. This foundation must fail closed rather than reusing the stage reservation inside dedicated laptop All Apps `CellLayout` instances. Normal HOME Workspace pages may use the reservation only when the feature is explicitly active for that mode.

## Existing Item Migration

The feature must not destructively rewrite the Launcher database on first enable.

If existing ordinary items occupy columns 0–1, the first foundation version must detect the conflict and leave the Stage overlay non-active for that page/session until a deterministic migration policy is implemented. It must not silently overlap the stage host on top of existing icons/widgets and must not delete or relocate items without an explicit migration contract.

A later migration task may shift compatible 6×4 layouts by +2 columns when every item can fit safely, but that behavior is outside this spec.

## Lifecycle

The Stage host follows Launcher/Workspace authority, not page authority:

1. Launcher setup/resume resolves the current Workspace root.
2. When a valid 8×4 `CellLayout` geometry is available, compute stage bounds.
3. Attach or update one fixed overlay host under the Launcher/Workspace root authority.
4. Page scroll changes do not recreate or translate the stage host.
5. Rotation/configuration changes invalidate geometry and recompute from the new stable grid.
6. Workspace/root replacement detaches the old host and clears its references.
7. Feature disable removes the host and restores normal placement legality.

No timer or delayed settle callback is an authority boundary.

## Proposed Components

### `StageWorkspacePolicy`

Pure policy. Owns:

- whether a profile/mode supports the reserved stage region;
- reserved column count (`2`);
- placement legality helper for ordinary items;
- stage bounds calculation from pure grid coordinates.

No Android `View` dependency.

### `StageWorkspaceHost`

Android runtime owner for the fixed overlay container. Owns:

- one weakly-bound Launcher/Workspace host;
- attach/detach;
- geometry updates;
- visibility;
- non-intercepting empty container for the foundation stage.

It does not source recent tasks.

### `HomeGridDropLegalityPolicy`

Extended to consume Stage reservation policy when active. Existing macroblock rules remain unchanged.

### `WorkspaceDropRuleHook`

Continues to bridge MIUI's drop legality callback to the pure policy. It must not duplicate Stage column arithmetic.

### `HomeGridHook`

Continues to own 8×4 geometry. It exposes/forwards stable grid geometry to `StageWorkspaceHost` after valid `CellLayout` layout; it does not translate Workspace or shrink the grid to 6×4.

## Failure Behavior

- Unsupported or transient grid count: do not activate the Stage host.
- Missing stable geometry: keep the Stage host hidden and retry only on normal layout/lifecycle callbacks.
- Existing item conflict in reserved columns: keep Stage host inactive for that page/session; preserve user content.
- Reflection/API mismatch: log and fail closed; native Launcher behavior remains usable.
- Workstation All Apps: never apply HOME stage reservation.

## Testing

### Pure tests

- 8×4 reservation rejects ordinary placements touching columns 0–1.
- placements wholly inside columns 2–7 remain legal.
- spans crossing from column 2 into 8 are rejected.
- existing 2×2 macroblock legality still applies.
- stage bounds equal `[mXs[0], mXs[2])` for representative grid coordinates.
- non-8×4 profiles do not activate reservation.

### Hook/contract tests

- `WorkspaceDropRuleHook` delegates reservation decisions to the pure policy.
- `HomeGridHook` does not translate the Workspace View for Stage Manager.
- stage host is attached outside `CellLayout` page ancestry.
- stage host is one-per-Workspace/root, not one-per-page.
- workstation All Apps is excluded.

### CI/device checks

- existing 8×4 icon/widget sizing tests remain green;
- rotation tests remain green;
- drag/drop into columns 0–1 is rejected;
- drag/drop inside columns 2–7 remains native;
- page swiping moves only the 6-column desktop content; empty stage host remains fixed;
- disabling the feature restores full 8×4 placement.

## Non-goals

- No recent-task UI in this foundation commit.
- No fake widget occupying 2×4 cells.
- No Workspace `translationX` workaround.
- No Launcher database migration in the first stage.
- No fixed-delay geometry retries.
