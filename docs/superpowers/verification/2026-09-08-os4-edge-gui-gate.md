# OS4 Edge GUI + Highlight Gate Verification — 2026-09-08

## Scope

Bounded follow-up on `port/os4-original-background-edge` after device feedback on CI #4391:

- background-driven OS4 edge direction is visually valid;
- edge remains too thin;
- OS4 directional highlight still responds to light X/Y when all existing highlight components are disabled;
- expose all seven OS4 edge controls in the existing Liquid Glass numeric GUI.

Frozen: no Bloom FBO, no PassBlur/OES/freshness/Launcher lifecycle ownership changes, no alpha expansion.

## Approved defaults/ranges

| Control | Key | Default | Range | Runtime unit |
| --- | --- | ---: | ---: | --- |
| Edge width | `liquid_os4_edge_width_px` | 20 | 4–64 | logical px |
| Reflection offset | `liquid_os4_reflect_offset_px` | 10 | 0–40 | logical px |
| Reflection strength | `liquid_os4_reflection_strength` | 28 | 0–200 | value / 100 |
| Dark lift | `liquid_os4_reflection_lighten` | 16 | 0–100 | value / 100 |
| Directional angle range | `liquid_os4_directional_angle_range` | 52 | 5–150 | value / 100 relative to PI |
| Main directional intensity | `liquid_os4_directional_intensity` | 42 | 0–200 | value / 100 |
| Opposite directional intensity | `liquid_os4_directional_opposite_intensity` | 14 | 0–200 | value / 100 |

## TDD evidence

### RED

API101 #4393 / run `34212305516` failed at test compilation because the seven approved `ConfigSchema.Glass.OS4_*` keys did not yet exist. This was the intended RED state.

### One-shot implementation verification

Run `34213234978` applied the exact multi-file patch and then verified, before committing production changes:

- `git diff --check` — PASS
- root-project `Os4EdgeConfigRuntimeTest` — PASS
- `:prismal` `PrismalOs4HighlightGateContractTest` — PASS
- full `./gradlew testDebugUnitTest assembleDebug --stacktrace` — PASS
- production commit + temporary workflow/script self-delete — PASS

Production commit: `00493e01d0dc6c791a3678afe86095028902f85c`.

## Runtime semantics

- Existing `Lit Rim` component gate controls OS4 main directional lighting.
- Existing `Opposite Rim` component gate controls OS4 opposite directional lighting.
- OS4 background reflection remains active when those lighting components are disabled.
- All seven OS4 values travel through `ConfigSchema -> LiquidDockConfig -> Miuix307PrismalMaterial.Params -> Miuix307PrismalAdapter -> PrismalParams`.
- CPU sampling guard accounts for the configured OS4 edge width/effective thickness and reflection offset so high GUI values do not silently exceed the existing scene sampling safety margin.

## Final gate

A normal-user docs-only descendant of the production commit must pass the repository API101 workflow before a device APK is presented as CI-verified. Target-device visual/GPU behavior remains a separate gate.
