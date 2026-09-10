# Security Center Liquid Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add fail-closed Global Dock/All Apps LiquidDock Prismal glass to the validated HyperOS 4 Security Center `:ui` process with one root-bound zero-copy PassBlur producer.

**Architecture:** Make PassBlur binding domain-explicit, extract a root-bound PassBlur/OES/freshness backend from `LauncherGlassSession` without changing Launcher semantics, and place a Security Center-specific session/coordinator above it. Security Center remains the authority for native restoration: the validated HyperOS 4 build may restore MiGlass/MaterialToken or ordinary blur; future HyperOS 3 support is blur-only and must never emulate soft-light glass.

**Tech Stack:** Java 17, Kotlin/Compose settings UI, libxposed API 101, Android hidden SurfaceControl PassBlur APIs, SurfaceTexture/OES, EGL/GLES, Prismal, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-10-security-center-liquid-glass-design.md`

## Global Constraints

- v1 supports only package `com.miui.securitycenter`, process `com.miui.securitycenter:ui`, Security Center versionCode `40011320` (`13.2.0-260806.0.1.pad`).
- v1 owns only Global Dock/type 4 and its All Apps page. Game Toolbox/type 1, Video Toolbox/type 3, and Conversation Assistant/type 5 remain vendor-owned.
- HyperOS 3 vendor semantics are `BACKGROUND_BLUR` only. Never emulate, restore, or advertise HyperOS 4 soft-light glass on HyperOS 3.
- `VendorMaterialCapability` and LiquidDock Prismal/PassBlur capability are independent authorities.
- No ScreenCapture fallback, bitmap readback, CPU backdrop copy, fixed delay, page-owned producer, fuzzy hook discovery, recursive background clearing, or guessed MaterialToken/MiGlass parameters.
- Native PassBlur scale remains `1.0`; any local resolution reduction happens after authoritative OES normalization.
- Global Dock and All Apps share one sidebar Window/ViewRoot/root SurfaceControl and therefore one native PassBlur producer.
- Rebind/recreate success is not freshness. Custom output becomes visible only after a real OES frame has been rendered for the current scene generation.
- Runtime effective state is `Core.ENABLED && Glass.ENABLED && Glass.SECURITY_CENTER_GLASS`.
- Disable publishes the effective state as false before releasing visual ownership/session resources. Every queued callback checks the current live state and generation again before mutation.
- Unsupported build, non-type-4 panel, invalid root, reflection failure, producer failure, EGL failure, or stale callback leaves vendor material authoritative.
- Vendor/system private reflection is confined to hook/bridge boundaries. LiquidDock-owned code uses typed/package-private calls.
- Runtime ownership/freshness/lifecycle tests exercise production state/policy classes. Runtime source-text tests are forbidden.
- `MainHook` receives no Security Center state or lifecycle responsibility.

---

## Task 1: Exact process/build/type/material capability policy

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterProcessPolicy.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterHookSpec.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialCapability.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterProcessPolicyTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterHookSpecTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialCapabilityTest.java`

**Consumes:** no new production interfaces.

**Produces:**

```java
final class SecurityCenterProcessPolicy {
    static final String PACKAGE = "com.miui.securitycenter";
    static final String UI_PROCESS = "com.miui.securitycenter:ui";
    static boolean shouldInstall(String packageName, String processName);
}

enum SecurityCenterVendorGeneration {
    OS3_BLUR_ONLY,
    OS4_SOFT_LIGHT_CAPABLE
}

enum SecurityCenterVendorMaterialCapability {
    BACKGROUND_BLUR,
    SOFT_LIGHT_GLASS;
    static SecurityCenterVendorMaterialCapability resolve(
            SecurityCenterVendorGeneration generation,
            boolean nativeSoftLightActive);
}

final class SecurityCenterHookSpec {
    static SecurityCenterHookSpec forVersionCode(long versionCode);
    long versionCode();
    SecurityCenterVendorGeneration vendorGeneration();
    String serviceClass();                 // com.miui.gamebooster.service.DockWindowManagerService
    String turboLayoutClass();             // com.miui.gamebooster.windowmanager.newbox.TurboLayout
    String sidebarWrapperClass();          // com.miui.dock.sidebar.p
    String dockWindowTypeClass();          // ja.a
    String prepareDockMethod();            // M
    String type4PredicateMethod();         // f
    String dockLayoutGetter();             // getDockLayout
    String appsLayoutGetter();             // getAppsLayout
    String toggleAllAppsMethod();          // d0
    String finalBackgroundMethod();        // U
    String allAppsPresentField();          // f17941q
    String transformingField();            // f17943s
    String os4MaterialHelperClass();       // gq.g
    String os4MaterialResetMethod();       // l
}
```

### Steps

- [ ] **Write process-policy tests.** Assert only exact package + `:ui` returns true; package main process, Launcher, null and empty process names return false.

- [ ] **Run the red test.**

```bash
./gradlew testDebugUnitTest --tests '*SecurityCenterProcessPolicyTest' --stacktrace
```

Expected: compilation/test failure because `SecurityCenterProcessPolicy` is not implemented.

- [ ] **Implement the process policy exactly.**

```java
final class SecurityCenterProcessPolicy {
    static final String PACKAGE = "com.miui.securitycenter";
    static final String UI_PROCESS = "com.miui.securitycenter:ui";
    private SecurityCenterProcessPolicy() {}

    static boolean shouldInstall(String packageName, String processName) {
        return PACKAGE.equals(packageName) && UI_PROCESS.equals(processName);
    }
}
```

- [ ] **Write build/spec tests.** Assert `forVersionCode(40011320L)` returns the exact names listed in `Produces`; all other tested version codes return `null`. Assert the v1 generation is `OS4_SOFT_LIGHT_CAPABLE`.

- [ ] **Implement the immutable v1 HookSpec.** Keep every obfuscated name in this file; do not duplicate `M`, `d0`, `U`, `f17941q`, or `f17943s` in `SecurityCenterGlassHook`.

```java
static SecurityCenterHookSpec forVersionCode(long versionCode) {
    return versionCode == 40011320L ? OS4_40011320 : null;
}
```

- [ ] **Write material-capability tests.** Assert:

```text
OS3_BLUR_ONLY + false -> BACKGROUND_BLUR
OS3_BLUR_ONLY + true  -> BACKGROUND_BLUR
OS4_SOFT_LIGHT_CAPABLE + false -> BACKGROUND_BLUR
OS4_SOFT_LIGHT_CAPABLE + true  -> SOFT_LIGHT_GLASS
```

- [ ] **Implement capability resolution.** The OS3 branch ignores the soft-light flag and always returns `BACKGROUND_BLUR`.

- [ ] **Run all three focused tests.**

```bash
./gradlew testDebugUnitTest \
  --tests '*SecurityCenterProcessPolicyTest' \
  --tests '*SecurityCenterHookSpecTest' \
  --tests '*SecurityCenterVendorMaterialCapabilityTest' \
  --stacktrace
```

Expected: PASS.

- [ ] **Commit.**

```bash
git add src/main/java/com/hellovoid/liquiddock/SecurityCenterProcessPolicy.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterHookSpec.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialCapability.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterProcessPolicyTest.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterHookSpecTest.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialCapabilityTest.java
git commit -m "test: define security center compatibility policy"
```

---

## Task 2: Typed config + Security Center-local runtime state

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeTransitionPolicy.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeState.java`
- Create: `src/test/java/com/hellovoid/liquiddock/config/SecurityCenterGlassConfigTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeTransitionPolicyTest.java`
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Modify: `src/main/res/values/strings.xml`
- Modify: `src/main/res/values-zh-rCN/strings.xml`

**Consumes:** `ConfigSchema`, `ConfigReader`, `ConfigCodec`, `PresetManager`, `LiquidDockConfig` conventions.

**Produces:**

```java
ConfigSchema.Glass.SECURITY_CENTER_GLASS
// key=liquid_security_center_glass
// uiDefault=false, runtimeFallback=false, exportDefault=false, ALWAYS

// inside LiquidDockConfig.Glass
final boolean securityCenterEnabled;

final class SecurityCenterGlassRuntimeTransitionPolicy {
    static final class Snapshot {
        final boolean coreEnabled;
        final boolean glassEnabled;
        final boolean securityCenterEnabled;
        boolean effective();
    }
    static final class Transition {
        final boolean releaseAll;
    }
    static Transition plan(Snapshot before, Snapshot after);
}

final class SecurityCenterGlassRuntimeState {
    interface Owner { void releaseAll(); }
    static void initialize(
            android.content.SharedPreferences preferences,
            boolean coreEnabled,
            boolean glassEnabled,
            boolean securityCenterEnabled);
    static void setOwner(Owner owner);
    static boolean isEnabled();
}
```

### Steps

- [ ] **Write config red tests.** Verify schema defaults, `ConfigCodec` Boolean import/export round-trip, `PresetManager.defaultValues()` contains `liquid_security_center_glass=false`, and `LiquidDockConfig.Glass.securityCenterEnabled` reads the typed key.

- [ ] **Run config red tests.**

```bash
./gradlew testDebugUnitTest --tests '*SecurityCenterGlassConfigTest' --stacktrace
```

Expected: FAIL before production changes.

- [ ] **Add the schema key and typed config field.** Use the normal schema iteration; do not add Security Center branches to `ConfigCodec` or `PresetManager`.

```java
public static final ConfigKey<Boolean> SECURITY_CENTER_GLASS = bool(
        "liquid_security_center_glass",
        false, false, false,
        ConfigKey.ExportMode.ALWAYS);
```

- [ ] **Write runtime-policy red tests.** Effective state is exactly:

```java
return coreEnabled && glassEnabled && securityCenterEnabled;
```

Assert a true->false transition caused by any one of the three flags produces `releaseAll=true`; false->false and false->true produce `releaseAll=false`.

- [ ] **Implement runtime transition policy and state.** `SecurityCenterGlassRuntimeState` must update/publish its three booleans before dispatching `Owner.releaseAll()` on the main thread. Preference callbacks read only the three typed schema keys. It never calls `MainHook`.

- [ ] **Add the Liquid Glass-page switch.** Bind it to `ConfigSchema.Glass.SECURITY_CENTER_GLASS.name()`. Use these strings:

```xml
<string name="liquid_security_center_glass_enable">Security Center sidebar glass</string>
<string name="liquid_security_center_glass_enable_summary">Use LiquidDock glass for the supported Global Dock and All Apps sidebar; Security Center restores its native material or blur when LiquidDock releases ownership.</string>
```

```xml
<string name="liquid_security_center_glass_enable">安全中心侧边栏玻璃</string>
<string name="liquid_security_center_glass_enable_summary">为已支持的全局侧边栏与所有应用使用 LiquidDock 液态玻璃；LiquidDock 释放所有权后由安全中心自行恢复原生材质或背景模糊。</string>
```

Do not mention HyperOS 3 support in user-facing v1 text.

- [ ] **Run focused tests, then full unit tests.**

```bash
./gradlew testDebugUnitTest --tests '*SecurityCenterGlassConfigTest' \
  --tests '*SecurityCenterGlassRuntimeTransitionPolicyTest' --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Commit.**

```bash
git add src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java \
        src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeTransitionPolicy.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeState.java \
        src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt \
        src/main/res/values/strings.xml src/main/res/values-zh-rCN/strings.xml \
        src/test/java/com/hellovoid/liquiddock/config/SecurityCenterGlassConfigTest.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeTransitionPolicyTest.java
git commit -m "feat: add security center glass configuration"
```

---

## Task 3: Domain-explicit PassBlur binding

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/PassBlurDomain.java`
- Create: `src/main/java/com/hellovoid/liquiddock/PassBlurBindRequest.java`
- Create: `src/main/java/com/hellovoid/liquiddock/PassBlurBindPolicy.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurBridge.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java`
- Create: `src/test/java/com/hellovoid/liquiddock/PassBlurBindPolicyTest.java`

**Consumes:** existing `Miuix307PassBlurBridge.bind(View, Surface, float)` behavior and `PassBlurQualityPolicy.bridgeScale(...)`.

**Produces:**

```java
enum PassBlurDomain {
    LAUNCHER_WORKSPACE,
    DOCK,
    SECURITY_CENTER
}

final class PassBlurBindRequest {
    static PassBlurBindRequest launcherWorkspace(View host, float requestedScale);
    static PassBlurBindRequest dock(View host, float requestedScale);
    static PassBlurBindRequest securityCenter(View authoritativeRoot);
    View host();
    PassBlurDomain domain();
    float nativeScale();
    String[] extraExclusions();
}

final class PassBlurBindPolicy {
    static String[] exclusions(String rootSurfaceName, String[] extraExclusions);
    static boolean requiresUnlockGate(PassBlurDomain domain);
}

// replacement bridge API
static Miuix307PassBlurBridge.Binding bind(
        PassBlurBindRequest request,
        android.view.Surface producerSurface);
```

### Steps

- [ ] **Write red policy tests.** Assert `securityCenter(root).nativeScale()==1.0f`; only `LAUNCHER_WORKSPACE` requires unlock gate; exclusion composition contains the actual non-empty root SurfaceControl name exactly once plus `NavigationBar`, `StatusBar`, `GestureStub`; extras are deduplicated.

- [ ] **Run red test.**

```bash
./gradlew testDebugUnitTest --tests '*PassBlurBindPolicyTest' --stacktrace
```

Expected: FAIL before new types exist.

- [ ] **Implement domain/request/policy.** Keep `DockAssistantView` only as an extra compatibility exclusion in the existing Dock request if current behavior needs it. Security Center correctness uses the runtime root SurfaceControl name, not that literal.

- [ ] **Replace bridge domain inference.** Delete bridge-level `LauncherGlassSceneController.findRoot(materialHost)` inference. Read `request.domain()` and apply unlock gating only when `PassBlurBindPolicy.requiresUnlockGate(...)` is true. Preserve existing SurfaceControl identity, surface-sequence/layer diagnostics, `SetPassBlurSurface`, update-texture, exclusions, pause/resume and unbind semantics.

- [ ] **Update both current call sites explicitly.** Confirm first with:

```bash
rg 'Miuix307PassBlurBridge\.bind' src/main/java
```

On the branch used by this plan the call sites must be migrated as follows:

```java
// LauncherGlassSession
Miuix307PassBlurBridge.bind(
        PassBlurBindRequest.launcherWorkspace(root, 1.0f), producerSurface);

// Miuix307PassBlurTextureView
Miuix307PassBlurBridge.bind(
        PassBlurBindRequest.dock(materialHost, requestedScale), producerSurface);
```

If `rg` reveals an additional pre-existing call site, migrate it with an explicit domain before compiling; do not restore hierarchy inference.

- [ ] **Run policy + full regression tests.**

```bash
./gradlew testDebugUnitTest --tests '*PassBlurBindPolicyTest' --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Commit.**

```bash
git add src/main/java/com/hellovoid/liquiddock/PassBlurDomain.java \
        src/main/java/com/hellovoid/liquiddock/PassBlurBindRequest.java \
        src/main/java/com/hellovoid/liquiddock/PassBlurBindPolicy.java \
        src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurBridge.java \
        src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java \
        src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java \
        src/test/java/com/hellovoid/liquiddock/PassBlurBindPolicyTest.java
git commit -m "refactor: make pass blur binding domain explicit"
```

---

## Task 4: Extract the root PassBlur/OES/freshness backend

**Risk gate:** this is the highest-risk task. Security Center vendor hooks are blocked until Launcher regression tests and debug assembly pass after this extraction.

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/RootPassBlurBackendState.java`
- Create: `src/main/java/com/hellovoid/liquiddock/RootPassBlurFrame.java`
- Create: `src/main/java/com/hellovoid/liquiddock/RootPassBlurBackend.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Reuse unchanged where possible: `src/main/java/com/hellovoid/liquiddock/ZeroCopyProducerRecoveryState.java`
- Create: `src/test/java/com/hellovoid/liquiddock/RootPassBlurBackendStateTest.java`

**Consumes:** `PassBlurBindRequest`, `Miuix307PassBlurBridge`, `ZeroCopyProducerRecoveryState`, `PassBlurSourceFrameGate`, `PassBlurRenderDomain` and existing Launcher session source cadence.

**Produces:**

```java
final class RootPassBlurFrame {
    final long generation;
    final int normalizedTextureId; // valid only on backend render thread during callback
    final int logicalWidth;
    final int logicalHeight;
    final int physicalWidth;
    final int physicalHeight;
    final int rotation;
    final LauncherGlassSurfaceContentRect contentRect;
}

final class RootPassBlurBackend {
    interface Consumer {
        void onFreshFrame(RootPassBlurBackend backend, RootPassBlurFrame frame);
        void onTerminalFailure(long generation, Throwable error);
    }

    RootPassBlurBackend(
            View authoritativeRoot,
            PassBlurBindRequest bindRequest,
            int physicalScalePercent,
            int renderFps,
            Consumer consumer,
            String renderThreadName);

    void requestFresh(long generation);
    void setUpdatesEnabled(boolean enabled, String reason);
    void requestRebind(String reason);
    void setQuality(int physicalScalePercent, int renderFps);
    boolean hasFreshFrame(long generation);
    void postToRenderThread(Runnable runnable);
    void shutdown();
}
```

`RootPassBlurBackend` owns the EGL/OES source and normalized backdrop texture; `Consumer.onFreshFrame` runs on that same render thread while its EGL context is current. Domain consumers may render Prismal/output surfaces during that callback but may not retain/use `normalizedTextureId` from another thread.

`RootPassBlurBackend` does **not** own Launcher node registries, workspace scroll projection, wallpaper-generation authority, Recents state, Workstation policy, folder/icon semantics, `TurboLayout`, or Security Center class names.

### Steps

- [ ] **Write backend-state red tests.** The production state must model requested generation, consumed generation and producer-recovery state. Test exact transitions:

```text
requestFresh(2) -> generation 1 no longer fresh
bindSucceeded(2) -> generation 2 still not fresh
sourceFrameConsumed(2) -> generation 2 fresh
requestFresh(3), then sourceFrameConsumed(2) -> generation 3 not fresh
requestRebind -> current freshness cleared
qualityOnlyChange -> no native endpoint recreation decision
```

- [ ] **Run red state test.**

```bash
./gradlew testDebugUnitTest --tests '*RootPassBlurBackendStateTest' --stacktrace
```

Expected: FAIL before the state/backend exists.

- [ ] **Implement `RootPassBlurBackendState`.** Compose `ZeroCopyProducerRecoveryState`; do not create a second recovery state machine with divergent meanings. Bind success ends rebind-pending but never calls the fresh-frame transition.

- [ ] **Extract source ownership from `LauncherGlassSession`.** Move only these responsibilities into `RootPassBlurBackend`: input `SurfaceTexture`/producer `Surface`, OES texture, root endpoint validation/binding, frame-available drain, normalization FBO, source-frame generation/freshness, producer recovery, render thread/EGL source lifecycle, local physical source resolution and source render FPS gate.

- [ ] **Keep Launcher policy in `LauncherGlassSession`.** The following remain Launcher-owned: node/static-node registries, output-node geometry, workspace scroll projection, wallpaper requested/authoritative generation, Recents coverage, Workstation recovery decisions, rotation-settle policy, Launcher highlight profiles and Launcher-specific scene controller calls.

- [ ] **Adapt `LauncherGlassSession` as `RootPassBlurBackend.Consumer`.** Its `onFreshFrame(...)` performs the existing Launcher Prismal/output work on the backend render thread. It must continue to treat wallpaper authority and scene generation as stricter gates above backend source freshness.

- [ ] **Do not refactor `Miuix307PassBlurTextureView` in this task.** It remains the stable Dock large-surface implementation and only received its explicit bind domain in Task 3.

- [ ] **Preserve cadence/quality invariants.** There is no Choreographer/timer source pump; capped frames still call `SurfaceTexture.updateTexImage()` to drain; current fresh generation bypasses expensive render cap; native PassBlur scale remains 1.0; changing local physical FBO resolution does not recreate the native endpoint.

- [ ] **Run focused and complete regression verification.**

```bash
./gradlew testDebugUnitTest --tests '*RootPassBlurBackendStateTest' --stacktrace
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected: all PASS / build exit 0. Any Launcher test failure is fixed inside this task before proceeding.

- [ ] **Commit.**

```bash
git add src/main/java/com/hellovoid/liquiddock/RootPassBlurBackendState.java \
        src/main/java/com/hellovoid/liquiddock/RootPassBlurFrame.java \
        src/main/java/com/hellovoid/liquiddock/RootPassBlurBackend.java \
        src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java \
        src/test/java/com/hellovoid/liquiddock/RootPassBlurBackendStateTest.java
git commit -m "refactor: extract root pass blur backend"
```

---

## Task 5: Security Center scene + ownership state machines

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassSceneState.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipState.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassSceneStateTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipStateTest.java`

**Consumes:** generation/freshness semantics from Task 4 and runtime-effective state from Task 2.

**Produces:**

```java
final class SecurityCenterGlassSceneState {
    enum Scene { DETACHED, PREPARING_DOCK, DOCK, TRANSITIONING,
                 PREPARING_ALL_APPS, ALL_APPS }
    enum Target { DOCK, ALL_APPS }

    static final class Decision {
        final boolean ensureSession;
        final boolean invalidateGeneration;
        final boolean requestFresh;
        final boolean hideCustom;
        final boolean releaseCustomOwnership;
        final boolean claimCustomOwnership;
        final boolean revealCustom;
        final boolean shutdownSession;
        final long generation;
    }

    Decision onRootAttached();
    Decision onTransitionStarted();
    Decision onGeometrySettled(Target target);
    Decision onFreshFrameRendered(long generation);
    Decision onRuntimeDisabled();
    Decision onTerminalFailure();
    Decision onRootDetached();
    Scene scene();
    long generation();
}

final class SecurityCenterMaterialOwnershipState {
    enum Owner { VENDOR, CUSTOM }
    boolean canSuppressVendor(long renderedGeneration, long currentGeneration);
    void onCustomClaimed();
    void releaseToVendor();
    Owner owner();
}
```

### Steps

- [ ] **Write scene red tests.** Verify initial attach -> `PREPARING_DOCK`; current fresh render -> `DOCK`; Dock -> transition -> All Apps geometry settled -> `PREPARING_ALL_APPS` -> fresh current generation -> `ALL_APPS`; reverse transition behaves symmetrically. Assert transition increments generation but never emits producer recreation.

- [ ] **Write stale/failure tests.** After generation advances, a fresh callback for the old generation cannot claim/reveal. Runtime disable, failure and detach must hide/release before session shutdown is acted on.

- [ ] **Write ownership red tests.** Vendor is initial owner. `canSuppressVendor` is true only when rendered generation equals current generation. Release is idempotent and immediately returns owner to `VENDOR`.

- [ ] **Run red tests.**

```bash
./gradlew testDebugUnitTest \
  --tests '*SecurityCenterGlassSceneStateTest' \
  --tests '*SecurityCenterMaterialOwnershipStateTest' \
  --stacktrace
```

Expected: FAIL before implementation.

- [ ] **Implement both Android-free production state classes.** They return decisions only; they do not hold Views or invoke callbacks.

- [ ] **Run focused tests + full unit suite.**

```bash
./gradlew testDebugUnitTest \
  --tests '*SecurityCenterGlassSceneStateTest' \
  --tests '*SecurityCenterMaterialOwnershipStateTest' \
  --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Commit.**

```bash
git add src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassSceneState.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipState.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassSceneStateTest.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipStateTest.java
git commit -m "feat: add security center glass scene state"
```

---

## Task 6: Security Center output/session/coordinator

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassGeometry.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassOutputView.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassSession.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassGeometryTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinatorPolicyTest.java`

**Consumes:** `RootPassBlurBackend`, `SecurityCenterGlassSceneState`, `SecurityCenterMaterialOwnershipState`, `LiquidDockConfig.Glass`, `Miuix307PrismalMaterial`, `Miuix307PrismalAdapter`.

**Produces:**

```java
final class SecurityCenterGlassCoordinator
        implements SecurityCenterGlassRuntimeState.Owner {
    void bindGlobalDock(View turboLayout, View dockLayout, View appsLayout);
    void onAllAppsToggleStarted(View turboLayout);
    void onAllAppsToggleSettled(View turboLayout, boolean allAppsPresent);
    boolean shouldSuppressVendorFinalBackground(Object turboLayout);
    void onVendorOwnershipReleased(Object turboLayout);
    @Override public void releaseAll();
}
```

`SecurityCenterGlassSession` owns exactly one `RootPassBlurBackend` and one large-surface output renderer per authoritative root.

### Steps

- [ ] **Write pure geometry tests.** Given root/target integer bounds, verify target bounds are converted to root-local logical coordinates without changing backdrop source generation. Include left/right sidebar placement and full-size All Apps target cases.

- [ ] **Implement `SecurityCenterGlassGeometry` as immutable snapshots/math.** Android View reads happen in coordinator; pure geometry calculations remain testable without Xposed.

- [ ] **Implement output placement exactly.** When `turboLayout.getParent()` is a `ViewGroup`, create one `SecurityCenterGlassOutputView` and insert it immediately before `turboLayout` in that same parent. Use `MATCH_PARENT x MATCH_PARENT`; draw transparent outside the current target geometry. Do not add it inside TurboLayout's child/Z-order system.

- [ ] **Configure output View ownership.** It is non-clickable, non-focusable, `IMPORTANT_FOR_ACCESSIBILITY_NO`, and hidden until current-generation authorization. It does not create a native producer.

- [ ] **Implement `SecurityCenterGlassSession` using `RootPassBlurBackend`.** Use:

```java
Miuix307PrismalMaterial.Params optical =
        Miuix307PrismalMaterial.fromConfig(glassConfig, density);
PrismalParams params = Miuix307PrismalAdapter.toPortable(optical);
PrismalHighlightProfile highlights = glassConfig.largeSurfaceHighlightProfile;
```

Render one large rounded panel for the active geometry; do not instantiate `DockGlassCompositor`, Launcher node registries, or Workstation policy.

- [ ] **Write coordinator-policy red tests around production state objects.** One stable root creates one session; Dock -> All Apps does not request session replacement; root identity replacement shuts down old session before new ownership; stale callback cannot reveal after `releaseAll()`.

- [ ] **Implement coordinator lifecycle.** Authoritative root is `turboLayout.getRootView()` after attach. A detach posts one main-loop recheck and shuts down only if that same root is still detached. This is attachment authority validation, not a time delay.

- [ ] **Recheck live state before every mutation.** Pre-draw, render-frame, main-handler and detach callbacks verify `SecurityCenterGlassRuntimeState.isEnabled()`, coordinator/session identity, root identity, scene generation and shutdown state.

- [ ] **Run tests and debug build.**

```bash
./gradlew testDebugUnitTest \
  --tests '*SecurityCenterGlassGeometryTest' \
  --tests '*SecurityCenterGlassCoordinatorPolicyTest' \
  --stacktrace
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected: PASS / exit 0.

- [ ] **Commit.**

```bash
git add src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassGeometry.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassOutputView.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassSession.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassGeometryTest.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinatorPolicyTest.java
git commit -m "feat: add security center glass coordinator"
```

---

## Task 7: Exact vendor material bridge + Security Center hooks

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialBridge.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java`
- Extend: `src/test/java/com/hellovoid/liquiddock/SecurityCenterHookSpecTest.java`
- Extend: `src/test/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipStateTest.java`

**Consumes:** exact v1 reverse-engineered semantics:
- `DockWindowManagerService.onCreate()` exists and supplies a real Service/Context;
- `TurboLayout.M(com.miui.dock.sidebar.p, ja.a)` is `prepareNewDockLayout` and receives the exact current dock-window type;
- `ja.a.f()` is `dockType == 4` / Global Dock;
- `TurboLayout.d0()` is `toggleAllAppsLayout`;
- `f17943s` is the vendor `isTransforming` state;
- `f17941q` is vendor `allAppsPresent` state;
- `TurboLayout.U()` is `setFinalNewDockBackground` and internally chooses the HyperOS 4 MiGlass path or ordinary blur path;
- `gq.g.l(View)` resets the HyperOS 4 view material.

**Produces:**

```java
final class SecurityCenterVendorMaterialBridge {
    SecurityCenterVendorMaterialBridge(
            ClassLoader classLoader,
            SecurityCenterHookSpec spec);
    void claimCustom(Object turboLayout, View dockLayout);
    void restoreVendor(Object turboLayout);
}

final class SecurityCenterGlassHook {
    static void install(ClassLoader classLoader, LiquidDockConfig initialConfig);
}
```

### Steps

- [ ] **Implement the bootstrap hook on exact `DockWindowManagerService.onCreate()`.** The hook calls the original first, casts `chain.getThisObject()` to `Context`, reads `getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode()`, and resolves `SecurityCenterHookSpec.forVersionCode(versionCode)`. Unknown build logs once and installs no version-specific scene/material hooks.

```java
HookUtil.hookMethod(serviceClass, "onCreate", new Class<?>[0], chain -> {
    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
    Context service = (Context) chain.getThisObject();
    installValidatedHooksOnce(service, classLoader);
    return result;
});
```

- [ ] **Install exact type-4 prepare hook after successful version gating.** Resolve parameter classes from target ClassLoader and hook:

```java
TurboLayout.M(com.miui.dock.sidebar.p, ja.a)
```

Call original first. Then invoke `HookUtil.requireInvoke(typeArg, spec.type4PredicateMethod())`; continue only when the result is `Boolean.TRUE`. Resolve `getDockLayout()` and `getAppsLayout()` through exact spec methods and register attach/pre-draw handling with `SecurityCenterGlassCoordinator`. Types 1/3/5/default are returned untouched.

- [ ] **Use vendor transition authority, not a fixed delay.** Hook `TurboLayout.d0()` around original. Before proceeding, read exact `transformingField`; if already true, do not emit a new LiquidDock transition because Security Center itself blocks the toggle. If false, call `coordinator.onAllAppsToggleStarted(turbo)` and proceed.

- [ ] **After a real toggle starts, install one pre-draw listener that remains until the vendor reports `transformingField == false`.** On each pre-draw it rechecks live/session/root state. When transforming becomes false, remove the listener, read exact `allAppsPresentField`, then call:

```java
coordinator.onAllAppsToggleSettled(turbo, allAppsPresent);
```

This vendor Boolean is the scene-settle authority. Do not infer completion from animation duration, child alpha, or a fixed frame count.

- [ ] **Implement vendor material claim.** After a current-generation Prismal frame is rendered but before revealing custom output:

```java
Class<?> materialHelper = Class.forName(
        spec.os4MaterialHelperClass(), false, classLoader);
HookUtil.requireInvokeStatic(
        materialHelper, spec.os4MaterialResetMethod(), dockLayout);
MiBlurBridge.clearPassWindowBlur(dockLayout);
```

Leave TurboLayout/root `setPassWindowBlurEnabled` state untouched in v1 so the root backdrop source is not speculatively disabled. Do not clear unrelated child backgrounds.

- [ ] **Gate the public vendor final-background semantic method `TurboLayout.U()`.** This is the only v1 material recompute suppression hook:

```java
HookUtil.hookMethod(turboClass, spec.finalBackgroundMethod(), new Class<?>[0], chain -> {
    Object turbo = chain.getThisObject();
    if (coordinator.shouldSuppressVendorFinalBackground(turbo)) {
        return null; // U() is void; CUSTOM owns the current background
    }
    return chain.proceed(chain.getArgs().toArray(new Object[0]));
});
```

Do not globally hook `gq.g`, `gq.m`, `MaterialToken`, or `com.miui.common.utils.m` to suppress other Security Center surfaces.

- [ ] **Restore through vendor `U()`, not private `S()`.** First publish/release ownership to `VENDOR` so the `U()` gate passes through, then invoke:

```java
HookUtil.requireInvoke(turboLayout, spec.finalBackgroundMethod());
```

Security Center then executes its complete final-background logic, including MiGlass when active and ordinary blur when that is the current native path. Never replay guessed token/blur/shadow values.

- [ ] **Handle claim/restore failure fail-closed.** Claim failure keeps custom output hidden and restores vendor authority. Restore invocation failure keeps custom hidden and logs the failure; it does not synthesize native material.

- [ ] **Run focused tests + full verification.**

```bash
./gradlew testDebugUnitTest \
  --tests '*SecurityCenterHookSpecTest' \
  --tests '*SecurityCenterMaterialOwnershipStateTest' \
  --stacktrace
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected: PASS / exit 0.

- [ ] **Commit.**

```bash
git add src/main/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialBridge.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassHook.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterHookSpecTest.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipStateTest.java
git commit -m "feat: hook security center sidebar glass"
```

---

## Task 8: Module composition + Xposed scope

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Modify: `src/main/resources/META-INF/xposed/scope.list`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterScopeContractTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/RuntimeBehaviorTestPolicyContractTest.java` only to add `SecurityCenterScopeContractTest.java` to the audited static-source allowlist if that gate sees its scope-file read.

**Consumes:** `SecurityCenterProcessPolicy`, `SecurityCenterGlassRuntimeState`, `SecurityCenterGlassHook`.

**Produces:** Security Center package routing in the libxposed composition root; no `MainHook` change.

### Steps

- [ ] **Write static scope red test.** Read only `src/main/resources/META-INF/xposed/scope.list` and assert the intended entries are exactly:

```text
com.miui.home
com.android.systemui
com.miui.securitycenter
```

This is an allowed static Xposed-scope contract; it must not inspect Java method bodies or runtime ordering.

- [ ] **Add Security Center scope entry.** Preserve existing Launcher/SystemUI lines and add `com.miui.securitycenter`.

- [ ] **Store process identity in `ModuleMain`.** Add one instance field assigned in `onModuleLoaded`:

```java
private String loadedProcessName;

@Override
public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
    Api101Bridge.init(this);
    loadedProcessName = param.getProcessName();
    ...
}
```

- [ ] **Add a Security Center branch before the Launcher-only return.** Do not run `LegacyConfigMigration` or `ConfigMigration` in this process.

```java
if (SecurityCenterProcessPolicy.PACKAGE.equals(packageName)) {
    if (!SecurityCenterProcessPolicy.shouldInstall(packageName, loadedProcessName)) return;
    ConfigReader reader = ConfigReader.load();
    LiquidDockConfig config = LiquidDockConfig.from(reader);
    SecurityCenterGlassRuntimeState.initialize(
            Api101Bridge.remotePreferences("config"),
            config.enabled,
            config.glass.enabled,
            config.glass.securityCenterEnabled);
    SecurityCenterGlassHook.install(param.getClassLoader(), config);
    return;
}
```

- [ ] **Keep existing composition unchanged.** SystemUI remains timing-source only; Launcher still performs migrations and installs `MainHook`; Security Center never calls `MainHook.install()`.

- [ ] **Run scope + full tests/build.**

```bash
./gradlew testDebugUnitTest --tests '*SecurityCenterScopeContractTest' --stacktrace
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected: PASS / exit 0.

- [ ] **Commit.**

```bash
git add src/main/java/com/hellovoid/liquiddock/ModuleMain.java \
        src/main/resources/META-INF/xposed/scope.list \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterScopeContractTest.java \
        src/test/java/com/hellovoid/liquiddock/RuntimeBehaviorTestPolicyContractTest.java
git commit -m "feat: scope liquid glass to security center ui"
```

---

## Task 9: Documentation, release verification, and device acceptance

**Files:**
- Modify: `ARCHITECTURE.md`
- Modify: `HOOKS.md`
- Modify: `FEATURES.md`
- Modify: `CHANGELOG.md` under the repository's existing current-development section only.
- Inspect: `src/main/keepRules/liquiddock.keep`; change it only if release-R8 evidence proves a required module-owned reflected entry needs a keep rule.

**Consumes:** completed Tasks 1-8.

**Produces:** documented support boundary plus final automated/device evidence.

### Steps

- [ ] **Update architecture docs.** Document a second injected domain `com.miui.securitycenter:ui`, v1 exact build `40011320`, type-4 Global Dock + All Apps only, one root/one producer, current-generation fresh-frame reveal, and vendor `U()` restoration authority.

- [ ] **Document platform semantics accurately.** HyperOS 4 may restore MiGlass/MaterialToken or ordinary blur. HyperOS 3 has no soft-light-glass vendor semantics; future HyperOS 3 Security Center specs are blur-only.

- [ ] **Document exact Hook boundaries.** Include `DockWindowManagerService.onCreate`, `TurboLayout.M(p, ja.a)`, `ja.a.f()`, `TurboLayout.d0()`, vendor `f17943s`/`f17941q` settle authority, `TurboLayout.U()` material gate/restore, and unsupported-build fail-closed behavior.

- [ ] **Run complete automated verification from the feature branch/worktree.**

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
./gradlew assembleRelease --no-daemon --stacktrace
```

Expected: all commands exit 0. If release tooling is unavailable because Android SDK configuration is missing, report that exact environmental failure and do not claim release verification.

- [ ] **Audit the diff for forbidden architecture regressions.**

```bash
git diff main...HEAD -- src/main src/test ARCHITECTURE.md HOOKS.md FEATURES.md CHANGELOG.md
rg 'ScreenCapture|Bitmap\.createBitmap|PixelCopy|postDelayed\(' \
   src/main/java/com/hellovoid/liquiddock/SecurityCenter* \
   src/main/java/com/hellovoid/liquiddock/RootPassBlur* || true
```

The Security Center/root-backend feature must contain no ScreenCapture/bitmap/PixelCopy path and no `postDelayed` timing workaround.

- [ ] **Commit docs.**

```bash
git add ARCHITECTURE.md HOOKS.md FEATURES.md CHANGELOG.md src/main/keepRules/liquiddock.keep
git commit -m "docs: document security center liquid glass"
```

### Real-device acceptance matrix

Use dedicated diagnostics, without logging user content:

```bash
adb logcat -c
adb logcat | grep -E '\[DC\]\[(SecurityCenterGlass|RootPassBlur|PassBlur)\]'
```

- [ ] HOME behind sidebar -> Global Dock: live HOME backdrop, no self-feedback, no transparent first frame.
- [ ] Normal app behind sidebar -> Global Dock: live app backdrop, not Launcher wallpaper.
- [ ] Video/dynamic app behind sidebar: source-driven updates remain live.
- [ ] Global Dock -> All Apps -> Global Dock for at least 10 round trips: session id/native endpoint stay stable unless ViewRoot/SurfaceControl authority actually changes.
- [ ] During each toggle, vendor owns transition until `f17943s == false`; new custom scene reveals only after a current-generation OES-backed Prismal render.
- [ ] Dismiss -> reopen: no stale previous backdrop before fresh frame.
- [ ] Portrait <-> landscape: old orientation remains hidden until authoritative fresh content.
- [ ] Runtime Security Center switch ON -> OFF: effective state publishes false, custom layer releases, vendor `U()` restores current OS4 native material/blur.
- [ ] Runtime switch OFF -> ON while global glass is enabled: no stale callback from previous ownership epoch can reclaim the old session.
- [ ] Global Liquid Glass switch OFF: Security Center custom output releases even if its component key remains true.
- [ ] Kill/restart `com.miui.securitycenter:ui`: old root/session disappears; new process creates a clean session.
- [ ] Type 1/3/5 toolbox surfaces remain unchanged in v1.
- [ ] Unsupported Security Center build, when available for testing: diagnostic only; no custom producer/material mutation.
- [ ] Launcher regression: HOME/APP, Recents return, wallpaper freshness, rotation, Workstation recovery, Dock, icon/widget/folder glass retain pre-feature behavior.

### Evidence-driven correction rule

If device validation fails, correct only from observed authority:

```text
stock layer visible -> identify exact owning View/material call before changing suppression
producer stops -> compare root/ViewRoot/SurfaceControl name/layer/surface sequence first
stable endpoint but no frames -> use producer recovery + fresh-frame gate
geometry wrong -> fix root/target coordinate authority, not source generation
```

Do not respond with recursive background clearing, disabling TurboLayout pass-window permission without evidence, revealing the last stale texture, or adding fixed delays. A correction that changes the approved ownership architecture requires a small design-spec amendment before code changes.

- [ ] **After any device-driven correction, rerun:**

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
./gradlew assembleRelease --no-daemon --stacktrace
```

Then rerun the affected device rows plus one complete Dock -> All Apps -> Dock loop and one Launcher regression loop.

---

## Review Gates

1. Task 1 proves exact process/build/type/capability semantics before runtime code exists.
2. Task 2 proves typed config and three-level effective gating before hooks read live state.
3. Task 3 removes implicit Launcher-domain inference from the PassBlur bridge.
4. Task 4 must pass the full Launcher unit/debug gate before Security Center depends on the extracted backend.
5. Tasks 5-6 establish production state/geometry/session ownership before vendor material mutation.
6. Task 7 uses exact Security Center semantic methods (`M`, `d0`, `U`) and type-4 predicate `f()`; it does not hook material internals globally.
7. Task 8 changes only composition/scope, not `MainHook` ownership.
8. Task 9 requires automated evidence plus real-device evidence before v1 support is declared.

## Definition of Done

The feature is complete only when all of these are evidenced:

- exact `com.miui.securitycenter:ui` + versionCode `40011320` + type-4 gating;
- one Global Dock/All Apps root session and one native producer across page transitions;
- custom material is never shown before a current-generation real OES-backed Prismal frame;
- vendor `TurboLayout.U()` remains the restoration authority and is suppressed only while CUSTOM owns that exact TurboLayout;
- unsupported/failing paths leave or return to vendor authority;
- `Core.ENABLED`, `Glass.ENABLED`, and Security Center component state all participate in live effective gating;
- HyperOS 3 remains blur-only in capability policy and is not advertised as v1 supported;
- no ScreenCapture/bitmap/fixed-delay fallback exists;
- no Security Center lifecycle state enters `MainHook`;
- runtime tests use production state/policy classes rather than source-text assertions;
- existing Launcher zero-copy/freshness/recovery behavior passes regression verification;
- debug/release verification is evidenced;
- the real-device matrix passes on the declared Security Center build.
