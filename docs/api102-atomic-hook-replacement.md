# API 102 atomic Hook replacement (no hot reload)

This branch starts at `main`. It **does not enable or implement SystemUI hot reload**.
`META-INF/xposed/module.prop` retains `autoHotReload=false`; module APK upgrades
still require normal target-process restarts to load new code.

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
- No changes to other hooks, callback priorities, EGL, PassBlur, GPU resources,
  app settings, the SystemUI process lifecycle, or `ModuleMain` callbacks.

## Validation

Run `./gradlew testDebugUnitTest assembleDebug`. JVM fake-handle tests verify:
replacement only for an ACTIVE owner, absent method rejection, stable handle
count, no unhook gap, retaining old handle after a failed replacement, and
unhooking **only the latest** handle on owner shutdown.

Device acceptance (API 102 framework): recreate the SystemUI navigation bar,
re-enter HOME/RECENTS, and confirm the native gesture handle alpha still
follows the configured visibility while no duplicate interceptors accumulate.
Test enabling/disabling the gesture feature, multiple nav-bar attachments, and
a replacement failure. A successful CI build does not prove device behavior.

**Not provided:** replacing an APK's code in an existing process, module
hot-reload callbacks, hot-reloading renderer resources, or no-restart updates
to external app code. These require separate lifecycle and framework work.
