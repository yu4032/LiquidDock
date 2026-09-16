# Gboard SurfaceControl Drag Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Gboard floating-keyboard glass presentation through `TextureView` with an application-owned `SurfaceControl` child so drag-time glass can present at the same cadence as 120 Hz Gboard geometry while preserving the existing zero-copy snapshot and Prismal rendering pipeline.

**Architecture:** Keep `GboardFloatingGlassCoordinator` as the only geometry authority and keep `GboardFloatingGlassSession` as the Prismal/render owner. Add a presentation abstraction with a preferred `SurfaceControl` backend and the current `TextureView` implementation as fail-closed fallback. The preferred child surface is reparented under the current Gboard root `AttachedSurfaceControl`, stays below the root content buffer, receives keyboard-local Prismal output, and is positioned/cropped by compositor transactions from current root-space geometry.

**Tech Stack:** Android API 33+, `View.getRootSurfaceControl()`, `AttachedSurfaceControl`, `SurfaceControl`, `Surface`, EGL/Prismal, Choreographer, JUnit4, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-16-gboard-surfacecontrol-drag-glass-design.md`

## Global Constraints

- Project minSdk is 33; use public Android APIs only.
- Do not use reflection, hidden ViewRoot fields, obfuscated Gboard symbols, or fixed resource IDs.
- Do not introduce Bitmap, PixelCopy, ImageReader, `glReadPixels`, CPU screenshot/readback, or fixed delays.
- Preserve Gboard vendor hierarchy as geometry authority and Choreographer as movement cadence.
- Preserve drag-start prepared full-root GPU backdrop semantics.
- Preserve Prismal local recomputation; do not move a previously rendered local glass image.
- Geometry movement must not trigger PassBlur producer reconciliation or new backdrop preparation.
- Fail closed: preferred output failure must keep TextureView fallback or restore stock visuals.
- Do not merge PR #201 automatically.
- Device behavior is authoritative for 120 Hz smoothness; CI proves contracts/build only.

---

## File Structure

### New production files

- `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassOutput.java` — presentation interface consumed by session/coordinator.
- `src/main/java/com/hellovoid/liquiddock/GboardSurfaceControlGlassOutput.java` — preferred public-API SurfaceControl implementation.
- `src/main/java/com/hellovoid/liquiddock/GboardTextureViewGlassOutput.java` — adapter around the existing `GboardFloatingGlassView` fallback.
- `src/main/java/com/hellovoid/liquiddock/GboardFloatingOutputSelection.java` — Android-free preferred/fallback selection state.
- `src/main/java/com/hellovoid/liquiddock/GboardFloatingLiveBackdropState.java` — Android-free `LIVE / DRAG_SNAPSHOT / WAITING_FOR_FRESH_LIVE` authority state.

### Modified production files

- `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassCoordinator.java` — construct preferred output, fall back safely, publish geometry, release output.
- `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassSession.java` — attach/detach through output abstraction; integrate explicit release-to-fresh-live state.
- `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassView.java` — remain TextureView implementation detail; remove responsibility for being the only output authority.
- `src/main/java/com/hellovoid/liquiddock/GboardDragDiagnostics.java` — add concise SurfaceControl lifecycle/transaction events if needed.

### Tests

- `src/test/java/com/hellovoid/liquiddock/GboardFloatingOutputSelectionTest.java`
- `src/test/java/com/hellovoid/liquiddock/GboardFloatingLiveBackdropStateTest.java`
- `src/test/java/com/hellovoid/liquiddock/GboardSurfaceControlGeometryPolicyTest.java`
- `src/test/java/com/hellovoid/liquiddock/GboardFloatingGlassContractTest.java` — extend existing approved static contract rather than adding a new source-inspection test family.

---

### Task 1: Preferred-output selection and fail-closed fallback

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/GboardFloatingOutputSelectionTest.java`
- Create: `src/main/java/com/hellovoid/liquiddock/GboardFloatingOutputSelection.java`

**Interfaces:**
- Produces: `GboardFloatingOutputSelection` with `Mode { SURFACE_CONTROL, TEXTURE_VIEW, STOCK }` and typed transitions.
- Later tasks use this state to decide whether to keep preferred output, fall back to TextureView, or restore stock.

- [ ] **Step 1: Write the failing typed state test**

```java
@Test public void preferredSurfaceControlWinsWhenReady() {
    GboardFloatingOutputSelection state = new GboardFloatingOutputSelection();
    assertEquals(GboardFloatingOutputSelection.Mode.SURFACE_CONTROL,
            state.onSurfaceControlReady());
}

@Test public void surfaceControlFailureFallsBackToTextureView() {
    GboardFloatingOutputSelection state = new GboardFloatingOutputSelection();
    assertEquals(GboardFloatingOutputSelection.Mode.TEXTURE_VIEW,
            state.onSurfaceControlFailed(true));
}

@Test public void failureWithoutTextureViewRestoresStock() {
    GboardFloatingOutputSelection state = new GboardFloatingOutputSelection();
    assertEquals(GboardFloatingOutputSelection.Mode.STOCK,
            state.onSurfaceControlFailed(false));
}
```

- [ ] **Step 2: Run CI and verify RED**

Run through PR CI: `./gradlew testDebugUnitTest assembleDebug --stacktrace`

Expected: compile failure because `GboardFloatingOutputSelection` does not exist.

- [ ] **Step 3: Implement the minimal Android-free state class**

```java
final class GboardFloatingOutputSelection {
    enum Mode { SURFACE_CONTROL, TEXTURE_VIEW, STOCK }

    private Mode mode = Mode.STOCK;

    Mode onSurfaceControlReady() {
        mode = Mode.SURFACE_CONTROL;
        return mode;
    }

    Mode onSurfaceControlFailed(boolean textureViewAvailable) {
        mode = textureViewAvailable ? Mode.TEXTURE_VIEW : Mode.STOCK;
        return mode;
    }

    Mode mode() { return mode; }
}
```

- [ ] **Step 4: Run tests and verify GREEN**

Expected: new typed state tests pass; full existing suite remains green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/GboardFloatingOutputSelection.java \
        src/test/java/com/hellovoid/liquiddock/GboardFloatingOutputSelectionTest.java
git commit -m "test: define Gboard output fallback authority"
```

---

### Task 2: Explicit drag-release live-backdrop state machine

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/GboardFloatingLiveBackdropStateTest.java`
- Create: `src/main/java/com/hellovoid/liquiddock/GboardFloatingLiveBackdropState.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassSession.java`

**Interfaces:**
- Produces: `GboardFloatingLiveBackdropState.Phase { LIVE, DRAG_SNAPSHOT, WAITING_FOR_FRESH_LIVE }`.
- Produces methods: `onDragStart(boolean prepared)`, `onDragEnd()`, `acceptLiveBackdrop()`, `onFreshBackdropPrepared(long generation)`, `onFreshOutputSwapped(long generation)`.

- [ ] **Step 1: Write failing state-machine tests**

```java
@Test public void releaseKeepsSnapshotUntilFreshBackdropAndSwap() {
    GboardFloatingLiveBackdropState state = new GboardFloatingLiveBackdropState();
    assertTrue(state.onDragStart(true));
    state.onDragEnd();
    assertEquals(GboardFloatingLiveBackdropState.Phase.WAITING_FOR_FRESH_LIVE, state.phase());
    assertTrue(state.acceptLiveBackdrop());

    state.onFreshBackdropPrepared(2L);
    assertEquals(GboardFloatingLiveBackdropState.Phase.WAITING_FOR_FRESH_LIVE, state.phase());

    state.onFreshOutputSwapped(2L);
    assertEquals(GboardFloatingLiveBackdropState.Phase.LIVE, state.phase());
}

@Test public void dragCannotStartWithoutPreparedBackdrop() {
    GboardFloatingLiveBackdropState state = new GboardFloatingLiveBackdropState();
    assertFalse(state.onDragStart(false));
    assertEquals(GboardFloatingLiveBackdropState.Phase.LIVE, state.phase());
}
```

- [ ] **Step 2: Run CI and verify RED**

Expected: missing `GboardFloatingLiveBackdropState` symbols only.

- [ ] **Step 3: Implement typed state**

Implementation rules:

```java
final class GboardFloatingLiveBackdropState {
    enum Phase { LIVE, DRAG_SNAPSHOT, WAITING_FOR_FRESH_LIVE }

    private Phase phase = Phase.LIVE;
    private long waitingGeneration = -1L;
    private boolean freshPrepared;

    synchronized boolean onDragStart(boolean prepared) {
        if (phase != Phase.LIVE || !prepared) return false;
        phase = Phase.DRAG_SNAPSHOT;
        return true;
    }

    synchronized void onDragEnd(long generation) {
        if (phase != Phase.DRAG_SNAPSHOT) return;
        phase = Phase.WAITING_FOR_FRESH_LIVE;
        waitingGeneration = generation;
        freshPrepared = false;
    }

    synchronized boolean acceptLiveBackdrop() {
        return phase != Phase.DRAG_SNAPSHOT;
    }

    synchronized void onFreshBackdropPrepared(long generation) {
        if (phase == Phase.WAITING_FOR_FRESH_LIVE && generation == waitingGeneration) {
            freshPrepared = true;
        }
    }

    synchronized void onFreshOutputSwapped(long generation) {
        if (phase == Phase.WAITING_FOR_FRESH_LIVE
                && freshPrepared && generation == waitingGeneration) {
            phase = Phase.LIVE;
            waitingGeneration = -1L;
            freshPrepared = false;
        }
    }

    synchronized Phase phase() { return phase; }
}
```

- [ ] **Step 4: Wire session without changing renderer semantics**

Replace immediate logical return to LIVE in `beginDragSnapshot/endDragSnapshot` with the typed state. On drag end:

```java
long generation = ++freshnessGeneration;
liveBackdropState.onDragEnd(generation);
sourceBackend.setUpdatesEnabled(true, "gboard-drag-release");
sourceBackend.reconcileRoot();
sourceBackend.requestFresh(generation);
```

When a matching fresh frame reaches `prepareBackdrop`, call:

```java
liveBackdropState.onFreshBackdropPrepared(frame.generation);
```

After a successful output swap of the corresponding fresh generation, call:

```java
liveBackdropState.onFreshOutputSwapped(renderGeneration);
```

Do not clear the prepared snapshot before that successful swap.

- [ ] **Step 5: Run full CI and commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/GboardFloatingLiveBackdropState.java \
        src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassSession.java \
        src/test/java/com/hellovoid/liquiddock/GboardFloatingLiveBackdropStateTest.java
git commit -m "fix: retain Gboard drag snapshot until fresh live swap"
```

---

### Task 3: Presentation abstraction and TextureView fallback adapter

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassOutput.java`
- Create: `src/main/java/com/hellovoid/liquiddock/GboardTextureViewGlassOutput.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassCoordinator.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassSession.java`

**Interfaces:**
- Produces interface:

```java
interface GboardFloatingGlassOutput {
    interface Listener {
        void onSurfaceReady(Surface surface, int width, int height);
        void onSurfaceSizeChanged(int width, int height);
        void onPresented();
        void onFailed(String reason, Throwable error);
    }

    boolean isPreferred();
    void updateGeometry(GboardFloatingGlassGeometry geometry);
    void showAfterFirstSwap();
    void release(String reason);
}
```

- [ ] **Step 1: Add interface and compile contract first**

Extend `GboardFloatingGlassContractTest` to require `GboardFloatingGlassOutput`, `GboardTextureViewGlassOutput`, and coordinator output selection wiring. Keep this inside the existing approved static contract class.

- [ ] **Step 2: Run RED**

Expected: missing output abstraction classes.

- [ ] **Step 3: Implement `GboardTextureViewGlassOutput` as a thin adapter**

Rules:

- continue to create/use `GboardFloatingGlassView`;
- forward `SurfaceTexture` callbacks to the output listener;
- preserve `Surface.setFrameRate()` hint;
- `updateGeometry` remains a no-op for compositor positioning because View layout already owns fallback position;
- `release()` disposes the TextureView and Surface once.

- [ ] **Step 4: Rewire session attach/detach to listener-based output surface**

The session should no longer know whether its EGL target originated from TextureView or SurfaceControl. It only receives `Surface + size` and reports successful swap/present lifecycle back through the output owner.

- [ ] **Step 5: Run CI and commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassOutput.java \
        src/main/java/com/hellovoid/liquiddock/GboardTextureViewGlassOutput.java \
        src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassView.java \
        src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassCoordinator.java \
        src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassSession.java \
        src/test/java/com/hellovoid/liquiddock/GboardFloatingGlassContractTest.java
git commit -m "refactor: abstract Gboard glass presentation output"
```

---

### Task 4: SurfaceControl geometry policy

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/GboardSurfaceControlGeometryPolicyTest.java`
- Create: `src/main/java/com/hellovoid/liquiddock/GboardSurfaceControlGeometryPolicy.java`

**Interfaces:**
- Produces Android-free value object `Frame` with integer compositor `x/y/width/height` derived from `GboardFloatingGlassGeometry`.
- Later SurfaceControl backend applies this frame without touching producer/backdrop state.

- [ ] **Step 1: Write failing geometry tests**

```java
@Test public void rootSpaceGeometryBecomesLocalBufferPosition() {
    GboardSurfaceControlGeometryPolicy.Frame frame =
            GboardSurfaceControlGeometryPolicy.from(372f, 421f, 1232, 978);
    assertEquals(372, frame.x);
    assertEquals(421, frame.y);
    assertEquals(1232, frame.width);
    assertEquals(978, frame.height);
}

@Test public void fractionalPositionsRoundToNearestPixel() {
    GboardSurfaceControlGeometryPolicy.Frame frame =
            GboardSurfaceControlGeometryPolicy.from(372.6f, 420.4f, 1232, 978);
    assertEquals(373, frame.x);
    assertEquals(420, frame.y);
}
```

- [ ] **Step 2: Run RED**

Expected: missing policy class.

- [ ] **Step 3: Implement minimal policy**

```java
final class GboardSurfaceControlGeometryPolicy {
    static final class Frame {
        final int x, y, width, height;
        Frame(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }

    static Frame from(float x, float y, int width, int height) {
        return new Frame(Math.round(x), Math.round(y), width, height);
    }
}
```

- [ ] **Step 4: Run GREEN and commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/GboardSurfaceControlGeometryPolicy.java \
        src/test/java/com/hellovoid/liquiddock/GboardSurfaceControlGeometryPolicyTest.java
git commit -m "test: define Gboard SurfaceControl geometry policy"
```

---

### Task 5: Preferred SurfaceControl output backend

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/GboardSurfaceControlGlassOutput.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/GboardFloatingGlassContractTest.java`

**Interfaces:**
- Consumes: `GboardFloatingGlassOutput`, `GboardSurfaceControlGeometryPolicy`, root `View`, display refresh rate.
- Produces: preferred output implementation with deterministic creation, geometry transaction, first-visible commit, and release.

- [ ] **Step 1: Extend existing static contract to require public APIs and forbid hidden/reflection paths**

Assertions must require production source to contain:

```java
root.getRootSurfaceControl()
new SurfaceControl.Builder()
buildReparentTransaction(
new Surface(child)
transaction.setPosition(
transaction.setWindowCrop(
transaction.setLayer(
transaction.show(
transaction.hide(
```

and must reject:

```text
Class.forName
getDeclaredField
ViewRootImpl
SurfaceControl$Builder reflection
PixelCopy
Bitmap.createBitmap
glReadPixels
```

- [ ] **Step 2: Run RED**

Expected: preferred output implementation absent.

- [ ] **Step 3: Implement constructor/factory with partial-resource cleanup**

Use a factory so failure is explicit:

```java
static GboardSurfaceControlGlassOutput tryCreate(
        View root,
        GboardFloatingGlassOutput.Listener listener) {
    if (root == null || !root.isAttachedToWindow()) return null;
    AttachedSurfaceControl attached = root.getRootSurfaceControl();
    if (attached == null) return null;
    // build child, reparent, create Surface, notify listener
}
```

Creation rules:

- child name: `LiquidDock-GboardGlass`;
- buffer size remains keyboard-local and is updated on geometry/resize;
- child starts hidden;
- child uses a negative layer relative to root content so Gboard foreground remains above;
- set preferred frame rate to current display refresh rate when valid;
- any exception releases `Surface` and `SurfaceControl` before returning null/failure.

- [ ] **Step 4: Implement geometry-only transactions**

For each distinct geometry:

```java
Frame frame = GboardSurfaceControlGeometryPolicy.from(...);
SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
tx.setPosition(child, frame.x, frame.y);
tx.setWindowCrop(child, frame.width, frame.height);
tx.apply();
```

Do not call PassBlur or Prismal from this class.

- [ ] **Step 5: Implement first-visible transaction**

After the first successful EGL swap:

```java
SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
tx.show(child);
tx.addTransactionCommittedListener(executor, () -> listener.onPresented());
tx.apply();
```

`onPresented()` is emitted only once for first-visible authority; later swap telemetry can use diagnostics without repeatedly hiding/showing.

- [ ] **Step 6: Implement idempotent release**

Release order:

```text
hide child -> apply -> release Surface -> release SurfaceControl -> clear refs
```

No later transaction may use released child.

- [ ] **Step 7: Add diagnostics**

Emit:

```text
SC_OUTPUT_CREATE
SC_OUTPUT_REPARENTED
SC_OUTPUT_SURFACE_READY
SC_GEOMETRY serial=N ...
SC_TX_APPLIED serial=N
SC_TX_COMMITTED serial=N
SC_FIRST_VISIBLE
SC_OUTPUT_RELEASE reason=...
SC_FALLBACK reason=...
```

- [ ] **Step 8: Run CI and commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/GboardSurfaceControlGlassOutput.java \
        src/test/java/com/hellovoid/liquiddock/GboardFloatingGlassContractTest.java
git commit -m "feat: add Gboard SurfaceControl glass output"
```

---

### Task 6: Coordinator selection, fallback, and geometry publishing

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassCoordinator.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassSession.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/GboardFloatingGlassContractTest.java`

**Interfaces:**
- Consumes preferred/fallback outputs and selection state.
- Produces one active output per session.

- [ ] **Step 1: Extend contract for preferred-first selection**

Require coordinator source to attempt:

```java
GboardSurfaceControlGlassOutput.tryCreate(...)
```

before constructing fallback `GboardTextureViewGlassOutput`.

- [ ] **Step 2: Implement preferred-first output creation**

Pseudo-flow:

```java
GboardFloatingGlassOutput output =
        GboardSurfaceControlGlassOutput.tryCreate(root, listener);
if (output != null) {
    selection.onSurfaceControlReady();
} else {
    output = GboardTextureViewGlassOutput.create(...);
    selection.onSurfaceControlFailed(output != null);
}
```

If neither path exists, do not hide stock background and abort replacement cleanly.

- [ ] **Step 3: Publish each authoritative geometry to both renderer and active output**

In the Choreographer frame path:

```java
state.session.updateGeometry(next);
state.output.updateGeometry(next);
```

There must be no producer reconcile in this path.

- [ ] **Step 4: Move first-visible stock suppression behind output presentation callback**

Only after the preferred output reports committed first visibility or fallback reports first TextureView present should coordinator set stock background alpha to zero.

- [ ] **Step 5: Release output together with session**

Ensure Choreographer callback is removed before `output.release(...)`.

- [ ] **Step 6: Run CI and commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassCoordinator.java \
        src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassSession.java \
        src/test/java/com/hellovoid/liquiddock/GboardFloatingGlassContractTest.java
git commit -m "feat: prefer SurfaceControl for Gboard glass presentation"
```

---

### Task 7: Root replacement, resize, and fail-closed recovery

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassCoordinator.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/GboardSurfaceControlGlassOutput.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/GboardFloatingGlassContractTest.java`

**Interfaces:**
- Recreates preferred output when root attachment or dimensions become invalid.

- [ ] **Step 1: Add contract for recreation triggers**

Require handling for:

```text
root identity change
root detached/unavailable
output size change
session rebuild
```

and explicitly forbid `postDelayed`.

- [ ] **Step 2: Implement authoritative lifecycle-triggered recreation**

On an invalid root/child relation:

```java
state.output.release("root-replaced");
restoreStockBackground(state);
recreateOutputFromCurrentAttachedRoot(state);
```

Do not carry old `SurfaceControl` across root attachment replacement.

- [ ] **Step 3: Resize without full producer reset**

A pure keyboard-local output size change should update child crop/buffer target and EGL surface sizing; it must not request a new PassBlur backdrop unless the root producer itself changed.

- [ ] **Step 4: Run CI and commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassCoordinator.java \
        src/main/java/com/hellovoid/liquiddock/GboardSurfaceControlGlassOutput.java \
        src/test/java/com/hellovoid/liquiddock/GboardFloatingGlassContractTest.java
git commit -m "fix: recover Gboard glass output across root lifecycle changes"
```

---

### Task 8: Full verification and device diagnostic build

**Files:**
- Review all files changed in Tasks 1-7.

- [ ] **Step 1: Run full CI on exact latest head**

Required command in workflow:

```bash
./gradlew testDebugUnitTest assembleDebug --stacktrace
```

Expected:

- zero-copy audit success;
- all unit tests success;
- debug assembly success;
- artifact upload success.

- [ ] **Step 2: Verify forbidden-path audit manually from diff**

Search changed production files for:

```text
PixelCopy
Bitmap.createBitmap
ImageReader
glReadPixels
postDelayed
Class.forName
getDeclaredField
0x7f
```

Expected: none in the new SurfaceControl path.

- [ ] **Step 3: Device log command**

```bash
adb logcat -c
adb logcat -v threadtime \
  | grep '\[DC\]\[GboardDragDiag\]' \
  | tee gboard-surfacecontrol-drag.log
```

- [ ] **Step 4: Device acceptance pass**

Confirm:

```text
SC_OUTPUT_CREATE
SC_OUTPUT_REPARENTED
SC_OUTPUT_SURFACE_READY
SC_GEOMETRY serial=N
SC_TX_APPLIED serial=N
SC_TX_COMMITTED serial=N
SC_FIRST_VISIBLE
```

During drag, geometry/transaction cadence should track ~8 ms on the 120 Hz device and no `TEXTURE_PRESENTED` events should be responsible for the preferred path.

- [ ] **Step 5: Verify visual behavior**

Acceptance requires:

- no visible glass trailing behind Gboard;
- glass contents change according to current position over the drag-start full-root snapshot;
- keys/text/handle remain above glass;
- no black/blank frame on attach/release/resize;
- release remains on snapshot until a fresh live backdrop is prepared and swapped;
- failure falls back safely.

- [ ] **Step 6: Commit any final diagnostic-only cleanup**

Do not merge PR #201 without explicit user approval.

---

## Self-Review

- Spec coverage: output abstraction, preferred SurfaceControl path, TextureView fallback, negative-layer composition, keyboard-local output buffer, Choreographer geometry publication, explicit fresh-live release state, first-visible authority, root/resize lifecycle, diagnostics, zero-copy and device acceptance are all mapped to tasks.
- Placeholder scan: no TBD/TODO/"implement later" placeholders remain.
- Type consistency: output abstraction and state-machine names/signatures are fixed above and reused consistently by later tasks.
- Scope: this plan changes only Gboard floating-glass presentation and release authority; Prismal shader design, hook discovery, appearance settings, and other LiquidDock surfaces remain out of scope.
