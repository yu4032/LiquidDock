# OS4 Native layout memory and automatic squeeze

User authorization: implement Native portrait/landscape layout memory and port the existing module's automatic squeeze behavior. Execute inline in the existing liquiddock4 checkout; retain unfinished Java drafts for reference.

## Contract

- Store complete independent positions for portrait and landscape, scoped to grid and item identity. Page membership follows the active layout in both directions.
- Remembered target positions take precedence. New items are placed on the same page without overlaps; impossible placement aborts atomically.
- Persist successful layouts across Launcher and device restart. Do not change vendor database schema or invent new pages during rotation.
- Retain automatic squeeze. Reference: HomeGridSqueezePlannerHook/Policy, CycleGuard and TransactionGuard. Stock behavior for ordinary icons, rectangular handling for multi-cell items, bounded traversal and rollback.
- Native functions must match complete fingerprints for fuxi 8583. A Java test or compilation is not a device acceptance result.

## Tasks

1. Prove a Dart-to-C observer bridge on the device: preserve arguments, Dart x15, native SP, integer/FPU registers and flags; never retain Dart pointers across safepoints. Use an ARM64 executable harness before live hooks.
2. Implement pure C snapshot reconciliation, same-page placement and atomic persistent store. Test independent round-trip, new/deleted/moved/resized items, impossible pages, corrupt files, grid isolation.
3. Trace and integrate the proven native CellLayout model/occupancy/commit boundaries. Restore before occupancy rebuild; capture after successful edits; persist only validated complete layouts. Verify field identities with database and screenshots.
4. Port and gate the original automatic squeeze selection/guards at the corresponding native boundaries. Verify single icons and multi-cell components on empty and occupied targets; impossible operations must leave the layout intact.
5. Build/package/sign with alignment preserved, verify signed ZIP and install. Test actual distinct icon positions per direction, cross-page edits, components, repeated rotations, Launcher restart and reboot. Restore auto-rotation and record evidence. Do not claim unverified cases.

## Review focus

Rapid rotation, stale source snapshots, concurrent database flushes, impossible widget footprints, rollback after partial squeeze. Fail closed rather than partially publish a layout.

## Ledger

- Existing Android 17 launcher is hyos_spawner with no ART. Java Activity hooks cannot implement this task.
- User clarified automatic squeeze is required; initial no-squeeze assumption is withdrawn.
- Baseline device package stable-27d78f85; full layout/config backup exists in task work directory.

- Native runtime now restores DTO batches before classification and uses the
  original Dart occupancy/commit continuation. Separate SQLite startup rewrites
  were abandoned after live migration/WAL failures; no such writes remain active.
- Odd-cell 2x1 and 2x2 widgets verified with automatic squeeze on the device.
- Startup capture is blocked until DTO preparation. Deletion/folder tombstones
  remove stale entries in both directions. New widget footprints can repack the
  opposite snapshot when preserving every pinned position is impossible.
- Review found and fixed readiness recomputation when Rust loads after Dart.
- Three-column restart reproduced a Rust bounds panic before Dart classification;
  complete-function guarded Rust delegation fixes it. Cold-boot inactive filter
  enumeration also reproduced a wrong-domain restore; validated active GOC
  geometry now takes precedence. Regression fixture failed before and passed after.
- Final native host fixtures, seven ELF verifier tests, all three ARM64 executable
  harnesses and Gradle testDebugUnitTest/assembleDebug passed (Gradle up-to-date).
- Final 3x8/8x3 full reboot and direction round-trip preserve all 21 IDs, pages,
  coordinates and spans exactly. Final 4x8 reboot validation follows in the
  user-facing acceptance record. Existing blank page-one widgets are retained.

- Follow-up regression: after hours of runtime, all sampled hook sites had their
  original APK bytes again (private clean file pages, no COW dirty pages), and
  rotation stopped reaching the batch planner. Reproduction on screen showed
  portrait coordinates clipped by a landscape viewport despite 21 DB records.
- Protect every successful inline patch's pages with mlock; pin failure disables
  custom counts/placement together with layout restoration. Only 40 KiB pinned
  in the tested process. Caller of the original page clearing remains unknown.
- New host + ARM64 red/green test reproduces MAP_PRIVATE patch discard and checks
  locked cross-page preservation. New full runtime fixture verifies two pages'
  CellItemInfo + model coordinates finish mapping before vendor continuation.
- Eight consecutive device rotations with compaction and sleep/wake: every turn
  retains all 15 page-two UI items within the viewport and all 21 exact DB rows.
  This expands acceptance beyond the earlier short-lived database-only checks.
