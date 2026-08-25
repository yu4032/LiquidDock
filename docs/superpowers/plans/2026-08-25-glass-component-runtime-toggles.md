# Glass Component Runtime Toggles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make icon, widget, small-folder, and large-folder glass switches authoritative at runtime so stale hooks and delayed callbacks cannot keep or reclaim visual ownership after a switch is disabled.

**Architecture:** Extend `GlassRuntimeState` with live per-component booleans. Hooks remain installed while the parent glass subsystem is active, but every mutation path gates on live state. Component disable transitions publish false first, then run component-specific teardown on the main thread; re-enable uses existing workspace reconciliation paths.

**Tech Stack:** Java 17, Android Views, libxposed API101 hooks, JUnit 4 source-contract tests, Gradle Android build.

**Spec:** `docs/superpowers/specs/2026-08-25-runtime-toggle-ownership-design.md`

## Global Constraints

- Do not hot-reload structural grid/widget-adaptation/resize-animation semantics in this plan.
- Do not guess vendor visual values during teardown; restore only state previously captured by LiquidDock.
- Delayed `post`, `postDelayed`, `postOnAnimation`, RemoteViews/MAML, folder recovery, and drag callbacks must re-check live state at execution time.
- No unrelated Prismal/rendering refactor.

---

### Task 1: Track all glass component switches as live state

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Test: `src/test/java/com/hellovoid/liquiddock/GlassComponentRuntimeToggleContractTest.java`

**Interfaces:**
- Produces: `GlassRuntimeState.isIconEnabled()`, `isWidgetEnabled()`, `isSmallFolderEnabled()`, `isLargeFolderEnabled()`.
- Produces disable callbacks to `MiuixLauncherStaticGlassHook` and `MiuixFolderGlassHook`.

- [ ] **Step 1: Write the failing contract test**

Create a source contract asserting `GlassRuntimeState` listens to `ICON_GLASS`, `WIDGET_GLASS`, `SMALL_FOLDER_GLASS`, and `LARGE_FOLDER_GLASS`, exposes four live getters, and `ModuleMain` seeds all four startup values.

```java
@Test public void runtimeTracksAllGlassComponentSwitches() throws Exception {
    String state = Files.readString(MAIN.resolve("GlassRuntimeState.java"));
    String module = Files.readString(MAIN.resolve("ModuleMain.java"));
    assertTrue(state.contains("ConfigSchema.Glass.ICON_GLASS.name()"));
    assertTrue(state.contains("ConfigSchema.Glass.WIDGET_GLASS.name()"));
    assertTrue(state.contains("ConfigSchema.Glass.SMALL_FOLDER_GLASS.name()"));
    assertTrue(state.contains("ConfigSchema.Glass.LARGE_FOLDER_GLASS.name()"));
    assertTrue(state.contains("static boolean isIconEnabled()"));
    assertTrue(state.contains("static boolean isSmallFolderEnabled()"));
    assertTrue(state.contains("static boolean isLargeFolderEnabled()"));
    assertTrue(module.contains("runtimeConfig.glass.iconEnabled"));
    assertTrue(module.contains("runtimeConfig.glass.smallFolderStyle.enabled"));
    assertTrue(module.contains("runtimeConfig.glass.largeFolderStyle.enabled"));
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.GlassComponentRuntimeToggleContractTest --stacktrace`

Expected: FAIL because icon/small/large live state and startup arguments do not exist yet.

- [ ] **Step 3: Implement minimal live state**

Add volatile booleans and extend `initialize(...)`/preference listener. In `apply(...)`, compute previous and next effective component states after publishing fields; on true -> false call component teardown on the main thread.

- [ ] **Step 4: Run focused test and verify GREEN**

Run the same Gradle test command. Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `fix: track glass component toggles at runtime`.

### Task 2: Gate and tear down icon glass ownership

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherDragOverlayHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockIconAnimationGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockGlassItemRegistry.java`
- Test: `src/test/java/com/hellovoid/liquiddock/GlassComponentRuntimeToggleContractTest.java`

**Interfaces:**
- Consumes: `GlassRuntimeState.isIconEnabled()`.
- Produces: `MiuixLauncherStaticGlassHook.onRuntimeIconGlassDisabled()`.

- [ ] **Step 1: Extend failing contract**

Assert icon callbacks use `GlassRuntimeState.isIconEnabled()`, there is an icon-only teardown path, and Dock icon registry/drag paths cannot accept new icon work while false.

- [ ] **Step 2: Run focused test and verify RED**

Expected: FAIL on missing live gates/teardown.

- [ ] **Step 3: Implement minimal icon teardown/gates**

`onRuntimeIconGlassDisabled()` must dispose observed ICON static nodes and unregister Dock icon candidates without touching widget nodes. `scheduleBind`, `reconcileExistingHost`, attach callbacks, drag source resolution, Dock launch animation callbacks, and registry registration must reject icon ownership while live false.

- [ ] **Step 4: Run focused test and verify GREEN**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `fix: release icon glass ownership on toggle`.

### Task 3: Gate and tear down small/large folder ownership independently

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherDragOverlayHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/FolderRuntimeToggleContractTest.java`

**Interfaces:**
- Consumes: `GlassRuntimeState.isSmallFolderEnabled()` and `isLargeFolderEnabled()`.
- Produces: `MiuixFolderGlassHook.onRuntimeSmallFolderGlassDisabled()` and `onRuntimeLargeFolderGlassDisabled()`.

- [ ] **Step 1: Write failing folder contract**

Assert variant-specific live gates are used in `attachFromFolderIcon`, `scheduleFolderRecovery`, `observeFolderIconAttach`, `syncLargeFolderCover`, `attachMaterial`, large-folder draw suppression, and drag-overlay style resolution. Assert variant-specific teardown calls `restoreMaterial`, disposes claimed nodes, releases covers, and cancels pending recovery for only the disabled variant.

- [ ] **Step 2: Run focused test and verify RED**

Expected: FAIL because folder enablement is still startup snapshot based.

- [ ] **Step 3: Implement variant-aware gates and teardown**

Add helpers `isFolderVariantEnabled(View/ViewGroup)` backed by live state. Before any alpha/background/Paint/cover mutation, reject disabled variants and restore currently claimed state. Teardown iterates weak ownership maps and only releases matching variant entries.

- [ ] **Step 4: Run focused test and verify GREEN**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `fix: release folder glass ownership by variant`.

### Task 4: Verify glass component plan

- [ ] **Step 1: Run focused component tests**

Run: `./gradlew testDebugUnitTest --tests '*GlassComponentRuntimeToggleContractTest' --tests '*FolderRuntimeToggleContractTest' --tests '*WidgetGlassRuntimeOwnershipContractTest' --stacktrace`

Expected: PASS.

- [ ] **Step 2: Run full unit tests**

Run: `./gradlew testDebugUnitTest --stacktrace`

Expected: PASS.
