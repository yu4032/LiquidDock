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

`ThirdPartyGlassProfiles.resolve(reader, profileId, baseGlass, defaults)` reads a namespaced profile and falls back to global glass values when individual keys are absent.

Names use the stable prefix:

`third_party_glass.<profileId>.<field>`

Supported fields are:

`enabled`, `blur`, `tint_r`, `tint_g`, `tint_b`, `tint_alpha`, `capture_scale_percent`, `render_fps`, `corner_radius_dp`, `fresh_on_resume`.

The parser accepts only a validated profile ID (`[a-z0-9_.-]+`) supplied by registered code. Configuration does not supply runtime class names or hook methods.

### 2. Adapter registry

Add `ThirdPartyGlassAdapterRegistry` with code-owned registrations. A registration contains:

- stable profile ID
- package name
- domain identifier
- adapter installer callback
- defaults for enable state, fresh-on-resume, and corner-radius override

The registry is package-indexed and used by `ModuleMain` for third-party adapter dispatch. The first registrations are:

- `gboard.floating` -> `com.google.android.inputmethod.latin`
- `miui.searchbox` -> `com.android.quicksearchbox`

Existing adapter implementations remain responsible for target discovery, stock-material suppression, freshness, teardown, and producer authority. The registry does not generalize those semantics into reflection.

### 3. Searchbox independent appearance

Add schema-backed Searchbox keys for GUI-supported appearance:

- `liquid_miui_searchbox_glass`
- `liquid_miui_searchbox_blur`
- `liquid_miui_searchbox_tint_r`
- `liquid_miui_searchbox_tint_g`
- `liquid_miui_searchbox_tint_b`
- `liquid_miui_searchbox_tint_alpha`

`MiuiSearchboxGlassPreferences.resolve()` mirrors Gboard behavior: if none of the appearance keys are present, Searchbox inherits global Prismal blur/tint. The Searchbox settings page exposes blur and RGBA tint controls; capture quality and lifecycle remain hidden profile-level controls.

The Searchbox session applies the resolved appearance to a copy of global Prismal parameters before rendering.

### 4. Hidden configuration compatibility

Because current import/export is schema-driven, hidden generic profile keys must be represented by a bounded set of schema keys for registered adapters rather than accepting arbitrary unknown JSON keys. `ThirdPartyGlassProfiles` therefore defines profile namespaces for registered adapters and their hidden fields, with `ConfigKey.ExportMode.IF_PRESENT` for non-GUI controls. This keeps configuration round-trippable while avoiding arbitrary runtime hook declarations.

Searchbox and Gboard may keep their existing public key names for backward compatibility; profile resolution can map those legacy/public keys into the shared runtime appearance model.

### 5. GUI boundary

Only existing explicit adapter pages are rendered:

- Gboard
- MIUI Searchbox

The generic registry does not enumerate adapters into settings. Future adapters can be registered and enabled through imported/configured values without receiving a GUI page.

## Testing

- Unit tests for profile ID validation, inheritance, clamping, explicit overrides, and hidden defaults.
- Searchbox tests proving independent blur/tint overrides while absent values inherit global glass.
- Registry contract tests proving only code-registered package adapters are dispatchable and configuration cannot provide hook class/method names.
- Config schema/codec round-trip tests for hidden IF_PRESENT fields.
- Existing Gboard/Searchbox geometry, freshness, scope, and runtime policy tests must remain green.
- Final verification: `./gradlew testDebugUnitTest assembleDebug --stacktrace` in GitHub Actions.