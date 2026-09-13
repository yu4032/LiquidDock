# Stage Manager Workspace Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the left two columns of the existing 8×4 HOME grid into a fixed Stage domain while migrating the ordinary 6×4 desktop right by exactly two logical cells.

**Architecture:** Preserve `HomeGridHook` as the physical 8×4 geometry authority. Add pure mapping/migration policy first, then wire drop legality and Launcher migration, and finally attach one fixed Stage overlay outside `CellLayout` page ancestry. Never translate the whole Workspace and never use timers as lifecycle authority.

**Tech Stack:** Java, libxposed API 101, Android View/Launcher private APIs through existing `HookUtil`, JUnit4, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-13-stage-manager-workspace-foundation-design.md`

## Global Constraints

- Physical HOME grid remains 8×4.
- Stage occupies physical columns 0–1; ordinary desktop occupies 2–7.
- Legacy logical desktop x maps to physical x+2.
- Migration is all-or-nothing and idempotent.
- No Workspace `translationX`.
- No fake Stage `ItemInfo` or occupancy cells.
- Stage host is one-per-Workspace/root and does not scroll with pages.
- Workstation All Apps remains excluded.
- No fixed-delay migration/geometry authority.

---

### Task 1: Pure Stage mapping policy

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/StageWorkspacePolicy.java`
- Create: `src/test/java/com/hellovoid/liquiddock/StageWorkspacePolicyTest.java`

**Interfaces:**
- Produces: `StageWorkspacePolicy.STAGE_COLUMNS`, `isSupported(HomeGridProfile,int,int,boolean)`, `mapLogicalX(int,int)`, `isOrdinaryPhysicalPlacementLegal(int,int)`, `stageBounds(int[])`.

- [ ] Write RED tests for x=0→2, x=5→7, span-2 x=4→6, overflow rejection, physical x<2 rejection, unsupported profile/mode rejection, and `[mXs[0],mXs[2])` bounds.
- [ ] Run `testDebugUnitTest` and confirm only the new mapping contract fails/does not compile.
- [ ] Implement the smallest Android-free policy satisfying those cases.
- [ ] Run unit tests and commit.

### Task 2: Idempotent all-or-nothing migration planner

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/StageWorkspaceMigration.java`
- Create: `src/test/java/com/hellovoid/liquiddock/StageWorkspaceMigrationTest.java`
- Reuse: `HomeGridItemPosition`, `HomeGridLayoutSnapshot`.

**Interfaces:**
- Produces: immutable `PlanResult` containing success/mapped-state/result positions.
- Consumes: ordinary legacy positions plus an explicit already-mapped state flag/token; never infer idempotence from `cellX` alone.

- [ ] RED: all items shift +2 while y/span/screen/id stay unchanged.
- [ ] RED: widgets/folders with spans preserve spans.
- [ ] RED: one invalid item fails the whole plan and returns no partial snapshot.
- [ ] RED: `alreadyMapped=true` returns unchanged positions.
- [ ] Implement the pure planner and rerun tests.
- [ ] Commit.

### Task 3: Stage-aware drop legality

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/HomeGridDropLegalityPolicy.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/WorkspaceDropRuleHook.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/HomeGridDropLegalityPolicyTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/WorkspaceDropRuleHookContractTest.java`

**Interfaces:**
- Add Stage-aware legality overload/flag; existing non-Stage behavior remains byte-for-byte semantically compatible.

- [ ] RED: Stage mode rejects physical x=0/1 and accepts valid placements in x=2..7.
- [ ] RED: existing non-Stage tests remain unchanged.
- [ ] Wire hook to pure Stage policy without duplicating +2 arithmetic.
- [ ] Run focused + full unit tests and commit.

### Task 4: Runtime migration authority

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/StageWorkspaceRuntime.java`
- Modify the existing HOME layout/orientation integration point identified during implementation; do not add an independent timer loop.
- Add focused runtime/contract tests.

**Interfaces:**
- `bindWorkspace(...)` resolves one authoritative Workspace/root.
- `ensureMapped(...)` preflights the whole layout before any Launcher mutation.
- explicit mapped-state authority prevents duplicate +2 shifts across recreation/rotation.

- [ ] RED: first activation plans +2 once.
- [ ] RED: second setup/rotation is a no-op.
- [ ] RED: failed preflight leaves original layout untouched and Stage inactive.
- [ ] Implement using existing Launcher item persistence/update path discovered in code/decompilation; do not mutate View coordinates only.
- [ ] Run CI and commit.

### Task 5: Fixed Stage overlay host

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/StageWorkspaceHost.java`
- Modify: `HomeGridHook.java` or the narrow existing Workspace layout callback used to expose stable physical geometry.
- Add pure geometry and source-contract tests.

**Interfaces:**
- one host per Workspace/root;
- bounds from physical `mXs[0]` and `mXs[2]`;
- host is outside `CellLayout` ancestry;
- empty host returns false/no-op for touch interception.

- [ ] RED geometry/ancestry/lifetime contracts.
- [ ] Implement attach/update/detach without timers.
- [ ] Verify page scrolling does not translate host.
- [ ] Commit.

### Task 6: Integration and device checkpoint

**Files:**
- Modify configuration/install wiring only as required by the completed components.
- Update docs after actual runtime behavior is verified.

- [ ] Run zero-copy audit, full `testDebugUnitTest`, and `assembleDebug` in CI.
- [ ] Verify existing 8×4, widget, rotation, Dock/Workspace glass tests remain green.
- [ ] Device check: existing icons move right once; left two columns stay visually separate; drag cannot place ordinary items in Stage; pages scroll while Stage host stays fixed; rotation/relaunch does not shift items again.
- [ ] Keep PR draft until device behavior is verified.
