# Third-Party Glass Profiles Design

## Goal

Extend LiquidDock's third-party glass support so MIUI Searchbox has its own blur/tint controls, while future third-party adapters can consume a common glass profile without requiring a GUI entry.

## Constraints

- Do not use JADX/R8-obfuscated class or method names as runtime hook anchors.
- Do not implement arbitrary configuration-driven reflection or class/method hooking.
- Every supported third-party app must still have a code-registered adapter with stable semantic anchors and explicit lifecycle/stock-material authority.
- Generic hidden profiles never create adapters by themselves and do not appear in the current GUI.
- Missing profile values inherit the global `LiquidDockConfig.Glass` values.
- Preserve the existing zero-copy `RootPassBlurBackend -> Prismal` path and producer-authority model.
- Preserve Searchbox exact window-space crop and visible-lifecycle fresh-frame barrier.

## Architecture

### 1. Shared appearance model

`ThirdPartyGlassAppearance` is the immutable runtime value shared by third-party adapters. It contains:

- `enabled`
- `hasAppearanceOverride`
- `blur`
- `tintR`, `tintG`, `tintB`, `tintAlpha`
- `captureScalePercent`
- `renderFps`
- `cornerRadiusOverrideDp` (`< 0` means adapter default)
- `highlightProfile`
- `freshOnResume`

`ThirdPartyGlassProfiles.resolve(reader, profileId, baseGlass, defaults)` reads a namespaced profile and falls back to global glass values when individual keys are absent.

Names use the stable prefix:

`third_party_glass.<profileId>.<field>`

The runtime profile supports:

- `enabled`
- `blur`
- `tint_r`, `tint_g`, `tint_b`, `tint_alpha`
- `capture_scale_percent`
- `render_fps`
- `corner_radius_dp`
- `fresh_on_resume`
- `highlight_sky_haze`
- `highlight_specular`
- `highlight_lit_rim`
- `highlight_opposite_rim`
- `highlight_corner_rim`
- `highlight_face_sheen`
- `highlight_plain`
- `highlight_caustics`
- `highlight_press_glow`

Profile IDs are validated with `[a-z0-9_.-]+`. Configuration never contains runtime class names, method names, resource selectors, or arbitrary hook declarations.

### 2. Adapter registry

`ThirdPartyGlassAdapterRegistry` owns the supported adapter registrations. Each registration contains only:

- stable profile ID
- package name
- code-owned installer callback

The registry is package-indexed and used by `ModuleMain` for third-party adapter dispatch. Initial registrations are:

- `gboard.floating` -> `com.google.android.inputmethod.latin`
- `miui.searchbox` -> `com.android.quicksearchbox`

Existing adapter implementations remain responsible for target discovery, stock-material suppression, freshness, teardown, and producer authority. The registry does not generalize those semantics into reflection.

A configuration profile for an unregistered package/profile may be stored and round-tripped, but it cannot cause any hook installation. It becomes effective only after code adds a matching registered adapter.

### 3. Searchbox independent appearance

Searchbox keeps stable public GUI keys:

- `liquid_miui_searchbox_glass`
- `liquid_miui_searchbox_blur`
- `liquid_miui_searchbox_tint_r`
- `liquid_miui_searchbox_tint_g`
- `liquid_miui_searchbox_tint_b`
- `liquid_miui_searchbox_tint_alpha`

`MiuiSearchboxGlassPreferences.resolve()` layers values in this order:

1. global glass defaults;
2. hidden `third_party_glass.miui.searchbox.*` profile;
3. visible Searchbox GUI keys for enable/blur/tint.

The Searchbox settings page exposes enable, blur, RGBA tint, inheritance reset, and the existing restart-search action. Capture quality, corner override, highlight controls, and lifecycle policy remain hidden configuration controls.

The Searchbox session applies the resolved appearance to a copy of global Prismal parameters without changing the validated window-root capture geometry or freshness barrier.

### 4. Hidden configuration compatibility

Normal LiquidDock JSON import/export must preserve hidden profiles, but only through the restricted `ThirdPartyGlassConfigCodec` namespace parser.

The codec:

- accepts any syntactically valid future profile ID so configuration can be prepared before an adapter receives GUI support;
- accepts only the allowlisted glass fields listed above;
- clamps tint, capture scale, FPS, blur, and corner-radius values to existing runtime policy bounds;
- rejects unknown fields such as `hook_class`, `method`, arbitrary selectors, and unrelated dynamic keys;
- never performs adapter registration or reflection.

This keeps hidden configuration round-trippable without expanding the attack or compatibility surface into arbitrary hook injection.

### 5. Gboard compatibility

Existing public Gboard keys remain unchanged. `GboardGlassPreferences.resolveShared()` maps both the existing public keys and hidden `third_party_glass.gboard.floating.*` values into `ThirdPartyGlassAppearance`.

The floating Gboard session directly consumes the shared blur/tint, PassBlur quality, and highlight profile while its existing structural target discovery and lifecycle remain unchanged.

### 6. GUI boundary

Only explicit adapter pages are rendered:

- Gboard
- MIUI Searchbox

The registry and generic profiles are not enumerated into settings. Future code-registered adapters can be configured through hidden profile values without receiving a GUI page.

## Testing

- Unit tests for profile ID validation, inheritance, policy clamping, explicit overrides, hidden highlights, and lifecycle defaults.
- Searchbox tests proving independent blur/tint overrides while absent values inherit global glass.
- Registry contract tests proving only code-registered package adapters are dispatchable and configuration cannot provide hook class/method names.
- Dynamic config-codec tests proving allowlisted hidden fields round-trip and arbitrary fields are dropped.
- Existing Gboard/Searchbox geometry, freshness, scope, producer-authority, and runtime-policy tests must remain green.
- Final verification: `./gradlew testDebugUnitTest assembleDebug --stacktrace` in GitHub Actions.
