# Third-Party Glass Profiles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add independent MIUI Searchbox blur/tint controls and a reusable hidden configuration/profile contract for code-registered third-party glass adapters.

**Architecture:** Keep adapter-specific target discovery and lifecycle code, but move appearance/quality configuration into a common `ThirdPartyGlassAppearance` + `ThirdPartyGlassProfiles` model. Dispatch remains code-registered through `ThirdPartyGlassAdapterRegistry`; configuration can tune or enable registered adapters but cannot inject arbitrary hook classes or method names.

**Tech Stack:** Java/Kotlin Android module, LSPosed hook runtime, `RootPassBlurBackend`, Prismal renderer, schema-driven configuration, JUnit4, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-15-third-party-glass-profiles-design.md`

## Global Constraints

- No JADX/R8-obfuscated hook anchors.
- No arbitrary configuration-driven reflection or hook declaration.
- Hidden third-party profiles are disabled by default and do not appear in the GUI.
- Missing appearance values inherit `LiquidDockConfig.Glass`.
- Preserve zero-copy PassBlur/Prismal rendering and current Searchbox geometry/freshness behavior.

---

### Task 1: Shared third-party profile value model

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/ThirdPartyGlassAppearance.java`
- Create: `src/main/java/com/hellovoid/liquiddock/ThirdPartyGlassProfiles.java`
- Test: `src/test/java/com/hellovoid/liquiddock/ThirdPartyGlassProfilesTest.java`

**Interfaces:**
- Produces: `ThirdPartyGlassAppearance ThirdPartyGlassProfiles.resolve(ConfigReader, String, LiquidDockConfig.Glass, Defaults)`
- Produces: validated code-owned profile IDs and namespaced preference-key helpers.

- [ ] **Step 1: Write failing tests** for global inheritance, explicit blur/tint override, channel/range clamping, hidden quality controls, default-disabled profiles, and rejection of invalid profile IDs.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*ThirdPartyGlassProfilesTest' --stacktrace` and verify failures are caused by missing production types.
- [ ] **Step 3: Implement** immutable appearance/default types plus key construction and `ConfigReader.has(...)`-based inheritance.
- [ ] **Step 4: Re-run** the focused test and verify PASS.
- [ ] **Step 5: Commit** `feat: add third-party glass profile model`.

### Task 2: Schema-backed registered hidden controls

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Test: `src/test/java/com/hellovoid/liquiddock/config/ConfigSchemaTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java`

**Interfaces:**
- Produces schema keys for registered hidden controls with `ConfigKey.ExportMode.IF_PRESENT`.
- Searchbox public appearance keys remain backward-compatible stable names.

- [ ] **Step 1: Add failing schema/codec tests** asserting Searchbox blur/tint key names and hidden profile quality/lifecycle fields round-trip only when present.
- [ ] **Step 2: Run** focused config tests and verify RED.
- [ ] **Step 3: Add** `ConfigSchema.Searchbox` and bounded registered-profile hidden keys; do not add support for arbitrary unknown config keys.
- [ ] **Step 4: Re-run** focused config tests and verify PASS.
- [ ] **Step 5: Commit** `feat: register third-party glass config keys`.

### Task 3: Searchbox independent appearance runtime

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuiSearchboxGlassPreferences.java`
- Create: `src/main/java/com/hellovoid/liquiddock/ThirdPartyPrismalParams.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuiSearchboxGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuiSearchboxGlassSession.java`
- Test: `src/test/java/com/hellovoid/liquiddock/MiuiSearchboxGlassPreferencesTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/MiuiSearchboxGlassContractTest.java`

**Interfaces:**
- Produces: `MiuiSearchboxGlassPreferences.resolve(...) -> ThirdPartyGlassAppearance`.
- Produces: `ThirdPartyPrismalParams.apply(PrismalParams base, ThirdPartyGlassAppearance appearance)` preserving all non-blur/tint Prismal fields.

- [ ] **Step 1: Write failing tests** proving Searchbox inherits global blur/tint when absent and overrides only explicitly configured appearance values.
- [ ] **Step 2: Run** focused Searchbox preference/contract tests and verify RED.
- [ ] **Step 3: Implement** preference resolution and Prismal override application; pass resolved appearance into the existing Searchbox session without changing geometry/freshness logic.
- [ ] **Step 4: Re-run** focused tests and verify PASS.
- [ ] **Step 5: Commit** `feat: add independent Searchbox glass appearance`.

### Task 4: Searchbox GUI controls

**Files:**
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/SearchboxSettingsActivity.kt`
- Modify: `src/main/res/values/gboard_settings_strings.xml`
- Test: `src/test/java/com/hellovoid/liquiddock/MiuiSearchboxGlassContractTest.java`

**Interfaces:**
- Exposes only Searchbox `enabled`, blur, tint RGB, tint alpha, and restart action in the existing third-level Searchbox page.
- Does not expose generic registry/hidden quality fields.

- [ ] **Step 1: Extend the static contract test** to require the Searchbox page to reference the new preference keys and retain restart-search behavior.
- [ ] **Step 2: Run** the contract test and verify RED.
- [ ] **Step 3: Add** controls following the existing Gboard numeric/tint preference patterns and existing preference persistence helpers.
- [ ] **Step 4: Re-run** the contract test and compile Kotlin via `./gradlew compileDebugKotlin`.
- [ ] **Step 5: Commit** `feat: expose Searchbox blur and tint controls`.

### Task 5: Code-owned third-party adapter registry

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/ThirdPartyGlassAdapterRegistry.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Test: `src/test/java/com/hellovoid/liquiddock/ThirdPartyGlassAdapterRegistryTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/MiuiSearchboxGlassContractTest.java`

**Interfaces:**
- Produces registry lookup by package name.
- Registrations contain only code-owned `profileId`, package, adapter installer, and profile defaults.
- Initial entries: `gboard.floating`, `miui.searchbox`.

- [ ] **Step 1: Write failing tests** proving only registered packages dispatch, profile IDs are stable, and registry data contains no configurable class/method-name hook surface.
- [ ] **Step 2: Run** focused registry tests and verify RED.
- [ ] **Step 3: Implement** the minimal registry and migrate `ModuleMain` third-party package dispatch to it while preserving existing per-adapter install functions.
- [ ] **Step 4: Re-run** registry and existing Gboard/Searchbox contract tests and verify PASS.
- [ ] **Step 5: Commit** `refactor: register third-party glass adapters`.

### Task 6: Migrate Gboard appearance onto shared runtime profile model

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/GboardGlassPreferences.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/GboardPrismalParams.java`
- Test: existing Gboard preference/Prismal tests plus a new shared-profile compatibility assertion.

**Interfaces:**
- Existing Gboard preference key names remain unchanged.
- Gboard resolves to `ThirdPartyGlassAppearance`; existing behavior is preserved byte-for-byte at the preference contract level.

- [ ] **Step 1: Add failing compatibility tests** that compare legacy Gboard resolved values to the new shared appearance model.
- [ ] **Step 2: Run** focused Gboard tests and verify RED.
- [ ] **Step 3: Adapt** Gboard preferences/Prismal helper to the shared model without touching floating-target discovery, stock visual authority, geometry, or coordinator lifecycle.
- [ ] **Step 4: Re-run** all Gboard tests and verify PASS.
- [ ] **Step 5: Commit** `refactor: share third-party glass appearance with Gboard`.

### Task 7: Runtime policy and full verification

**Files:**
- Modify only if required by policy: `src/test/java/com/hellovoid/liquiddock/RuntimeBehaviorTestPolicyContractTest.java`
- Test: complete unit/debug build suite.

**Interfaces:**
- No new runtime behavior tests may bypass project policy.

- [ ] **Step 1: Run** `./gradlew testDebugUnitTest assembleDebug --stacktrace`.
- [ ] **Step 2: Fix only demonstrated policy/compile/test regressions**; do not broaden scope.
- [ ] **Step 3: Re-run** the full command until green.
- [ ] **Step 4: Push final branch state and open/update a draft PR against `main` with the exact configuration contract and verification results.