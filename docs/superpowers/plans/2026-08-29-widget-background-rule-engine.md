# Widget Background Rule Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Java hard-coded MAML widget background ownership with a bundled XML rule engine behind one widget-background controller, while preserving conservative restore behavior and existing RemoteViews suppression.

**Architecture:** `LauncherWidgetBackgroundController` becomes the semantic entry point. `LauncherGlassVendorMaterialSuppressor` keeps only generic vendor/RemoteViews material operations, while `LauncherMamlBackgroundRuleExecutor` consumes rules from a pure-Java `WidgetBackgroundRuleEngine` loaded from `src/main/resources/widget_background_rules.xml`. Exact product rules outrank package/span fallbacks; missing targets never cause partial mutation.

**Tech Stack:** Java 17, Android/HyperOS Launcher hooks, libxposed API 101, JUnit 4, Java XML DOM parser, Android Gradle Plugin resource packaging.

**Spec:** `docs/superpowers/specs/2026-08-29-widget-background-rule-engine-design.md`

## Global Constraints

- Work only on `feat/widget-dark-content-adaptation`; do not merge PR #68.
- Keep zero-copy glass and existing RemoteViews direct-root ownership semantics unchanged.
- Production Java must contain no Weather product IDs or `com.miui.weather2` package constant.
- Unknown widgets must never trigger guessed destructive actions.
- A multi-element rule is atomic: resolve every target before hiding any target.
- `release(host)` restores exact original Drawable / MAML `mShow` values.
- Built-in rules are immutable at process start; user customization is TODO-only for this implementation.
- Final gate is `testDebugUnitTest` + `assembleDebug` + APK resource verification.

---

### Task 1: Rule Model, Parser, and Built-in XML

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/WidgetBackgroundIdentity.java`
- Create: `src/main/java/com/hellovoid/liquiddock/WidgetBackgroundRule.java`
- Create: `src/main/java/com/hellovoid/liquiddock/WidgetBackgroundRuleEngine.java`
- Create: `src/main/resources/widget_background_rules.xml`
- Create: `src/test/java/com/hellovoid/liquiddock/WidgetBackgroundRuleEngineTest.java`

**Interfaces:**
- Produces: `WidgetBackgroundRuleEngine.loadBundled()` and `WidgetBackgroundRuleEngine.parse(InputStream)`.
- Produces: `WidgetBackgroundRuleEngine.match(WidgetBackgroundIdentity)` returning the highest-specificity matching rule or null.
- Produces: `WidgetBackgroundRule.elementNames()` as an immutable ordered list.

- [ ] **Step 1: Write failing parser/matcher tests**

Test exact product precedence, package fallback, all optional span attributes, multiple `<hide-element>` actions, malformed input safety, and unknown identity returning null.

- [ ] **Step 2: Run `./gradlew testDebugUnitTest --stacktrace`**

Expected: FAIL because model/parser classes and bundled XML do not exist.

- [ ] **Step 3: Implement minimal pure-Java model/parser**

Use `DocumentBuilderFactory` with external entity/doctype processing disabled. Match only rule attributes defined by the schema. Specificity must make `productId` dominate `appPackage`, which dominates span-only constraints; document order breaks ties.

- [ ] **Step 4: Add built-in XML**

Rules:
- `b8006e83-c497-4642-9815-f674b82842b0` -> `skyColor`.
- `c989887f-fa0d-4963-8c57-896c03e37efc` -> `background`.
- `bc0f0cd2-43fd-4323-8061-55a8bc997e1f` -> `background`.
- `appPackage=com.miui.weather2` -> diagnostic-only, zero hide actions.

- [ ] **Step 5: Run unit tests**

Expected: new rule-engine tests PASS.

### Task 2: Generic MAML Rule Executor

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherMamlBackgroundRuleExecutor.java`
- Delete: `src/main/java/com/hellovoid/liquiddock/LauncherMamlBackgroundSuppressor.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/LauncherMamlWeatherBackgroundContractTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/LauncherMamlWeatherSizeOwnerContractTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherMamlBackgroundRuleExecutorContractTest.java`

**Interfaces:**
- Consumes: `WidgetBackgroundRuleEngine.match(identity)`.
- Produces: `LauncherMamlBackgroundRuleExecutor.claim(View host)`.
- Produces: `LauncherMamlBackgroundRuleExecutor.claimLoadedRoot(View host, Object root)`.
- Produces: `LauncherMamlBackgroundRuleExecutor.release(View host)`.

- [ ] **Step 1: Rewrite contracts to forbid Weather constants in production Java**

The tests must require rule IDs/product IDs only in `widget_background_rules.xml`, not Java.

- [ ] **Step 2: Add failing atomic multi-element/restore contracts**

Require a claim to retain a list of original `mShow` states. Require all targets to be resolved before the first `show(false)`. Require missing targets and diagnostic-only rules to leave the tree untouched and emit one registry dump.

- [ ] **Step 3: Run unit tests**

Expected: FAIL against the existing Weather-specific suppressor.

- [ ] **Step 4: Implement generic executor**

Read identity fields from `MaMlWidgetInfo`, match a rule, resolve all elements, then atomically hide and store all original states. On root/claim replacement, restore old states first. Keep the existing sorted `mElements` dump utility, generalized to any matched/diagnostic rule.

- [ ] **Step 5: Delete Weather-specific suppressor and run tests**

Expected: generic executor contracts PASS and no Weather product IDs remain in Java.

### Task 3: One Widget Background Controller and Hook Migration

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherWidgetBackgroundController.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassVendorMaterialSuppressor.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherMamlRootLoadedHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java`
- Modify: existing widget ownership contract tests.

**Interfaces:**
- Produces: `LauncherWidgetBackgroundController.claim(View host)`.
- Produces: `LauncherWidgetBackgroundController.claimLoadedMamlRoot(View host, Object root)`.
- Produces: `LauncherWidgetBackgroundController.release(View host)`.
- Keeps: `LauncherGlassVendorMaterialSuppressor.claimFolderMaterial(View material)` for non-widget folder material.

- [ ] **Step 1: Write failing facade contracts**

Require all widget call sites to use the controller. Require `LauncherGlassVendorMaterialSuppressor` to contain no `LauncherMaml*` calls and no widget-specific identity strings.

- [ ] **Step 2: Run unit tests**

Expected: FAIL because call sites still invoke the low-level vendor/MAML helpers directly.

- [ ] **Step 3: Implement controller and narrow vendor helper**

Controller order on claim: generic vendor material -> MAML rule executor. Release order: MAML restore -> generic material restore. Loaded-root callback invokes only the MAML rule executor because generic vendor ownership was already claimed through the widget lifecycle.

- [ ] **Step 4: Migrate hook call sites**

Replace `LauncherGlassVendorMaterialSuppressor.claimWidget/releaseWidget` in widget paths with controller calls. Change `LauncherMamlRootLoadedHook` to `claimLoadedMamlRoot`. Leave folder material call sites unchanged.

- [ ] **Step 5: Run full unit tests**

Expected: all existing and new tests PASS.

### Task 4: TODO and Packaging Regression Gates

**Files:**
- Modify: `TODO.md`
- Create: `src/test/java/com/hellovoid/liquiddock/WidgetBackgroundRulePackagingContractTest.java`

**Interfaces:**
- No runtime interface changes.

- [ ] **Step 1: Add TODO for future user-editable rules**

Track a future settings/import layer with schema validation, per-rule enable/disable, import/export, safe preview/restore, and no arbitrary reflection actions.

- [ ] **Step 2: Add packaging/source contract**

Require `src/main/resources/widget_background_rules.xml` to exist, require known rules to be absent from Java, and require the controller facade to be the widget semantic entry point.

- [ ] **Step 3: Run `./gradlew testDebugUnitTest --stacktrace`**

Expected: PASS.

### Task 5: Full CI and APK Verification

**Files:** none beyond previous tasks.

- [ ] **Step 1: Run `./gradlew assembleDebug --stacktrace`**

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Inspect built APK**

Run `unzip -l build/outputs/apk/debug/*.apk | grep widget_background_rules.xml`.
Expected: exactly one packaged classpath resource named `widget_background_rules.xml`.

- [ ] **Step 3: Verify no hard-coded widget ownership remains in Java**

Run a source scan for the three Weather UUIDs and `com.miui.weather2` under `src/main/java`; expected zero hits.

- [ ] **Step 4: Verify PR CI**

Require the current clean PR head to complete `testDebugUnitTest`, `assembleDebug`, and both artifact uploads successfully before reporting GREEN.
