# Stage Manager Workspace Foundation Design

## Status

Approved first-stage architecture for a macOS/iPadOS-style Stage Manager area on the Launcher workspace.

## Goal

Keep LiquidDock's existing 8×4 physical home grid, turn columns 0–1 into a new persistent Stage region, and map the ordinary desktop into columns 2–7. Existing 6-column desktop content is shifted right by exactly two logical cells, so the visible result is a fixed 2×4 Stage region plus a normal 6×4 desktop.

## Core Model

The physical grid remains 8×4:

```text
column:   0   1 | 2   3   4   5   6   7
          Stage | ordinary desktop 6×4
```

The left two columns are not empty desktop cells and are not ordinary Launcher occupancy. They are a new Stage domain.

Ordinary desktop coordinates use a logical 6-column domain and are projected into the physical 8-column grid:

```text
physicalCellX = logicalDesktopX + 2
logicalDesktopX = physicalCellX - 2
```

Therefore an existing ordinary item at physical/legacy desktop column `x` in the previous 6-column logical layout maps as:

```text
0 -> 2
1 -> 3
2 -> 4
3 -> 5
4 -> 6
5 -> 7
```

The mapping must preserve `cellY`, `spanX`, `spanY`, `screenId`, item identity, and page membership. Widgets/folders/icons move as grid items; no pixel translation is used.

## Scope

This foundation stage implements:

1. a pure 6→8 horizontal mapping policy with a fixed Stage prefix of two columns;
2. deterministic migration of ordinary existing home items into columns 2–7;
3. drop/restore/orientation legality that treats columns 0–1 as outside the ordinary desktop domain;
4. a fixed Stage overlay host whose bounds come from physical columns 0–1;
5. one Stage host per Workspace/root, independent of page scrolling;
6. lifecycle/rotation rules required to keep the two domains stable.

Deferred to a later stage:

- recent-task acquisition;
- snapshot/live thumbnail rendering;
- task switching/closing gestures;
- Stage card visuals and animations;
- freeform/multi-window control.

## Existing Authorities

### Grid geometry

`HomeGridHook` remains the sole authority for physical 8×4 CellLayout geometry. The feature must not translate the whole `Workspace`, shrink the CellLayout to 6×4, or create a second pixel-coordinate system.

`mXs`, `mYs`, `mCellWidth`, `mCellHeight`, and gap values remain physical 8×4 coordinates.

### Ordinary desktop mapping

A new pure `StageWorkspacePolicy` owns the logical/physical horizontal mapping.

For the 8×4 profile in supported HOME landscape mode:

- `STAGE_COLUMNS = 2`;
- logical ordinary desktop width = 6;
- physical ordinary desktop range = `[2, 8)`;
- Stage range = `[0, 2)`.

An ordinary item with logical desktop coordinate `(x, spanX)` is mappable only when:

```text
x >= 0
x + spanX <= 6
```

Its physical coordinate is `x + 2`.

A physical ordinary item is legal only when:

```text
cellX >= 2
cellX + spanX <= 8
```

### Placement legality

`HomeGridDropLegalityPolicy` remains the pure legality authority consumed by `WorkspaceDropRuleHook` and orientation/restore logic. When Stage mode is active, ordinary placements touching columns 0–1 are illegal. Existing span bounds and 2×2 macroblock constraints continue to apply after projection into physical coordinates.

### Native occupancy

Launcher still owns the actual occupancy matrix and collision resolution. LiquidDock does not create fake occupied cells for Stage. Instead, ordinary item legality excludes physical columns 0–1 and the fixed Stage overlay remains outside every `CellLayout`.

## Existing Item Migration

Enabling Stage mode explicitly migrates ordinary desktop items right by two physical columns.

Migration contract:

- input ordinary coordinates are interpreted in the prior 6-column desktop domain;
- output coordinate is `cellX + 2`;
- `cellY`, spans, screen/page, rank, and item identity are unchanged;
- an item that cannot fit the 6-column logical domain is rejected before mutation;
- migration is planned for the whole page/layout first and then committed; partial per-item migration is forbidden;
- migration is idempotent: content already recognized as Stage-mapped must not be shifted again on Launcher recreation, rotation, or process restart;
- disabling or temporarily hiding the Stage overlay does not implicitly migrate content back left.

Idempotence must come from explicit layout-state/mapping authority, not from guessing `cellX >= 2`, because valid legacy layouts can already contain items in those columns.

The first implementation may keep Stage mode enabled only after a complete migration plan succeeds. If preflight fails, preserve the original layout and leave Stage inactive.

## Stage Overlay Host

Create one Launcher-root/Workspace-level overlay host, never one host per CellLayout page.

Properties:

- fixed relative to Workspace/root while pages scroll underneath/right of it;
- horizontal bounds equal physical Stage columns 0–1;
- empty foundation host does not intercept touch;
- no `ItemInfo`, database row, drag target, or occupancy entry;
- rebound only when Launcher/Workspace authority changes;
- geometry refreshed from stable 8×4 layout callbacks, including rotation.

The host must not be inserted into a `CellLayout`, because page-local ancestry would make Stage scroll with the page.

## Geometry Contract

Given valid 8×4 physical coordinates:

```text
stageLeft  = mXs[0]
stageRight = mXs[2]
```

No fixed dp stage width and no fixed-delay geometry retry are allowed. If `mXs[2]` is not yet available, Stage remains non-presentable until a normal layout/lifecycle callback supplies valid geometry.

Vertical bounds initially follow the Workspace content region. Visual insets belong to the later Stage-card design.

## Orientation and Mode Rules

### Landscape HOME

The first implementation targets the 8×4 HOME landscape profile. The Stage domain is active only when the feature is enabled and the current Workspace is the normal HOME Workspace.

### Portrait

Portrait Stage UI is not part of the first implementation. Rotation must not reapply the +2 migration. Existing orientation-memory/restore logic must preserve item identity and avoid treating Stage columns as ordinary drop targets when returning to the Stage-enabled landscape layout.

### Workstation/Laptop mode

Dedicated laptop All Apps and workstation-specific grid geometry are excluded from this first Stage implementation. Existing workstation authorities remain unchanged.

## Lifecycle

1. Launcher setup/resume resolves current Workspace/root.
2. Stage mode preflights the ordinary-layout migration once for the authoritative layout state.
3. After successful migration/state recognition, ordinary placements are constrained to physical columns 2–7.
4. Stable 8×4 CellLayout geometry supplies Stage bounds.
5. Attach/update one fixed overlay host under Workspace/root authority.
6. Workspace page scroll does not recreate or translate the host.
7. Rotation invalidates geometry only; it does not repeat migration.
8. Workspace/root replacement detaches the old host and binds a new one from persisted/authoritative mapped state.

No timer is an authority boundary.

## Proposed Components

### `StageWorkspacePolicy`

Pure policy, no Android View dependency. Owns:

- `STAGE_COLUMNS = 2`;
- profile/mode support;
- logical 6-column ↔ physical 8-column mapping;
- ordinary placement legality in Stage mode;
- stage horizontal bounds from physical grid coordinates;
- migration preflight helpers.

### `StageWorkspaceMigration`

Pure migration planner. Consumes item positions and produces an all-or-nothing mapped snapshot/plan. It must not mutate Launcher objects directly and must expose enough state for idempotence.

### `StageWorkspaceHost`

Android runtime owner of the fixed overlay container. Owns attach/detach, root binding, stable-grid geometry, visibility, and empty non-intercepting Stage container.

### `HomeGridDropLegalityPolicy`

Extended so Stage-enabled ordinary placements exclude physical columns 0–1 while retaining existing span and macroblock rules.

### `WorkspaceDropRuleHook`

Continues to bridge MIUI's native drop callback to the pure legality policy. It must not duplicate `+2` arithmetic.

### `HomeGridHook`

Continues to own 8×4 physical geometry and provides stable geometry callbacks to `StageWorkspaceHost`. It does not translate Workspace or recalculate a separate 6×4 pixel grid.

## Failure Behavior

- unsupported/transient grid: do not activate Stage;
- incomplete migration preflight: no partial moves; preserve current desktop and keep Stage inactive;
- missing stable geometry: host hidden until normal layout callback;
- reflection/API mismatch: log and fail closed;
- workstation All Apps: never apply HOME Stage mapping;
- repeated Launcher setup/rotation: recognize mapped state and never apply +2 twice.

## TDD Requirements

### Pure mapping tests

- `logical x=0 -> physical x=2`;
- `logical x=5 -> physical x=7` for a 1×1 item;
- 2-wide item at logical `x=4 -> physical x=6` and fits exactly;
- item whose logical `x + spanX > 6` is rejected;
- physical ordinary placement at x=0 or x=1 is illegal;
- physical placement fully within x=2..7 is legal;
- non-8×4/unsupported modes do not activate Stage mapping.

### Migration tests

- a full legacy 6×4 arrangement maps every item by +2 with all non-X fields unchanged;
- mixed icons/folders/widgets preserve spans and page IDs;
- preflight failure yields no partial result;
- applying migration to a layout already marked mapped is a no-op;
- recreation/rotation does not shift a mapped layout from x=2..7 to x=4..9.

### Geometry/host tests

- Stage bounds equal `[mXs[0], mXs[2])`;
- host is one-per-Workspace/root;
- host ancestry is outside CellLayout;
- page scrolling does not alter host x;
- empty foundation host does not intercept touches.

### Integration checks

- existing 8×4 sizing and widget tests remain green;
- enabling Stage shifts existing ordinary desktop content right once;
- drag/drop rejects Stage columns and accepts ordinary columns;
- page swipe moves only ordinary desktop pages;
- rotation does not duplicate migration;
- Stage overlay lifecycle does not disturb Dock/Workspace glass.

## Non-goals

- no fake 2×4 widget;
- no Workspace `translationX`;
- no fixed-delay migration or geometry retry;
- no recent-task UI in the foundation stage;
- no automatic reverse migration on feature disable in the first stage.
