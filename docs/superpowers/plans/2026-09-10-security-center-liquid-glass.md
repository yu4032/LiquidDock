# Security Center Liquid Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add fail-closed Global Dock/All Apps LiquidDock Prismal glass to the validated HyperOS 4 Security Center `:ui` process with one root-bound zero-copy PassBlur producer.

**Architecture:** Make the existing PassBlur binding contract domain-explicit, extract the reusable root PassBlur/OES/freshness core needed by Launcher glass, and attach a Security Center-specific coordinator/state/material bridge to that shared backend without changing Launcher semantics. Vendor material restoration remains Security Center-owned: the validated HyperOS 4 path may restore MiGlass/MaterialToken, while ordinary blur remains the fallback; future HyperOS 3 support is explicitly blur-only.

**Tech Stack:** Java 17, Kotlin/Compose settings UI, libxposed API 101, Android hidden SurfaceControl PassBlur APIs, SurfaceTexture/OES, EGL/GLES, Prismal, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-10-security-center-liquid-glass-design.md`

## Global constraints

- v1 supports only `com.miui.securitycenter:ui` and Security Center versionCode `40011320` (`13.2.0-260806.0.1.pad`).
- HyperOS 3 vendor material semantics are `BACKGROUND_BLUR` only. Never emulate, restore, or advertise HyperOS 4 soft-light glass on HyperOS 3.
- HyperOS 4 native MiGlass capability and LiquidDock Prismal capability are separate authorities.
- No ScreenCapture fallback, bitmap readback, CPU backdrop copy, fixed delay, page-owned producer, fuzzy hook discovery, or recursive background clearing.
- Native PassBlur scale remains `1.0`; quality reduction, if reused, occurs only after OES normalization.
- Global Dock and All Apps share one sidebar root session and one native producer.
- Rebind/recreate success is not freshness. Reveal custom output only after a real OES frame has been rendered for the current scene generation.
- Unsupported build, invalid root, reflection failure, producer failure, EGL failure, or stale callback leaves the vendor material authoritative.
- Runtime disable publishes `false` before teardown; every queued callback rechecks live state and generation before acting.
- Vendor/system private reflection stays inside hook/bridge boundaries. LiquidDock-owned classes communicate through typed/package-private APIs.
- Runtime ownership/freshness/lifecycle tests drive production state/policy objects. Do not add source-reader runtime tests or legacy test debt.
- Do not modify `MainHook` to store or coordinate Security Center state.
- Do not expand v1 to Game Toolbox, Video Toolbox, or Conversation Toolbox.

---

## Task 1: Define exact process, build, type, and vendor-capability policies

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterProcessPolicy.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterHookSpec.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialCapability.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterProcessPolicyTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterHookSpecTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialCapabilityTest.java`

### Steps

- [ ] Write failing `SecurityCenterProcessPolicyTest` cases proving installation is accepted only for package `com.miui.securitycenter` with process `com.miui.securitycenter:ui`; reject the package main process, another suffix, Launcher, and null/empty process names.
- [ ] Run `./gradlew testDebugUnitTest --tests '*SecurityCenterProcessPolicyTest' --stacktrace` and confirm the test fails because the production policy does not exist.
- [ ] Implement the Android-free process policy:

```java
final class SecurityCenterProcessPolicy {
    static final String PACKAGE = "com.miui.securitycenter";
    static final String UI_PROCESS = "com.miui.securitycenter:ui";

    static boolean shouldInstall(String packageName, String processName) {
        return PACKAGE.equals(packageName) && UI_PROCESS.equals(processName);
    }
}
```

- [ ] Write failing `SecurityCenterHookSpecTest` cases for exact build gating and Global Dock semantic type. The only v1 spec is versionCode `40011320`; unknown versions resolve to no spec. Store exact validated names in the spec, including `com.miui.gamebooster.windowmanager.newbox.TurboLayout`, `ja.a`, type predicate `f`, dock getter `getDockLayout`, apps getter `getAppsLayout`, All Apps toggle `d0`, and material recompute method `S`.
- [ ] Make type-4 detection a typed spec operation at the vendor boundary: the hook obtains the `ja.a` object from the validated TurboLayout member/access path and invokes exact `f()`; do not infer Global Dock from dimensions, child count, class shape, or window-title strings. `DockAssistantView` is diagnostic only.
- [ ] Write failing capability tests with these invariants:
  - `OS3_BLUR_ONLY + nativeGlassFlag=true -> BACKGROUND_BLUR`;
  - `OS3_BLUR_ONLY + false -> BACKGROUND_BLUR`;
  - `OS4_SOFT_LIGHT_CAPABLE + true -> SOFT_LIGHT_GLASS`;
  - `OS4_SOFT_LIGHT_CAPABLE + false -> BACKGROUND_BLUR`.
- [ ] Implement `SecurityCenterVendorMaterialCapability` and a small `VendorGeneration` enum without Android dependencies.
- [ ] Run the three focused test classes and confirm green.
- [ ] Commit:

```bash
git add src/main/java/com/hellovoid/liquiddock/SecurityCenter* \
        src/test/java/com/hellovoid/liquiddock/SecurityCenter*Test.java
git commit -m "test: define security center compatibility policy"
```

**Stop condition:** no Security Center runtime/view code is allowed until exact package/process/build/type policy is green.

---

## Task 2: Add typed configuration and a Security Center-local live runtime state

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeTransitionPolicy.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeState.java`
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Modify: `src/main/res/values/strings.xml`
- Modify: `src/main/res/values-zh-rCN/strings.xml`
- Create or extend typed config/runtime tests under `src/test/java/com/hellovoid/liquiddock/` and `src/test/java/com/hellovoid/liquiddock/config/` using the repository's existing config test patterns.

### Steps

- [ ] Add a failing schema/config test for one new key:

```java
ConfigSchema.Glass.SECURITY_CENTER_GLASS
// name: liquid_security_center_glass
// uiDefault=false, runtimeFallback=false, exportDefault=false
// ExportMode.ALWAYS
```

- [ ] Prove through `ConfigCodec.exportValues/importValues` tests that the Boolean round-trips through the existing schema iteration. Do not add a special-case branch to `ConfigCodec`.
- [ ] Prove `PresetManager.defaultValues()` naturally contains the key as `false` through the `ALWAYS` schema iteration. Do not add a separate preset override.
- [ ] Add `final boolean securityCenterEnabled` to `LiquidDockConfig.Glass` and load it through `ConfigSchema.Glass.SECURITY_CENTER_GLASS` using the same typed `ConfigReader` path as other glass component flags.
- [ ] Create `SecurityCenterGlassRuntimeTransitionPolicy` as an Android-free planner. Its effective state is `masterEnabled && securityCenterEnabled`; a true-to-false transition produces one dominant `releaseAll` decision.
- [ ] Write red tests proving master disable and component disable both request release, false-to-false is a no-op, and enable does not masquerade as already-installed ownership.
- [ ] Implement `SecurityCenterGlassRuntimeState` only for the Security Center `:ui` process. It listens to the two relevant keys, publishes new booleans before dispatching teardown on the main thread, and exposes a typed teardown callback/owner registration. It must not call `MainHook`.
- [ ] Add the Compose switch to the existing Liquid Glass page rather than creating a new top-level settings page. Suggested copy:
  - English title: `Security Center sidebar glass`
  - English summary: `Use LiquidDock glass for the supported Global Dock and All Apps sidebar; Security Center remains responsible for restoring its native material or blur.`
  - Chinese title: `安全中心侧边栏玻璃`
  - Chinese summary: `为已支持的全局侧边栏与所有应用使用 LiquidDock 液态玻璃；关闭或失败时由安全中心自行恢复原生材质或背景模糊。`
- [ ] Do not claim HyperOS 3 Security Center support in UI copy; the OS3 blur-only rule is compatibility semantics for future specs.
- [ ] Run focused config/runtime tests, then `./gradlew testDebugUnitTest --stacktrace`.
- [ ] Commit:

```bash
git add src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java \
        src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeTransitionPolicy.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassRuntimeState.java \
        src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt \
        src/main/res/values/strings.xml src/main/res/values-zh-rCN/strings.xml \
        src/test
git commit -m "feat: add security center glass configuration"
```

---

## Task 3: Make the PassBlur binding boundary domain-explicit

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/PassBlurDomain.java`
- Create: `src/main/java/com/hellovoid/liquiddock/PassBlurBindRequest.java`
- Create: `src/main/java/com/hellovoid/liquiddock/PassBlurBindPolicy.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurBridge.java`
- Modify only existing bind call sites required by compilation, expected primarily:
  - `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
  - `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java`
- Create: `src/test/java/com/hellovoid/liquiddock/PassBlurBindPolicyTest.java`

### Steps

- [ ] Write red tests for an explicit domain model:

```java
enum PassBlurDomain {
    LAUNCHER_WORKSPACE,
    DOCK,
    SECURITY_CENTER
}
```

- [ ] Define typed factories rather than free-form constructor use:

```java
PassBlurBindRequest.launcherWorkspace(View host, float requestedScale)
PassBlurBindRequest.dock(View host, float requestedScale)
PassBlurBindRequest.securityCenter(View authoritativeRoot)
```

The Security Center factory always carries native scale `1.0f`.

- [ ] Put exclusion-list composition into pure `PassBlurBindPolicy`. The resolved runtime root SurfaceControl name is mandatory and deduplicated. Retain platform exclusions (`NavigationBar`, `StatusBar`, `GestureStub`) and keep existing Dock compatibility exclusions only in the appropriate request factory; do not use hardcoded `DockAssistantView` as Security Center's correctness authority.
- [ ] Modify `Miuix307PassBlurBridge.Binding` to store `PassBlurDomain` instead of the semantic Boolean `launcherWorkspace` where practical. Unlock capture gating applies only to `LAUNCHER_WORKSPACE`.
- [ ] Remove `LauncherGlassSceneController.findRoot(materialHost)` from bridge-level domain inference. The caller declares its domain.
- [ ] Preserve all existing root identity, SurfaceControl validation, surface sequence/layer diagnostics, `SetPassBlurSurface`, update-texture, exclusion, pause/resume, and unbind behavior.
- [ ] Update existing callers with behavior-preserving factories: Launcher root session uses `launcherWorkspace`; existing Dock `Miuix307PassBlurTextureView` uses `dock`. Do not otherwise refactor `Miuix307PassBlurTextureView` in this task.
- [ ] Run `PassBlurBindPolicyTest`, all existing PassBlur/quality tests, then the full unit suite.
- [ ] Commit only after existing Launcher and Dock tests remain green:

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

## Task 4: Extract the reusable root PassBlur/OES/freshness backend

**Risk:** Highest-risk implementation task. Complete the Launcher regression gate in this task before any Security Center UI hook work starts.

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/RootPassBlurBackendState.java`
- Create: `src/main/java/com/hellovoid/liquiddock/RootPassBlurBackend.java`
- Create: `src/main/java/com/hellovoid/liquiddock/RootPassBlurFrame.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Reuse: `src/main/java/com/hellovoid/liquiddock/ZeroCopyProducerRecoveryState.java`
- Create: `src/test/java/com/hellovoid/liquiddock/RootPassBlurBackendStateTest.java`
- Extend existing producer/freshness tests only where the production abstraction has actually moved.

### Boundary to extract

`RootPassBlurBackend` owns only root-bound infrastructure common to Launcher and Security Center:

- authoritative root `View` and root SurfaceControl identity;
- one PassBlur producer `Surface` / input `SurfaceTexture`;
- OES texture and normalization target;
- endpoint bind/rebind/rollover;
- `ZeroCopyProducerRecoveryState`;
- scene generation versus consumed generation freshness;
- EGL context/thread resources needed to own the OES/normalized texture;
- source-frame drain and current-generation fresh-frame callback;
- local physical-FBO quality scaling after OES normalization.

It must not contain Launcher node registries, workspace scroll projection, wallpaper-generation authority, Recents policy, Workstation policy, folder/icon semantics, `TurboLayout`, or Security Center class names.

### Typed render-thread contract

- [ ] First write red `RootPassBlurBackendStateTest` cases proving:
  - requesting generation 2 invalidates generation 1 freshness;
  - `onBindSucceeded()` does not mark generation 2 fresh;
  - only a real source frame consumed for generation 2 marks generation 2 publishable;
  - a stale generation callback cannot publish after generation advances;
  - endpoint rebind clears fresh state;
  - duplicate rebind requests remain coalesced by `ZeroCopyProducerRecoveryState`;
  - local FBO size/quality change does not request native endpoint recreation.
- [ ] Keep GL texture ownership on the backend render thread. Do not pass raw texture IDs to UI-thread code. Use a callback executed on the backend render thread, for example:

```java
interface FrameConsumer {
    void renderFreshFrame(RootPassBlurFrame frame);
    void onTerminalFailure(long generation, Throwable error);
}
```

`RootPassBlurFrame` is valid only during the render callback and carries generation, logical/physical dimensions, rotation/content mapping, and backend-owned normalized texture access needed by the consumer.

- [ ] Implement lifecycle API with a narrow surface:

```java
void requestFresh(long generation);
boolean rebind(String reason);
void setUpdatesEnabled(boolean enabled, String reason);
void setQuality(int physicalScalePercent, int renderFps);
boolean hasFreshFrame(long generation);
void shutdown();
```

- [ ] Move only the corresponding root/OES/EGL/freshness mechanics out of `LauncherGlassSession`. Keep Launcher-specific scene/output behavior in `LauncherGlassSession` and adapt it as a `FrameConsumer`.
- [ ] Keep `LauncherGlassSessionRegistry` public/package-private behavior unchanged, including its Launcher-specific stable-root resolution and Recents/unlock entry points.
- [ ] Do not move or rewrite the stable Dock-specific `Miuix307PassBlurTextureView` renderer in this task.
- [ ] Preserve existing source-driven cadence: no Choreographer/timer producer pump; capped frames still drain `SurfaceTexture.updateTexImage()`; a fresh generation bypasses expensive render throttling.
- [ ] Preserve native PassBlur scale `1.0` and logical-versus-physical coordinate separation.
- [ ] Run the new state test first. Then run all existing Launcher glass, producer-recovery, quality, wallpaper-freshness and Workstation tests. Finally run:

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

- [ ] If any Launcher behavior test fails, fix the shared-backend extraction before proceeding; do not compensate in Security Center code.
- [ ] Commit:

```bash
git add src/main/java/com/hellovoid/liquiddock/RootPassBlurBackend*.java \
        src/main/java/com/hellovoid/liquiddock/RootPassBlurFrame.java \
        src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java \
        src/test/java/com/hellovoid/liquiddock/RootPassBlurBackendStateTest.java \
        src/test/java/com/hellovoid/liquiddock
git commit -m "refactor: extract root pass blur backend"
```

**Hard gate:** do not start Task 5 until the complete unit suite and debug assembly pass after this extraction.

---

## Task 5: Model Security Center scene and material ownership before touching vendor Views

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassSceneState.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipState.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassSceneStateTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipStateTest.java`

### Steps

- [ ] Write the scene state as Android-free production code with states:

```text
DETACHED
PREPARING_DOCK
DOCK
TRANSITIONING
PREPARING_ALL_APPS
ALL_APPS
```

- [ ] Model events explicitly: root attached as Global Dock, transition requested, target geometry settled, fresh Prismal frame rendered for generation, runtime disabled, terminal failure, root detached.
- [ ] Model decisions explicitly rather than invoking callbacks inside the pure state object. At minimum decisions can carry: `ensureSession`, `invalidateGeneration`, `requestFresh`, `hideCustom`, `releaseCustomOwnership`, `claimCustomOwnership`, `revealCustom`, `shutdownSession`, and `generation`.
- [ ] Red tests must prove Dock -> All Apps -> Dock advances scene generation without requesting producer recreation; transition immediately makes the previous custom frame non-authoritative; stale generation fresh callbacks do nothing; current-generation fresh render is the only path to `revealCustom`; disable/failure/detach returns to vendor authority.
- [ ] Implement `SecurityCenterMaterialOwnershipState` with `VENDOR` and `CUSTOM` authority. `CUSTOM` can be acquired only after a current-generation rendered-frame authorization. Transition, disable, terminal failure, or detach releases to `VENDOR` before any session teardown.
- [ ] Include a test for the exact disable ordering at the policy/state level: published disabled state causes ownership release decision before shutdown decision can be acted on.
- [ ] Run focused tests and the full unit suite.
- [ ] Commit:

```bash
git add src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassSceneState.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipState.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassSceneStateTest.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipStateTest.java
git commit -m "feat: add security center glass scene state"
```

---

## Task 6: Add Security Center large-surface Prismal session and geometry coordinator

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassOutputView.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassGeometry.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassSession.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassGeometryTest.java`
- Extend scene/ownership tests for coordinator policy objects if additional pure decisions are introduced.

### Steps

- [ ] Implement pure geometry snapshots from an authoritative root and active target bounds. Keep root coordinates, target visible bounds, clipping, corner radius, logical framebuffer dimensions, and rotation separate from source-content generation.
- [ ] Use attach/layout/pre-draw authority only. Do not introduce `postDelayed`, arbitrary frame counts for scene settling, or animation-duration constants.
- [ ] `SecurityCenterGlassSession` is a thin domain consumer of `RootPassBlurBackend`, not a second producer implementation. It uses existing `LiquidDockConfig.Glass` optical parameters via `Miuix307PrismalMaterial.fromConfig(...)` and `Miuix307PrismalAdapter.toPortable(...)`, with the existing `largeSurfaceHighlightProfile`.
- [ ] Render one large rounded panel geometry for the current Security Center target. Do not instantiate `DockGlassCompositor`, Launcher icon registries, workspace node machinery, or Workstation policy.
- [ ] `SecurityCenterGlassOutputView` must be non-clickable, non-focusable, accessibility-no, initially hidden/alpha-authority false, and owned by the coordinator. Its output surface never creates its own PassBlur source.
- [ ] Coordinator invariant: one authoritative sidebar root -> one `SecurityCenterGlassSession`. Switching Global Dock/All Apps changes target geometry and scene generation only.
- [ ] Root detach uses the existing project pattern: post one main-loop recheck and shut down only if the same authoritative root is still detached. This is lifecycle validation, not a timing workaround.
- [ ] All posted/pre-draw/frame callbacks recheck runtime enabled, root identity, session identity, scene generation, and shutdown state immediately before mutation/reveal.
- [ ] Run geometry/state tests, full unit suite, and debug assembly.
- [ ] Commit:

```bash
git add src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassOutputView.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassGeometry.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassSession.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterGlassGeometryTest.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenter*Test.java
git commit -m "feat: add security center glass coordinator"
```

---

## Task 7: Implement reversible Security Center vendor-material ownership

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialBridge.java`
- Modify only if required for exact classic cleanup: `src/main/java/com/hellovoid/liquiddock/MiBlurBridge.java`
- Extend: `src/test/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipStateTest.java`
- Add an audited static reflection-boundary contract test only if necessary; do not use source inspection to prove runtime ordering.

### Validated OS4 behavior to encode

The analyzed Security Center applies its Dock material in private `TurboLayout.S()`. HyperOS 4 material reset helper `gq.g.l(View)` clears the MIUIX material state. The classic fallback uses pass-window/background blur on the Dock view. On release, `TurboLayout.S()` is the vendor authority that decides whether the current environment should use MiGlass or ordinary blur.

### Steps

- [ ] Resolve `gq.g` with the target Security Center ClassLoader inside the bridge/hook boundary. Use `HookUtil.requireInvokeStatic(gqGClass, "l", dockLayout)` for the v1 invariant reset; do not import/compile against Security Center classes.
- [ ] Claim sequence for v1:
  1. ensure current-generation Prismal output is rendered but still hidden;
  2. call vendor OS4 material reset on **DockLayout only**;
  3. call `MiBlurBridge.clearPassWindowBlur(dockLayout)` to remove the classic Dock fallback if present;
  4. leave TurboLayout/root pass-window permission untouched so LiquidDock does not accidentally disable its own root backdrop source;
  5. if all required claim operations succeed and generation/live checks still match, show custom output.
- [ ] Do not clear TurboLayout pass-window state in v1 without device evidence that it is a separate visible stock layer rather than producer permission. Treat any remaining visible stock layer as a device-validation failure requiring a new evidence-backed change.
- [ ] Release sequence:
  1. hide custom output / publish vendor authority in state;
  2. remove any LiquidDock suppression gate;
  3. `HookUtil.requireInvoke(turboLayout, spec.applyMaterialMethod)` where v1 spec names private `S`;
  4. Security Center itself recomputes MiGlass or ordinary blur.
- [ ] Never cache, fabricate, or replay `MiGlass` arrays, radii, blend colors, MaterialToken values, or unknown shadow state.
- [ ] A failed claim leaves custom output hidden and requests vendor recompute. A failed recompute remains logged and custom stays hidden; do not guess a replacement native material. On a future OS3 HookSpec, vendor capability is always ordinary background blur.
- [ ] Run focused ownership tests and full unit suite.
- [ ] Commit:

```bash
git add src/main/java/com/hellovoid/liquiddock/SecurityCenterVendorMaterialBridge.java \
        src/main/java/com/hellovoid/liquiddock/MiBlurBridge.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenterMaterialOwnershipStateTest.java
git commit -m "feat: add security center material ownership"
```

---

## Task 8: Wire exact Security Center hooks without heuristic discovery

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassHook.java`
- Extend: `src/main/java/com/hellovoid/liquiddock/SecurityCenterHookSpec.java`
- Add typed hook-installation policy tests where behavior is pure.

### Steps

- [ ] Use a minimal bootstrap in `com.miui.securitycenter:ui` to obtain a real target Context before installing version-specific mutation hooks. Prefer the validated `DockWindowManagerService` lifecycle as bootstrap. Resolve versionCode from `PackageManager` using that target Context, then select `SecurityCenterHookSpec.forVersionCode(...)`.
- [ ] If versionCode is not `40011320`, log one unsupported-build diagnostic and install no version-specific material/scene hooks.
- [ ] Resolve every v1 class/member from `SecurityCenterHookSpec` using the target ClassLoader and exact signatures/names. No method-shape scans and no fallback list of obfuscated names.
- [ ] Hook the validated TurboLayout creation/initialization boundary only to register lifecycle listeners; do not mutate material in the constructor.
- [ ] Before creating a coordinator for a TurboLayout, resolve its `ja.a` dock-window-type object using the exact v1 spec member/access path and require `ja.a.f() == true`. Reject types 1, 3, 5 and unknown/default types.
- [ ] On attach/pre-draw, resolve authoritative root, DockLayout and AppsLayout through exact spec getters. Acquire/reconcile one coordinator/session for that root.
- [ ] Hook exact `TurboLayout.d0()` as the semantic Global Dock <-> All Apps transition boundary. Before the original call, tell the coordinator to release custom visible authority for the old scene. After the original call, install a one-shot pre-draw listener; determine the target scene from the actual validated All Apps/Dock view visibility/attachment state, update geometry, advance generation, and request a fresh frame. Do not wait a fixed number of milliseconds.
- [ ] Treat `com.miui.dock.allapps.w.onAttachedToWindow()` only as an optional semantic corroboration/geometry notification if exact hooking is needed; it must never allocate a producer or session.
- [ ] Ensure `SecurityCenterGlassCoordinator` unregisters/relinquishes ownership if TurboLayout/root is replaced, type ceases to be Global Dock, runtime is disabled, or the root terminally detaches.
- [ ] Diagnostics may include versionCode, root SurfaceControl name, ViewRoot identity, surface sequence, root layer id, session id, scene, requested generation, consumed generation and failure stage. Never log app/sidebar user content.
- [ ] Run focused policy/state tests plus full unit tests and debug assembly.
- [ ] Commit:

```bash
git add src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassHook.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterHookSpec.java \
        src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java \
        src/test/java/com/hellovoid/liquiddock/SecurityCenter*Test.java
git commit -m "feat: hook security center sidebar glass"
```

---

## Task 9: Add process composition and Xposed scope without expanding MainHook

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Modify: `src/main/resources/META-INF/xposed/scope.list`
- Create or extend a legitimate static scope/config contract test under `src/test/java/com/hellovoid/liquiddock/`.
- Modify `RuntimeBehaviorTestPolicyContractTest.java` only if a brand-new static source/resource reader truly requires explicit allowlisting; prefer extending an existing audited static scope contract if available.

### Steps

- [ ] Add `com.miui.securitycenter` to `scope.list` while preserving Launcher and SystemUI entries.
- [ ] Store `ModuleLoadedParam.getProcessName()` in a `ModuleMain` instance field during `onModuleLoaded`. This is composition-root process identity, not feature mutable state.
- [ ] Add `SECURITY_CENTER_PACKAGE` routing in `onPackageReady`. Gate it through `SecurityCenterProcessPolicy.shouldInstall(packageName, loadedProcessName)`.
- [ ] In the Security Center branch, do **not** run `LegacyConfigMigration.migrateAtProcessStart()` or `ConfigMigration.migrateAtProcessStart()`. Those remain Launcher/settings migration ownership. Load only the side-effect-free current config snapshot/remote preferences required by this process.
- [ ] Initialize `SecurityCenterGlassRuntimeState` with the typed master/component values and install `SecurityCenterGlassHook` only for the `:ui` process.
- [ ] Leave existing Launcher and SystemUI composition unchanged; do not route Security Center through `MainHook.install()` and do not add Security Center fields to `MainHook`.
- [ ] Add a static scope contract test proving `scope.list` contains exactly the intended package entries. Because this is a static Xposed-scope contract, source/resource inspection is legitimate; do not use it to prove runtime sequencing.
- [ ] Run full unit tests and debug assembly.
- [ ] Commit:

```bash
git add src/main/java/com/hellovoid/liquiddock/ModuleMain.java \
        src/main/resources/META-INF/xposed/scope.list \
        src/test/java/com/hellovoid/liquiddock
git commit -m "feat: scope liquid glass to security center ui"
```

---

## Task 10: Documentation, release-shrinker check, and complete automated verification

**Files:**
- Modify: `ARCHITECTURE.md`
- Modify: `HOOKS.md`
- Modify: `FEATURES.md`
- Modify: `CHANGELOG.md` only under the repository's current unreleased/current-development section; do not invent a release version.
- Inspect: `src/main/keepRules/liquiddock.keep`
- Modify keep rules only if release-R8 verification proves a directly required reflective LiquidDock-owned entry needs stable naming. Security Center vendor classes themselves are resolved by target ClassLoader strings and are not module classes to keep.

### Steps

- [ ] Document a second injected runtime domain: `com.miui.securitycenter:ui`, v1 exact build `40011320`, Global Dock/All Apps only.
- [ ] Document one-root/one-producer Security Center lifecycle and that Dock/All Apps transitions advance scene generation without producer replacement.
- [ ] Document vendor capability semantics accurately: HyperOS 4 may restore MiGlass/MaterialToken or ordinary blur; HyperOS 3 has no soft-light-glass semantics and future support must restore ordinary blur only.
- [ ] Document Hook points and private boundaries, including exact `ja.a.f()` type-4 gate, TurboLayout transition/material recompute methods, and fail-closed unsupported-build behavior.
- [ ] Run complete verification from a clean feature branch checkout/worktree:

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

- [ ] Because this feature adds reflected vendor entry points and changes the PassBlur boundary, also run the release optimization path when the Android SDK is available:

```bash
./gradlew assembleRelease --no-daemon --stacktrace
```

- [ ] Inspect the resulting test/build output. Do not claim success from earlier CI or from compilation alone.
- [ ] Review the branch diff specifically for forbidden regressions:

```bash
git diff main...HEAD -- src/main src/test ARCHITECTURE.md HOOKS.md FEATURES.md CHANGELOG.md
```

Confirm no ScreenCapture/bitmap path was reintroduced, no fixed-delay Security Center workaround exists, no new Security Center mutable state entered `MainHook`, and runtime tests do not read production source to prove behavior.
- [ ] Commit documentation/verification-support changes:

```bash
git add ARCHITECTURE.md HOOKS.md FEATURES.md CHANGELOG.md src/main/keepRules/liquiddock.keep src/test
git commit -m "docs: document security center liquid glass"
```

---

## Task 11: Real-device acceptance and evidence-driven correction only

This task is required before declaring v1 support. It is not replaced by emulator/unit/CI success.

### Diagnostic filter

Use the feature's dedicated log tag, for example:

```bash
adb logcat -c
adb logcat | grep -E '\[DC\]\[(SecurityCenterGlass|RootPassBlur|PassBlur)\]'
```

### Validation matrix

- [ ] HOME behind sidebar -> open Global Dock: background corresponds live to HOME; no self-feedback; no transparent frame.
- [ ] A normal app behind sidebar -> open Global Dock: live app backdrop is sampled, not Launcher wallpaper.
- [ ] Video/dynamic app behind sidebar: source updates remain live and source-driven.
- [ ] Global Dock -> All Apps -> Global Dock, at least 10 repeated round trips: session id and native producer endpoint stay stable unless root/SurfaceControl authority actually changes.
- [ ] During each Dock/All Apps transition: vendor owns the transition until a current-scene OES-backed Prismal frame is rendered; no stretched stale Dock frame appears on All Apps.
- [ ] Dismiss sidebar -> reopen: stale previous backdrop is not revealed before the new fresh frame.
- [ ] Portrait <-> landscape: geometry remaps correctly; old-orientation frames stay hidden until fresh source content is authoritative.
- [ ] Runtime component switch ON -> OFF: custom layer disappears and Security Center reruns native material authority. On the validated OS4 build, observe MiGlass when system material conditions are active and ordinary blur when they are not.
- [ ] Runtime component switch OFF -> ON: no stale queued callback from the prior ownership epoch can reveal/claim the old session.
- [ ] Kill/restart `com.miui.securitycenter:ui`: old session releases; new process/root starts cleanly.
- [ ] Verify exact type gating: Game Toolbox, Video Toolbox and Conversation Toolbox remain untouched in v1.
- [ ] Verify unsupported Security Center build behavior if a second device/build is available: diagnostic only, no material mutation and no producer creation.
- [ ] Verify Launcher Liquid Glass, Recents return, Workstation recovery, wallpaper freshness, icon/widget/folder glass, and Dock glass still behave as before the shared-backend extraction.

### Evidence rule for corrections

- [ ] If stock material remains visible over the custom output, capture diagnostics showing which exact View still owns it before changing suppression. Do not respond by recursively clearing backgrounds or disabling TurboLayout pass-window state speculatively.
- [ ] If producer disappears during page switching, compare root View identity, ViewRoot identity, SurfaceControl name/layer id and surface sequence before adding recovery. Do not add fixed delays.
- [ ] If endpoint identity remains stable but frames stop, use `ZeroCopyProducerRecoveryState`/backend rollover with fresh-frame gating; never reveal the last stale texture as fallback.
- [ ] Any correction that changes architecture/ownership beyond this plan requires a small design amendment before implementation.

### Final verification after device-driven fixes

- [ ] Re-run:

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
./gradlew assembleRelease --no-daemon --stacktrace
```

- [ ] Re-run the affected device matrix rows plus one complete Dock -> All Apps -> Dock loop and one Launcher regression loop.
- [ ] Record the validated Security Center versionCode/versionName and observed root SurfaceControl name in the PR description/engineering notes, not as a new hardcoded correctness dependency unless the version spec requires it.

---

## Implementation order and review gates

1. Pure policy/build/type/capability definitions.
2. Typed config and Security Center-local runtime state.
3. Domain-explicit PassBlur bridge.
4. Shared root PassBlur backend extraction **with full Launcher regression gate**.
5. Security Center scene/material state machines.
6. Security Center renderer/coordinator.
7. Reversible vendor material bridge.
8. Exact version/type hooks.
9. Module process routing and Xposed scope.
10. Docs + complete debug/release verification.
11. Real-device validation and evidence-driven corrections.

Do not collapse Tasks 3-4 and 6-8 into one large change. The intended review boundary is that existing Launcher/Dock zero-copy behavior is proven unchanged before Security Center vendor hooks are allowed to depend on the shared backend.

## Definition of done

Implementation is complete only when automated verification and real-device evidence jointly show:

- exact v1 build/process/type gating;
- Global Dock and All Apps share one root-bound producer;
- no custom reveal before current-generation fresh OES-backed Prismal render;
- vendor material remains/restores authority on failure and disable;
- HyperOS 3 semantics remain blur-only and are not advertised as soft-light glass;
- no ScreenCapture/bitmap/fixed-delay fallback exists;
- no Security Center state is added to `MainHook`;
- existing Launcher zero-copy/freshness/recovery behavior has no regression;
- debug and release builds complete successfully;
- the real-device acceptance matrix passes on the declared Security Center build.
