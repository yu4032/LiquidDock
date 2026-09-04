# HookUtil Vendor Reflection Contract Redesign

Date: 2026-09-04

Status: Design approved in chat; implementation not started

Base: `main@d68c3c12d713513d5df12b0c8a3c6fad57cdebec`

## 1. Problem

`HookUtil` currently mixes four responsibilities:

1. libxposed hook installation;
2. reflective field lookup/access;
3. reflective method overload resolution;
4. invocation failure policy.

The current method resolver walks the class hierarchy, collects compatible methods, then chooses the first candidate returned by reflection. That makes overload selection dependent on reflection enumeration order. `invoke()` and `invokeStatic()` then catch every `Throwable` and return `null`, making these states indistinguishable:

- method invoked successfully and returned `null`;
- no compatible method exists;
- overload resolution is ambiguous;
- access failed;
- the invoked vendor method threw.

Several call sites immediately cast/unbox that nullable result, so the compatibility failure appears later as an unrelated `NullPointerException`.

A second architectural violation exists in `LauncherGlassSessionRegistry`: project-owned `LauncherGlassSession` internals are accessed through `HookUtil` (`binding`, `renderHandler`, `rebindProducer()`). This forces R8 keep rules for project-private members and can produce false-success logging when reflective invocation fails silently.

## 2. Goals

This refactor establishes a strict boundary:

- `HookUtil` remains the single façade for vendor/private reflection and libxposed hook installation.
- Vendor reflection has two explicit contracts: optional `try*` and required `require*`.
- Overload resolution is deterministic, cached, and rejects true ambiguity.
- Field resolution is cached and superclass-aware.
- Successful `null` results remain distinguishable from reflection failure.
- Project-owned classes must communicate through normal typed Java APIs, never through `HookUtil`.
- `LauncherGlassSessionRegistry -> LauncherGlassSession` becomes fully package-private typed interaction.
- Existing vendor compatibility behavior is preserved where failure is currently expected and intentionally soft.
- No new numeric coercion/widening policy is introduced in this refactor.

## 3. Non-goals

This change does not:

- redesign libxposed hook installation APIs;
- replace all vendor reflection with dedicated per-vendor adapter classes;
- change HyperOS feature behavior, visual output, producer freshness semantics, or Workstation policy;
- introduce general dependency injection;
- add Java numeric widening beyond the compatibility rules currently accepted by `HookUtil`;
- refactor unrelated `MainHook`, Grid, Widget, or GPU responsibilities.

## 4. Architecture

### 4.1 Public boundary

`HookUtil` remains the project-facing vendor boundary. Hook installation methods stay conceptually unchanged:

- `hook(Method, Hooker)`
- `hook(Constructor<?>, Hooker)`
- `hookMethod(...)`
- `findMethodExact(...)`

Dynamic invocation changes to explicit APIs:

- `tryInvoke(target, methodName, args...)`
- `tryInvokeStatic(clazz, methodName, args...)`
- `tryInvokeStatic(className, methodName, args...)`
- `requireInvoke(target, methodName, args...)`
- `requireInvokeStatic(clazz, methodName, args...)`
- `requireInvokeStatic(className, methodName, args...)`

The old silent-null `invoke()` / `invokeStatic()` APIs are removed rather than retained as deprecated aliases.

### 4.2 Pure resolver

Introduce a package-private pure-Java component named `VendorMemberResolver`.

Responsibilities:

- method candidate discovery;
- deterministic overload selection;
- ambiguity detection;
- field lookup;
- Method/Field accessibility setup;
- positive and negative resolution caching;
- diagnostic candidate signature formatting.

It must not depend on Android, libxposed, Views, or project runtime state. This keeps overload behavior directly unit-testable on the host JVM.

`HookUtil` delegates reflection lookup to this resolver and owns invocation/error-policy translation.

## 5. Invocation result contract

### 5.1 `InvocationResult`

`tryInvoke*` returns an explicit result type instead of a nullable method result.

The implementation uses `HookUtil.InvocationResult<T>` so call sites do not depend directly on resolver internals.

Conceptual shape:

```java
static final class InvocationResult<T> {
    boolean succeeded();
    T value();
    Failure failure();
    Method method();
}
```

A successful vendor method that legitimately returns `null` is represented as:

- `succeeded() == true`
- `value() == null`
- `failure() == null`

A failed reflection operation is represented as:

- `succeeded() == false`
- no usable value;
- structured failure metadata.

Callers must not infer failure from `value() == null`.

### 5.2 Failure kinds

At minimum, failures distinguish:

- `TARGET_NULL`
- `CLASS_NOT_FOUND`
- `METHOD_NOT_FOUND`
- `AMBIGUOUS_METHOD`
- `FIELD_NOT_FOUND` where field try APIs are later needed
- `ACCESS_FAILURE`
- `INVOCATION_FAILURE`

`INVOCATION_FAILURE` preserves the underlying target exception/cause rather than swallowing it.

The result contains enough context to diagnose:

- target/declaring class;
- method name;
- static vs instance request;
- runtime argument types including explicit null markers;
- resolved method when invocation reached that stage;
- competing candidate signatures for ambiguity;
- original cause.

### 5.3 Required invocation

`requireInvoke*` returns the actual vendor return value and may legitimately return `null` if the invoked method returned `null`.

Resolution or invocation failure throws `VendorReflectionException` containing the same structured context used by `InvocationResult`.

Required invocation is used only when the surrounding feature cannot proceed correctly without the reflective operation.

## 6. Deterministic overload resolution

### 6.1 Candidate filtering

Candidates are discovered from the target class and its superclass chain.

A method is eligible only when:

- name matches exactly;
- static/instance mode matches exactly;
- arity matches exactly;
- every runtime argument is assignment-compatible with the declared parameter under the existing primitive-wrapper compatibility rule.

This refactor does not add varargs expansion or numeric primitive widening.

### 6.2 Specificity order

Resolution uses deterministic semantic comparison rather than reflection enumeration order.

Preference order:

1. exact runtime reference type match;
2. primitive-wrapper exact match (`int` <-> `Integer`, etc.);
3. smaller inheritance/interface distance from runtime argument type to declared parameter type;
4. for otherwise-equivalent overridden signatures, declaration nearest to the runtime target class;
5. for otherwise-equivalent synthetic/bridge and real declarations, prefer the non-synthetic/non-bridge declaration.

The comparison is performed per parameter and then across the complete signature. A candidate only wins when it is strictly more specific than every remaining competitor under the defined rules.

### 6.3 Null arguments

A `null` runtime argument cannot provide a concrete runtime type.

For null positions:

- primitive parameters are ineligible;
- among reference parameters, subtype specificity may establish a unique winner (`String` is more specific than `Object`);
- unrelated reference types do not receive arbitrary ordering.

If two or more remaining candidates are incomparable, resolution is ambiguous.

Example:

```java
m(CharSequence)
m(Number)
```

with `null` is ambiguous and must not select either method.

### 6.4 Ambiguity

True ambiguity is a first-class resolution outcome.

The resolver must never:

- sort by method name/signature text and silently pick one;
- select `candidates.get(0)`;
- depend on JVM reflection member order.

`tryInvoke*` returns `AMBIGUOUS_METHOD`; `requireInvoke*` throws `VendorReflectionException` with all tied candidate signatures.

## 7. Caching

### 7.1 Method cache

Method resolution cache key contains:

- runtime target `Class<?>`;
- method name;
- static/instance flag;
- runtime argument type vector;
- an explicit null marker for null arguments.

Cache values represent all stable resolution outcomes:

- resolved `Method`;
- method-not-found;
- ambiguous candidate set.

Caching negative and ambiguous outcomes avoids repeatedly walking vendor class hierarchies on hot UI/animation paths.

### 7.2 Field cache

Field cache key contains:

- runtime `Class<?>`;
- field name.

Resolution walks from the runtime class upward and picks the nearest declared field with that exact name.

Resolved `Field` and field-not-found outcomes are cached.

### 7.3 Cache lifetime

Caches are process-local static concurrent maps. Java class member sets do not mutate after class definition in the supported runtime model, so resolution outcomes are stable for a given `Class<?>` identity.

The design must not use class-name strings as cache identity because different ClassLoaders may load classes with the same name.

For tests, `VendorMemberResolver` exposes package-private cache reset and cache-size inspection methods. They are not part of the production façade and exist only to make cache behavior directly verifiable without timing-based assertions.

## 8. Field access policy

Existing field getters/setters currently have required semantics: they throw when the field cannot be accessed. They are migrated internally to the cached resolver without changing their external behavior in this phase.

Examples:

- `getField`
- `getIntField`
- `getLongField`
- `getBooleanField`
- `setField`
- `setIntField`
- `setLongField`

Do not add a broad family of `tryGet*` APIs preemptively. During migration, if a concrete vendor call site proves that field absence is a normal compatibility case, add the narrow optional field API required by that call site and test it.

## 9. Call-site migration policy

Every production use of old dynamic `HookUtil.invoke*` is classified manually.

### Use `tryInvoke*` when

- the vendor method may legitimately be absent across supported HyperOS variants;
- failure means "feature-local compatibility path unavailable";
- the caller can explicitly choose a safe fallback or skip behavior;
- diagnostics are useful but the process must continue.

The caller must inspect `succeeded()`; it must not only read `value()`.

### Use `requireInvoke*` when

- a preceding hook/class probe already established the vendor contract;
- continuing with no result would corrupt feature semantics;
- the value is immediately required for arithmetic, state mutation, or invariant enforcement;
- current code casts/unboxes the old silent-null result and would otherwise fail later.

This moves failure to the reflection boundary with a meaningful exception instead of a delayed NPE.

## 10. Project-owned reflection prohibition

`HookUtil` is for vendor/private runtime boundaries, not for ordinary interaction among LiquidDock classes.

Production code must not use `HookUtil` to access members declared on project-owned `com.hellovoid.liquiddock.*` classes when a normal Java call can express the relationship.

This phase explicitly eliminates every `LauncherGlassSessionRegistry -> LauncherGlassSession` reflective access.

An architecture test guards this concrete boundary. Broader enforcement may be added where mechanically reliable, but the test must not incorrectly reject vendor objects merely because the call site itself lives in the LiquidDock package.

## 11. LauncherGlassSession typed API

The Registry currently reaches into three Session internals:

- `binding` to pause producer updates;
- `renderHandler` to enqueue a completion sentinel;
- private `rebindProducer()`.

All three reflective accesses are removed.

`LauncherGlassSession` exposes these exact package-private lifecycle operations:

```java
boolean suspendProducerForUnlockCapture();
boolean rebindProducer();
boolean rebindProducer(Runnable rolloverComplete);
```

### 11.1 `suspendProducerForUnlockCapture()`

This method owns access to `binding`.

It returns `true` only when the Session is live, a binding exists, and the Session actually issues the producer pause operation. It returns `false` when there is no live producer to pause or the Session is shutting down.

The Registry logs counts based on this boolean result, not reflective access attempts.

### 11.2 `rebindProducer()`

This overload is used by Session-internal lifecycle paths and Workstation return.

It returns `true` only when endpoint rollover work is accepted by the Session render queue. It returns `false` when the Session is shutting down, the render thread is dead, or queue submission is rejected.

A `true` return means rollover work was enqueued; it does not mean a fresh frame or vendor re-bind has completed.

Workstation return increments its rebound count only for `true` results.

### 11.3 `rebindProducer(Runnable rolloverComplete)`

This overload is used by unlock capture return.

Its producer teardown/recreate work is the same as `rebindProducer()`. After the render-thread rollover task has completed, the Session posts `rolloverComplete` to its existing `mainHandler`.

The callback therefore means:

- the render queue has executed old endpoint release and new endpoint creation;
- completion has been marshalled back to the Launcher/main looper;
- it does **not** claim that asynchronous vendor `bindProducerWhenReady()` has already produced a fresh frame.

If queue submission fails, the method returns `false` and does not invoke the success callback. The Registry marks that Session as failed and keeps unlock capture fail-closed.

This preserves the current render-queue ordering while removing direct Registry access to `renderHandler`.

## 12. R8 cleanup

After Registry no longer reflects Session internals, remove Session-specific keep rules that exist only for that self-reflection:

- `LauncherGlassSession.binding`
- `LauncherGlassSession.renderHandler`
- `LauncherGlassSession.rebindProducer()`

Do not remove keep rules still required for actual vendor/private reflection elsewhere.

`R8ReleaseKeepContractTest` is updated to assert these Session self-reflection keeps are absent while retaining required vendor reflection rules.

## 13. Testing strategy

### 13.1 Resolver behavior tests

Pure JVM tests use fixture classes to verify:

- exact reference overload wins;
- primitive-wrapper exact match works;
- closest superclass/interface type wins;
- subclass override wins over superclass declaration;
- non-bridge/non-synthetic declaration wins when semantically equivalent;
- null selects a unique more-specific reference type when one exists;
- null across unrelated types produces ambiguity;
- static and instance methods never cross-resolve;
- no compatible method produces method-not-found;
- successful invocation returning null is distinct from failure;
- target-thrown exception is preserved as invocation failure;
- `requireInvoke` throws the typed exception at the boundary;
- repeated method and field resolution reuse cache entries via the package-private resolver cache inspection hooks.

Tests must not assert JDK reflection enumeration order.

### 13.2 Architecture tests

Add/modify source-level architecture gates for rules that are legitimately structural:

- old production `HookUtil.invoke(` and `HookUtil.invokeStatic(` APIs are absent;
- `LauncherGlassSessionRegistry` contains no `HookUtil` reference;
- Registry does not reflect Session fields or methods;
- Session self-reflection R8 keeps are absent.

Source-level tests are appropriate here because they enforce forbidden dependency/wiring patterns, not runtime lifecycle behavior.

### 13.3 Existing runtime contracts

Update Workstation/unlock source contract tests that currently require `HookUtil.invoke(session, "rebindProducer")` so they instead require the typed lifecycle API and real success accounting.

No test may encode a false requirement that callback completion means a fresh OES frame has arrived.

## 14. TDD sequence

Implementation follows red-green-refactor:

1. add resolver behavior tests and architecture tests against current code;
2. run `testDebugUnitTest` and confirm failures are specifically caused by missing new contract / existing forbidden patterns;
3. implement resolver + result/exception contract;
4. migrate vendor dynamic call sites deliberately to `try*` or `require*`;
5. replace Registry self-reflection with typed Session APIs;
6. remove obsolete Session keep rules;
7. run complete tests;
8. run `assembleDebug`;
9. review diff for remaining silent-null or internal-reflection patterns;
10. open Draft PR only after full verification.

## 15. Acceptance criteria

The implementation is complete only when all of the following hold:

- no production call uses old silent-null `HookUtil.invoke()` / `invokeStatic()`;
- optional and required vendor invocation semantics are explicit at every migrated call site;
- successful null return values are distinguishable from failures;
- overload resolution is deterministic and ambiguity is reported rather than guessed;
- method and field resolution use Class-identity-based caches;
- `LauncherGlassSessionRegistry` uses only typed Session APIs;
- Registry no longer reads Session `binding` or `renderHandler`;
- Workstation rebound diagnostics reflect actual accepted rollover operations;
- unlock rollover completion preserves current render-queue ordering and fail-closed behavior;
- Session self-reflection R8 keep rules are removed;
- resolver behavior and architecture tests pass;
- existing unit tests pass;
- `./gradlew testDebugUnitTest --stacktrace` passes;
- `./gradlew assembleDebug --stacktrace` passes;
- no unrelated refactor is mixed into the branch.

## 16. Follow-up work intentionally deferred

After this contract is stable, separate later changes may consider:

- dedicated vendor adapter/bridge classes for high-volume APIs;
- stronger project-wide static enforcement of internal reflection prohibition;
- metrics/one-shot diagnostics around optional reflection misses;
- replacing remaining direct Java reflection outside `HookUtil` where it represents vendor boundary logic;
- splitting hooking operations from reflection façade if `HookUtil` remains too broad.

These are not prerequisites for this refactor.