# Technical-Debt Cleanup Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the highest-confidence Workstation and Widget/Grid ownership debt without changing zero-copy glass correctness or visual behavior.

**Architecture:** Move Workstation mode/timing/layout-snapshot state into one controller; centralize Widget classification and supported spans; make Widget sizing pure; extract the real Widget adaptation Hook owner; make bundled Widget-rule failure visible through one-shot structured diagnostics. Producer/EGL/OES/fresh-frame ownership is frozen during Phase 1.

**Tech Stack:** Java 17, Android/libxposed API 101, JUnit 4, Gradle 9.6.1, HyperOS Launcher 4.50 vendor reflection through the existing `HookUtil` boundary.

**Spec:** `docs/superpowers/specs/2026-09-07-technical-debt-cleanup-design.md`

## Global Constraints

- Baseline: HyperOS 3.0.307+ / `com.miui.home` release-4.50.x.x / libxposed API 101.
- Zero-copy only; do not restore ScreenCapture, PixelCopy, bitmap readback, or screenshot fallback.
- Scene/wallpaper generation plus fresh OES frame remains the only reveal authority.
- Workstation Recents recovery remains coverage-gated, Workstation-only, `workstationBindEpoch`-protected, and fail-closed.
- Widget adaptation changes allocation/frame only; MIUI retains placement/occupancy authority.
- Supported Widget spans remain exactly `1×1`, `2×1`, `2×2`, `4×2`.
- Workstation/Grid structural Hook selection remains restart-bound.
- No new entry may be added to `RuntimeBehaviorTestPolicyContractTest.LEGACY_SOURCE_DEBT`.

---

## File Map

### New production files

- `src/main/java/com/hellovoid/liquiddock/WorkstationModeController.java`
- `src/main/java/com/hellovoid/liquiddock/WidgetClassifier.java`
- `src/main/java/com/hellovoid/liquiddock/WidgetSpecRegistry.java`
- `src/main/java/com/hellovoid/liquiddock/HomeGridWidgetAdaptationHook.java`
- `src/main/java/com/hellovoid/liquiddock/OneShotDiagnostic.java`

### Modified production files

- `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- `src/main/java/com/hellovoid/liquiddock/HomeGridHook.java`
- `src/main/java/com/hellovoid/liquiddock/WidgetGridSizing.java`
- `src/main/java/com/hellovoid/liquiddock/WidgetBackgroundRuleEngine.java`
- `src/main/java/com/hellovoid/liquiddock/LauncherMamlBackgroundRuleExecutor.java`

### New/modified tests

- `src/test/java/com/hellovoid/liquiddock/WorkstationModeControllerTest.java`
- `src/test/java/com/hellovoid/liquiddock/WidgetClassifierTest.java`
- `src/test/java/com/hellovoid/liquiddock/WidgetSpecRegistryTest.java`
- `src/test/java/com/hellovoid/liquiddock/WidgetGridSizingTest.java`
- `src/test/java/com/hellovoid/liquiddock/HomeGridWidgetAdaptationHookTest.java`
- `src/test/java/com/hellovoid/liquiddock/WidgetBackgroundRuleEngineTest.java`
- `src/test/java/com/hellovoid/liquiddock/WidgetBackgroundRuleDiagnosticsTest.java`
- `src/test/java/com/hellovoid/liquiddock/ConfigLoadPolicyTest.java`

---

### Task 1: Centralize Workstation mode, generation, and layout-snapshot ownership

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/WorkstationModeController.java`
- Create: `src/test/java/com/hellovoid/liquiddock/WorkstationModeControllerTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`

**Interfaces:**

`WorkstationModeController` must expose exactly these package-private operations:

```java
boolean isWorkstationMode();
long generation();
long beginUnconfirmedProbe();
boolean acceptFallbackProbe(long expectedGeneration, boolean probedMode);
void onVendorModeChanged(boolean workstationMode);
void clearNormalLayoutBackup();
void rememberNormalItem(long itemId, long screenId,
                        int cellX, int cellY, int spanX, int spanY);
HomeItemPosition normalItem(long itemId);
boolean hasNormalLayoutBackup();
```

`HomeItemPosition` moves from `MainHook` into `WorkstationModeController` with the existing fields: `screenId`, `cellX`, `cellY`, `spanX`, `spanY`.

- [ ] **Step 1: Write failing generation tests**

```java
@Test
public void staleFallbackCannotOverrideNewerVendorCallback() {
    WorkstationModeController controller = new WorkstationModeController();
    long pending = controller.beginUnconfirmedProbe();

    controller.onVendorModeChanged(true);

    assertFalse(controller.acceptFallbackProbe(pending, false));
    assertTrue(controller.isWorkstationMode());
}

@Test
public void currentUnconfirmedFallbackIsAcceptedOnce() {
    WorkstationModeController controller = new WorkstationModeController();
    long pending = controller.beginUnconfirmedProbe();

    assertTrue(controller.acceptFallbackProbe(pending, true));
    assertTrue(controller.isWorkstationMode());
    assertFalse(controller.acceptFallbackProbe(pending, false));
}

@Test
public void newProbeInvalidatesOlderProbe() {
    WorkstationModeController controller = new WorkstationModeController();
    long first = controller.beginUnconfirmedProbe();
    long second = controller.beginUnconfirmedProbe();

    assertFalse(controller.acceptFallbackProbe(first, true));
    assertTrue(controller.acceptFallbackProbe(second, false));
}
```

- [ ] **Step 2: Write failing layout-snapshot tests**

```java
@Test
public void normalLayoutSnapshotRoundTripsExactStoredFields() {
    WorkstationModeController controller = new WorkstationModeController();
    controller.rememberNormalItem(42L, 3L, 4, 5, 2, 1);

    WorkstationModeController.HomeItemPosition item = controller.normalItem(42L);
    assertNotNull(item);
    assertEquals(3L, item.screenId);
    assertEquals(4, item.cellX);
    assertEquals(5, item.cellY);
    assertEquals(2, item.spanX);
    assertEquals(1, item.spanY);
}

@Test
public void clearingNormalLayoutRemovesAllStoredItems() {
    WorkstationModeController controller = new WorkstationModeController();
    controller.rememberNormalItem(42L, 3L, 4, 5, 2, 1);
    controller.clearNormalLayoutBackup();

    assertFalse(controller.hasNormalLayoutBackup());
    assertNull(controller.normalItem(42L));
}
```

- [ ] **Step 3: Run focused tests and verify failure**

```bash
./gradlew testDebugUnitTest --tests '*WorkstationModeControllerTest' --stacktrace
```

Expected: FAIL because `WorkstationModeController` does not exist.

- [ ] **Step 4: Implement the minimal controller**

Use a monotonic generation and a single pending fallback generation. Keep Android `Handler` out of the controller state machine.

```java
final class WorkstationModeController {
    static final class HomeItemPosition {
        final long screenId;
        final int cellX;
        final int cellY;
        final int spanX;
        final int spanY;

        HomeItemPosition(long screenId, int cellX, int cellY, int spanX, int spanY) {
            this.screenId = screenId;
            this.cellX = cellX;
            this.cellY = cellY;
            this.spanX = spanX;
            this.spanY = spanY;
        }
    }

    private long generation;
    private long pendingFallbackGeneration = -1L;
    private boolean workstationMode;
    private boolean vendorConfirmed;
    private final java.util.Map<Long, HomeItemPosition> normalLayoutBackup =
            new java.util.HashMap<>();

    boolean isWorkstationMode() { return workstationMode; }
    long generation() { return generation; }

    long beginUnconfirmedProbe() {
        vendorConfirmed = false;
        pendingFallbackGeneration = ++generation;
        return pendingFallbackGeneration;
    }

    boolean acceptFallbackProbe(long expectedGeneration, boolean probedMode) {
        if (vendorConfirmed || pendingFallbackGeneration != expectedGeneration) return false;
        workstationMode = probedMode;
        pendingFallbackGeneration = -1L;
        generation++;
        return true;
    }

    void onVendorModeChanged(boolean mode) {
        workstationMode = mode;
        vendorConfirmed = true;
        pendingFallbackGeneration = -1L;
        generation++;
    }

    void clearNormalLayoutBackup() { normalLayoutBackup.clear(); }

    void rememberNormalItem(long itemId, long screenId,
                            int cellX, int cellY, int spanX, int spanY) {
        normalLayoutBackup.put(itemId,
                new HomeItemPosition(screenId, cellX, cellY, spanX, spanY));
    }

    HomeItemPosition normalItem(long itemId) { return normalLayoutBackup.get(itemId); }
    boolean hasNormalLayoutBackup() { return !normalLayoutBackup.isEmpty(); }
}
```

- [ ] **Step 5: Run focused tests and verify PASS**

```bash
./gradlew testDebugUnitTest --tests '*WorkstationModeControllerTest' --stacktrace
```

- [ ] **Step 6: Wire the existing delayed mode fallback through generation ownership**

In `MainHook`:

1. replace direct `workstationMode` / `workstationModeHookConfirmed` mutation with the controller;
2. before the existing 2-second `postDelayed`, call `long expected = controller.beginUnconfirmedProbe()`;
3. inside the delayed runnable, probe the existing vendor API and call `controller.acceptFallbackProbe(expected, probedMode)`;
4. run Workstation transition side effects only when the fallback returns `true`;
5. vendor callback paths call `controller.onVendorModeChanged(mode)` before side effects;
6. do not add a second retry or another delay value.

The 2-second fallback remains only as a boot-time vendor-state probe; generation decides whether it is still authoritative when it executes.

- [ ] **Step 7: Move normal-layout mutable state out of `MainHook`**

Replace the current `normalLayoutBackup.put(id, new HomeItemPosition(...))` with `controller.rememberNormalItem(...)`, and replace restore lookups with `controller.normalItem(id)`. Delete `MainHook.normalLayoutBackup` and its nested `HomeItemPosition` after all call sites compile.

Keep the existing View-tree collection and field writes semantically unchanged. This task moves state ownership; it does not redesign placement.

- [ ] **Step 8: Run Workstation/global tests**

```bash
./gradlew testDebugUnitTest --tests '*Workstation*' --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS and no new source-reader debt exception.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/WorkstationModeController.java \
        src/main/java/com/hellovoid/liquiddock/MainHook.java \
        src/test/java/com/hellovoid/liquiddock/WorkstationModeControllerTest.java
git commit -m "refactor: centralize workstation mode ownership"
```

---

### Task 2: Centralize Widget classification on the real HookUtil API

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/WidgetClassifier.java`
- Create: `src/test/java/com/hellovoid/liquiddock/WidgetClassifierTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/HomeGridHook.java`

**Interfaces:**

```java
static boolean isWidget(Object itemInfo);
static boolean isWidgetFallbackType(int itemType);
```

- [ ] **Step 1: Write tests covering vendor-primary and fallback behavior**

```java
public static final class FakeItemInfo {
    public int itemType;
    private final boolean widget;

    FakeItemInfo(boolean widget, int itemType) {
        this.widget = widget;
        this.itemType = itemType;
    }

    public boolean isWidget() { return widget; }
}

public static final class FallbackOnlyItemInfo {
    public int itemType;
    FallbackOnlyItemInfo(int itemType) { this.itemType = itemType; }
}

@Test
public void vendorIsWidgetTrueWins() {
    assertTrue(WidgetClassifier.isWidget(new FakeItemInfo(true, 0)));
}

@Test
public void knownItemTypeFallbackIsAcceptedWhenVendorMethodIsFalse() {
    assertTrue(WidgetClassifier.isWidget(new FakeItemInfo(false, 4)));
}

@Test
public void knownItemTypeFallbackIsAcceptedWhenVendorMethodIsUnavailable() {
    assertTrue(WidgetClassifier.isWidget(new FallbackOnlyItemInfo(19)));
}

@Test
public void unrelatedTypeIsRejected() {
    assertFalse(WidgetClassifier.isWidget(new FakeItemInfo(false, 1)));
}
```

- [ ] **Step 2: Verify focused failure**

```bash
./gradlew testDebugUnitTest --tests '*WidgetClassifierTest' --stacktrace
```

- [ ] **Step 3: Implement using the existing `HookUtil.tryInvoke` signature**

```java
final class WidgetClassifier {
    private WidgetClassifier() {}

    static boolean isWidgetFallbackType(int itemType) {
        return itemType == 4 || itemType == 5 || itemType == 19;
    }

    static boolean isWidget(Object itemInfo) {
        if (itemInfo == null) return false;
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(itemInfo, "isWidget");
        if (result.succeeded() && Boolean.TRUE.equals(result.value())) return true;
        return isWidgetFallbackType(HookUtil.getIntField(itemInfo, "itemType"));
    }
}
```

- [ ] **Step 4: Replace direct Widget classification in `HomeGridHook`**

Every Widget adaptation path must call `WidgetClassifier.isWidget(info)`. Remove the direct `itemType == 4 || itemType == 5 || itemType == 19` branch from `HomeGridHook`.

- [ ] **Step 5: Run focused/global tests**

```bash
./gradlew testDebugUnitTest --tests '*WidgetClassifierTest' --stacktrace
./gradlew testDebugUnitTest --tests '*WidgetGridSizingTest' --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/WidgetClassifier.java \
        src/main/java/com/hellovoid/liquiddock/HomeGridHook.java \
        src/test/java/com/hellovoid/liquiddock/WidgetClassifierTest.java
git commit -m "refactor: centralize widget classification"
```

---

### Task 3: Make Widget span policy immutable and Widget sizing stateless

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/WidgetSpecRegistry.java`
- Create: `src/test/java/com/hellovoid/liquiddock/WidgetSpecRegistryTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/WidgetGridSizing.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/HomeGridHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/WidgetGridSizingTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/ConfigLoadPolicyTest.java`

**Interfaces:**

```java
WidgetSpecRegistry.DEFAULT.supports(int spanX, int spanY);
WidgetGridSizing.gridRect(boolean adaptationEnabled,
                          int cellX, int cellY, int spanX, int spanY,
                          int[] xs, int[] ys,
                          int cellWidth, int cellHeight,
                          int widthGap, int heightGap);
```

- [ ] **Step 1: Write registry tests**

```java
@Test
public void defaultRegistryContainsExactlyCurrentSpecs() {
    WidgetSpecRegistry registry = WidgetSpecRegistry.DEFAULT;
    assertTrue(registry.supports(1, 1));
    assertTrue(registry.supports(2, 1));
    assertTrue(registry.supports(2, 2));
    assertTrue(registry.supports(4, 2));
    assertFalse(registry.supports(3, 2));
    assertFalse(registry.supports(4, 1));
    assertFalse(registry.supports(1, 2));
}
```

- [ ] **Step 2: Implement the immutable registry with a complete lookup**

```java
final class WidgetSpecRegistry {
    static final WidgetSpecRegistry DEFAULT = new WidgetSpecRegistry(
            new int[][]{{1, 1}, {2, 1}, {2, 2}, {4, 2}});

    private final int[][] specs;

    private WidgetSpecRegistry(int[][] specs) {
        this.specs = new int[specs.length][2];
        for (int i = 0; i < specs.length; i++) {
            this.specs[i][0] = specs[i][0];
            this.specs[i][1] = specs[i][1];
        }
    }

    boolean supports(int spanX, int spanY) {
        for (int[] spec : specs) {
            if (spec[0] == spanX && spec[1] == spanY) return true;
        }
        return false;
    }
}
```

- [ ] **Step 3: Rewrite sizing tests to pass enable state explicitly**

Remove all calls to `WidgetGridSizing.setWidgetAdaptationEnabled(...)`. Add:

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

Pass `true` as the first argument in existing geometry tests.

- [ ] **Step 4: Verify focused failure**

```bash
./gradlew testDebugUnitTest --tests '*WidgetSpecRegistryTest' \
        --tests '*WidgetGridSizingTest' --stacktrace
```

- [ ] **Step 5: Remove process-global config from `WidgetGridSizing`**

Delete:

```java
private static volatile boolean widgetAdaptationEnabled;
static void setWidgetAdaptationEnabled(boolean enabled)
```

Change `gridRect(...)` to return the empty rectangle when its explicit `adaptationEnabled` argument is false. Keep all existing bounds/axis math otherwise unchanged.

- [ ] **Step 6: Remove the setter call from `MainHook.install()`**

Delete `WidgetGridSizing.setWidgetAdaptationEnabled(...)`. Compute the immutable install decision once from:

```java
boolean widgetAdaptationEnabled =
        WidgetGridSizing.shouldAdaptWidgets(config.grid.enabled, config.grid.widgetAdaptation);
```

Pass that value into the Grid/Widget Hook owner; do not store it back in `WidgetGridSizing`.

- [ ] **Step 7: Route supported-span decisions through the registry**

Before adapting a Widget frame, require both:

```java
WidgetClassifier.isWidget(info)
WidgetSpecRegistry.DEFAULT.supports(spanX, spanY)
```

Do not change placement/occupancy code.

- [ ] **Step 8: Run Widget/Grid/config tests**

```bash
./gradlew testDebugUnitTest --tests '*Widget*' --stacktrace
./gradlew testDebugUnitTest --tests '*HomeGrid*' --stacktrace
./gradlew testDebugUnitTest --tests '*ConfigLoadPolicyTest' --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS and no test resets a process-global Widget sizing flag.

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

### Task 4: Extract the real Widget layout Hook owner from `HomeGridHook`

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/HomeGridWidgetAdaptationHook.java`
- Create: `src/test/java/com/hellovoid/liquiddock/HomeGridWidgetAdaptationHookTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/HomeGridHook.java`

**Interfaces:**

```java
HomeGridWidgetAdaptationHook(boolean adaptationEnabled);
void install(ClassLoader classLoader);
boolean shouldAdapt(Object itemInfo, int spanX, int spanY);
```

The extracted owner installs only the existing Widget frame hooks around `CellLayout.setupLayoutParam()` and post-layout enforcement after `CellLayout.onLayout()`.

- [ ] **Step 1: Write a production-used decision test**

```java
@Test
public void disabledOwnerNeverAdapts() {
    HomeGridWidgetAdaptationHook hook = new HomeGridWidgetAdaptationHook(false);
    WidgetClassifierTest.FakeItemInfo info =
            new WidgetClassifierTest.FakeItemInfo(true, 4);
    assertFalse(hook.shouldAdapt(info, 2, 1));
}

@Test
public void enabledOwnerRequiresWidgetAndSupportedSpan() {
    HomeGridWidgetAdaptationHook hook = new HomeGridWidgetAdaptationHook(true);
    WidgetClassifierTest.FakeItemInfo info =
            new WidgetClassifierTest.FakeItemInfo(true, 4);
    assertTrue(hook.shouldAdapt(info, 2, 1));
    assertFalse(hook.shouldAdapt(info, 3, 2));
}
```

If sharing the test fake across classes is awkward, define the same minimal fake inside this test; do not add reflection solely for test access.

- [ ] **Step 2: Implement the decision used by the callbacks**

```java
boolean shouldAdapt(Object itemInfo, int spanX, int spanY) {
    return adaptationEnabled
            && WidgetClassifier.isWidget(itemInfo)
            && WidgetSpecRegistry.DEFAULT.supports(spanX, spanY);
}
```

- [ ] **Step 3: Move the existing Widget hook installation/callback code**

Move only the code that:

- intercepts/adjusts `CellLayout.setupLayoutParam()` for Widget allocation;
- reasserts the final Widget frame after `CellLayout.onLayout()`.

Do not move cell count, page indicator, folder alignment, orientation memory, rotation/refresh, or occupancy/placement logic.

The new class receives `adaptationEnabled` in its constructor and calls the same `WidgetGridSizing.gridRect(true, ...)` geometry path only after `shouldAdapt(...)` returns true.

- [ ] **Step 4: Remove duplicate Widget frame ownership from `HomeGridHook`**

`HomeGridHook` installs `HomeGridWidgetAdaptationHook` but no longer contains the two Widget frame callbacks themselves.

- [ ] **Step 5: Run focused/global tests**

```bash
./gradlew testDebugUnitTest --tests '*HomeGridWidgetAdaptationHookTest' --stacktrace
./gradlew testDebugUnitTest --tests '*HomeGrid*' --tests '*Widget*' --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/HomeGridWidgetAdaptationHook.java \
        src/main/java/com/hellovoid/liquiddock/HomeGridHook.java \
        src/test/java/com/hellovoid/liquiddock/HomeGridWidgetAdaptationHookTest.java
git commit -m "refactor: extract widget grid adaptation owner"
```

---

### Task 5: Replace silent bundled Widget-rule degradation with one-shot structured diagnostics

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/OneShotDiagnostic.java`
- Create: `src/test/java/com/hellovoid/liquiddock/WidgetBackgroundRuleDiagnosticsTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/WidgetBackgroundRuleEngine.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherMamlBackgroundRuleExecutor.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/WidgetBackgroundRuleEngineTest.java`

**Interfaces:**

```java
final class OneShotDiagnostic {
    OneShotDiagnostic(java.util.function.Consumer<String> sink);
    void emit(String key, String message);
}
```

`WidgetBackgroundRuleEngine` adds:

```java
enum LoadStatus { LOADED, MISSING_RESOURCE, PARSE_FAILED }
static final class LoadResult {
    WidgetBackgroundRuleEngine engine();
    LoadStatus status();
}
static LoadResult loadBundled();
static LoadResult loadBundled(ClassLoader loader);
```

`parse(InputStream)` remains available and continues to fail-safe to `EMPTY` for parser unit tests/callers.

- [ ] **Step 1: Write the one-shot diagnostic test**

```java
@Test
public void diagnosticKeyEmitsOnlyOnce() {
    java.util.List<String> messages = new java.util.ArrayList<>();
    OneShotDiagnostic diagnostic = new OneShotDiagnostic(messages::add);

    diagnostic.emit("widget_rules_load_failed", "first");
    diagnostic.emit("widget_rules_load_failed", "second");

    assertEquals(java.util.List.of("first"), messages);
}
```

- [ ] **Step 2: Implement `OneShotDiagnostic`**

```java
final class OneShotDiagnostic {
    private final java.util.function.Consumer<String> sink;
    private final java.util.Set<String> emitted =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    OneShotDiagnostic(java.util.function.Consumer<String> sink) {
        this.sink = java.util.Objects.requireNonNull(sink);
    }

    void emit(String key, String message) {
        if (emitted.add(key)) sink.accept(message);
    }
}
```

- [ ] **Step 3: Add load-status tests**

Use a test `ClassLoader` whose `getResourceAsStream(...)` returns `null` for missing-resource coverage and a malformed XML stream for parse-failure coverage.

```java
@Test
public void missingBundledResourceIsDistinguishable() {
    ClassLoader loader = new ClassLoader(null) {
        @Override
        public java.io.InputStream getResourceAsStream(String name) {
            return null;
        }
    };
    assertEquals(
            WidgetBackgroundRuleEngine.LoadStatus.MISSING_RESOURCE,
            WidgetBackgroundRuleEngine.loadBundled(loader).status());
}

@Test
public void malformedBundledResourceIsDistinguishable() {
    ClassLoader loader = new ClassLoader(null) {
        @Override
        public java.io.InputStream getResourceAsStream(String name) {
            return new java.io.ByteArrayInputStream("<broken".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    };
    assertEquals(
            WidgetBackgroundRuleEngine.LoadStatus.PARSE_FAILED,
            WidgetBackgroundRuleEngine.loadBundled(loader).status());
}
```

Also assert both failure results return a non-null engine whose `match(...)` behaves like the existing empty engine.

- [ ] **Step 4: Implement strict internal parse plus fail-safe public parse**

Refactor XML construction into a private `parseStrict(InputStream)` that throws on malformed XML. Keep:

```java
static WidgetBackgroundRuleEngine parse(InputStream input) {
    if (input == null) return EMPTY;
    try {
        return parseStrict(input);
    } catch (Throwable ignored) {
        return EMPTY;
    }
}
```

Implement `loadBundled(ClassLoader)` so `null` loader/input returns `MISSING_RESOURCE`, successful `parseStrict` returns `LOADED`, and thrown parse errors return `PARSE_FAILED`; both failure statuses carry `EMPTY` as the engine.

- [ ] **Step 5: Wire one-shot logging in `LauncherMamlBackgroundRuleExecutor`**

Replace the current direct `RULES = WidgetBackgroundRuleEngine.loadBundled()` initialization with:

```java
private static final WidgetBackgroundRuleEngine.LoadResult RULE_LOAD =
        WidgetBackgroundRuleEngine.loadBundled();
private static final WidgetBackgroundRuleEngine RULES = RULE_LOAD.engine();
private static final OneShotDiagnostic DIAGNOSTIC =
        new OneShotDiagnostic(MainHook::log);

static {
    if (RULE_LOAD.status() != WidgetBackgroundRuleEngine.LoadStatus.LOADED) {
        DIAGNOSTIC.emit(
                "widget_rules_" + RULE_LOAD.status().name(),
                "[DC][WidgetRules] bundled_rules_unavailable status=" + RULE_LOAD.status());
    }
}
```

Do not log from `match()`.

- [ ] **Step 6: Run focused/global tests**

```bash
./gradlew testDebugUnitTest --tests '*WidgetBackgroundRule*' --stacktrace
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS; normal valid bundled rules produce no warning.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/OneShotDiagnostic.java \
        src/main/java/com/hellovoid/liquiddock/WidgetBackgroundRuleEngine.java \
        src/main/java/com/hellovoid/liquiddock/LauncherMamlBackgroundRuleExecutor.java \
        src/test/java/com/hellovoid/liquiddock/WidgetBackgroundRuleEngineTest.java \
        src/test/java/com/hellovoid/liquiddock/WidgetBackgroundRuleDiagnosticsTest.java
git commit -m "fix: report bundled widget rule degradation"
```

---

### Task 6: Full Phase 1 verification and documentation closure

**Files:**
- Create: `docs/superpowers/verification/2026-09-07-technical-debt-cleanup-phase1.md`
- Modify: `TODO.md`
- Modify: `ARCHITECTURE.md`
- Modify: `HOOKS.md`
- Modify: `CONTRIBUTING.md`
- Modify: `FEATURES.md` only when the implementation changes a user-visible description.

- [ ] **Step 1: Run the full unit suite**

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Step 2: Build Debug APK**

```bash
./gradlew assembleDebug --stacktrace
```

Expected: PASS.

- [ ] **Step 3: Confirm the source-reader debt gate did not grow**

Run the full tests and verify `RuntimeBehaviorTestPolicyContractTest` passes with the existing-or-smaller `LEGACY_SOURCE_DEBT` set. Do not add any new debt entry to make Phase 1 green.

- [ ] **Step 4: Run the device matrix**

Record PASS/FAIL plus relevant log excerpts for:

1. normal Launcher startup;
2. Workstation enter -> exit -> quick re-enter;
3. vendor Workstation callback before delayed fallback;
4. stale delayed fallback after a newer transition;
5. normal-layout backup/restore;
6. HOME -> Recents -> HOME at least five consecutive times;
7. Recents-adjacent rotation;
8. 1×1 / 2×1 / 2×2 / 4×2 Widgets in portrait and landscape;
9. Widget adaptation disabled;
10. normal mode without Workstation;
11. normal bundled Widget-rule load with no warning;
12. test-only missing/malformed bundled-rule paths producing one warning and empty-rule fail-safe behavior.

- [ ] **Step 5: Write the verification record**

The verification file must contain:

- tested commit SHA;
- device / ROM / Launcher version;
- exact Gradle commands and exit status;
- each device-matrix result;
- exact reproduction/log excerpt for every failure;
- explicit statement that Phase 1 did not alter producer/EGL/OES/fresh-frame authority.

Do not mark the phase complete while a required device case is untested.

- [ ] **Step 6: Update current-state docs after implementation**

Only after code is verified:

- mark completed Phase 1 debt in `TODO.md`;
- describe `WorkstationModeController` as current production ownership in `ARCHITECTURE.md` / `HOOKS.md`;
- remove current-state descriptions of the static Widget flag and direct magic-number branch after they are actually gone;
- shrink the `HomeGridHook` responsibility list only for ownership that actually moved;
- keep producer/Recents/unlock correctness boundaries unchanged.

- [ ] **Step 7: Commit verification/docs**

```bash
git add TODO.md ARCHITECTURE.md HOOKS.md CONTRIBUTING.md \
        docs/superpowers/verification/2026-09-07-technical-debt-cleanup-phase1.md
git commit -m "docs: close phase 1 debt cleanup verification"
```

Add `FEATURES.md` to that commit only if user-visible wording changed.

---

## Phase 1 Stop Gate

Do not begin EGL/OES/producer lifecycle extraction merely because `LauncherGlassSession` and `Miuix307PassBlurTextureView` remain large.

Phase 2/3 starts only after:

1. all Phase 1 CI gates pass;
2. the Workstation/Grid device matrix is recorded;
3. no regression requires changing the existing fresh-frame authority;
4. a resource-owner graph explicitly records creator, thread/context, generation, bind/rebind owner, and release trigger for `LauncherGlassSession`, `Miuix307PassBlurTextureView`, and Prismal resources.

The next glass implementation plan must identify exact lifecycle primitives proven common by that owner graph before introducing any shared resource abstraction.
