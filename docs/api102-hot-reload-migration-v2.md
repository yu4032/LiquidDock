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
