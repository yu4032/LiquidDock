# Glass Scene Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace ad-hoc Launcher glass lifecycle fixes with the approved Workspace/Dock dual-domain scene architecture, then pass unit tests and build a debug APK.

**Architecture:** Workspace uses one shared static glass layer controlled by `LauncherGlassSceneController`; Dock remains a separate continuous compositor and owns Dock item glass; Drag remains one transient overlay. Workspace visibility/freshness and rotation are generation-gated so stale frames are never revealed. Component styling is split into icon/widget/small-folder/large-folder styles with backward-compatible config migration.

**Tech Stack:** Java, Android View/SurfaceTexture/EGL/GLES, MIUI/HyperOS hooks, Prismal renderer, JUnit4, Android Gradle plugin, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-23-glass-scene-architecture-design.md`

## Global Constraints

- Keep exactly one physical Workspace static output per stable Launcher root.
- Keep Drag as zero-or-one transient output.
- Keep exactly one Dock output regardless of Dock item count.
- Keep the wallpaper/background data path GPU-only: no PixelCopy, ImageReader, Bitmap capture/readback, `glReadPixels`, or screen recording.
- Do not change the already validated 90°/270° optical/buffer swap semantics.
- Workspace remains caller-managed/fresh-burst based; Dock remains continuous.
- Press interaction must never be required to recover startup freshness.
- Existing old JSON keys remain readable; new canonical keys win when both are present.
- Every production behavior change is test-first.

---

### Task 1: Rotation producer coherence gate

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassProducerGeometryGate.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassRotationGenerationTest.java`

**Interfaces:**
- Produces: `static boolean LauncherGlassProducerGeometryGate.matchesRoot(int rootWidth, int rootHeight, int surfaceWidth, int surfaceHeight, int insetLeft, int insetTop, int insetRight, int insetBottom)`.
- `LauncherGlassSession` must call the gate before accepting producer geometry and must invalidate `frameAvailable` and `hasConsumedFrame` on a mismatch/generation change.

- [x] **Step 1: Keep the existing RED rotation behavior test as the first gate**

The test already requires `LauncherGlassProducerGeometryGate`, opposite-orientation rejection, frame invalidation, and rot1/3 buffer swap.

- [x] **Step 2: Verify RED in CI**

Observed in run 32605163006: three rotation tests fail because the gate is missing/session does not call it. Existing vendor-suppression RED tests also fail, so they remain active gates.

- [ ] **Step 3: Implement the pure geometry gate**

```java
final class LauncherGlassProducerGeometryGate {
    private LauncherGlassProducerGeometryGate() {}

    static boolean matchesRoot(int rootWidth, int rootHeight,
            int surfaceWidth, int surfaceHeight,
            int left, int top, int right, int bottom) {
        int contentWidth = surfaceWidth - Math.max(0, left) - Math.max(0, right);
        int contentHeight = surfaceHeight - Math.max(0, top) - Math.max(0, bottom);
        return rootWidth > 0 && rootHeight > 0
                && contentWidth == rootWidth && contentHeight == rootHeight;
    }
}
```

- [ ] **Step 4: Integrate the gate in producer geometry refresh**

Reject incoherent opposite-orientation geometry before an OES frame can be rendered; log `producer geometry not coherent with root`; clear `frameAvailable` and `hasConsumedFrame`.

- [ ] **Step 5: Verify GREEN**

Run `./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.LauncherGlassRotationGenerationTest --stacktrace` in CI, then full unit tests.

---

### Task 2: Workspace scene state and passive layer ownership

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSceneController.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticLayer.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSessionRegistry.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassSceneControllerTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassSceneOwnershipContractTest.java`

**Interfaces:**
- Produces: scene states `DETACHED`, `BOOTSTRAPPING`, `HOME_WAITING_FRESH_FRAME`, `HOME_VISIBLE`, `COVERED`.
- Produces: controller methods `onRootReady()`, `setCovered(boolean)`, `onGenerationInvalidated()`, `onFreshFrameReady(long generation)`, `generation()`, `isLayerVisible()`.
- `LauncherGlassStaticLayer` exposes controller-driven `setSceneVisible(boolean)` and never self-reveals from node registration.

- [ ] **Step 1: Add failing pure state-machine tests**
- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Implement the pure state machine and root-scoped controller registry**
- [ ] **Step 4: Make `LauncherGlassStaticLayer` passive**
- [ ] **Step 5: Verify GREEN and existing one-layer tests**

---

### Task 3: Effective visibility and rotation-generation node geometry

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassVisibility.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassVisibilityTest.java`

- [ ] **Step 1: Write failing tests for ancestor visibility accumulation**
- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Implement effective visibility helper**
- [ ] **Step 4: Integrate static nodes**
- [ ] **Step 5: Verify GREEN**

---

### Task 4: Bootstrap reconciliation and fresh-frame barrier

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSceneController.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassBootstrapContractTest.java`

- [ ] **Step 1: Add failing contract tests**
- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Implement bootstrap scan/classification hooks**
- [ ] **Step 4: Split session redraw from freshness**
- [ ] **Step 5: Verify GREEN**

---

### Task 5: Four independent component styles and migration

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/GlassComponentStyle.java`
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassNodeKind.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java`
- Modify: `src/main/res/xml/preferences.xml`
- Test: `src/test/java/com/hellovoid/liquiddock/GlassComponentStyleConfigTest.java`

- [ ] **Step 1: Add failing config tests**
- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Implement schema/runtime migration**
- [ ] **Step 4: Update legacy settings XML**
- [ ] **Step 5: Verify GREEN**

---

### Task 6: Geometry size offset and GPU-safe Auto icon shape

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassBoundsPolicy.java`
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassIconShapeResolver.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassBoundsPolicyTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassIconShapeResolverContractTest.java`

- [ ] **Step 1: Write failing pure geometry tests**
- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Implement bounds policy**
- [ ] **Step 4: Add icon shape tests and implement resolver**
- [ ] **Step 5: Integrate node-specific style resolution and verify GREEN**

---

### Task 7: Type-specific vendor material suppression

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassVendorMaterialSuppressor.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassVendorMaterialSuppressionContractTest.java`

- [x] **Step 1: Existing RED source/behavior contracts are present**
- [x] **Step 2: Verify RED** — run 32605163006 fails both widget and small-folder suppression tests.
- [ ] **Step 3: Implement suppressor and wire hooks**
- [ ] **Step 4: Verify GREEN**

---

### Task 8: Dock generation rebind and one-surface item batching

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/DockGlassItemNode.java`
- Create: `src/main/java/com/hellovoid/liquiddock/DockGlassCompositor.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyRenderer.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/DockGlassSceneContractTest.java`

- [ ] **Step 1: Write failing Dock generation/batching contracts**
- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Implement explicit Dock producer generation replacement**
- [ ] **Step 4: Implement Dock item batching and shared icon style wiring**
- [ ] **Step 5: Verify GREEN**

---

### Task 9: Coverage hooks and full regression build

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherDragOverlayHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassCoverageAndSurfaceContractTest.java`

- [ ] **Step 1: Write failing coverage/surface-count tests**
- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Wire lifecycle hooks into scene controller**
- [ ] **Step 4: Run complete unit suite** — `./gradlew testDebugUnitTest --stacktrace`
- [ ] **Step 5: Build debug APK** — `./gradlew assembleDebug --stacktrace`
- [ ] **Step 6: Download and inspect the workflow artifact**

## Plan self-review

- Spec coverage: Workspace scene lifecycle, bootstrap, fresh-frame barrier, effective visibility, rotation generation, Dock continuous rebind, Dock item batching, four style groups, migration, size offset, Auto icon shape, vendor suppression, and three-output-domain surface contract all have explicit tasks.
- Placeholder scan: no TBD/TODO placeholders remain.
- Type consistency: controller generation is `long`; four component styles are represented by one `GlassComponentStyle` type; Workspace static classification uses `LauncherGlassNodeKind`; Dock items use a separate `DockGlassItemNode` and are never Workspace static nodes.
