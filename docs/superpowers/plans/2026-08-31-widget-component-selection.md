# Widget Component Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace product-specific hard-coded widget component hiding with runtime discovery, a GUI picker, persisted user selectors, and conservative runtime application.

**Architecture:** Launcher discovers real RemoteViews/MAML component descriptors and sends them upstream through an explicit, token-authenticated broadcast to the module app, which persists a private local catalog. The settings app stores selected hide selectors in normal config preferences, already mirrored down to Launcher by API101 Remote Preferences. Runtime suppression is a separate executor that restores original state on release; bundled XML rules remain compatibility defaults.

**Tech Stack:** Android Views/RemoteViews, Xiaomi MAML reflection, libxposed API101 Remote Preferences, explicit Android broadcast receiver, Compose Miuix, JUnit contract tests.

**Spec:** `docs/superpowers/specs/2026-08-31-widget-component-selection-design.md`

## Global Constraints

- RemoteViews selector: provider component + resource entry name; class name is exact safety validation; hierarchy path is diagnostic only.
- MAML selector: productId + element name; class name is exact safety validation.
- RemoteViews suppression uses `INVISIBLE`, never `GONE`, and restores original visibility.
- MAML suppression uses `show(false)` and restores original `mShow`.
- Unknown/missing targets fail open.
- Existing bundled XML MAML rules remain enabled as compatibility defaults.
- Discovery is event-driven from existing widget lifecycle boundaries; no frame polling.
- Injected Launcher never writes API101 Remote Preferences.

---

### Task 1: Discovery catalog and selector codec

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/WidgetComponentStore.java`
- Create: `src/main/java/com/hellovoid/liquiddock/WidgetDiscoveryReceiver.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherWidgetComponentDiscovery.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LiquidDockApp.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/ConfigReader.java`
- Modify: `src/main/AndroidManifest.xml`
- Test: `src/test/java/com/hellovoid/liquiddock/WidgetComponentSelectionContractTest.java`

- [x] Write and verify failing catalog contract.
- [x] Implement tab-delimited descriptor/selectors.
- [x] Generate and synchronize a discovery token through normal config.
- [x] Send explicit authenticated discovery broadcasts to the module receiver.
- [x] Persist accepted descriptors in private local `widget_components` preferences.
- [x] De-duplicate RemoteViews publication within each scanned content tree.
- [x] Add runtime StringSet reading to ConfigReader.

### Task 2: Runtime selection executor

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherWidgetComponentSelectionExecutor.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherWidgetBackgroundController.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherMamlBackgroundRuleExecutor.java`

- [x] Verify failing executor/controller contracts.
- [x] Apply selected RemoteViews resource selectors using `INVISIBLE` with original visibility claims.
- [x] Apply selected MAML element selectors using `show(false)` with original `mShow` claims.
- [x] Restore user claims before built-in MAML claims during release.
- [x] Enumerate every loaded MAML root for discovery, not only diagnostic fallback roots.

### Task 3: Compose widget component picker

**Files:**
- Create: `src/main/kotlin/com/hellovoid/liquiddock/WidgetComponentsPage.kt`
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Modify: `src/main/res/values/strings.xml`
- Modify: `src/main/res/values-zh-rCN/strings.xml`

- [x] Verify failing UI contract.
- [x] Add a Liquid subpage for widget components.
- [x] Load the private local discovery catalog.
- [x] Group by RemoteViews provider or MAML productId and show resource/element names plus class summaries.
- [x] Hide `VariableElement` MAML internals by default with a `显示全部内部元素` toggle.
- [x] Persist checked selectors to `WidgetComponentStore.SELECTION_KEY` in ordinary local preferences.
- [x] Add refresh, clear/rescan, and apply/restart actions.

### Task 4: PR verification

- [ ] Review diff for accidental widget-content mutation outside the selection executor.
- [ ] Run GitHub Actions `testDebugUnitTest` and `assembleDebug` on the final head.
- [ ] Record final APK artifact ID and digest on Draft PR #87.
