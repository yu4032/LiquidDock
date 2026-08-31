# Widget Component Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace product-specific hard-coded widget component hiding with runtime discovery, a GUI picker, persisted user selectors, and conservative runtime application.

**Architecture:** Launcher publishes discovered RemoteViews and MAML component descriptors into a dedicated API101 Remote Preferences catalog. The settings app reads that catalog and stores selected hide selectors in the normal config preferences, which are already mirrored to runtime. Runtime suppression is a separate executor that restores original state on release; bundled XML rules remain as compatibility defaults.

**Tech Stack:** Android Views/RemoteViews, Xiaomi MAML reflection, libxposed API101 Remote Preferences, Compose Miuix, JUnit contract tests.

**Spec:** Runtime behavior validated by the read-only `spike/widget-component-scanner` device test and `[DC][WidgetDiscover]` logs.

## Global Constraints

- RemoteViews selector: provider component + resource entry name; class name is optional validation; hierarchy path is diagnostic only.
- MAML selector: productId + element name; class name is optional validation.
- RemoteViews suppression uses `INVISIBLE`, never `GONE`, and restores original visibility.
- MAML suppression uses `show(false)` and restores original `mShow`.
- Unknown/missing targets fail open.
- Existing bundled XML MAML rules remain enabled as compatibility defaults.
- Discovery is event-driven from existing widget lifecycle boundaries; no frame polling.

---

### Task 1: Discovery catalog and selector codec

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/WidgetComponentStore.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherWidgetComponentDiscovery.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/ConfigReader.java`
- Test: `src/test/java/com/hellovoid/liquiddock/WidgetComponentSelectionContractTest.java`

- [ ] Write/verify failing catalog contract.
- [ ] Implement tab-delimited descriptor/selectors with a dedicated `widget_components` Remote Preferences group.
- [ ] Publish de-duplicated discovery entries from RemoteViews and MAML.
- [ ] Add runtime StringSet reading to ConfigReader.
- [ ] Run unit tests.

### Task 2: Runtime selection executor

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherWidgetComponentSelectionExecutor.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherWidgetBackgroundController.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherMamlBackgroundRuleExecutor.java`

- [ ] Verify failing executor/controller contracts.
- [ ] Apply selected RemoteViews resource selectors using `INVISIBLE` with original visibility claims.
- [ ] Apply selected MAML element selectors using `show(false)` with original `mShow` claims.
- [ ] Restore user claims before built-in MAML claims during release.
- [ ] Enumerate every loaded MAML root for discovery, not only diagnostic fallback roots.
- [ ] Run unit tests.

### Task 3: Compose widget component picker

**Files:**
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Modify: `src/main/res/values/strings.xml`
- Modify: `src/main/res/values-zh-rCN/strings.xml`

- [ ] Verify failing UI contract.
- [ ] Add a Liquid subpage for widget components.
- [ ] Load discovery catalog from `LiquidDockApp.remotePreferences(WidgetComponentStore.DISCOVERY_GROUP)`.
- [ ] Group by RemoteViews provider or MAML productId and show resource/element names plus class summaries.
- [ ] Hide non-visual MAML internals by default with a `显示全部内部元素` toggle.
- [ ] Persist checked selectors to `WidgetComponentStore.SELECTION_KEY` in ordinary local preferences.
- [ ] Run unit tests and assembleDebug.

### Task 4: PR verification

- [ ] Review diff for accidental widget-content mutation outside the selection executor.
- [ ] Run GitHub Actions `testDebugUnitTest` and `assembleDebug`.
- [ ] Create/refresh Draft PR and record APK artifact digest.
