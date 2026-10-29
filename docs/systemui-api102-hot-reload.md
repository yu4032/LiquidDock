# SystemUI guarded hot reload — experimental (libxposed API 102)

## Intent

Load a **new installed LiquidDock APK generation** into the already running
`com.android.systemui` without killing the process, using libxposed 102's official
`XposedService.hotReloadModule(HookedTarget, Bundle, HotReloadCallback)`.
This is deliberately **not** a general-purpose restart-free module update.

Official references:
- https://libxposed.github.io/api/io/github/libxposed/api/XposedModuleInterface.html
- https://libxposed.github.io/api/io/github/libxposed/api/XposedModuleInterface.HotReloadedParam.html
- https://libxposed.github.io/service/io/github/libxposed/service/XposedService.html
- https://libxposed.github.io/service/io/github/libxposed/service/HookedTarget.html
- https://repo.maven.apache.org/maven2/io/github/libxposed/

## Supported path

1. **After the first install of this branch, restart SystemUI once.** The old
   on-device code must already implement the teardown callback before it can reload.
2. Install a later APK update with the **same application ID and signing key**.
3. Without opening SystemUI's native caption/HandleMenu glass since the last
   SystemUI process restart, open **许可 → 运行诊断 → SystemUI 热重载（实验性）**.
4. Tap **热重载 SystemUI**. The UI selects only the live exact
   `com.android.systemui` target returned by `getRunningTargets()`. It skips
   `UP_TO_DATE` and `RELOADING` targets.
5. The service asynchronously reports the result. A successful submission does
   **not** mean reload succeeded. Check framework log for
   `[DC][API102] SystemUI integration bootstrap attempted in new generation`.

## Admission and retirement contract

The old SystemUI generation MUST:
- have API 102, live Application and no active Surface alpha listeners;
- have **never owned** a caption menu binding in that generation (conservative
  proxy for potentially pending PassBlur/GL/SurfaceTexture cleanup);
- disarm and unregister the external Remote Preferences listener, drop queued
  config dispatch, and stop keyguard and gesture observer domains;
- restore the latest vendor-requested gesture alpha **on SystemUI main looper** where applicable, using a bounded main-thread retirement task;
- detach all anonymous `HookUtil` handles, and verify the process registry is
  empty before granting the old-generation handoff;
- pass only a loader-neutral `String` as saved state.

The new generation explicitly runs SystemUI initialization from
`onHotReloaded()`, because libxposed does **not** replay
`onModuleLoaded()` / `onPackageReady()`.

**Non-goals:** automatic APK hot reload; Launcher hot reload; `system`
server hot reload; general GL-backed owner replacement; GPU buffer retirement
proof. `autoHotReload=false` intentionally remains unchanged.

## Failure handling

A `FAILED`, `UNSUPPORTED`, `IN_PROGRESS`, or `PROCESS_DIED` service
callback is **not** success. If the old owner refused or teardown partially
completed, some optional SystemUI module features may remain disabled until
a standard **SystemUI scope restart**. Do not retry repeatedly to defeat the
guard, and do not force unhooked renderers back into old code.

A SurfaceControl transaction-committed event is **not** a GPU fence. Even after
a menu closes, this implementation refuses reload if any menu had been owned
during the current SystemUI generation.

## Acceptance checklist (real device required)

- [ ] GitHub Actions unit tests, R8 debug build, and artifact creation pass.
- [ ] First branch install + one normal SystemUI restart; verify keyguard,
      HOME/RECENTS native gesture alpha, and caption menu fallback.
- [ ] Install a later signed APK without restarting SystemUI. With no menu
      opened since previous restart, verify service returns `STALE`; request
      reload; observe `SUCCEEDED` and stable **same SystemUI PID**.
- [ ] After successful reload, verify keyguard GONE events, changed gesture
      setting, navigation bar recreation, menu-first-open and native fallback;
      ensure no doubled callbacks or retained old `HookHandle` callbacks.
- [ ] With a caption menu previously opened, verify the old code returns
      `false`, the service reports `FAILED`, and native resources are not
      force-destroyed; then restart SystemUI normally.
- [ ] Test rapid config changes during request, an already-running reload,
      no service, unsupported framework, and repeated failed unhook.
- [ ] Track `dumpsys meminfo com.android.systemui`, native render threads,
      EGL/SurfaceTexture allocation counts and module old-loader retention.

**Code review and CI do not constitute successful device validation.**
