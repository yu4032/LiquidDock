# API 102 atomic Hook replacement

`HookHandle.replaceHook()` replaces callbacks inside the current process. The module sets `autoHotReload=false`; APK updates take effect after restarting the corresponding target process.

## Official API 102 contract

`XposedInterface.HookHandle.replaceHook(newHooker)` atomically exchanges a
currently registered callback. It retains the same executable, hook ID, priority
and exception mode and returns a **new** handle. The previous handle becomes
invalid. Existing in-flight calls keep their original chain snapshot.

Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.HookHandle.html

## Implementation

- `Api102HookDomain.replaceActive(method, hooker)` only operates when the
  domain is ACTIVE and already owns that exact stable method-signature ID.
  It delegates to the existing `Api102HookRegistry.replaceIdentified`.
- The registry records only the new returned handle after replacement succeeds;
  on failure it retains the old handle. Neither replacement nor failure calls
  `unhook` or re-registers another interceptor.
- **SystemUI gesture handle**: on the first discovery of its native
  `setAlpha(float, boolean)`, install a named hook. On subsequent
  `NavigationBar` recaptures of the same native handle class, **replace the
  Hooker atomically** instead of skipping the already-hooked class or adding a
  duplicate callback. Scene state and vendor alpha bookkeeping stay unchanged.
- All existing `HookUtil.hook`, constructor hooks, `hookWithPriority` and both
  `hookMethod` variants now pass through the same API 102 registry. Each unique
  source registration site, actual `Executable` (including its ClassLoader identity)
  and priority gets an independent named hook slot; repeated registrations at that
  exact site use `HookHandle.replaceHook()` rather than stacking another callback.
- Unrelated registration sites hooking the same Method stay separate; no flattening
  of multi-hook chains. Existing named `Api102HookDomain` hooks remain owner-scoped.
- Site lookup uses the caller frame at hook-registration time, not at invocation time.
  ART's line-less `dalvik.system.VMStack` frames are skipped before reading the real
  registration site. If no located caller exists, a unique slot avoids replacing
  an unrelated hook. Site identities are process-local, not transferable between APKs.
- The separate, device-verified shortcut-popup/drag PassBlur source-window lifetime
  fix is integrated into the same PR; see `transient-passblur-detach-ownership.md`.
  It does not change hook priorities, hot-reload settings or ModuleMain callbacks.

## Validation

Run `./gradlew testDebugUnitTest assembleDebug`. JVM fake-handle tests verify:
replacement only for an ACTIVE owner, absent method rejection, stable handle
count, no unhook gap, retaining old handle after a failed replacement, and
unhooking **only the latest** handle on owner shutdown. Shared-registry tests cover
independent multi-hooks on the same target, constructor hooks, priority separation,
repeated same-site replacement and replacing one class without touching another.
Five additional tests feed ART-shaped stack traces to the source-site resolver.
An R8 release device run confirmed 207 located registrations and 0 unlocated
registrations after the ART frame fix; initial startup does not exercise replacement.

Device acceptance (API 102 framework): recreate the SystemUI navigation bar,
re-enter HOME/RECENTS, and confirm the native gesture handle alpha still
follows the configured visibility while no duplicate interceptors accumulate.
Test enabling/disabling the gesture feature, multiple nav-bar attachments, and
a replacement failure. CI covers registration and replacement contracts; the device checklist covers SystemUI scene transitions and callback reuse.

**Lifecycle boundary:** The process owns its installed Hook callbacks and renderer resources. APK code and assets are reloaded at the next target-process start.
