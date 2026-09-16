# Third-Party Glass Profiles Design

## Goal

Extend LiquidDock's third-party glass support so MIUI Searchbox has its own blur/tint controls, while future third-party adapters can consume a common glass profile without requiring a GUI entry.

## Constraints

- Do not use JADX/R8-obfuscated class or method names as runtime hook anchors.
- Do not implement arbitrary configuration-driven reflection or class/method hooking.
- Every supported third-party app must still have a code-registered adapter with stable semantic anchors and explicit lifecycle/stock-material authority.
- Hidden third-party profiles are disabled by default and must not appear in the current GUI.
- Missing profile values inherit the global `LiquidDockConfig.Glass` material values.
- Preserve the existing zero-copy `RootPassBlurBackend -> Prismal` path and producer-authority model.
- Preserve Searchbox exact window-space crop and visible-lifecycle fresh-frame barrier.
- Searchbox entrance animation must not continuously chase moving producer frames; one authoritative backdrop is latched per visible cycle.

## Architecture

### 1. Shared appearance model

Add `ThirdPartyGlassAppearance` as a small immutable runtime value containing:

- `enabled`
- `hasAppearanceOverride`
- `blur`
- `tintR`, `tintG`, `tintB`, `tintAlpha`
- `captureScalePercent`
- `renderFps`
- `cornerRadiusOverrideDp` (`< 0` means adapter default)
- `freshOnResume`
- per-component Prismal highlight toggles

`ThirdPartyGlassProfiles.resolve(reader, profileId, baseGlass, defaults)` reads a namespaced profile and falls back to global glass values when individual keys are absent.

Names use the stable prefix:

`third_party_glass.<profileId>.<field>`

Supported fields are bounded by `ThirdPartyGlassConfigCodec`: enable state, blur/tint, capture scale, render FPS, corner radius, fresh-on-resume and known Prismal highlight fields. The parser accepts only a validated profile ID (`[a-z0-9_.-]+`). Configuration never supplies runtime class names, resource selectors or hook methods.

### 2. Adapter registry

`ThirdPartyGlassAdapterRegistry` contains code-owned registrations. A registration contains a stable profile ID, package name, domain identifier, installer callback and defaults. The first registrations are:

- `gboard.floating` -> `com.google.android.inputmethod.latin`
- `miui.searchbox` -> `com.android.quicksearchbox`

Existing adapter implementations remain responsible for target discovery, stock-material suppression, freshness, teardown and producer authority. The registry does not generalize those semantics into reflection.

### 3. Searchbox independent appearance

Searchbox keeps explicit GUI-supported keys:

- `liquid_miui_searchbox_glass`
- `liquid_miui_searchbox_blur`
- `liquid_miui_searchbox_tint_r`
- `liquid_miui_searchbox_tint_g`
- `liquid_miui_searchbox_tint_b`
- `liquid_miui_searchbox_tint_alpha`

`MiuiSearchboxGlassPreferences.resolve()` mirrors Gboard behavior: if none of the public appearance keys are present, Searchbox inherits global Prismal blur/tint. Hidden profile controls remain available for capture quality, corner radius, lifecycle and highlight selection.

### 4. Searchbox one-shot snapshot presentation

Searchbox uses the same root-owned PassBlur source but does not continuously refresh its backdrop during the upward entrance animation.

Each `refreshVisible()` cycle:

1. invalidates the previous presentation state;
2. arms `MiuiSearchboxSnapshotState`;
3. requests one fresh authoritative source generation;
4. accepts only the first matching `RootPassBlurFrame`;
5. prepares and renders Prismal from that frame;
6. immediately pauses Searchbox producer updates;
7. retains that rendered backdrop while the Searchbox window performs its entrance animation.

The next visible cycle re-arms the gate and resumes the producer only long enough to obtain a new fresh frame. This is frame-driven and uses no fixed delay or animation-duration guess.

`MiuiSearchboxPassBlurContinuousAuthority` continues to own the producer Surface and scale, but also tracks LiquidDock's desired update state. After snapshot latch, vendor `setUpdateTextureFlag(true, ...)` attempts are coerced back to the code-owned paused state; on the next capture cycle the desired state is set back to enabled before fresh capture.

### 5. Hidden configuration compatibility

Generic hidden profile fields are imported/exported through `ThirdPartyGlassConfigCodec`, which accepts only the known glass field allowlist and validated profile IDs. Unknown fields such as `hook_class` or `method` are dropped. Future profile values can therefore be carried in a normal configuration file, but a profile does not activate anything unless a matching code-owned adapter exists in the registry.

### 6. GUI boundary

Only existing explicit adapter pages are rendered:

- Gboard
- MIUI Searchbox

The generic registry does not enumerate adapters into settings. Future adapters can be registered and enabled through configured values without automatically receiving a GUI page.

## Testing

- Unit tests for profile ID validation, inheritance, clamping, explicit overrides and hidden defaults.
- Searchbox tests proving independent blur/tint overrides while absent values inherit global glass.
- Registry contract tests proving only code-registered package adapters are dispatchable and configuration cannot provide hook class/method names.
- Config codec round-trip tests for the bounded dynamic profile namespace.
- `MiuiSearchboxSnapshotStateTest` proves only one fresh frame is accepted per visible capture cycle and that a new cycle re-arms capture.
- Static Searchbox contract verifies the Session latches one frame and pauses producer updates through Searchbox authority.
- Existing Gboard/Searchbox geometry, freshness, scope and runtime policy tests must remain green.
- Final verification: `./gradlew testDebugUnitTest assembleDebug --stacktrace` in GitHub Actions.
