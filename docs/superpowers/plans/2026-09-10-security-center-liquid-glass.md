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
- `SecurityCenterVendorMaterialCapability` and LiquidDock Prismal/PassBlur capability are independent authorities.
- No ScreenCapture fallback, bitmap readback, CPU backdrop copy, fixed delay, page-owned producer, fuzzy hook discovery, recursive background clearing, or guessed MaterialToken/MiGlass parameters.
- Native PassBlur scale remains `1.0`; local resolution reduction happens only after authoritative OES normalization.
- Global Dock and All Apps share one sidebar Window/ViewRoot/root SurfaceControl and therefore one native PassBlur producer.
- Rebind/recreate success is not freshness. Custom output becomes visible only after a real OES frame is rendered for the current scene generation.
- Runtime effective state is `Core.ENABLED && Glass.ENABLED && Glass.SECURITY_CENTER_GLASS`.
- Disable publishes effective state `false` before releasing visual ownership/session resources. Every queued callback rechecks live state, root/session identity and generation before mutation.
- Unsupported build, non-type-4 panel, invalid root, reflection failure, producer failure, EGL failure, or stale callback leaves vendor material authoritative.
- Vendor/system private reflection is confined to hook/bridge boundaries. LiquidDock-owned code uses typed/package-private APIs.
- Runtime ownership/freshness/lifecycle tests exercise production state/policy classes. Runtime source-text tests are forbidden.
- `MainHook` receives no Security Center state or lifecycle responsibility.

---

## Task 1: Exact process/build/type/material policy

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterProcessPolicy.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterHookSpec.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterVendorGeneration.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialCapability.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterProcessPolicyTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterHookSpecTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialCapabilityTest.java`

**Consumes:** existing project package/process conventions only.

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
    static final String BOOTSTRAP_SERVICE_CLASS =
            "com.miui.gamebooster.service.DockWindowManagerService";
    static SecurityCenterHookSpec forVersionCode(long versionCode);

    long versionCode();
    SecurityCenterVendorGeneration vendorGeneration();
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

- [ ] **Step 1: Write failing process-policy tests.** Assert exact package + `:ui` is true; package main process, another suffix, Launcher, null and empty values are false.

- [ ] **Step 2: Run red test.**

```bash
./gradlew testDebugUnitTest --tests '*SecurityCenterProcessPolicyTest' --stacktrace
```

Expected: FAIL because the production class does not yet exist.

- [ ] **Step 3: Implement process policy.**

```java
static boolean shouldInstall(String packageName, String processName) {
    return PACKAGE.equals(packageName) && UI_PROCESS.equals(processName);
}
```

- [ ] **Step 4: Write HookSpec red tests.** Assert only `40011320L` resolves. Verify every exact name listed in `Produces`; assert generation is `OS4_SOFT_LIGHT_CAPABLE`.

- [ ] **Step 5: Implement immutable HookSpec.** Keep all obfuscated names in this class and nowhere else in LiquidDock production code.

```java
static SecurityCenterHookSpec forVersionCode(long versionCode) {
    return versionCode == 40011320L ? OS4_40011320 : null;
}
```

- [ ] **Step 6: Write material-capability red tests.** Verify:

```text
OS3_BLUR_ONLY + false -> BACKGROUND_BLUR
OS3_BLUR_ONLY + true  -> BACKGROUND_BLUR
OS4_SOFT_LIGHT_CAPABLE + false -> BACKGROUND_BLUR
OS4_SOFT_LIGHT_CAPABLE + true  -> SOFT_LIGHT_GLASS
```

- [ ] **Step 7: Implement material-capability resolver.** OS3 always returns `BACKGROUND_BLUR` regardless of the Boolean input.

- [ ] **Step 8: Run focused tests.**

```bash
./gradlew testDebugUnitTest \
  --tests '*SecurityCenterProcessPolicyTest' \
  --tests '*SecurityCenterHookSpecTest' \
  --tests '*SecurityCenterVendorMaterialCapabilityTest' \
  --stacktrace
```

Expected: PASS.

- [ ] **Step 9: Commit.**

```bash
git add src/main/java/com/hellovoid/liquiddock/SecurityCenterProcessPolicy.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterHookSpec.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterVendorGeneration.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialCapability.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterProcessPolicyTest.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterHookSpecTest.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialCapabilityTest.java
git commit -m "test: define security center compatibility policy"
```

---

## Task 2: Typed configuration and process-local live state

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeTransitionPolicy.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeState.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassConfigTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeTransitionPolicyTest.java`
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Modify: `src/main/res/values/strings.xml`
- Modify: `src/main/res/values-zh-rCN/strings.xml`

**Consumes:** `ConfigSchema`, `ConfigReader`, `ConfigCodec`, `PresetManager`, `LiquidDockConfig`.

**Produces:**

```java
ConfigSchema.Glass.SECURITY_CENTER_GLASS
// "liquid_security_center_glass", false, false, false, ALWAYS

// LiquidDockConfig.Glass
final boolean securityCenterEnabled;

final class SecurityCenterGlassRuntimeTransitionPolicy {
    static final class Snapshot {
        final boolean coreEnabled;
        final boolean glassEnabled;
        final boolean securityCenterEnabled;
        boolean effective();
    }
    static final class Transition { final boolean releaseAll; }
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

- [ ] **Step 1: Write config red tests.** Use `new ConfigReader(Map<String,Object>)` as existing tests do. Verify schema defaults, `ConfigCodec` Boolean round-trip, default preset value `false`, and `LiquidDockConfig.from(reader).glass.securityCenterEnabled`.

- [ ] **Step 2: Run config red test.**

```bash
./gradlew testDebugUnitTest --tests '*SecurityCenterGlassConfigTest' --stacktrace
```

Expected: FAIL before production changes.

- [ ] **Step 3: Add schema key and typed snapshot field.**

```java
public static final ConfigKey<Boolean> SECURITY_CENTER_GLASS = bool(
        "liquid_security_center_glass", false, false, false,
        ConfigKey.ExportMode.ALWAYS);
```

Do not add special branches to `ConfigCodec` or `PresetManager`; schema iteration remains authoritative.

- [ ] **Step 4: Write runtime-policy red tests.** Assert effective state is exactly:

```java
return coreEnabled && glassEnabled && securityCenterEnabled;
```

Any true->false effective transition yields `releaseAll=true`; false->false and false->true yield false.

- [ ] **Step 5: Implement runtime state.** Update all three live flags before dispatching `Owner.releaseAll()` on the main thread. Preference callbacks read only `ConfigSchema.Core.ENABLED`, `ConfigSchema.Glass.ENABLED`, and `ConfigSchema.Glass.SECURITY_CENTER_GLASS`. Never call `MainHook`.

- [ ] **Step 6: Add settings switch under the existing Liquid Glass page.** Use:

```xml
<string name="liquid_security_center_glass_enable">Security Center sidebar glass</string>
<string name="liquid_security_center_glass_enable_summary">Use LiquidDock glass for the supported Global Dock and All Apps sidebar; Security Center restores its native material or blur when LiquidDock releases ownership.</string>
```

```xml
<string name="liquid_security_center_glass_enable">安全中心侧边栏玻璃</string>
<string name="liquid_security_center_glass_enable_summary">为已支持的全局侧边栏与所有应用使用 LiquidDock 液态玻璃；LiquidDock 释放所有权后由安全中心自行恢复原生材质或背景模糊。</string>
```

Do not advertise HyperOS 3 support in v1 text.

- [ ] **Step 7: Run focused + full tests.**

```bash
./gradlew testDebugUnitTest \
  --tests '*SecurityCenterGlassConfigTest' \
  --tests '*SecurityCenterGlassRuntimeTransitionPolicyTest' \
  --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Step 8: Commit.**

```bash
git add src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java \
        src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeTransitionPolicy.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeState.java \
        src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt \
        src/main/res/values/strings.xml src/main/res/values-zh-rCN/strings.xml \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassConfigTest.java \
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

**Consumes:** existing `Miuix307PassBlurBridge.bind(View, Surface, float)` and `PassBlurQualityPolicy` invariants.

**Produces:**

```java
enum PassBlurDomain { LAUNCHER_WORKSPACE, DOCK, SECURITY_CENTER }

final class PassBlurBindPolicy {
    static float nativeScale(PassBlurDomain domain, float requestedScale);
    static boolean requiresUnlockGate(PassBlurDomain domain);
    static String[] exclusions(String rootSurfaceName, String[] extras);
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

static Miuix307PassBlurBridge.Binding bind(
        PassBlurBindRequest request,
        android.view.Surface producerSurface);
```

- [ ] **Step 1: Write Android-free policy red tests.** Do not instantiate `View`. Assert:

```text
nativeScale(SECURITY_CENTER, any requested) == 1.0
nativeScale(LAUNCHER_WORKSPACE, any requested) == 1.0
requiresUnlockGate(LAUNCHER_WORKSPACE) == true
requiresUnlockGate(DOCK) == false
requiresUnlockGate(SECURITY_CENTER) == false
```

Also assert `exclusions("DockAssistantView#42", extras)` contains the runtime root name exactly once plus `NavigationBar`, `StatusBar`, `GestureStub`, with duplicates removed.

- [ ] **Step 2: Run red test.**

```bash
./gradlew testDebugUnitTest --tests '*PassBlurBindPolicyTest' --stacktrace
```

Expected: FAIL before new types exist.

- [ ] **Step 3: Implement domain/request/policy.** `securityCenter(View)` always uses native scale 1.0. Keep hardcoded `DockAssistantView` only as an existing compatibility extra for the Dock domain if required to preserve current behavior; Security Center correctness uses the actual resolved root SurfaceControl name.

- [ ] **Step 4: Replace bridge domain inference.** Delete `LauncherGlassSceneController.findRoot(materialHost)` from bridge-level policy. Unlock gating is based only on `request.domain()`. Preserve existing ViewRoot/SurfaceControl identity, surface sequence/layer diagnostics, `SetPassBlurSurface`, update-texture, exclusion, pause/resume and unbind semantics.

- [ ] **Step 5: Update all current bind call sites.** First run:

```bash
rg 'Miuix307PassBlurBridge\.bind' src/main/java
```

Migrate `LauncherGlassSession` with `PassBlurBindRequest.launcherWorkspace(...)` and `Miuix307PassBlurTextureView` with `PassBlurBindRequest.dock(...)`. Any additional pre-existing result must receive an explicit domain; hierarchy inference must not return.

- [ ] **Step 6: Run focused + full tests.**

```bash
./gradlew testDebugUnitTest --tests '*PassBlurBindPolicyTest' --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Commit.**

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

## Task 4: Extract generic root PassBlur/OES/freshness backend

**Risk Gate:** Security Center vendor hooks are blocked until the full Launcher unit suite and debug assembly pass after this task.

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/RootPassBlurContentRect.java`
- Delete: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSurfaceContentRect.java`
- Create: `src/main/java/com/hellovoid/liquiddock/RootPassBlurBackendState.java`
- Create: `src/main/java/com/hellovoid/liquiddock/RootPassBlurFrame.java`
- Create: `src/main/java/com/hellovoid/liquiddock/RootPassBlurBackend.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Delete: `src/test/java/com/hellovoid/liquiddock/LauncherGlassSurfaceContentRectTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/RootPassBlurContentRectTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/RootPassBlurBackendStateTest.java`
- Reuse: `src/main/java/com/hellovoid/liquiddock/ZeroCopyProducerRecoveryState.java`

**Consumes:** Task 3 bind request; `ZeroCopyProducerRecoveryState`; existing source-frame/quality policies.

**Produces:**

```java
final class RootPassBlurContentRect {
    final float left, bottom, width, height;
    static RootPassBlurContentRect full();
    static RootPassBlurContentRect resolve(
            int surfaceWidth, int surfaceHeight,
            int insetLeft, int insetTop, int insetRight, int insetBottom);
    boolean sameAs(RootPassBlurContentRect other);
}

final class RootPassBlurFrame {
    final long generation;
    final int normalizedTextureId; // render-thread callback lifetime only
    final int logicalWidth, logicalHeight;
    final int physicalWidth, physicalHeight;
    final int rotation;
    final RootPassBlurContentRect contentRect;
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

- [ ] **Step 1: Replace Launcher-named surface-content helper with generic helper.** Copy its current pure mapping semantics exactly into `RootPassBlurContentRect`; update `LauncherGlassSession` references; replace the old reflection-based test with a same-package typed `RootPassBlurContentRectTest` calling `resolve(...)` directly.

- [ ] **Step 2: Run the content-rect test.**

```bash
./gradlew testDebugUnitTest --tests '*RootPassBlurContentRectTest' --stacktrace
```

Expected: PASS after the rename/migration and no same-project reflection in the new test.

- [ ] **Step 3: Write backend-state red tests.** Drive production state through these exact transitions:

```text
requestFresh(2) -> generation 1 not fresh
bindSucceeded -> generation 2 still not fresh
sourceFrameConsumed(2) -> generation 2 fresh
requestFresh(3); sourceFrameConsumed(2) -> generation 3 not fresh
requestRebind -> freshness cleared
qualityOnlyChange -> no native endpoint recreation
```

- [ ] **Step 4: Run red backend-state test.**

```bash
./gradlew testDebugUnitTest --tests '*RootPassBlurBackendStateTest' --stacktrace
```

Expected: FAIL before backend state exists.

- [ ] **Step 5: Implement `RootPassBlurBackendState`.** Compose `ZeroCopyProducerRecoveryState`; do not duplicate its rebind-pending/fresh-frame meanings. Bind success ends rebind-pending but never marks fresh.

- [ ] **Step 6: Extract source infrastructure from `LauncherGlassSession`.** Move only: authoritative root endpoint validation/binding, producer `Surface`, input `SurfaceTexture`, OES texture, normalization FBO, root surface-content rect, source-frame drain, source freshness generation, producer recovery, render-thread/EGL source lifecycle, local physical source resolution and source render-FPS gate.

- [ ] **Step 7: Keep Launcher policy outside backend.** `LauncherGlassSession` retains node/static-node registries, output geometry, workspace scroll projection, wallpaper authoritative generation, Recents coverage, Workstation policy, rotation settle, Launcher-specific scene calls and node/output rendering semantics.

- [ ] **Step 8: Adapt Launcher session as backend consumer.** `onFreshFrame(...)` runs on the backend render thread while its EGL context is current. `normalizedTextureId` must never be used from the UI thread or retained past the render callback. Launcher wallpaper/scene authority remains an additional gate above backend source freshness.

- [ ] **Step 9: Preserve existing cadence/quality behavior.** No Choreographer/timer source pump; throttled frames still drain `updateTexImage()`; fresh generation bypasses expensive render cap; native PassBlur scale stays 1.0; local FBO resolution change does not recreate the native endpoint.

- [ ] **Step 10: Do not refactor `Miuix307PassBlurTextureView`.** It remains the stable Dock large-surface renderer apart from Task 3's explicit bind domain.

- [ ] **Step 11: Run the hard regression gate.**

```bash
./gradlew testDebugUnitTest \
  --tests '*RootPassBlurBackendStateTest' \
  --tests '*RootPassBlurContentRectTest' \
  --stacktrace
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected: all PASS / exit 0. Any Launcher failure is fixed before Task 5.

- [ ] **Step 12: Commit.**

```bash
git add -A src/main/java/com/hellovoid/liquiddock/RootPassBlurContentRect.java \
           src/main/java/com/hellovoid/liquiddock/LauncherGlassSurfaceContentRect.java \
           src/main/java/com/hellovoid/liquiddock/RootPassBlurBackendState.java \
           src/main/java/com/hellovoid/liquiddock/RootPassBlurFrame.java \
           src/main/java/com/hellovoid/liquiddock/RootPassBlurBackend.java \
           src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java \
           src/test/java/com/hellovoid/liquiddock/LauncherGlassSurfaceContentRectTest.java \
           src/test/java/com/hellovoid/liquiddock/RootPassBlurContentRectTest.java \
           src/test/java/com/hellovoid/liquiddock/RootPassBlurBackendStateTest.java
git commit -m "refactor: extract root pass blur backend"
```

---

## Task 5: Scene and material-ownership state machines

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassSceneState.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipState.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassSceneStateTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipStateTest.java`

**Consumes:** Task 2 effective live state; Task 4 freshness generations.

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

- [ ] **Step 1: Write red scene tests.** Cover initial attach -> Dock preparation -> fresh Dock; Dock -> All Apps -> Dock; generation increments on page change but no producer recreation decision exists.

- [ ] **Step 2: Write stale/failure red tests.** Old-generation fresh callback cannot claim/reveal. Disable/failure/detach hides and releases before shutdown is acted on.

- [ ] **Step 3: Write ownership red tests.** Initial owner is vendor; only equal rendered/current generations permit suppression; release is idempotent.

- [ ] **Step 4: Run red tests.**

```bash
./gradlew testDebugUnitTest \
  --tests '*SecurityCenterGlassSceneStateTest' \
  --tests '*SecurityCenterMaterialOwnershipStateTest' \
  --stacktrace
```

Expected: FAIL before implementation.

- [ ] **Step 5: Implement both Android-free state classes.** They return decisions only and hold no Android/Xposed objects.

- [ ] **Step 6: Run focused + full tests.**

```bash
./gradlew testDebugUnitTest \
  --tests '*SecurityCenterGlassSceneStateTest' \
  --tests '*SecurityCenterMaterialOwnershipStateTest' \
  --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Commit.**

```bash
git add src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassSceneState.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipState.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassSceneStateTest.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipStateTest.java
git commit -m "feat: add security center glass scene state"
```

---

## Task 6: Large-surface Prismal session and coordinator

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassGeometry.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassOutputView.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassSession.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassGeometryTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinatorPolicyTest.java`

**Consumes:** `RootPassBlurBackend`, Task 5 states, `LiquidDockConfig.Glass`, `Miuix307PrismalMaterial`, `Miuix307PrismalAdapter`.

**Produces:**

```java
final class SecurityCenterGlassCoordinator
        implements SecurityCenterGlassRuntimeState.Owner {
    void bindGlobalDock(View turboLayout, View dockLayout, View appsLayout);
    void onAllAppsToggleStarted(View turboLayout);
    void onAllAppsToggleSettled(View turboLayout, boolean allAppsPresent);
    boolean shouldSuppressVendorFinalBackground(Object turboLayout);
    @Override public void releaseAll();
}
```

- [ ] **Step 1: Write pure geometry red tests.** Verify left/right sidebar target bounds and All Apps bounds map to root-local logical coordinates; geometry changes do not alter content generation.

- [ ] **Step 2: Implement immutable `SecurityCenterGlassGeometry`.** Coordinator reads Views; geometry math itself remains Android-free where possible.

- [ ] **Step 3: Implement output hierarchy.** When `turboLayout.getParent()` is a `ViewGroup`, insert exactly one `SecurityCenterGlassOutputView` immediately before `turboLayout` in that parent, `MATCH_PARENT x MATCH_PARENT`. Render transparent outside current target geometry. Do not add the output inside TurboLayout's own child/Z ordering.

- [ ] **Step 4: Configure output ownership.** Non-clickable, non-focusable, `IMPORTANT_FOR_ACCESSIBILITY_NO`, hidden until fresh-frame authorization. It does not allocate a PassBlur producer.

- [ ] **Step 5: Implement `SecurityCenterGlassSession`.** It owns one `RootPassBlurBackend` and one large-surface Prismal/output renderer per root. Use existing optical config:

```java
Miuix307PrismalMaterial.Params optical =
        Miuix307PrismalMaterial.fromConfig(glassConfig, density);
PrismalParams params = Miuix307PrismalAdapter.toPortable(optical);
PrismalHighlightProfile profile = glassConfig.largeSurfaceHighlightProfile;
```

Do not instantiate `DockGlassCompositor`, Launcher static-node registries, or Workstation policy.

- [ ] **Step 6: Write coordinator-policy red tests around production state decisions.** One root -> one session; page change -> same session; root replacement -> old session shutdown before new ownership; stale callback after `releaseAll()` cannot reveal.

- [ ] **Step 7: Implement coordinator lifecycle.** Authoritative root is `turboLayout.getRootView()` after attach. Root detach posts one main-loop recheck and shuts down only if that same root is still detached. No timing constant is used.

- [ ] **Step 8: Recheck authority in every queued callback.** Verify runtime enabled, coordinator/session identity, root identity, scene generation and shutdown state immediately before any claim/reveal.

- [ ] **Step 9: Run tests/build.**

```bash
./gradlew testDebugUnitTest \
  --tests '*SecurityCenterGlassGeometryTest' \
  --tests '*SecurityCenterGlassCoordinatorPolicyTest' \
  --stacktrace
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected: PASS / exit 0.

- [ ] **Step 10: Commit.**

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

## Task 7: Reversible vendor material bridge and exact hooks

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialBridge.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java`
- Extend: `src/test/java/com/hellovoid/liquiddock/SecurityCenterHookSpecTest.java`
- Extend: `src/test/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipStateTest.java`

**Consumes:** validated OS4 semantics:
- `DockWindowManagerService.onCreate()` supplies a real Service/Context;
- `TurboLayout.M(com.miui.dock.sidebar.p, ja.a)` is `prepareNewDockLayout`;
- `ja.a.f()` is exactly type 4 / Global Dock;
- `TurboLayout.d0()` is All Apps toggle;
- `f17943s` is the vendor transformation-in-progress Boolean;
- `f17941q` is the vendor All Apps-present Boolean;
- `TurboLayout.U()` is `setFinalNewDockBackground` and restores the complete vendor final background;
- `gq.g.l(View)` resets HyperOS 4 view material.

**Produces:**

```java
final class SecurityCenterVendorMaterialBridge {
    SecurityCenterVendorMaterialBridge(ClassLoader loader, SecurityCenterHookSpec spec);
    void claimCustom(Object turboLayout, View dockLayout);
    void restoreVendor(Object turboLayout);
}

final class SecurityCenterGlassHook {
    static void install(ClassLoader classLoader, LiquidDockConfig initialConfig);
}
```

- [ ] **Step 1: Install bootstrap hook only in the already process-gated Security Center branch.** Resolve `SecurityCenterHookSpec.BOOTSTRAP_SERVICE_CLASS` and exact no-arg `onCreate`. Call original first, obtain `Context` from `chain.getThisObject()`, read `PackageInfo.getLongVersionCode()`, then resolve HookSpec. Unknown version logs once and installs no version-specific mutation hook.

```java
HookUtil.hookMethod(serviceClass, "onCreate", new Class<?>[0], chain -> {
    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
    Context service = (Context) chain.getThisObject();
    installValidatedHooksOnce(service, classLoader);
    return result;
});
```

- [ ] **Step 2: Install exact type-4 prepare hook after version validation.** Resolve target classes with the target ClassLoader and hook:

```java
TurboLayout.M(com.miui.dock.sidebar.p, ja.a)
```

Call original first. Use `chain.getArgs().get(1)` as the `ja.a` object and `HookUtil.requireInvoke(typeArg, spec.type4PredicateMethod())`. Continue only for `Boolean.TRUE`. Then exact-call `getDockLayout()` and `getAppsLayout()` and register coordinator attach/pre-draw handling. Types 1/3/5/default are untouched.

- [ ] **Step 3: Hook exact All Apps toggle.** Before `d0()` proceeds, read `HookUtil.getBooleanField(turbo, spec.transformingField())`. If already true, do not start a LiquidDock transition because Security Center itself blocks that toggle. Otherwise call `coordinator.onAllAppsToggleStarted(turbo)` and proceed.

- [ ] **Step 4: Wait on vendor transformation authority, not time.** After a real toggle starts, add one pre-draw listener. While `transformingField` is true, leave the listener installed. When it becomes false, remove it, read `allAppsPresentField`, and call:

```java
coordinator.onAllAppsToggleSettled(turbo, allAppsPresent);
```

No duration, delayed runnable or fixed frame count may determine completion.

- [ ] **Step 5: Implement custom material claim only after current-generation Prismal render.**

```java
Class<?> helper = Class.forName(
        spec.os4MaterialHelperClass(), false, classLoader);
HookUtil.requireInvokeStatic(helper, spec.os4MaterialResetMethod(), dockLayout);
MiBlurBridge.clearPassWindowBlur(dockLayout);
```

Leave TurboLayout/root pass-window permission untouched. Do not clear unrelated child backgrounds.

- [ ] **Step 6: Gate only public vendor semantic `TurboLayout.U()`.** While CUSTOM owns that exact TurboLayout, skip the void method; otherwise pass through:

```java
HookUtil.hookMethod(turboClass, spec.finalBackgroundMethod(), new Class<?>[0], chain -> {
    Object turbo = chain.getThisObject();
    if (coordinator.shouldSuppressVendorFinalBackground(turbo)) return null;
    return chain.proceed(chain.getArgs().toArray(new Object[0]));
});
```

Do not globally hook `gq.g`, `gq.m`, `MaterialToken`, or `com.miui.common.utils.m`.

- [ ] **Step 7: Restore through vendor `U()`, never private `S()`.** First set ownership to VENDOR so the gate passes, then:

```java
HookUtil.requireInvoke(turboLayout, spec.finalBackgroundMethod());
```

Security Center itself chooses current MiGlass or ordinary blur and applies its complete final-background/shadow path. Never replay guessed token/blur/shadow values.

- [ ] **Step 8: Fail closed on claim/restore errors.** Claim error keeps custom hidden and vendor authoritative. Restore error keeps custom hidden and logs the exact failed invocation; LiquidDock does not synthesize a substitute vendor material.

- [ ] **Step 9: Run tests/build.**

```bash
./gradlew testDebugUnitTest \
  --tests '*SecurityCenterHookSpecTest' \
  --tests '*SecurityCenterMaterialOwnershipStateTest' \
  --stacktrace
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected: PASS / exit 0.

- [ ] **Step 10: Commit.**

```bash
git add src/main/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialBridge.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassHook.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterHookSpecTest.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipStateTest.java
git commit -m "feat: hook security center sidebar glass"
```

---

## Task 8: Module composition and Xposed scope

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Modify: `src/main/resources/META-INF/xposed/scope.list`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterScopeContractTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/RuntimeBehaviorTestPolicyContractTest.java` only if the static scope-file read requires explicit allowlisting.

**Consumes:** Tasks 1, 2 and 7.

**Produces:** Security Center routing in the libxposed composition root; no `MainHook` modification.

- [ ] **Step 1: Write static scope red test.** Read only `src/main/resources/META-INF/xposed/scope.list`; assert exact entries:

```text
com.miui.home
com.android.systemui
com.miui.securitycenter
```

This is a static Xposed-scope contract, not a runtime-behavior source test.

- [ ] **Step 2: Add scope entry.** Preserve Launcher/SystemUI entries and add Security Center.

- [ ] **Step 3: Store process identity in `ModuleMain`.**

```java
private String loadedProcessName;

@Override
public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
    Api101Bridge.init(this);
    loadedProcessName = param.getProcessName();
    ...
}
```

- [ ] **Step 4: Add Security Center branch before Launcher-only return.** Do not run config migrations in this process.

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

- [ ] **Step 5: Keep existing domains unchanged.** SystemUI remains timing-source only; Launcher still owns migrations and `MainHook`; Security Center never calls `MainHook.install()`.

- [ ] **Step 6: Run tests/build.**

```bash
./gradlew testDebugUnitTest --tests '*SecurityCenterScopeContractTest' --stacktrace
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected: PASS / exit 0.

- [ ] **Step 7: Commit.**

```bash
git add src/main/java/com/hellovoid/liquiddock/ModuleMain.java \
        src/main/resources/META-INF/xposed/scope.list \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterScopeContractTest.java \
        src/test/java/com/hellovoid/liquiddock/RuntimeBehaviorTestPolicyContractTest.java
git commit -m "feat: scope liquid glass to security center ui"
```

---

## Task 9: Documentation, release verification, and real-device acceptance

**Files:**
- Modify: `ARCHITECTURE.md`
- Modify: `HOOKS.md`
- Modify: `FEATURES.md`
- Modify: `CHANGELOG.md` only under the repository's existing current-development section.
- Inspect: `src/main/keepRules/liquiddock.keep`; change it only if release-R8 evidence proves a module-owned reflected entry needs stable naming.

**Consumes:** completed Tasks 1-8.

**Produces:** documented support boundary and final automated/device evidence.

- [ ] **Step 1: Update docs.** Document `com.miui.securitycenter:ui`, exact build `40011320`, type-4 Global Dock + All Apps only, one-root/one-producer lifecycle, current-generation reveal, `M/d0/U` semantic Hook boundaries, and fail-closed unsupported builds.

- [ ] **Step 2: Document platform semantics.** HyperOS 4 may restore MiGlass/MaterialToken or blur. HyperOS 3 has no soft-light-glass vendor semantics; future HyperOS 3 Security Center support is blur-only.

- [ ] **Step 3: Run complete automated verification.**

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
./gradlew assembleRelease --no-daemon --stacktrace
```

Expected: all exit 0. If release tooling is unavailable because Android SDK configuration is missing, report that exact environmental failure and do not claim release verification.

- [ ] **Step 4: Audit forbidden regressions.**

```bash
git diff main...HEAD -- src/main src/test ARCHITECTURE.md HOOKS.md FEATURES.md CHANGELOG.md
rg 'ScreenCapture|Bitmap\.createBitmap|PixelCopy|postDelayed\(' \
   src/main/java/com/hellovoid/liquiddock/SecurityCenter* \
   src/main/java/com/hellovoid/liquiddock/RootPassBlur* || true
```

There must be no ScreenCapture/bitmap/PixelCopy path and no Security Center/root-backend `postDelayed` timing workaround.

- [ ] **Step 5: Commit docs.**

```bash
git add ARCHITECTURE.md HOOKS.md FEATURES.md CHANGELOG.md src/main/keepRules/liquiddock.keep
git commit -m "docs: document security center liquid glass"
```

### Real-device matrix

Use diagnostics that do not log user content:

```bash
adb logcat -c
adb logcat | grep -E '\[DC\]\[(SecurityCenterGlass|RootPassBlur|PassBlur)\]'
```

- [ ] HOME behind sidebar -> Global Dock: live HOME backdrop, no self-feedback, no transparent first frame.
- [ ] Normal app behind sidebar -> Global Dock: live app backdrop, not Launcher wallpaper.
- [ ] Video/dynamic app behind sidebar: source-driven updates remain live.
- [ ] Global Dock -> All Apps -> Global Dock for at least 10 round trips: session/native endpoint stay stable unless ViewRoot/SurfaceControl authority actually changes.
- [ ] During each toggle, vendor owns transition until `f17943s == false`; custom scene reveals only after a current-generation OES-backed Prismal render.
- [ ] Dismiss -> reopen: no stale previous backdrop before fresh frame.
- [ ] Portrait <-> landscape: old orientation remains hidden until fresh content is authoritative.
- [ ] Security Center component ON -> OFF: effective state publishes false; custom layer releases; vendor `U()` restores current OS4 material/blur.
- [ ] Component OFF -> ON while global glass remains enabled: no stale callback from the previous ownership epoch reclaims the old session.
- [ ] Global Liquid Glass OFF: Security Center output releases even if component key remains true.
- [ ] Kill/restart `com.miui.securitycenter:ui`: old root/session releases; new process starts cleanly.
- [ ] Type 1/3/5 toolbox surfaces remain unchanged.
- [ ] Unsupported Security Center build, when available: diagnostic only; no producer/material mutation.
- [ ] Launcher regression: HOME/APP, Recents return, wallpaper freshness, rotation, Workstation recovery, Dock, icon/widget/folder glass retain pre-feature behavior.

### Evidence-driven correction rule

If device validation fails, correct only from observed authority:

```text
stock layer visible -> identify exact owning View/material call before changing suppression
producer stops -> compare root/ViewRoot/SurfaceControl name/layer/surface sequence first
stable endpoint but no frames -> use producer recovery + fresh-frame gate
geometry wrong -> fix root/target coordinate authority, not source generation
```

Do not respond with recursive background clearing, disabling TurboLayout pass-window permission without evidence, revealing the last stale texture, or adding fixed delays. A correction that changes the approved ownership architecture requires a design-spec amendment before code changes.

- [ ] **Step 6: After any device-driven correction, rerun full verification.**

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
./gradlew assembleRelease --no-daemon --stacktrace
```

Then rerun affected device rows plus one complete Dock -> All Apps -> Dock loop and one Launcher regression loop.

---

## Review Gates

1. Task 1 proves exact process/build/type/capability semantics before runtime mutation code exists.
2. Task 2 proves typed config and three-level effective gating before hooks consume live state.
3. Task 3 removes implicit Launcher-domain inference from the PassBlur bridge.
4. Task 4 removes the last Launcher-named type from the shared source backend and must pass the full Launcher unit/debug gate before Security Center depends on it.
5. Tasks 5-6 establish production state/geometry/session ownership before vendor material mutation.
6. Task 7 uses exact Security Center semantic boundaries: `M(p, ja.a)`, `ja.a.f()`, `d0()`, vendor transform/presence fields, and public restore method `U()`; it does not hook material internals globally.
7. Task 8 changes composition/scope only and does not expand `MainHook`.
8. Task 9 requires automated evidence plus real-device evidence before v1 support is declared.

## Definition of Done

The feature is complete only when all are evidenced:

- exact `com.miui.securitycenter:ui` + versionCode `40011320` + type-4 gating;
- one Global Dock/All Apps root session and one native producer across page transitions;
- no custom reveal before a current-generation real OES-backed Prismal frame;
- vendor `TurboLayout.U()` is the restoration authority and is suppressed only while CUSTOM owns that exact TurboLayout;
- unsupported/failing paths leave or return to vendor authority;
- Core master, Liquid Glass master and Security Center component state all participate in effective gating;
- HyperOS 3 remains blur-only in capability policy and is not advertised as v1 supported;
- no ScreenCapture/bitmap/fixed-delay fallback exists;
- no Security Center lifecycle state enters `MainHook`;
- shared backend contains no `Launcher*` dependency;
- runtime tests use production state/policy classes rather than source-text assertions;
- existing Launcher zero-copy/freshness/recovery behavior passes regression verification;
- debug/release verification is evidenced;
- the real-device matrix passes on the declared Security Center build.
