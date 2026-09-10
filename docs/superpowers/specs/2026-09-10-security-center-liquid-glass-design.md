# Security Center Liquid Glass — Design Spec

Date: 2026-09-10
Status: Design approved for specification; implementation not yet authorized
Branch: `feature/security-center-liquid-glass`

## 1. Goal

Add Security Center sidebar liquid-glass support as a first-class LiquidDock feature without reintroducing screen capture, bitmap readback, duplicate PassBlur producers, or Launcher-specific lifecycle assumptions into the Security Center process.

The first implementation scope is deliberately limited to:

- package: `com.miui.securitycenter`;
- process: `com.miui.securitycenter:ui`;
- scenes: Global Dock and All Apps;
- currently analyzed Security Center build: versionCode `40011320`, versionName `13.2.0-260806.0.1.pad`.

Game Toolbox, Video Toolbox and Conversation Toolbox are follow-up scenes. They may reuse the same root-bound backend, but are not part of the first implementation unit.

## 2. Platform capability rule

HyperOS 3.x and HyperOS 4.x must not be treated as exposing the same native material capability.

### HyperOS 3.x

HyperOS 3.x does not provide the HyperOS 4 soft-light glass / MiGlass material model used by the analyzed Security Center build. The system/vendor fallback is ordinary background blur. LiquidDock must not label this as native soft-light glass, must not require `setMiGlass`, `setMiGlassBlurRadius`, `setMiViewMaterialType` or `MaterialToken`, and must not fabricate equivalent material parameters.

When LiquidDock does not own the Security Center material, HyperOS 3.x therefore falls back to the vendor background-blur path.

### HyperOS 4.x

When the validated Security Center build and runtime capability checks expose the native material path, the vendor owner may use the Security Center MiGlass / MaterialToken path. When that capability is unavailable, the vendor fallback remains ordinary background blur.

### LiquidDock custom material

LiquidDock Prismal rendering is a separate custom material domain. It may use the zero-copy PassBlur source where the PassBlur backend is available, independent of whether the vendor system itself exposes HyperOS 4 soft-light glass.

The implementation must therefore keep two concepts separate:

1. `VendorMaterialCapability`: what the OS/Security Center can restore when LiquidDock releases ownership;
2. `LiquidDockGlassCapability`: whether LiquidDock can bind the root PassBlur source and render Prismal safely.

No code may infer one from the other.

## 3. Confirmed Security Center topology

The analyzed Security Center implementation creates the sidebar panel as an independent WindowManager root and then switches Global Dock / All Apps content under the same hierarchy.

Conceptual structure:

```text
DockWindowManager
    -> WindowManager.addView(...)
       -> Sidebar root FrameLayout
          -> TurboLayout
             -> DockLayout
             -> AllAppsLayout
```

Global Dock and All Apps therefore share the same sidebar Window / ViewRoot in the analyzed build. A scene transition must not create a second native PassBlur producer.

The root Window, not an individual page, owns the backdrop session.

## 4. Architectural decision

Do not directly reuse `Miuix307PassBlurTextureView` inside Security Center, because it contains Launcher-, Workspace-, Workstation- and Dock-specific behavior. Do not copy that renderer into a new Security Center implementation either.

Instead, extract only the root-bound zero-copy responsibilities that are actually common:

```text
Launcher domain ------------------+
                                   |
                                   v
                         shared root PassBlur backend
                                   ^
                                   |
Security Center domain ------------+
```

The shared backend owns:

- ViewRoot / SurfaceControl identity;
- PassBlur producer binding;
- SurfaceTexture / OES source;
- producer rollover and freshness state;
- EGL lifecycle;
- OES normalization;
- Prismal rendering primitives;
- fresh-frame publication boundary.

The domain coordinator owns:

- vendor class/method knowledge;
- scene semantics;
- geometry and clipping policy;
- material ownership handoff;
- runtime enable/disable;
- vendor restore authority.

The shared backend must not know about `Launcher`, `Workspace`, `HotSeats`, `TurboLayout`, `AllAppsLayout`, Workstation state or Security Center obfuscated classes.

## 5. Proposed production components

Names are implementation guidance and may be adjusted during the plan if an existing abstraction already covers the responsibility cleanly.

### `SecurityCenterGlassHook`

Single vendor-hook installation boundary for `com.miui.securitycenter:ui`.

Responsibilities:

- version/capability gating;
- hook validated lifecycle entry points;
- discover the sidebar root and semantic scene events;
- translate vendor events into typed coordinator calls.

It must not own renderer state and must not read raw preferences.

### `SecurityCenterHookSpec`

Version-scoped description of validated vendor entry points.

For unsupported builds, installation is fail-closed: no mutation, no material suppression, no producer, and one diagnostic log.

No fuzzy method guessing, structural method scanning, or "try a sequence of probable obfuscated names" is allowed for correctness-critical hooks.

### `SecurityCenterGlassCoordinator`

Main-thread owner for one Security Center sidebar root session.

Responsibilities:

- bind/reconcile one session per stable sidebar root;
- consume semantic scene changes;
- coordinate geometry, fresh-frame requests and reveal;
- coordinate vendor material claim/release;
- destroy the session when the owning root is terminally detached or replaced.

It must use typed LiquidDock APIs only; no `HookUtil` reflection between LiquidDock-owned classes.

### `SecurityCenterGlassSceneState`

Android/Xposed-free production state machine consumed by the coordinator and directly exercised by unit tests.

Minimum semantic states:

```text
DETACHED
PREPARING_DOCK
DOCK
TRANSITIONING
PREPARING_ALL_APPS
ALL_APPS
```

The state object emits decisions such as:

- ensure session;
- invalidate scene generation;
- request fresh frame;
- hide custom output;
- claim vendor material;
- release vendor material;
- reveal custom output;
- shutdown session.

It must not contain Views, Android callbacks or vendor class names.

### `SecurityCenterGlassRuntimeState`

Owns the live Security Center component enable flag and teardown dispatch.

Disable order is mandatory:

```text
publish false
-> queued callbacks observe disabled state
-> release custom visual ownership
-> unbind/teardown custom session
-> return vendor material authority
```

### `SecurityCenterMaterialController`

Owns reversible handoff between Security Center vendor material and LiquidDock output.

Rules:

- never clear unknown backgrounds blindly;
- never invent MiGlass, blur, blend, shadow or token parameters;
- suppress the vendor material only after LiquidDock has rendered a fresh frame for the current scene generation;
- on release, call a validated vendor semantic restore/recompute boundary when available;
- if a reliable restore boundary is unavailable for a build, that build is unsupported.

Vendor restore policy is capability-dependent:

- HyperOS 3.x: vendor background blur;
- HyperOS 4.x with validated native material support: vendor soft-light glass / MiGlass path;
- HyperOS 4.x without that capability: vendor background blur.

### Shared root PassBlur backend

The existing `Miuix307PassBlurBridge` remains the hidden SurfaceControl boundary, but its input contract should be tightened so domain-specific assumptions are not embedded in the bridge.

Conceptual bind request:

```text
PassBlurBindRequest
    rootView
    producerSurface
    domain
    nativeScale
    exclusions
```

Security Center requirements:

- native PassBlur scale is `1.0`;
- the actual runtime root-surface name is excluded to prevent self-feedback;
- `DockAssistantView` may be used as diagnostics, not as the sole correctness authority;
- local quality scaling, if reused, occurs only after OES normalization and never changes the native backdrop geometry authority.

## 6. One-root / one-producer invariant

For the first supported Security Center root:

```text
one sidebar Window
-> one ViewRoot
-> one native PassBlur endpoint
-> one OES source
-> one Prismal session
```

Global Dock -> All Apps -> Global Dock changes scene state and geometry only.

A producer rollover is permitted only when the underlying endpoint authority changes or fails, for example:

- ViewRoot identity changes;
- SurfaceControl identity changes;
- surface sequence changes;
- root is destroyed and recreated;
- BufferQueue / endpoint enters a terminal failure state.

A successful bind or producer recreation never constitutes fresh content by itself.

## 7. Fresh-frame and scene-generation rules

Each semantic scene transition increments or advances a scene generation.

For Dock -> All Apps:

```text
transition begins
-> custom output becomes non-authoritative
-> vendor owns visible transition
-> All Apps geometry settles
-> request fresh source for new scene generation
-> consume a real OES frame
-> render Prismal for that generation
-> atomically claim material ownership
-> reveal custom output
```

All Apps -> Dock follows the same rule.

The first implementation intentionally does not stretch or reuse the previous scene's Prismal frame across vendor transition geometry. This avoids stale or spatially incorrect output and avoids guessing vendor animation timing.

A normal View invalidation is not freshness evidence.

## 8. Material ownership handoff

Initial attach sequence:

```text
vendor UI/material visible
-> create LiquidDock output hidden
-> bind PassBlur producer
-> wait for real OES frame
-> render current scene
-> verify runtime enabled + root identity + scene generation
-> suppress vendor material
-> reveal LiquidDock output
```

Failure before the final handoff leaves the Security Center vendor material untouched.

This is the fail-closed contract.

Queued callbacks such as `post`, `postOnAnimation`, layout listeners, bind retries and `OnFrameAvailable` handlers must re-check at execution time:

- runtime enabled;
- current root/session identity;
- scene generation;
- shutdown state.

A stale callback must never reclaim ownership after disable, detach or scene replacement.

## 9. Configuration

The first version adds one component switch under the existing Glass schema:

```text
liquid_security_center_glass
```

Recommended defaults:

```text
uiDefault       = false
runtimeFallback = false
exportDefault   = false
```

Required typed chain:

```text
ConfigSchema.Glass
-> ConfigReader
-> LiquidDockConfig.Glass immutable snapshot
-> SecurityCenterGlassRuntimeState
-> SecurityCenterGlassCoordinator
```

Hooks must not read raw preferences.

No Security Center-specific blur, refraction, highlight or Prismal tuning is added in v1. The existing Liquid Glass optical configuration remains the visual authority.

## 10. Package/process composition

Add `com.miui.securitycenter` to Xposed scope.

`ModuleMain` remains a composition root only. Security Center installation is accepted only when both package and process match the supported target:

```text
package == com.miui.securitycenter
process == com.miui.securitycenter:ui
```

Do not add Security Center mutable state to `MainHook`.

## 11. Reflection and private API boundary

Project rules apply unchanged:

- vendor/system private access only in hook/bridge boundaries;
- LiquidDock-owned modules communicate through typed/package-private Java APIs;
- optional vendor calls use explicit success results;
- correctness-critical vendor calls use required invocation and fail closed;
- no silent-null reflection facade;
- R8/keep impact must be considered for any reflected entry point.

## 12. Testing contract

Runtime behavior must be tested through production state/policy objects, never by source-text order assertions.

Minimum new/extended tests:

- `SecurityCenterGlassSceneStateTest`;
- `SecurityCenterGlassRuntimeTransitionPolicyTest`;
- `SecurityCenterMaterialOwnershipStateTest`;
- `SecurityCenterHookSpecTest`;
- `PassBlurBindRequestPolicyTest`;
- producer rollover/freshness tests proving rebind != fresh;
- configuration round-trip tests;
- Xposed scope static contract test.

Tests must verify at least:

- one root produces one session;
- Dock/All Apps transitions do not rebuild the producer;
- stale scene callbacks cannot reveal output;
- disable publishes false before teardown decisions;
- vendor material is never suppressed before a fresh rendered frame;
- endpoint failure keeps or restores vendor material;
- unsupported builds perform no mutation;
- HyperOS 3 vendor capability resolves to background blur, never soft-light glass;
- HyperOS 4 native material capability and ordinary blur fallback are distinct states.

Pre-merge build baseline:

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

## 13. Device validation matrix

Before declaring support, validate on a real supported build:

- HOME -> open Global Dock;
- app -> open Global Dock;
- Dock -> All Apps -> Dock;
- repeated Dock/All Apps round trips;
- dismiss -> reopen;
- portrait/landscape;
- dynamic/video content behind the sidebar;
- runtime OFF -> ON and ON -> OFF;
- kill/restart `com.miui.securitycenter:ui`;
- unsupported build fail-closed;
- no self-feedback;
- no transparent flash before fresh-frame handoff;
- no Launcher Liquid Glass regression.

Diagnostics should capture root surface name, ViewRoot identity, surface sequence, root layer id, session id, scene generation and fresh-frame generation without logging user content.

## 14. Explicit non-goals for v1

The first implementation does not include:

- Game Toolbox;
- Video Toolbox;
- Conversation Toolbox;
- ScreenCapture or bitmap fallback;
- CPU readback;
- per-page native producer allocation;
- Security Center-specific Prismal parameter sets;
- fuzzy support for unknown Security Center builds;
- recursive background clearing;
- replacement of vendor transition animation;
- Security Center state inside `MainHook`;
- fabricated HyperOS 3 soft-light glass support.

## 15. Follow-up expansion rule

After Global Dock / All Apps validates the root-surface lifecycle, additional toolbox scenes may be added as semantic scene adapters only.

They must reuse the same domain/root-session architecture. New toolbox support must not introduce a second PassBlur backend or page-owned producer.

## 16. Acceptance criteria

The v1 feature is acceptable only when all of the following hold:

1. supported Security Center build is explicitly version-gated;
2. Global Dock and All Apps use one root-bound producer across transitions;
3. custom output is never revealed before a fresh OES-backed Prismal render;
4. vendor material remains authoritative on startup failure, unsupported build and terminal backend failure;
5. runtime disable releases ownership without stale callback re-entry;
6. HyperOS 3 native fallback is background blur, not claimed soft-light glass;
7. HyperOS 4 native soft-light glass is treated as a separate vendor capability;
8. no ScreenCapture/bitmap path is reintroduced;
9. Launcher behavior and existing tests remain unchanged outside the shared-backend extraction required by this feature;
10. unit tests and debug assembly pass, followed by the real-device matrix above.
