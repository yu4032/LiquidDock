# API 102 migration v2 — clean main baseline

Baseline: `b975f36b09cc520d5e71473a816ff85e1768093b` (LiquidDock 2.7.5).
Branch: `experiment/libxposed-api102-v2`.
Historical experiment: PR #291; do not merge its divergent file tree.

## Phase 1: fresh API 102 compile baseline

- Update libxposed API and service dependencies to `102.0.0`.
- Explicitly require API 102 in `module.prop`, retain `staticScope=true`.
- Explicit `autoHotReload=false`: package updates still require restarting injected processes.
- Preserve all current main process integrations, runtime-config paths, renderer implementations and config migrations; do not copy the obsolete historical `ModuleMain`.
- Add API 102 lifecycle callbacks with a process-local pre-install handoff gate. System's `onModuleLoaded` path and all targeted `onPackageReady` paths claim ownership **before** invoking any installation code. A failed or partial bootstrap still blocks hot reload.
- In an admitted pre-install handoff, any late old-generation target initialization is suppressed; only a simple process name crosses generations.
- Retain the historical `Api101Bridge` type name to avoid a large unrelated mechanical rename.

## Limits / next checkpoints

The phase-1 handoff does **not** unhook a live process. It has no domain-specific teardown,
no in-flight hook barrier, no listener/receiver unregistration, and no EGL/SurfaceTexture shutdown.
`onPackageReady` / `onModuleLoaded` do not automatically replay after hot reload. The
`Api102ReloadGate` only prevents an unsafe install/handoff overlap.

1. Run R8-optimized `testDebugUnitTest assembleDebug` on the new PR.
2. Confirm the module loads under an API 102 framework in Launcher, SystemUI and scoped process targets; validate preference binder behavior. Do not install this experiment on 101-only frameworks.
3. Move the previous test-proven `Api102HookRegistry` / `Api102HookDomain` designs to the **current** main-derived code one owner at a time, preserving priority and vendor behavior.
4. Audit SystemUI gesture native alpha restoration, dynamic hook/listener epochs, and HandleMenu EGL resources separately.
5. Only enable automatic hot reload after every affected process owner can atomically drain, release and resume, with device evidence.

A passing CI proves build/JVM compatibility only. There is no device validation yet.

## Phase 2: HookHandle ownership pilot

- Migrated `Api102HookRegistry` and `Api102HookDomain` against the current main-derived code.
- Existing ordinary HookUtil method and constructor interceptors retain unnamed handles; multiple
  valid callbacks on the same method are not assigned duplicate synthetic IDs.
- High-priority Dock and HomeGrid interceptors route through tracked, priority-preserving
  `HookUtil.hookWithPriority` without changing their callback logic.
- `SystemUiKeyguardGoneSource` now uses a named, owner-scoped hook transaction with partial
  install rollback, blocked-on-failed-unhook behavior, and a bounded in-flight side-effect barrier.
- A named registry handle can be replaced in isolation for testing; production never calls the
  replace primitive during reload. JVM tests cover identities, rollback and drain behavior.
- Module entry rejects generation handoff when the registry contains hooks, in addition to the
  existing owner lifecycle gate. `autoHotReload=false` remains mandatory.

Still blocked: SystemUI gesture/native alpha hooks, preferences listener generations,
HandleMenu EGL sessions, Launcher Views, native PassBlur producers, and all-process teardown.
CI green is insufficient to claim device support; real API 102 verification remains required.

## Phase 3: SystemUI gesture owner registry and listener epoch

- Fixed gesture hooks and dynamically discovered TaskStack, LauncherProxy, and native setAlpha
  hooks use `Api102HookDomain("systemui.gesture.handle")` with stable per-signature IDs.
- Listener registration now uses `Api102ListenerEpoch`; stale preference callbacks and posted
  main-thread notification Runnables are ignored after initialization changes or shutdown.
- `GestureHandleSystemUiSceneState.resetForFutureReload()` clears stale scene authority.
- Domain startup/abort is transactional. A partially installed LauncherProxyListener retains its
  class marker, avoiding duplicate callback registration.
- The gesture owner has NO public complete `stopForFutureReload()`: native alpha writes,
  HOME_HANDLES, and in-flight vendor writes cannot yet be restored/drained atomically.
- Tests cover listener epochs, stale scene reset, domain tracking and stop failure. These are
  JVM-only and never make a device safe for full APK hot reload.

## Phase 3 audit corrections (follow-up)

- Preference callbacks now acquire the same lifecycle monitor as initialize/shutdown **before**
  checking the listener epoch and mutating live state. The previous pre-lock epoch check allowed
  a stale callback to resume after shutdown and re-enable the gesture runtime.
- A failed preference listener unregister now retains the old listener references and fails
  initialization closed rather than silently leaking an untracked registered observer.
- Hook registry rollback diagnostics are best-effort; a logging failure must not interrupt
  reverse-order unhook of other handles.
- These are targeted race/error-path corrections; no new hot-reload capability is claimed.

## Phase 4: reversible gesture alpha + ordered HandleMenu teardown (experimental)

- `GestureHandleAlphaState` tracks the latest **vendor-requested** alpha with weak keys.
  New native handles captured during an already-hidden HOME/RECENTS scene are immediately
  hidden; a failed alpha write is retried even if the scene flag does not change.
- `SystemUiGestureHandleFadeHook` now holds `Api102HookDomain` read-side leases for
  all native hook post-effects, including dynamically discovered listeners and alpha writes.
  The owner-scoped `stopForFutureReload()` disarms preference callbacks, drains/unhooks its
  domain, restores native alpha, and retains failed restores for retry. It is **not called**
  from `ModuleMain` and does **not** imply process-level hot-reload safety.
- `SystemUiHandleMenuPrismalSession.shutdown()` now orders native ViewRoot PassBlur
  producer unbinding on the main looper **before** EGL/SurfaceTexture/Surface destruction
  on the render looper. Previously independent posts could race and destroy a still-bound
  producer. This remains asynchronous, best-effort cleanup: a failed native unbind, rejected
  handler post or incomplete GL release is not yet surfaced to a process-wide owner gate.
- The PR workflow display name and debug APK artifact are now labeled API102. The
  workflow filename remains `.github/workflows/api101-build.yml` for historical continuity.
- Pure-JVM `GestureHandleAlphaStateTest` covers late handle capture, vendor writes,
  retryable native restore, and retry of failed visibility writes.

### Remaining blockers and device checks

1. Do **not** invoke gesture teardown from the module reload callback until every SystemUI
   owner (HandleMenu, keyguard, global SurfaceControl hooks, glass config listeners) is
   drained and independently reversible.
2. Verify native gesture alpha across HOME, RECENTS, Overview, a second NavigationBar
   instance, and preference changes. Confirm the latest vendor alpha is restored after
   owner teardown, not an assumed default of 1.0.
3. Exercise HandleMenu open/close, Prismal first-frame failure, root detach, rapid menu
   replacement and renderer failure; inspect producer unbind and EGL resource counts on
   an API102 device. A successful JVM/R8 build does not validate GPU/native teardown.
4. Explicitly account for `SystemUiHandleMenuSurfaceAnimationAuthority`'s global
   `SurfaceControl.Transaction.setAlpha` hook and all active/pending view bindings before
   considering process-wide reload. No process-wide cleanup contract exists yet.

`autoHotReload=false` and Draft PR status remain unchanged pending device evidence.

### Follow-up race audit

- `SystemUiHandleMenuPrismalSession.start()` rechecks `shuttingDown` inside its
  render-thread task; the initial caller-side guard alone allowed a queued start to
  recreate EGL after a concurrently scheduled shutdown. Shutdown dispatch is idempotent.
- `Miuix307PassBlurBridge.unbind()` currently catches native transaction failures and
  clears its `Binding.bound` flag even on failure. Thus ordered shutdown cannot yet
  *prove* the producer was detached. A checked, retryable unbind contract is required
  before treating HandleMenu as a reloadable process owner.

## Phase 5: checked native PassBlur detach (experimental)

- `Miuix307PassBlurBridge.tryUnbind(binding)` returns **false** on an invalid root,
  reflection failure or native transaction failure, without clearing `Binding.bound`,
  `updatesEnabled`, or the vendor continuous-authority claims. Claims are released only
  after a successful detach transaction. **Phase 6 supersedes this intermediate
  apply-only behavior:** legacy void `unbind()` callers have been removed, and
  a true result now requires the transaction-committed callback.
- `SystemUiHandleMenuPrismalSession` now uses `HandleMenuTeardownGate` to forbid
  render-thread EGL/SurfaceTexture destruction until checked producer detach succeeds.
  Failed detaches are retried up to three attempts with an 80ms delay; failures and
  rejected Handler posts retain a strong, explicitly countable blocked-session reference.
  `retryBlockedTeardowns()` retries those sessions when a new HandleMenu starts.
  Permanent native failures can intentionally retain GPU resources rather than release
  a still-bound producer; this is a **fail-closed leak risk**, not full cleanup.
- A ViewRoot generation mismatch now retains its producer Binding if detach fails,
  rather than discarding the only retryable reference.
- Pure-JVM `HandleMenuTeardownGateTest` covers false/throwing detach, blocked render
  release, duplicate release prevention, and retry after rejected render enqueue.

### Remaining safety blockers

1. Render cleanup functions still swallow some EGL/GL/Surface release failures.
   A queued render cleanup is not proof of successful native resource destruction.
2. All direct Bridge call sites now use checked retirement, but the other owners still
   require API102 device lifecycle verification for their asynchronous callbacks.
3. HandleMenu's global `SurfaceControl.Transaction.setAlpha` hook, live alpha listeners,
   pending/active View bindings, and cross-owner glass callbacks are not fully drainable.
4. An invalidated ViewRoot may never become detachable again. Retained failed sessions
   require explicit diagnostics and device verification; no automatic hot reload admission.
5. The native bind error path may have partial compositor side effects before returning
   null; this needs a separate transactional bind audit.

`autoHotReload=false` and Draft PR #465 are unchanged. Do not infer device safety from
JVM tests or a successful CI build.

## Phase 6: compositor-committed producer retirement

- `tryUnbind(binding, onCommitted)` installs a real
  `SurfaceControl.Transaction.addTransactionCommittedListener` before applying the detach
  transaction. `Binding.bound` remains true until this callback fires; a transaction
  submission or invalid-root failure does not count as detached. Vendor authority claims
  are released only in the commit callback.
- `PassBlurDetachCoordinator` retains the Binding **and owner cleanup continuations**
  strongly until compositor acknowledgment. It attempts bounded retries, can retry blocked
  entries explicitly, and exposes `pendingCount()`. A never-acknowledged transaction
  intentionally blocks producer destruction and remains an outstanding resource liability.
- All three direct Bridge callers were migrated: `RootPassBlurBackend` (one-shot folder
  retirement, rebind, shutdown, ViewRoot generation mismatch);
  `Miuix307PassBlurTextureView` (Dock rebind and shutdown); and
  `SystemUiHandleMenuPrismalSession` (checked detach, delayed completion retry).
  The old fire-and-forget `unbind()` method was removed to catch regressions at compile time.
- `PassBlurDetachCallbacks` isolates independent cleanup callbacks so one failing
  continuation or logger cannot suppress others. JVM tests simulate late commit,
  concurrent close, and callback failure.
- **Semantics:** the Android commit event confirms that the transaction's updates
  have been committed and are ready to be presented; it explicitly does NOT mean
  all buffers are released by GPU consumers. The implementation does not provide
  buffer-release fencing or a verified drain of in-flight GPU work.
- HandleMenu now retains blocked cleanup state until the render-thread release task
  actually completes; thrown cleanup errors trigger retry instead of premature success.
  Individual EGL return codes may still be swallowed by existing low-level routines.
- Callbacks, render-thread cleanup, producer-retirement completion, and blocked
  handler dispatch still require on-device lifecycle verification. Native bind's
  partial-success error path and other owner-specific EGL teardown failures remain
  independent migration blockers.
- Existing `RootPassBlurBackendState`/producer recovery bookkeeping is retained;
  no whole-process hot-reload callback is wired and **autoHotReload remains false**.

### Validation checkpoint

- Await GitHub Actions' final unit/R8/assemble conclusion for this HEAD; prior
  successful run #40274057874 predates the compositor-commit implementation.
- Verify root rebind and folder snapshot remain responsive after asynchronous detach;
  inspect the coordinator pending count for leaks, and test missing/delayed native
  callbacks and forced root invalidation on a real API102 framework.

## Phase 6 regression: missing Workspace glass after ViewRoot rollover

- Identified a recovery deadlock introduced by commit-gated native detach:
  `RootPassBlurBackend.requestRebind()` previously waited for the previous
  `SurfaceControl.Transaction` committed callback **before** creating the
  replacement producer. A destroyed previous ViewRoot cannot submit a new
  transaction; its callback may never arrive, leaving the recovery state
  `rebindPending=true`, no new BufferQueue, and Workspace glass absent.
- Added `WorkspacePassBlurRolloverPolicy`: only a **Workspace** owner with a
  distinct, verified live replacement ViewRoot **and an invalid old root**
  can isolate the old source. Other domains and valid roots retain the
  strict native-commit-before-resource-release ordering.
- Invalid-root recovery now quarantines the old `Binding`, Surface,
  SurfaceTexture and OES texture without freeing them; a new GPU producer
  is created separately so the Workspace can bind and present again.
  An eventual compositor-committed callback releases the quarantined
  resources on the render thread. No callback = retained orphan resource;
  this is a deliberate fail-closed trade-off until device validation.
- Rechecks the pending rollover when a new ViewRoot is discovered after the
  initial request, and at bounded delays (300/1200ms) so a late invalidation
  does not require another user gesture.
- Shutdown does not destroy the EGL context while a quarantined producer
  still awaits native acknowledgment. JVM tests assert recovery-state
  transitions and guard against incorrectly isolating live/same-generation
  producers.
- No real-device confirmation yet, and `autoHotReload=false` remains unchanged.
