# HookUtil Vendor Reflection Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace LiquidDock's silent-null vendor reflection with explicit `tryInvoke*` / `requireInvoke*` contracts, deterministic cached member resolution, and typed package-private `LauncherGlassSession` lifecycle APIs.

**Architecture:** `HookUtil` remains the vendor/private reflection façade, while a pure-Java `VendorMemberResolver` owns deterministic overload/field resolution and caching. Existing vendor call sites are migrated deliberately to optional or required invocation semantics, and all `LauncherGlassSessionRegistry -> LauncherGlassSession` self-reflection is removed in favor of typed package-private methods.

**Tech Stack:** Java 17, Android/AGP, libxposed API 101, JUnit 4 host-side tests, R8 keep rules, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-04-hookutil-vendor-contract-design.md`

## Global Constraints

- Base branch is `main@d68c3c12d713513d5df12b0c8a3c6fad57cdebec`; implementation branch is `refactor/hookutil-vendor-contract`.
- Keep libxposed hook installation APIs (`hook`, `hookMethod`, `findMethodExact`) behaviorally unchanged.
- Do not add varargs expansion or numeric primitive widening to dynamic invocation.
- `tryInvoke*` must distinguish successful `null` from failure.
- `requireInvoke*` must throw `VendorReflectionException` at the reflection boundary on resolution/invocation failure.
- True overload ambiguity must never be resolved by reflection enumeration order.
- Method/field cache identity must use `Class<?>`, never class-name strings.
- `LauncherGlassSessionRegistry` must not access project-owned `LauncherGlassSession` through `HookUtil` or raw Java reflection.
- `LauncherGlassSession` lifecycle methods remain package-private; do not make renderer internals public.
- Unlock rollover completion means render-queue endpoint rollover finished, not that a fresh OES frame has arrived.
- Workstation rebound diagnostics count only accepted/enqueued rollovers.
- Preserve existing fail-soft vendor compatibility paths where absence is expected.
- Do not mix unrelated MainHook/Grid/Widget/GPU refactors into this branch.
- Verification gate is `./gradlew testDebugUnitTest --stacktrace` followed by `./gradlew assembleDebug --stacktrace`.

---

## File Structure

### New production files

- `src/main/java/com/hellovoid/liquiddock/VendorMemberResolver.java` — pure-Java method/field resolution, ambiguity detection, accessibility setup, and caches.
- `src/main/java/com/hellovoid/liquiddock/VendorReflectionException.java` — required-reflection boundary exception carrying structured failure context.

### Modified core files

- `src/main/java/com/hellovoid/liquiddock/HookUtil.java` — `tryInvoke*`, `requireInvoke*`, `InvocationResult`, resolver delegation, cached field access, removal of old silent-null APIs.
- `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java` — package-private producer suspend/rebind APIs and callback sequencing.
- `src/main/java/com/hellovoid/liquiddock/LauncherGlassSessionRegistry.java` — typed Session calls only.
- `src/main/keepRules/runtime-reflection.keep` — remove Session self-reflection keeps.
- `TODO.md` — mark the PR #65 self-reflection hardening item completed while leaving unrelated Workstation TODOs intact.

### Vendor call-site migration groups

**Grid / Workstation / geometry**
- `HomeGridHook.java`
- `HomeGridDragBoundsHook.java`
- `HomeGridVerticalBoundsHook.java`
- `HomeGridProfileOverlayHook.java`
- `WorkstationDockGeometryHook.java`
- `WorkspaceDropRuleHook.java`
- `MainHook.java`

**Dock / Glass**
- `Miuix307MaterialPipeline.java`
- `DockBottomGeometryHook.java`
- `DockDividerHook.java`
- `DockIconAnimationGlassHook.java`
- `DockMirrorShortcutHook.java`
- `DockAnimationTrace.java`
- `DockStrokeRenderer.java`
- `MiuixFolderGlassHook.java`
- `MiuixLauncherStaticGlassHook.java`

**Widget / MAML**
- `LauncherWidgetDarkContentAdapter.java`
- `LauncherMamlBackgroundRuleExecutor.java`
- `LauncherWidgetComponentSelectionExecutor.java`
- `LauncherWidgetComponentDiscovery.java`
- `LauncherWidgetTransitionCoordinator.java`
- `LauncherWidgetTransitionHook.java`

**SystemUI**
- `SystemUiKeyguardGoneRuntime.java`
- `SystemUiKeyguardGoneSource.java`
- `SystemUiHomeTransitionRuntime.java`
- `SystemUiHomeTransitionSource.java`

### New / modified tests

- Create `src/test/java/com/hellovoid/liquiddock/VendorMemberResolverTest.java`.
- Create `src/test/java/com/hellovoid/liquiddock/HookUtilInvocationContractTest.java`.
- Create `src/test/java/com/hellovoid/liquiddock/HookUtilArchitectureContractTest.java`.
- Modify `src/test/java/com/hellovoid/liquiddock/R8ReleaseKeepContractTest.java`.
- Modify `src/test/java/com/hellovoid/liquiddock/WorkstationStaticLayerRecentsRecoveryContractTest.java`.
- Update existing source-contract tests only when they explicitly assert old `HookUtil.invoke*` source text.

---

### Task 1: Deterministic cached member resolver

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/VendorMemberResolver.java`
- Create: `src/test/java/com/hellovoid/liquiddock/VendorMemberResolverTest.java`

**Interfaces:**
- Produces: `VendorMemberResolver.resolveMethod(Class<?> targetClass, String name, Object[] args, boolean requireStatic)`.
- Produces: `VendorMemberResolver.resolveField(Class<?> targetClass, String name)`.
- Produces immutable `MethodResolution` with statuses `RESOLVED`, `NOT_FOUND`, `AMBIGUOUS`, `ACCESS_FAILURE`.
- Produces immutable `FieldResolution` with statuses `RESOLVED`, `NOT_FOUND`, `ACCESS_FAILURE`.
- Produces package-private cache test hooks: `clearCachesForTests()`, `methodCacheSizeForTests()`, `fieldCacheSizeForTests()`.

- [ ] **Step 1: Write resolver tests before production code**

Create fixtures inside `VendorMemberResolverTest` covering:

```java
static class Base {
    String choose(Object value) { return "object"; }
    String choose(Number value) { return "number"; }
    String primitive(int value) { return "int"; }
    String nullable(Object value) { return "object"; }
    String ambiguous(CharSequence value) { return "chars"; }
    String ambiguous(Number value) { return "number"; }
    static String staticOnly(Integer value) { return "static"; }
    String instanceOnly(Integer value) { return "instance"; }
    private int inheritedField = 7;
}

static class Derived extends Base {
    String choose(Integer value) { return "integer"; }
    String nullable(String value) { return "string"; }
    @Override String choose(Number value) { return "derived-number"; }
}
```

Required assertions:

```java
assertEquals(Derived.class,
        resolve(Derived.class, "choose", new Object[]{1}, false).method().getDeclaringClass());
assertArrayEquals(new Class<?>[]{Integer.class},
        resolve(...).method().getParameterTypes());
assertArrayEquals(new Class<?>[]{int.class},
        resolve(Derived.class, "primitive", new Object[]{1}, false).method().getParameterTypes());
assertArrayEquals(new Class<?>[]{String.class},
        resolve(Derived.class, "nullable", new Object[]{null}, false).method().getParameterTypes());
assertEquals(MethodStatus.AMBIGUOUS,
        resolve(Derived.class, "ambiguous", new Object[]{null}, false).status());
assertEquals(MethodStatus.NOT_FOUND,
        resolve(Derived.class, "staticOnly", new Object[]{1}, false).status());
assertEquals(MethodStatus.NOT_FOUND,
        resolve(Derived.class, "instanceOnly", new Object[]{1}, true).status());
```

Also verify nearest overridden declaration wins, missing lookups are cached, ambiguous lookups are cached, and `resolveField(Derived.class, "inheritedField")` finds the Base field once and then hits the field cache.

- [ ] **Step 2: Run the focused resolver test and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests "com.hellovoid.liquiddock.VendorMemberResolverTest" --stacktrace
```

Expected: compilation/test failure because `VendorMemberResolver` does not exist.

- [ ] **Step 3: Implement resolver candidate filtering and cache keys**

Use concurrent maps keyed by class identity, method name, static flag, and runtime arg type vector with a dedicated null marker. Do not key by strings alone.

Candidate filter:

```java
if (!method.getName().equals(name)) continue;
if (Modifier.isStatic(method.getModifiers()) != requireStatic) continue;
if (method.getParameterCount() != args.length) continue;
if (!parametersApplicable(method.getParameterTypes(), args)) continue;
```

Applicability rule:

```java
if (arg == null) return !parameter.isPrimitive();
if (parameter.isPrimitive()) return wrap(parameter) == arg.getClass();
return parameter.isAssignableFrom(arg.getClass());
```

No numeric widening and no varargs expansion.

- [ ] **Step 4: Implement deterministic specificity / ambiguity**

For each pair of applicable candidates, define `isStrictlyMoreSpecific(a, b, args, targetClass)`:

- exact reference type beats primitive-wrapper exact;
- primitive-wrapper exact beats an assignable supertype;
- smaller class/interface distance beats larger distance;
- for `null`, subtype parameter beats supertype parameter;
- unrelated null parameter types are incomparable;
- equal erased parameter signatures prefer declaration nearest `targetClass`;
- otherwise-equivalent real declarations beat bridge/synthetic declarations.

Compute the non-dominated candidate set. One candidate means resolved; more than one means `AMBIGUOUS`; zero applicable candidates means `NOT_FOUND`. Sort only diagnostic signature strings, never to decide the winner.

- [ ] **Step 5: Implement cached field resolution**

Walk `targetClass -> superclass` and return the nearest exact field name. Call `setAccessible(true)` once before caching the positive result. Cache `NOT_FOUND` and `ACCESS_FAILURE` too.

- [ ] **Step 6: Re-run focused tests and verify GREEN**

Run the same focused Gradle command; expected PASS.

- [ ] **Step 7: Commit Task 1**

```bash
git add src/main/java/com/hellovoid/liquiddock/VendorMemberResolver.java \
        src/test/java/com/hellovoid/liquiddock/VendorMemberResolverTest.java
git commit -m "refactor: add deterministic vendor member resolver"
```

---

### Task 2: Explicit try/require invocation contract and cached field access

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/HookUtil.java`
- Create: `src/main/java/com/hellovoid/liquiddock/VendorReflectionException.java`
- Create: `src/test/java/com/hellovoid/liquiddock/HookUtilInvocationContractTest.java`

**Interfaces:**
- Consumes: `VendorMemberResolver` from Task 1.
- Produces nested `HookUtil.InvocationResult<T>` and `HookUtil.Failure` / `FailureKind`.
- Produces `tryInvoke`, `tryInvokeStatic(Class<?>)`, `tryInvokeStatic(String)`.
- Produces `requireInvoke`, `requireInvokeStatic(Class<?>)`, `requireInvokeStatic(String)`.
- Existing `invoke` / `invokeStatic` remain temporarily only so unmigrated call sites compile; they are removed in Task 6.

- [ ] **Step 1: Write invocation-contract tests**

Fixtures must include:

```java
static final class Fixture {
    String returnsNull() { return null; }
    String echo(String value) { return value; }
    String explode() { throw new IllegalStateException("boom"); }
    String ambiguous(CharSequence value) { return "chars"; }
    String ambiguous(Number value) { return "number"; }
    static String staticEcho(String value) { return value; }
}
```

Assert:

```java
HookUtil.InvocationResult<Object> ok = HookUtil.tryInvoke(fixture, "returnsNull");
assertTrue(ok.succeeded());
assertNull(ok.value());
assertNull(ok.failure());

HookUtil.InvocationResult<Object> missing = HookUtil.tryInvoke(fixture, "missing");
assertFalse(missing.succeeded());
assertEquals(HookUtil.FailureKind.METHOD_NOT_FOUND, missing.failure().kind());

HookUtil.InvocationResult<Object> ambiguous = HookUtil.tryInvoke(fixture, "ambiguous", (Object) null);
assertEquals(HookUtil.FailureKind.AMBIGUOUS_METHOD, ambiguous.failure().kind());
assertEquals(2, ambiguous.failure().candidateSignatures().size());

HookUtil.InvocationResult<Object> thrown = HookUtil.tryInvoke(fixture, "explode");
assertEquals(HookUtil.FailureKind.INVOCATION_FAILURE, thrown.failure().kind());
assertTrue(thrown.failure().cause() instanceof IllegalStateException);

try {
    HookUtil.requireInvoke(fixture, "missing");
    fail("required invocation must throw");
} catch (VendorReflectionException expected) {
    assertEquals(HookUtil.FailureKind.METHOD_NOT_FOUND, expected.failure().kind());
}
```

Also cover target-null, class-not-found string static call, static success, and ambiguous required invocation.

- [ ] **Step 2: Verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests "com.hellovoid.liquiddock.HookUtilInvocationContractTest" --stacktrace
```

Expected: failure because new APIs/result types do not exist.

- [ ] **Step 3: Add `InvocationResult`, `Failure`, and `FailureKind`**

`FailureKind` must contain exactly the currently needed categories:

```java
TARGET_NULL,
CLASS_NOT_FOUND,
METHOD_NOT_FOUND,
AMBIGUOUS_METHOD,
ACCESS_FAILURE,
INVOCATION_FAILURE
```

`Failure` stores target class name, method name, static flag, runtime arg type labels including `<null>`, resolved method when available, immutable candidate signatures, and cause.

- [ ] **Step 4: Implement `tryInvoke*`**

Flow:

```java
if (target == null) return InvocationResult.failure(targetNullFailure(...));
MethodResolution resolution = VendorMemberResolver.resolveMethod(...);
if (!resolution.resolved()) return InvocationResult.failure(mapResolutionFailure(...));
try {
    Object value = resolution.method().invoke(targetOrNull, args);
    return InvocationResult.success(value, resolution.method());
} catch (InvocationTargetException error) {
    return InvocationResult.failure(invocationFailure(error.getCause(), ...));
} catch (IllegalAccessException error) {
    return InvocationResult.failure(accessFailure(error, ...));
} catch (Throwable error) {
    return InvocationResult.failure(invocationFailure(error, ...));
}
```

For string static calls, `Class.forName(className)` failure maps to `CLASS_NOT_FOUND`.

- [ ] **Step 5: Implement `requireInvoke*`**

Each required API delegates to its `try*` counterpart; on failure it throws `new VendorReflectionException(result.failure())`, otherwise returns `result.value()` including legitimate null.

- [ ] **Step 6: Route field helpers through resolver cache**

Replace the manual superclass walk in `findField` with `VendorMemberResolver.resolveField`. Preserve existing throwing semantics for `getField`, primitive getters, and setters. Do not add optional field APIs in this task.

- [ ] **Step 7: Run focused resolver + invocation tests**

```bash
./gradlew testDebugUnitTest \
  --tests "com.hellovoid.liquiddock.VendorMemberResolverTest" \
  --tests "com.hellovoid.liquiddock.HookUtilInvocationContractTest" \
  --stacktrace
```

Expected: PASS.

- [ ] **Step 8: Commit Task 2**

```bash
git add src/main/java/com/hellovoid/liquiddock/HookUtil.java \
        src/main/java/com/hellovoid/liquiddock/VendorReflectionException.java \
        src/test/java/com/hellovoid/liquiddock/HookUtilInvocationContractTest.java
git commit -m "refactor: split optional and required vendor invocation"
```

---

### Task 3: Migrate Grid / Workstation / geometry vendor calls

**Files:**
- Modify: `HomeGridHook.java`
- Modify: `HomeGridDragBoundsHook.java`
- Modify: `HomeGridVerticalBoundsHook.java`
- Modify: `HomeGridProfileOverlayHook.java`
- Modify: `WorkstationDockGeometryHook.java`
- Modify: `WorkspaceDropRuleHook.java`
- Modify: `MainHook.java`
- Modify relevant existing tests if they assert exact old call text.

**Interfaces:**
- Consumes Task 2 `tryInvoke*` / `requireInvoke*`.
- Produces no old `HookUtil.invoke*` calls in this file group.

**Migration matrix:**

- `HomeGridHook.isWidget()` — `tryInvoke`; if unavailable, keep existing `itemType` fallback.
- `HomeGridDragBoundsHook.getRootView()` — `tryInvoke`; keep existing alternate root/bounds path.
- `HomeGridVerticalBoundsHook.getCellSize()` and equivalent probe reads — `tryInvoke`; preserve existing numeric/type fallback.
- `HomeGridProfileOverlayHook.getMHCells()/getMVCells()` — `tryInvoke`; `transformCounts()` returns null when either call fails or returns non-Integer, exactly as current compatibility behavior intends.
- `WorkstationDockGeometryHook` vendor geometry reads — `tryInvoke` where current code checks type/has fallback; use `requireInvoke` only for immediately cast/unboxed values inside an already installed exact vendor hook.
- `WorkspaceDropRuleHook.getCellCountX()/getCellCountY()` — `tryInvokeStatic`; preserve skip behavior when values are unavailable/non-Integer.
- `MainHook.getItemCount()` inside known Recycler/list hook — `requireInvoke` because current code immediately casts/unboxes and cannot continue meaningfully without the count.
- `MainHook` workstation/laptop mode probes — `tryInvokeStatic`; preserve existing fallback chain.

- [ ] **Step 1: Add/adjust group contract assertions**

Where an existing source contract currently asserts `HookUtil.invoke`, change it to assert the intended `tryInvoke` or `requireInvoke`. Do not add broad string tests for implementation details that behavior tests already cover.

- [ ] **Step 2: Run affected Grid/Workstation tests before migration and confirm the new source assertions fail**

Use existing targeted test class names found beside the touched code; include at minimum `WorkstationAllAppsHookContractTest` when present.

- [ ] **Step 3: Migrate every call in the seven files**

Optional read pattern:

```java
HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(target, "method");
Object value = result.succeeded() ? result.value() : null;
```

Required pattern:

```java
int itemCount = (Integer) HookUtil.requireInvoke(chain.getThisObject(), "getItemCount");
```

Every `tryInvoke*` call must inspect `succeeded()`; do not recreate silent-null by reading only `value()`.

- [ ] **Step 4: Run affected tests and compile test sources**

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS before moving to the next migration group.

- [ ] **Step 5: Commit Task 3**

Commit message:

```text
refactor: migrate grid vendor reflection contract
```

---

### Task 4: Migrate Dock / Glass vendor calls

**Files:**
- Modify: `Miuix307MaterialPipeline.java`
- Modify: `DockBottomGeometryHook.java`
- Modify: `DockDividerHook.java`
- Modify: `DockIconAnimationGlassHook.java`
- Modify: `DockMirrorShortcutHook.java`
- Modify: `DockAnimationTrace.java`
- Modify: `DockStrokeRenderer.java`
- Modify: `MiuixFolderGlassHook.java`
- Modify: `MiuixLauncherStaticGlassHook.java`
- Modify existing Dock/Glass source contracts that assert old call text.

**Migration matrix:**

- `Miuix307MaterialPipeline.getItemCount()` in the installed vendor adapter/list hook — `requireInvoke` because the value is immediately cast/unboxed and controls mutation.
- `DockBottomGeometryHook` GridController/config lookup chain — `tryInvokeStatic` + `tryInvoke`; keep current try/fallback geometry behavior.
- `DockDividerHook` vendor helper reads/mutations — `tryInvoke` unless the surrounding exact hook already requires the returned value to maintain its invariant.
- `DockIconAnimationGlassHook` compatibility/probe calls — `tryInvoke`.
- `DockMirrorShortcutHook.onMirrorSeatUpdate()` — `tryInvoke`; increment `refreshed` only when `succeeded()` is true. On failure log the structured failure instead of producing a false-success count.
- `DockAnimationTrace` — `tryInvoke`; diagnostics must print an unavailable/failure marker without affecting runtime behavior.
- `DockStrokeRenderer.MiShadowUtils.applyViewShadow` — `tryInvokeStatic`; this remains best-effort vendor rendering compatibility.
- `MiuixFolderGlassHook` / `MiuixLauncherStaticGlassHook` dynamic vendor probes — `tryInvoke` unless a returned value is immediately required inside an exact installed vendor hook.

- [ ] **Step 1: Make false-success behavior a red test**

Update/add a source/logic contract around `DockMirrorShortcutHook` requiring the `refreshed` counter to depend on `result.succeeded()` rather than invocation attempt count.

- [ ] **Step 2: Run affected tests and verify RED**

Include `DockVendorNativeShadowReuseContractTest` and existing launcher glass vendor suppression tests when they assert touched paths.

- [ ] **Step 3: Migrate the nine files using the matrix**

Keep optional failures feature-local. Do not introduce a global logger in this task; existing class-local logs are sufficient.

- [ ] **Step 4: Run full unit tests**

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit Task 4**

Commit message:

```text
refactor: migrate dock and glass vendor reflection
```

---

### Task 5: Migrate Widget / MAML and SystemUI vendor calls

**Files:**
- Modify: `LauncherWidgetDarkContentAdapter.java`
- Modify: `LauncherMamlBackgroundRuleExecutor.java`
- Modify: `LauncherWidgetComponentSelectionExecutor.java`
- Modify: `LauncherWidgetComponentDiscovery.java`
- Modify: `LauncherWidgetTransitionCoordinator.java`
- Modify: `LauncherWidgetTransitionHook.java`
- Modify: `SystemUiKeyguardGoneRuntime.java`
- Modify: `SystemUiKeyguardGoneSource.java`
- Modify: `SystemUiHomeTransitionRuntime.java`
- Modify: `SystemUiHomeTransitionSource.java`
- Modify relevant Widget/MAML/SystemUI contract tests.

**Migration matrix:**

- Widget/MAML element discovery (`getItemInfo`, `findElement`, variable access, MAML update calls) — `tryInvoke`; missing methods/elements are supported compatibility outcomes and must skip/restore safely.
- Widget transition dynamic vendor reads — `tryInvoke` unless the exact hooked method's result is immediately required to preserve transition state.
- `ActivityThread.currentApplication()` in SystemUI runtime/source classes — `tryInvokeStatic`; if unavailable, keep the existing no-registration/no-publish path rather than throwing.

- [ ] **Step 1: Update old source-text contracts to the explicit optional API**

At minimum inspect/update `WidgetMamlRenderTreeDiscoveryContractTest` and any transition tests that assert `HookUtil.invoke` text.

- [ ] **Step 2: Run affected tests and confirm RED where source contract changed**

- [ ] **Step 3: Migrate all ten files**

For best-effort mutation calls that return void/null, still inspect `succeeded()`; a successful null is success, while failure follows the existing local fallback/diagnostic branch.

- [ ] **Step 4: Run full unit tests**

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit Task 5**

Commit message:

```text
refactor: migrate widget and systemui vendor reflection
```

---

### Task 6: Remove old silent-null APIs and add architecture gate

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/HookUtil.java`
- Create: `src/test/java/com/hellovoid/liquiddock/HookUtilArchitectureContractTest.java`
- Modify any remaining source-contract tests that still mention old API names.

**Interfaces:**
- Removes: `HookUtil.invoke(Object, String, Object...)`.
- Removes: all `HookUtil.invokeStatic(...)` legacy overloads.
- Keeps: `tryInvoke*`, `requireInvoke*`, hooking methods, exact method lookup, field helpers.

- [ ] **Step 1: Write architecture test that scans production Java sources**

The test walks `src/main/java/com/hellovoid/liquiddock` and asserts:

```java
assertFalse(source.contains("HookUtil.invoke("));
assertFalse(source.contains("HookUtil.invokeStatic("));
```

Do not match `tryInvoke` / `requireInvoke`. Also assert `HookUtil.java` itself no longer declares the old method signatures.

- [ ] **Step 2: Run architecture test and verify RED**

It should fail while the compatibility methods still exist in `HookUtil` even if call sites are migrated.

- [ ] **Step 3: Remove legacy methods and old first-candidate resolver code**

Delete `findMethodBestMatch`, `parametersMatch`, and the old `invoke`/`invokeStatic` methods if no remaining hook-install path uses them. Keep `findMethodExact` unchanged.

- [ ] **Step 4: Run architecture + resolver + invocation tests**

```bash
./gradlew testDebugUnitTest \
  --tests "com.hellovoid.liquiddock.HookUtilArchitectureContractTest" \
  --tests "com.hellovoid.liquiddock.VendorMemberResolverTest" \
  --tests "com.hellovoid.liquiddock.HookUtilInvocationContractTest" \
  --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit Task 6**

Commit message:

```text
refactor: remove silent null HookUtil invocation
```

---

### Task 7: Replace LauncherGlassSession self-reflection with typed lifecycle APIs

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSessionRegistry.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/WorkstationStaticLayerRecentsRecoveryContractTest.java`
- Modify/add the unlock rollover source/behavior contract test that covers `prepareUnlockCaptureReturn`.

**Interfaces:**
- Produces package-private `boolean suspendProducerForUnlockCapture()`.
- Produces package-private `boolean rebindProducer()`.
- Produces package-private `boolean rebindProducer(Runnable rolloverComplete)`.

- [ ] **Step 1: Rewrite Workstation contract test to require typed call**

Replace the old assertion:

```java
assertTrue(registry.contains("HookUtil.invoke(session, \"rebindProducer\")"));
```

with assertions that Registry has no `HookUtil` reference and checks `if (session.rebindProducer()) rebound++;` or equivalent success-gated logic.

- [ ] **Step 2: Add unlock typed-boundary assertions**

Require Registry to call `session.suspendProducerForUnlockCapture()` and `session.rebindProducer(completeOne)` without reading `binding` or `renderHandler`. Require failure to set the existing fail-closed flag and complete accounting without invoking `ready`.

- [ ] **Step 3: Run the two lifecycle contract tests and verify RED**

Expected: current Registry still contains self-reflection.

- [ ] **Step 4: Implement `suspendProducerForUnlockCapture()`**

Inside Session:

```java
boolean suspendProducerForUnlockCapture() {
    if (shuttingDown) return false;
    Miuix307PassBlurBridge.Binding current = binding;
    if (current == null) return false;
    Miuix307PassBlurBridge.pauseUpdates(current);
    return true;
}
```

Do not expose `binding`.

- [ ] **Step 5: Refactor `rebindProducer` to return queue acceptance**

Keep existing state reset/unbind behavior, but make render-queue submission result observable. The no-callback overload delegates to the callback form:

```java
boolean rebindProducer() {
    return rebindProducer(null);
}
```

The callback form must:

1. reject when shutting down/render thread dead;
2. clear/unbind old binding exactly once;
3. enqueue endpoint rollover on `renderHandler` through existing `postRender`;
4. after release + `createInputProducer()` finish, post callback to `mainHandler`;
5. return the boolean result of queue submission;
6. never invoke success callback on rejected submission.

Do not claim fresh-frame completion here.

- [ ] **Step 6: Convert Registry**

- `suspendForUnlockCapture()` increments `paused` only on `session.suspendProducerForUnlockCapture() == true`.
- `prepareUnlockCaptureReturn()` calls `session.rebindProducer(completeOne)`; if false, set `failed` and post/account `completeOne` once.
- Remove all `java.lang.reflect.Method`, `HookUtil.getField`, `renderHandler`, and `binding` access.
- `prepareWorkstationRecentsReturn()` increments `rebound` only when `session.rebindProducer()` returns true.

- [ ] **Step 7: Run lifecycle tests and full unit suite**

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Step 8: Commit Task 7**

Commit message:

```text
refactor: use typed launcher glass session lifecycle api
```

---

### Task 8: Remove obsolete R8 self-reflection keeps and close architecture TODO

**Files:**
- Modify: `src/main/keepRules/runtime-reflection.keep`
- Modify: `src/test/java/com/hellovoid/liquiddock/R8ReleaseKeepContractTest.java`
- Modify: `TODO.md`

- [ ] **Step 1: Change R8 test from positive keep assertions to absence assertions**

Require:

```java
assertFalse(reflectionRules.contains("android.os.Handler renderHandler;"));
assertFalse(reflectionRules.contains("Miuix307PassBlurBridge$Binding binding;"));
assertFalse(reflectionRules.contains("void rebindProducer();"));
```

If the file becomes empty after this cleanup, the test should allow the file to contain only explanatory comments or delete the file only if the AGP source-set configuration does not require its presence. Do not remove unrelated keep rules.

- [ ] **Step 2: Verify RED before changing keep rules**

Run `R8ReleaseKeepContractTest`; expected FAIL on the new absence assertions.

- [ ] **Step 3: Remove the Session-specific keep blocks**

Delete only the blocks that preserve `renderHandler`, `binding`, and `rebindProducer()` for Registry self-reflection.

- [ ] **Step 4: Update TODO**

Remove/mark completed only the first PR #65 hardening bullet about reflective `rebindProducer()` and false-positive rebound logging. Leave the separate Recents-covered gating, producer success diagnostics, real-device regression, and varargs warning bullets intact unless this implementation directly satisfies them.

- [ ] **Step 5: Run R8 contract + full unit suite**

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Step 6: Commit Task 8**

Commit message:

```text
chore: drop launcher session reflection keep rules
```

---

### Task 9: Final verification, diff audit, and Draft PR

**Files:**
- No new production behavior unless verification uncovers a concrete defect.
- Update plan checkboxes only if the repository convention expects tracked completion.

- [ ] **Step 1: Search for forbidden old APIs and self-reflection**

Verify production source contains none of:

```text
HookUtil.invoke(
HookUtil.invokeStatic(
HookUtil.getField(session,
HookUtil.findMethodExact(session.getClass(), "rebindProducer"
"renderHandler" access from LauncherGlassSessionRegistry
```

- [ ] **Step 2: Search for every `tryInvoke*` call and confirm success inspection**

Every optional invocation must branch on `succeeded()` directly or through a local helper that does so. Reject code that merely replaces old `invoke()` with `tryInvoke().value()`.

- [ ] **Step 3: Run complete unit tests fresh**

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: exit 0, zero failing tests.

- [ ] **Step 4: Run Debug build fresh**

```bash
./gradlew assembleDebug --stacktrace
```

Expected: exit 0 and Debug APK produced.

- [ ] **Step 5: Review branch diff against `main`**

Acceptance audit:

- deterministic ambiguity-safe resolver present;
- positive/negative/ambiguous method cache and field cache present;
- successful null vs failure tested;
- required invocation exception tested;
- all old dynamic invocation call sites migrated;
- Registry has no reflection into Session;
- Workstation count is success-based;
- unlock callback keeps render-queue completion semantics and fail-closed behavior;
- obsolete keep rules removed;
- no unrelated architectural refactor included.

- [ ] **Step 6: Open Draft PR**

PR title:

```text
Refactor HookUtil vendor reflection contract
```

PR body must summarize the `try/require` boundary, deterministic resolver/cache, call-site migration, typed Session API, R8 cleanup, and fresh `testDebugUnitTest` + `assembleDebug` evidence. Keep it Draft until CI is green and final review is complete.
