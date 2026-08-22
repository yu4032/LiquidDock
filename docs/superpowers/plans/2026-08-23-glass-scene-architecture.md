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

- [ ] **Step 1: Keep the existing RED rotation behavior test as the first gate**

The test already requires `LauncherGlassProducerGeometryGate`, opposite-orientation rejection, frame invalidation, and rot1/3 buffer swap.

- [ ] **Step 2: Verify RED in CI**

Push only the test/spec/plan state and inspect the Actions run. Expected failure: `missing LauncherGlassProducerGeometryGate` or equivalent behavior-level rotation failure.

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

```java
@Test public void coveredSceneRequiresFreshFrameBeforeReveal() {
    LauncherGlassSceneController.StateMachine s = new LauncherGlassSceneController.StateMachine();
    s.onRootReady();
    long g = s.generation();
    s.onFreshFrameReady(g);
    assertTrue(s.isLayerVisible());
    s.setCovered(true);
    assertFalse(s.isLayerVisible());
    s.setCovered(false);
    assertFalse(s.isLayerVisible());
    s.onFreshFrameReady(s.generation());
    assertTrue(s.isLayerVisible());
}
```

Also assert stale generation callbacks cannot reveal the layer.

- [ ] **Step 2: Verify RED**

Expected: controller/state machine missing.

- [ ] **Step 3: Implement the pure state machine and root-scoped controller registry**

Keep Android callbacks thin; state logic remains testable without a device.

- [ ] **Step 4: Make `LauncherGlassStaticLayer` passive**

Remove implicit visibility authority. Controller creates/acquires the layer and sets `VISIBLE` only in `HOME_VISIBLE`; use `INVISIBLE` while covered/waiting.

- [ ] **Step 5: Verify GREEN and existing one-layer tests**

Run new scene tests plus existing static-layer contract tests.

---

### Task 3: Effective visibility and rotation-generation node geometry

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassVisibility.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassVisibilityTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassGenerationContractTest.java`

**Interfaces:**
- Produces: `LauncherGlassVisibility.Effective` with `boolean visible` and `float alpha`.
- Static node geometry publication records a scene generation and can bypass ordinary two-frame stability on a generation transition.

- [ ] **Step 1: Write failing tests for ancestor visibility accumulation**

Test visible leaf under invisible parent, alpha multiplication, and zero/NaN alpha rejection using a pure helper input representation.

- [ ] **Step 2: Verify RED**

Expected: helper missing.

- [ ] **Step 3: Implement effective visibility helper**

Walk host → scene root and accumulate visibility/alpha. Do not let leaf-only `isShown()` replace the explicit parent walk.

- [ ] **Step 4: Integrate static nodes**

`captureGeometry` returns null when effective visibility is false. Store/compare generation so a new rotation generation immediately resolves new-orientation geometry instead of waiting for the normal two-frame stability gate.

- [ ] **Step 5: Verify GREEN**

Run visibility/generation tests and existing geometry stability tests.

---

### Task 4: Bootstrap reconciliation and fresh-frame barrier

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSceneController.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassBootstrapContractTest.java`

**Interfaces:**
- Controller performs one idempotent scan of the current Workspace/CellLayout tree before first reveal.
- Session exposes distinct freshness hooks: `requestFreshBackdrop(long generation)`, `invalidateGeneration(long generation)`, `requestSceneRedraw()`.

- [ ] **Step 1: Add failing contract tests**

Assert controller source contains reconciliation-before-refresh order; node registration cannot directly call `LauncherGlassStaticLayer.acquire`; press paths contain no producer bind/recovery calls.

- [ ] **Step 2: Verify RED**

Expected: direct layer acquire still present and explicit freshness API missing.

- [ ] **Step 3: Implement bootstrap scan/classification hooks**

Scan existing attached Workspace descendants, route recognized FolderIcon material, ShortcutIcon, LauncherAppWidgetHostView, and MaMlHostView through the same idempotent registration paths used by future constructor hooks.

- [ ] **Step 4: Split session redraw from freshness**

Interaction/geometry redraw reuses valid prepared backdrop; HOME restore/bootstrap/rotation uses fresh-producer request and only calls controller reveal after current-generation OES consumption/backdrop preparation.

- [ ] **Step 5: Verify GREEN**

Run bootstrap contracts plus folder startup/press/lifecycle regression tests.

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
- Test: `src/test/java/com/hellovoid/liquiddock/GlassComponentStyleMigrationTest.java`

**Interfaces:**
- `GlassComponentStyle(boolean enabled, float sizeOffsetDp, float cornerRadiusDp)`.
- `LiquidDockConfig.Glass` exposes `iconStyle`, `widgetStyle`, `smallFolderStyle`, `largeFolderStyle`.
- New canonical keys exactly match the architecture spec.

- [ ] **Step 1: Add failing config tests**

Assert all twelve canonical keys exist, `0` radius means Auto, new keys override legacy fallbacks, and `liquid_folder_glass` / `liquid_folder_corner_radius` feed both small/large styles only when new keys are absent.

- [ ] **Step 2: Verify RED**

Expected: keys/types missing.

- [ ] **Step 3: Implement schema/runtime migration**

Keep existing icon/widget enable keys canonical. Add small/large enable/offset/radius and icon/widget offset/radius keys. Preserve legacy import compatibility.

- [ ] **Step 4: Update legacy settings XML**

Expose four groups (Icon, Widget, Small Folder, Large Folder), each with enable, size offset, and corner radius (`0 = Auto`). Icon summary states it also controls Dock icons.

- [ ] **Step 5: Verify GREEN**

Run config codec/migration/export/import tests and new style tests.

---

### Task 6: Geometry size offset and GPU-safe Auto icon shape

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassBoundsPolicy.java`
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassIconShapeResolver.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassBoundsPolicyTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassIconShapeResolverTest.java`

**Interfaces:**
- `LauncherGlassBoundsPolicy.expand(left, top, right, bottom, offsetPx)` performs symmetric edge expansion/inset and guarantees positive dimensions.
- `LauncherGlassIconShapeResolver` returns a rounded-rect-compatible auto radius for circle/rounded adaptive icon outlines and conservative fallback otherwise.

- [ ] **Step 1: Write failing pure geometry tests**

Test +12 expansion, -20 inset, positive-dimension clamp, and forced radius cap at half minimum final dimension.

- [ ] **Step 2: Verify RED**

Expected: helper missing.

- [ ] **Step 3: Implement bounds policy**

Apply offset after native visual bounds resolution and before root-local normalization.

- [ ] **Step 4: Add icon shape tests and implement resolver**

Keep resolver drawable/outline based. Do not sample pixels or add arbitrary path/stencil support.

- [ ] **Step 5: Integrate node-specific style resolution and verify GREEN**

Run icon geometry/alignment tests plus new bounds/shape tests.

---

### Task 7: Type-specific vendor material suppression

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassVendorMaterialPolicy.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassVendorSuppressionContractTest.java`

**Interfaces:**
- Policy distinguishes `SMALL_FOLDER`, `LARGE_FOLDER`, `WIDGET`, `ICON` ownership and suppresses only vendor material/blur layers, never the real icon drawable/widget content subtree.

- [ ] **Step 1: Add failing source/behavior contracts**

Assert no suppression path calls `host.setAlpha(0)`, `host.setVisibility(GONE)`, or `removeAllViews()` on widget/icon content hosts; folder policy preserves preview children.

- [ ] **Step 2: Verify RED where current generic suppression violates classification**

- [ ] **Step 3: Implement policy and wire hooks**

Use concrete vendor material targets (`mIconImageView`, FolderIcon material owner, widget host blur/background owner) and leave RemoteViews/MAML/icon drawable content intact.

- [ ] **Step 4: Verify GREEN**

Run folder/widget/icon hook regression contracts.

---

### Task 8: Dock generation rebind and one-surface item batching

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/DockGlassItemNode.java`
- Create: `src/main/java/com/hellovoid/liquiddock/DockGlassCompositor.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyRenderer.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/DockGlassGenerationContractTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/DockGlassBatchContractTest.java`

**Interfaces:**
- Dock generation change destroys old input `Surface`/`SurfaceTexture`, creates new producer, rebinds PassBlur, then resumes continuous updates.
- `DockGlassCompositor` owns lightweight item nodes and draws body + items between one `beginGlassFrame` and one output swap.

- [ ] **Step 1: Write failing Dock generation contract**

Assert rotation/root-surface generation calls `rebindProducer` and replacement code releases old input producer rather than only calling `setDefaultBufferSize`.

- [ ] **Step 2: Verify RED**

- [ ] **Step 3: Implement explicit Dock producer generation replacement**

Reuse the existing continuous producer update mode after rebind.

- [ ] **Step 4: Write failing one-surface batching contract**

Assert Dock icons are never `LauncherGlassStaticNode`s; item count does not create TextureViews/Surfaces; compositor has one swap path.

- [ ] **Step 5: Implement Dock item batching and shared icon style wiring**

Discover Dock ShortcutIcon/folder item views under the Dock material host, register lightweight nodes, and render them in Dock-local coordinates using the same icon optical style values as Workspace.

- [ ] **Step 6: Verify GREEN**

Run Dock generation/batch tests and all existing Dock shadow/geometry/Prismal tests.

---

### Task 9: Coverage hooks and full regression build

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherDragOverlayHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassCoverageContractTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassSurfaceCountContractTest.java`

**Interfaces:**
- Recents, folder-open, and overlay edit states feed controller coverage.
- Drag source suppression stays node-local while scene coverage stays controller-global.

- [ ] **Step 1: Write failing coverage tests**

Assert folder-open hooks call controller coverage in addition to source-node suppression; HOME restore requires fresh frame. Add source contract proving static Workspace and Dock icon paths do not instantiate per-object TextureViews.

- [ ] **Step 2: Verify RED**

- [ ] **Step 3: Wire lifecycle hooks into scene controller**

Use the narrowest available MIUI Launcher hooks already present in the project. Do not add process-wide global View polling.

- [ ] **Step 4: Run complete unit suite**

Command: `./gradlew testDebugUnitTest --stacktrace`
Expected: all tests pass.

- [ ] **Step 5: Build debug APK**

Command: `./gradlew assembleDebug --stacktrace`
Expected artifact: `build/outputs/apk/debug/*.apk` uploaded by `.github/workflows/api101-build.yml` as `LiquidDock-api101-debug`.

- [ ] **Step 6: Download and inspect the workflow artifact**

Confirm the artifact exists, contains exactly the expected debug APK(s), and report the final commit SHA and APK file.

## Plan self-review

- Spec coverage: Workspace scene lifecycle, bootstrap, fresh-frame barrier, effective visibility, rotation generation, Dock continuous rebind, Dock item batching, four style groups, migration, size offset, Auto icon shape, vendor suppression, and three-output-domain surface contract all have explicit tasks.
- Placeholder scan: no TBD/TODO placeholders remain.
- Type consistency: controller generation is `long`; four component styles are represented by one `GlassComponentStyle` type; Workspace static classification uses `LauncherGlassNodeKind`; Dock items use a separate `DockGlassItemNode` and are never Workspace static nodes.
