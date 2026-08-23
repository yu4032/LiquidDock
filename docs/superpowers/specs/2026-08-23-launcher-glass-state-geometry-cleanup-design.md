# Launcher Glass State and Geometry Cleanup Design

## Goal

Preserve the hardware-validated Workspace producer architecture from `main @ 1351e4af4381be181c382ac4bce2975888228dab` while removing lifecycle semantic conflation, dead/shadow state, and redundant per-frame geometry observers.

## Baseline and source-of-truth rule

The current CI artifact is not built from repository Java sources alone. `.github/workflows/api101-build.yml` applies four `ci/24bug-*.patch` files before unit tests and assembly. The first change in this refactor must materialize those four patches into repository source and remove the build-time patch indirection. After that commit, repository source and APK source must be identical.

## Hard regression boundaries

The following behavior is already vendor- or hardware-validated and must remain unchanged by this cleanup:

- endpoint rollover
- rotation settle and its producer gating
- wallpaper candidate/authoritative token semantics and WallpaperContentGeneration
- CLOSE_TO_HOME proxy hidden/visible ownership tri-state
- Layer2 `mIsDrawIcon`
- Folder/Recents semantic covered behavior
- Surface replacement detection based on the real Surface generation path
- Surface identity guard using `viewRootIdentity + surfaceSequenceId + rootLayerId`

## 1. Dead and shadow state removal

- Delete `LauncherGlassGeometryStability`; its two-frame stability branch is unreachable under the current Session call path.
- Delete `StateMachine.covered`; derive covered state from `state == COVERED`.
- Delete Launcher Session `hasConsumedFrame`; `consumedGeneration >= 0` is the single representation of whether a valid source has been consumed.
- Delete `LauncherGlassFramePolicy.requestBackdropRefresh()` and its self-only tests because production has no callers.

## 2. HOME lifecycle semantics

`requestFreshBackdrop()` represents real source invalidation only.

- `setAnimTargetVisibility(VISIBLE)` may end an existing CLOSE_TO_HOME proxy owner, but ordinary VISIBLE must not be interpreted as HOME-return.
- Remove the Mingou-specific recovery hook.
- `Launcher.onResume` must not force a fresh producer. It may reconcile the current page and allow the normal pre-draw path to observe geometry.
- Surface replacement remains Session-owned via actual Surface generation changes.
- Wallpaper change remains owned by WallpaperContentGeneration.
- Rotation remains owned by the validated rotation-settle path.

## 3. One static Workspace geometry authority

Each static node computes exactly one final `LauncherGlassGeometry.Snapshot` per pre-draw and compares it directly with the previous Snapshot. The final Snapshot already includes ancestor scroll, ancestor/local transforms, final root-space bounds, alpha, and effective visibility.

Delete redundant observation state:

- `LauncherGlassScrollMotionTracker`
- `LauncherGlassRootTransformTracker`
- `LauncherGlassEffectiveVisibilityTracker`
- `LauncherGlassGeometryStability`
- cached `lastLeft/lastTop/lastRight/lastBottom`
- cached `lastVisibility/lastAlpha`
- cached `lastMatrix`

`LauncherGlassStaticNode` reuses node-owned point and Matrix buffers so dense 10x6 pages do not repeatedly allocate `float[8]` and `Matrix` during pre-draw geometry capture.

## 4. Identity-aware ViewTreeObserver ownership

`recoverFreshBackdropOnUi()` must not unconditionally remove and re-add its observer. Register only when:

- no observer has been installed yet;
- the stored observer is dead; or
- `root.getViewTreeObserver()` returns a different observer identity.

A normal fresh request does not churn VTO listeners.

## 5. Generation semantics

Controller remains the only creator of scene generations. Session's scene-generation value is a render-thread expected epoch / consumption token, not a second generation authority. Keep the field structurally; renaming to `expectedSceneGeneration` is permitted only if it improves clarity without changing behavior.

## 6. Surface identity

Do not simplify `viewRootIdentity + surfaceSequenceId + rootLayerId` in this refactor. `rootLayerId` remains a vendor generation guard after recent HyperOS BLAST/rotation failures.

## 7. Tests

Prefer behavior tests over source-shape assertions. Remove tests whose only subject is deleted helper/state machinery, including the obsolete scroll-motion, resume-recovery, scene-ownership/source-shape contracts where behavior is already covered elsewhere. `AppReturnProxyGeometryTest` retains only real VisualOwnerState/ProxyVisibility behavior.

Do not add brittle `contains()` assertions merely to prove a field was deleted.

## Verification

At each independent change:

1. Preserve or add a behavior-level failing test where semantics change.
2. Make the smallest production change to pass it.
3. Run `testDebugUnitTest` through GitHub Actions.
4. Run `assembleDebug` through the same workflow.
5. Before completion, inspect the final diff to ensure none of the hard regression paths were structurally rewritten.
