# Technical-Debt Cleanup Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the highest-confidence ownership and compatibility debt from Workstation and Widget/Grid paths without changing zero-copy glass correctness or visual behavior.

**Architecture:** Move Workstation mode/timing ownership into one controller; centralize Widget classification and supported span policy; make Widget sizing pure; add one-shot diagnostics for required bundled Widget rules. Keep current producer/EGL/OES/fresh-frame ownership unchanged in this phase.

**Tech Stack:** Java 17, Android/libxposed API 101, JUnit 4, Gradle 9.6.1, HyperOS Launcher 4.50 vendor reflection through existing `HookUtil` boundaries.

**Spec:** `docs/superpowers/specs/2026-09-07-technical-debt-cleanup-design.md`

## Global Constraints

- Baseline is HyperOS 3.0.307+ / `com.miui.home` release-4.50.x.x / libxposed API 101.
- Zero-copy only; do not restore ScreenCapture, PixelCopy, bitmap readback, or screenshot fallback.
- Existing scene/wallpaper generation plus fresh OES frame remains the only reveal authority.
- Workstation Recents recovery stays coverage-gated, Workstation-only, epoch-protected, and fail-closed.
- Widget adaptation changes allocation/frame only; MIUI retains placement/occupancy authority.
- Supported Widget specs remain exactly `1×1`, `2×1`, `2×2`, `4×2` in this phase.
- Structural Workstation/Grid Hook selection remains restart-bound.
- No new entries may be added to `RuntimeBehaviorTestPolicyContractTest.LEGACY_SOURCE_DEBT`.

---

## File map

### New production files

- `src/main/java/com/hellovoid/liquiddock/WorkstationModeController.java` — Workstation mode, vendor confirmation, generation/cancellation, and Workstation-owned normal-layout backup/restore coordination.
- `src/main/java/com/hellovoid/liquiddock/WidgetClassifier.java` — single Widget classification boundary.
- `src/main/java/com/hellovoid/liquiddock/WidgetSpecRegistry.java` — immutable supported span registry.
- `src/main/java/com/hellovoid/liquiddock/OneShotDiagnostic.java` — minimal process-local one-shot diagnostic gate if an equivalent reusable primitive does not already exist after repository inspection.

### Modified production files

- `src/main/java/com/hellovoid/liquiddock/MainHook.java` — composition/wiring only for new Workstation controller; remove direct Workstation confirmation and naked delayed recheck ownership.
- `src/main/java/com/hellovoid/liquiddock/HomeGridHook.java` — consume `WidgetClassifier` / `WidgetSpecRegistry`; stop embedding Widget magic-number classification.
- `src/main/java/com/hellovoid/liquiddock/WidgetGridSizing.java` — remove static mutable enable flag; become pure geometry.
- `src/main/java/com/hellovoid/liquiddock/WidgetBackgroundRuleEngine.java` — return/load status that distinguishes required bundled-resource failure from an intentionally empty/valid ruleset.
- `src/main/java/com/hellovoid/liquiddock/LauncherMamlBackgroundRuleExecutor.java` — emit one structured diagnostic for bundled-rule load failure.

### New/modified tests

- `src/test/java/com/hellovoid/liquiddock/WorkstationModeControllerTest.java`
- `src/test/java/com/hellovoid/liquiddock/WidgetClassifierTest.java`
- `src/test/java/com/hellovoid/liquiddock/WidgetSpecRegistryTest.java`
- `src/test/java/com/hellovoid/liquiddock/WidgetGridSizingTest.java`
- `src/test/java/com/hellovoid/liquiddock/WidgetBackgroundRuleEngineTest.java`
- `src/test/java/com/hellovoid/liquiddock/WidgetBackgroundRuleDiagnosticsTest.java`
- `src/test/java/com/hellovoid/liquiddock/RuntimeBehaviorTestPolicyContractTest.java` only if a migrated source-reader debt entry can be removed in the same task; never add an exception.

---

### Task 1: Establish deterministic Workstation mode-generation ownership

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/WorkstationModeController.java`
- Create: `src/test/java/com/hellovoid/liquiddock/WorkstationModeControllerTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java` only after the pure controller tests are green.

**Interfaces:**
- Consumes: existing vendor mode probe/callback code currently installed by `MainHook`.
- Produces:
  - `boolean isWorkstationMode()`
  - `void onVendorModeChanged(boolean workstationMode)`
  - `long beginUnconfirmedProbe()`
  - `boolean acceptFallbackProbe(long expectedGeneration, boolean probedMode)`
  - `long generation()`
  - controller-owned normal-layout backup/restore methods used by existing MainHook call sites.

- [ ] **Step 1: Write failing generation tests**

Create tests that prove an old fallback callback cannot win after a newer vendor transition:

```java
@Test
public void staleFallbackProbeCannotOverrideNewerVendorCallback() {
    WorkstationModeController controller = new WorkstationModeController();
    long generation = controller.beginUnconfirmedProbe();

    controller.onVendorModeChanged(true);

    assertFalse(controller.acceptFallbackProbe(generation, false));
    assertTrue(controller.isWorkstationMode());
}

@Test
public void fallbackProbeIsAcceptedOnlyForCurrentUnconfirmedGeneration() {
    WorkstationModeController controller = new WorkstationModeController();
    long generation = controller.beginUnconfirmedProbe();

    assertTrue(controller.acceptFallbackProbe(generation, true));
    assertTrue(controller.isWorkstationMode());
    assertFalse(controller.acceptFallbackProbe(generation, false));
}
```

Also add tests for duplicate vendor callbacks and generation monotonicity.

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew testDebugUnitTest --tests '*WorkstationModeControllerTest' --stacktrace
```

Expected: compilation/test failure because `WorkstationModeController` does not exist.

- [ ] **Step 3: Implement the minimal controller state machine**

The controller must use a monotonic `long generation`, distinguish vendor-confirmed state from a pending fallback generation, and reject stale/duplicate fallback application. Keep Android `Handler` out of the pure transition logic.

A valid minimal shape is:

```java
final class WorkstationModeController {
    private long generation;
    private long pendingFallbackGeneration = -1L;
    private boolean workstationMode;
    private boolean vendorConfirmed;

    boolean isWorkstationMode() { return workstationMode; }
    long generation() { return generation; }

    long beginUnconfirmedProbe() {
        vendorConfirmed = false;
        pendingFallbackGeneration = ++generation;
        return pendingFallbackGeneration;
    }

    void onVendorModeChanged(boolean mode) {
        workstationMode = mode;
        vendorConfirmed = true;
        pendingFallbackGeneration = -1L;
        generation++;
    }

    boolean acceptFallbackProbe(long expectedGeneration, boolean mode) {
        if (vendorConfirmed || pendingFallbackGeneration != expectedGeneration) return false;
        workstationMode = mode;
        pendingFallbackGeneration = -1L;
        generation++;
        return true;
    }
}
```

If MainHook requires richer transition side effects, keep those in explicit controller methods; do not expose mutable fields or setter bags.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run the same focused Gradle command. Expected: PASS.

- [ ] **Step 5: Wire existing vendor callback and delayed fallback through the controller**

In `MainHook`:

1. replace direct `workstationMode` / `workstationModeHookConfirmed` mutation with controller calls;
2. when scheduling the existing delayed fallback probe, capture the generation returned by `beginUnconfirmedProbe()`;
3. when the delayed runnable executes, read the vendor mode, then call `acceptFallbackProbe(expectedGeneration, probedMode)`;
4. perform Workstation transition side effects only if the controller accepts the change/current generation;
5. ensure a vendor callback arriving first invalidates the delayed fallback;
6. do not add another fixed-delay retry chain.

The existing 2-second fallback may remain temporarily, but it must no longer be a naked authority.

- [ ] **Step 6: Move Workstation-only normal-layout backup/restore ownership behind the controller**

Move the `normalLayoutBackup` map and its Workstation transition entry points out of `MainHook`. Preserve the current stored value shape and exact restore behavior; do not redesign MIUI placement.

If view-specific animation state is not strictly Workstation-owned, leave that animator with its actual Dock owner rather than forcing it into the controller.

- [ ] **Step 7: Run Workstation and global unit tests**

```bash
./gradlew testDebugUnitTest --tests '*Workstation*' --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS; `RuntimeBehaviorTestPolicyContractTest` adds no new debt exceptions.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/WorkstationModeController.java \
        src/main/java/com/hellovoid/liquiddock/MainHook.java \
        src/test/java/com/hellovoid/liquiddock/WorkstationModeControllerTest.java
git commit -m "refactor: centralize workstation mode ownership"
```

---

### Task 2: Centralize Widget classification

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/WidgetClassifier.java`
- Create: `src/test/java/com/hellovoid/liquiddock/WidgetClassifierTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/HomeGridHook.java`

**Interfaces:**
- Consumes: vendor `ItemInfo.isWidget()` through the existing `HookUtil.tryInvoke*` boundary; `itemType` field fallback.
- Produces: `static boolean isWidget(Object itemInfo)`.

- [ ] **Step 1: Write classification tests**

The pure classification decision should be testable independently of real Launcher classes. Separate the fallback decision from vendor invocation so tests can cover it without reflection:

```java
@Test
public void knownFallbackItemTypesAreWidgets() {
    assertTrue(WidgetClassifier.isWidgetFallbackType(4));
    assertTrue(WidgetClassifier.isWidgetFallbackType(5));
    assertTrue(WidgetClassifier.isWidgetFallbackType(19));
}

@Test
public void unrelatedItemTypesAreNotWidgets() {
    assertFalse(WidgetClassifier.isWidgetFallbackType(0));
    assertFalse(WidgetClassifier.isWidgetFallbackType(1));
    assertFalse(WidgetClassifier.isWidgetFallbackType(6));
}
```

Add one static architecture contract only if needed to assert the vendor `isWidget()` path stays primary; do not use source order/slicing to prove runtime behavior.

- [ ] **Step 2: Verify tests fail**

```bash
./gradlew testDebugUnitTest --tests '*WidgetClassifierTest' --stacktrace
```

Expected: FAIL because the classifier does not exist.

- [ ] **Step 3: Implement `WidgetClassifier`**

Use the current semantics exactly:

```java
final class WidgetClassifier {
    private WidgetClassifier() {}

    static boolean isWidgetFallbackType(int itemType) {
        return itemType == 4 || itemType == 5 || itemType == 19;
    }

    static boolean isWidget(Object itemInfo) {
        if (itemInfo == null) return false;
        HookUtil.InvocationResult<Boolean> result = HookUtil.tryInvokeBoolean(itemInfo, "isWidget");
        if (result.succeeded() && Boolean.TRUE.equals(result.value())) return true;
        return isWidgetFallbackType(HookUtil.getIntField(itemInfo, "itemType"));
    }
}
```

Use the actual existing `HookUtil` result type/method names from the repository rather than inventing a parallel reflection wrapper if the signature differs.

- [ ] **Step 4: Replace direct classification in `HomeGridHook`**

Every Widget-adaptation path in `HomeGridHook` must call `WidgetClassifier.isWidget(info)`. Remove direct `itemType == 4 || itemType == 5 || itemType == 19` control flow from `HomeGridHook`.

- [ ] **Step 5: Run focused and global tests**

```bash
./gradlew testDebugUnitTest --tests '*WidgetClassifierTest' --stacktrace
./gradlew testDebugUnitTest --tests '*WidgetGridSizingTest' --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/WidgetClassifier.java \
        src/main/java/com/hellovoid/liquiddock/HomeGridHook.java \
        src/test/java/com/hellovoid/liquiddock/WidgetClassifierTest.java
git commit -m "refactor: centralize widget classification"
```

---

### Task 3: Move supported Widget spans into an immutable registry and make sizing stateless

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/WidgetSpecRegistry.java`
- Create: `src/test/java/com/hellovoid/liquiddock/WidgetSpecRegistryTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/WidgetGridSizing.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/HomeGridHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/WidgetGridSizingTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/ConfigLoadPolicyTest.java` if it still resets the static sizing flag.

**Interfaces:**
- `WidgetSpecRegistry.DEFAULT.supports(int spanX, int spanY)`.
- `WidgetGridSizing.gridRect(boolean adaptationEnabled, int cellX, int cellY, int spanX, int spanY, int[] xs, int[] ys, int cellWidth, int cellHeight, int widthGap, int heightGap)`.

- [ ] **Step 1: Write registry tests**

```java
@Test
public void defaultRegistryContainsOnlyCurrentSupportedSpans() {
    WidgetSpecRegistry registry = WidgetSpecRegistry.DEFAULT;
    assertTrue(registry.supports(1, 1));
    assertTrue(registry.supports(2, 1));
    assertTrue(registry.supports(2, 2));
    assertTrue(registry.supports(4, 2));
    assertFalse(registry.supports(3, 2));
    assertFalse(registry.supports(4, 1));
}
```

- [ ] **Step 2: Rewrite sizing tests to pass enable state explicitly**

Remove `@Before/@After` calls to `WidgetGridSizing.setWidgetAdaptationEnabled(...)`. Add explicit disabled-path coverage:

```java
@Test
public void disabledAdaptationReturnsEmptyRect() {
    assertArrayEquals(
            new int[]{0, 0, 0, 0},
            WidgetGridSizing.gridRect(
                    false, 0, 0, 2, 1,
                    new int[]{10, 110}, new int[]{20, 120},
                    80, 80, 20, 20));
}
```

Preserve all existing geometry cases with the first argument set to `true`.

- [ ] **Step 3: Run tests and verify failure**

```bash
./gradlew testDebugUnitTest --tests '*WidgetSpecRegistryTest' --tests '*WidgetGridSizingTest' --stacktrace
```

Expected: FAIL until signatures/registry exist.

- [ ] **Step 4: Implement immutable registry**

Use a small value representation with no runtime mutation. Do not add dynamic registration in Phase 1.

A sufficient API is:

```java
final class WidgetSpecRegistry {
    static final WidgetSpecRegistry DEFAULT = new WidgetSpecRegistry(
            new int[][]{{1, 1}, {2, 1}, {2, 2}, {4, 2}});

    boolean supports(int spanX, int spanY) { /* exact pair lookup */ }
}
```

- [ ] **Step 5: Remove static config from `WidgetGridSizing`**

Delete:

```java
private static volatile boolean widgetAdaptationEnabled;
static void setWidgetAdaptationEnabled(boolean enabled)
```

Make `gridRect(...)` depend only on its arguments. Keep `shouldAdaptWidgets(gridEnabled, adaptationEnabled)` only if production call sites still benefit from the pure boolean helper; otherwise inline the explicit immutable config decision at installation.

- [ ] **Step 6: Remove `MainHook` global sizing setter**

Delete the `WidgetGridSizing.setWidgetAdaptationEnabled(...)` call from `MainHook.install()`. Pass the immutable `config.grid.enabled && config.grid.widgetAdaptation` decision into the Widget adaptation installer/Hook instance instead.

Do not make `WidgetGridSizing` read `LiquidDockConfig` directly.

- [ ] **Step 7: Make `HomeGridHook` consult the registry**

Before adapting a Widget frame:

1. classify via `WidgetClassifier`;
2. read spanX/spanY through existing vendor-field access;
3. skip adaptation when `WidgetSpecRegistry.DEFAULT.supports(spanX, spanY)` is false;
4. call `WidgetGridSizing.gridRect(adaptationEnabled, ...)`.

Do not modify placement/occupancy paths.

- [ ] **Step 8: Run Widget/Grid and config-load tests**

```bash
./gradlew testDebugUnitTest --tests '*Widget*' --stacktrace
./gradlew testDebugUnitTest --tests '*HomeGrid*' --stacktrace
./gradlew testDebugUnitTest --tests '*ConfigLoadPolicyTest' --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS and no test needs to reset process-global Widget sizing state.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/WidgetSpecRegistry.java \
        src/main/java/com/hellovoid/liquiddock/WidgetGridSizing.java \
        src/main/java/com/hellovoid/liquiddock/HomeGridHook.java \
        src/main/java/com/hellovoid/liquiddock/MainHook.java \
        src/test/java/com/hellovoid/liquiddock/WidgetSpecRegistryTest.java \
        src/test/java/com/hellovoid/liquiddock/WidgetGridSizingTest.java \
        src/test/java/com/hellovoid/liquiddock/ConfigLoadPolicyTest.java
git commit -m "refactor: make widget sizing ownership explicit"
```

---

### Task 4: Move actual Widget adaptation Hook ownership out of `HomeGridHook`

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/HomeGridWidgetAdaptationHook.java`
- Create: `src/test/java/com/hellovoid/liquiddock/HomeGridWidgetAdaptationContractTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/HomeGridHook.java`

**Interfaces:**
- Consumes: immutable adaptation-enabled flag, `WidgetClassifier`, `WidgetSpecRegistry`, `WidgetGridSizing`, and the existing CellLayout/ItemInfo vendor classes.
- Produces: `void install(ClassLoader classLoader)` that installs only Widget allocation/frame adaptation hooks.

- [ ] **Step 1: Identify the exact Widget-only Hook entry points currently inside `HomeGridHook`**

Before editing, enumerate only the methods that mutate Widget layout/allocation/frame. Exclude:

- cell-count hooks;
- page indicator;
- folder alignment;
- orientation memory;
- rotation/refresh;
- occupancy/placement.

Record these symbols in the commit/PR description so review can confirm the extraction is ownership migration rather than helper proliferation.

- [ ] **Step 2: Add a contract test for installation boundary**

The contract test may use audited static inspection only to assert architecture boundaries, not runtime sequencing. It should assert that direct Widget layout hook targets are installed from `HomeGridWidgetAdaptationHook` and no longer duplicated in `HomeGridHook`.

If this becomes a production-source reader, add it to the existing audited static allowlist only if it satisfies the static-contract rules; never put it in `LEGACY_SOURCE_DEBT`.

- [ ] **Step 3: Extract Widget Hook installation and callbacks**

Move the real Widget-specific installation/callback code into `HomeGridWidgetAdaptationHook`. Pass explicit immutable dependencies through its constructor or install method. Do not expose MainHook globals.

- [ ] **Step 4: Keep `HomeGridHook` as coordinator for the remaining Grid owners**

`HomeGridHook` may instantiate/install `HomeGridWidgetAdaptationHook`, but must not retain duplicate Widget frame logic.

- [ ] **Step 5: Run Grid/Widget tests**

```bash
./gradlew testDebugUnitTest --tests '*HomeGrid*' --tests '*Widget*' --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/HomeGridWidgetAdaptationHook.java \
        src/main/java/com/hellovoid/liquiddock/HomeGridHook.java \
        src/test/java/com/hellovoid/liquiddock/HomeGridWidgetAdaptationContractTest.java \
        src/test/java/com/hellovoid/liquiddock/RuntimeBehaviorTestPolicyContractTest.java
git commit -m "refactor: extract widget grid adaptation owner"
```

Only include `RuntimeBehaviorTestPolicyContractTest.java` if its audited static allowlist genuinely needs updating.

---

### Task 5: Make bundled Widget-rule degradation observable once

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/WidgetBackgroundRuleEngine.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherMamlBackgroundRuleExecutor.java`
- Create: `src/main/java/com/hellovoid/liquiddock/OneShotDiagnostic.java` only if no equivalent process-local one-shot primitive already exists.
- Modify: `src/test/java/com/hellovoid/liquiddock/WidgetBackgroundRuleEngineTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/WidgetBackgroundRuleDiagnosticsTest.java`

**Interfaces:**
- `WidgetBackgroundRuleEngine.LoadResult` or equivalent immutable result containing:
  - parsed engine;
  - status enum such as `LOADED`, `MISSING_RESOURCE`, `PARSE_FAILED`;
  - optional short reason for diagnostics.
- Runtime matcher behavior remains unchanged: failure still behaves like an empty ruleset.

- [ ] **Step 1: Write load-result tests**

Add tests that separately prove:

```java
assertEquals(Status.LOADED, validResult.status());
assertEquals(Status.PARSE_FAILED, malformedResult.status());
assertEquals(Status.MISSING_RESOURCE, missingResult.status());
```

For parser-only `parse(InputStream)` tests, preserve the existing fail-safe semantics; the load API is where required bundled-resource status becomes observable.

- [ ] **Step 2: Write one-shot diagnostic test**

Use an injected sink or minimal package-private primitive:

```java
@Test
public void sameDiagnosticKeyEmitsOnlyOnce() {
    List<String> messages = new ArrayList<>();
    OneShotDiagnostic diagnostics = new OneShotDiagnostic(messages::add);

    diagnostics.emit("widget_rules_load_failed", "first");
    diagnostics.emit("widget_rules_load_failed", "second");

    assertEquals(List.of("first"), messages);
}
```

- [ ] **Step 3: Run focused tests and verify they fail**

```bash
./gradlew testDebugUnitTest --tests '*WidgetBackgroundRule*' --stacktrace
```

Expected: FAIL until the load result/diagnostic primitive exists.

- [ ] **Step 4: Implement structured load status**

Do not throw from normal Launcher startup. `loadBundled()` should still yield an engine that safely matches nothing on failure, but callers must be able to distinguish missing/parse failure from a successfully loaded empty ruleset.

Do not log every `match()` call.

- [ ] **Step 5: Emit one structured warning at executor initialization/first use**

The diagnostic must include at least:

```text
[DC][WidgetRules] bundled_rules_unavailable status=<MISSING_RESOURCE|PARSE_FAILED>
```

Use the project’s existing logging facility where possible. Emission must be one-shot per process/status key.

- [ ] **Step 6: Run focused and global tests**

```bash
./gradlew testDebugUnitTest --tests '*WidgetBackgroundRule*' --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/WidgetBackgroundRuleEngine.java \
        src/main/java/com/hellovoid/liquiddock/LauncherMamlBackgroundRuleExecutor.java \
        src/main/java/com/hellovoid/liquiddock/OneShotDiagnostic.java \
        src/test/java/com/hellovoid/liquiddock/WidgetBackgroundRuleEngineTest.java \
        src/test/java/com/hellovoid/liquiddock/WidgetBackgroundRuleDiagnosticsTest.java
git commit -m "fix: report bundled widget rule degradation"
```

Omit `OneShotDiagnostic.java` from the commit if an existing equivalent primitive is reused.

---

### Task 6: Phase 1 verification and documentation closure

**Files:**
- Modify: `TODO.md`
- Modify: `ARCHITECTURE.md`
- Modify: `HOOKS.md`
- Modify: `CONTRIBUTING.md`
- Modify: `FEATURES.md` only if user-visible behavior wording changed.
- Create: `docs/superpowers/verification/2026-09-07-technical-debt-cleanup-phase1.md`

**Interfaces:**
- Consumes: all Task 1–5 implementation commits.
- Produces: a recorded CI/device verification result and a reduced active-debt ledger.

- [ ] **Step 1: Run full unit tests**

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Step 2: Build Debug APK**

```bash
./gradlew assembleDebug --stacktrace
```

Expected: PASS.

- [ ] **Step 3: Run the device smoke matrix**

Record PASS/FAIL plus relevant log excerpts for:

1. normal Launcher startup;
2. Workstation enter -> exit -> quick re-enter;
3. vendor Workstation callback arriving before delayed fallback;
4. stale delayed fallback after a newer transition;
5. normal-layout backup/restore;
6. HOME -> Recents -> HOME repeated at least five times;
7. Recents-adjacent rotation;
8. 1×1 / 2×1 / 2×2 / 4×2 Widgets in portrait and landscape;
9. Widget adaptation disabled;
10. normal mode without Workstation;
11. bundled Widget rules loaded normally;
12. test-only malformed/missing bundled rule path emits one diagnostic and fails safe.

- [ ] **Step 4: Write verification record**

The verification document must include:

- tested commit SHA;
- device/ROM/Launcher version;
- Gradle test/build commands and exit status;
- each matrix result;
- unresolved failures, if any, with exact reproduction steps;
- confirmation that no producer/EGL/OES/freshness semantics changed in Phase 1.

Do not mark the phase complete if required device cases remain untested.

- [ ] **Step 5: Update active debt ledger**

In `TODO.md`, mark only genuinely completed Phase 1 items as completed. Keep Phase 2–4 ownership/audit work active. Do not delete historical constraints that prevent reopening already-closed ScreenCapture, Recents-recovery, or unlock-authority work.

- [ ] **Step 6: Update architecture/hook docs to the implemented state**

Documentation must describe what production now does, not this plan’s target state. In particular:

- `MainHook` composition-root wording must match actual ownership after Task 1/4;
- Workstation delayed fallback must be described as generation-protected if implemented;
- Widget classifier/spec registry must be described as active only after code is merged;
- `HomeGridHook` responsibility list must shrink only for code that actually moved.

- [ ] **Step 7: Commit verification/docs**

```bash
git add TODO.md ARCHITECTURE.md HOOKS.md CONTRIBUTING.md FEATURES.md \
        docs/superpowers/verification/2026-09-07-technical-debt-cleanup-phase1.md
git commit -m "docs: close phase 1 debt cleanup verification"
```

If `FEATURES.md` has no user-visible change, omit it from the commit.

---

## Phase 1 stop gate

Do not begin EGL/OES/producer lifecycle extraction immediately after this plan merely because the classes remain large.

Phase 2/3 work starts only after:

1. all Phase 1 CI gates pass;
2. the Workstation/Grid device matrix is recorded;
3. no regression requires changing the existing fresh-frame authority;
4. an explicit resource-owner graph exists for `LauncherGlassSession`, `Miuix307PassBlurTextureView`, and Prismal.

The next implementation plan must argue from that owner graph and identify exact common lifecycle primitives before any shared resource abstraction is introduced.
