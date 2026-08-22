# Launcher Static Page Glass Compositor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-static-object Launcher glass surfaces with one batched root static compositor, fix clipped static geometry, and cache the root backdrop so page scrolling does not repeat blur preparation.

**Architecture:** `LauncherGlassStaticNode` becomes the lightweight static material binding. One `LauncherGlassStaticLayer` is inserted at the bottom of the stable root and supplies the only static output Surface. `LauncherGlassSession` owns both this output and the existing optional drag output, prepares one root-sized wallpaper backdrop only when the source/producer/config requires it, then batch-draws static nodes into one transparent full-root scene.

**Tech Stack:** Android View/TextureView, OpenGL ES 2.0, EGL14, SurfaceTexture/OES, HyperOS PassBlur, Prismal renderer, libxposed hooks, JUnit source/logic contracts, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-23-launcher-static-page-glass-compositor-design.md`

## Global Constraints

- Stable Launcher root remains the sole PassBlur producer owner.
- No PixelCopy, ImageReader, Bitmap readback, `glReadPixels`, screen recording, or CPU wallpaper capture.
- Dock PassBlur/render ownership is unchanged.
- Drag keeps one reusable overlay/output and reuses the Launcher session backdrop.
- Static object count must not increase TextureView/Surface/EGLSurface/static-swap count.
- Folder radius GUI/config semantics remain unchanged.

---

### Task 1: Lock the new architecture with failing contracts

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherGlassStaticCompositorContractTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherGlassUnclippedGeometryContractTest.java`

**Interfaces:**
- Consumes: current source tree at `49f55c76...`.
- Produces: RED contracts requiring `LauncherGlassStaticNode`, `LauncherGlassStaticLayer`, root backdrop separation, and no static `getGlobalVisibleRect()`.

- [ ] **Step 1: Write architecture/source contracts** that assert static Folder code no longer claims `LauncherGlassSinkView`, static node owns no `Surface`, static layer is singleton-per-root, and session has one explicit static output.
- [ ] **Step 2: Write geometry contract** requiring full local-corner matrix mapping and forbidding `getGlobalVisibleRect()` in static geometry.
- [ ] **Step 3: Run the two new tests.**

Run: `./gradlew testDebugUnitTest --tests '*LauncherGlassStaticCompositorContractTest' --tests '*LauncherGlassUnclippedGeometryContractTest' --stacktrace`

Expected: FAIL because the static-node/layer architecture does not exist yet.

### Task 2: Add lightweight static nodes and unclipped root geometry

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java`

**Interfaces:**
- Produces: `LauncherGlassStaticNode.attachToMaterial(View,float,LiquidDockConfig.Glass)`, `find(View)`, `materialHost()`, `syncFromMaterial()`, `captureGeometry(View)`, `setPressInteraction(...)`, `setSuppressedByFolderOpen(...)`, `setSuppressedByDrag(...)`, `requestLifecycleRefresh()`, `dispose()`.
- Consumes: `LauncherGlassSession.registerStaticNode/unregisterStaticNode/updateStaticInteraction` from Task 3.

- [ ] **Step 1: Implement complete-corner matrix mapping.** Use `View.transformMatrixToGlobal()` on material and root; invert the root matrix; transform four local corners; resolve unclipped root bounds and radius scale.
- [ ] **Step 2: Move static press/open/drag/lifecycle state from the old sink behavior into `LauncherGlassStaticNode` without adding any View/Surface ownership.**
- [ ] **Step 3: Migrate `MiuixFolderGlassHook` CLAIMED/opened-folder references and attachment paths from `LauncherGlassSinkView` to `LauncherGlassStaticNode`.** Preserve material transparency and startup recovery.

### Task 3: Create the single static root output

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticLayer.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`

**Interfaces:**
- `LauncherGlassStaticLayer.acquire(View root, LauncherGlassSession session)` returns one weakly registered layer per stable root and inserts it at index 0 of the root ViewGroup.
- Session produces `registerStaticNode`, `unregisterStaticNode`, `updateStaticInteraction`, `attachStaticOutput`, `resizeStaticOutput`, `detachStaticOutput`.

- [ ] **Step 1: Implement a transparent, noninteractive full-root `TextureView` singleton with SurfaceTexture lifecycle forwarding to the session.**
- [ ] **Step 2: Add static-node state and one `OutputState staticOutput` to the session.** Static nodes have geometry stability/interaction but no output state.
- [ ] **Step 3: Ensure registering the first static node acquires the root layer; detach/shutdown releases only the one static output.**

### Task 4: Replace atlas-coupled static rendering with one root backdrop cache

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`

**Interfaces:**
- `renderNormalizationRoot()` normalizes the full stable-root OES domain to `rawTexture` with `uBackdropRect=(0,0,1,1)`.
- Geometry-only changes call `requestFrame(false)`; source/root/config changes call `requestBackdropRebuild()`.

- [ ] **Step 1: Allocate the normalized raw target at `rootWidth x rootHeight` rather than an object atlas for the static compositor path.**
- [ ] **Step 2: Rebuild Prismal backdrop only when OES source, producer geometry, root size, or optical config changes.**
- [ ] **Step 3: On every static scene redraw call `beginGlassFrame()`, draw all valid static root-space nodes, and present the full texture once to `staticOutput`.**
- [ ] **Step 4: Keep drag sinks functional by reusing the same prepared backdrop, beginning a fresh glass frame for the active drag node, and presenting its root-space crop to the existing drag output.**
- [ ] **Step 5: Leave `LauncherGlassGpuAtlas` in the repository for compatibility/history but remove it from the active static render scheduling path.**

### Task 5: Rewire drag suppression without restoring per-folder sinks

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherDragOverlayHook.java`

**Interfaces:**
- `DragRecord` and `ResolvedSource` hold `WeakReference<LauncherGlassStaticNode>` / `LauncherGlassStaticNode` for the static counterpart.
- Drag overlay itself still creates exactly one `LauncherGlassSinkView` carrier sink.

- [ ] **Step 1: Resolve folder static state through `LauncherGlassStaticNode.find(material)` instead of scanning material parents for a sink View.**
- [ ] **Step 2: Install `onDragContainerBgAnimAlpha` suppression from the node's material class when the node is first claimed.**
- [ ] **Step 3: Preserve DragView classification, drag geometry authority, folder radius override and begin/end behavior unchanged.**

### Task 6: GREEN verification and build

**Files:**
- Test: all `src/test` and `prismal/src/test` suites.
- Build: standard debug APK.

- [ ] **Step 1: Run focused new contracts.**

Run: `./gradlew testDebugUnitTest --tests '*LauncherGlassStaticCompositorContractTest' --tests '*LauncherGlassUnclippedGeometryContractTest' --stacktrace`

Expected: PASS.

- [ ] **Step 2: Run the complete unit suite.**

Run: `./gradlew testDebugUnitTest --stacktrace`

Expected: BUILD SUCCESSFUL with no regression failures.

- [ ] **Step 3: Build debug APK.**

Run: `./gradlew assembleDebug --stacktrace`

Expected: BUILD SUCCESSFUL and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 4: Verify source constraints**: no static `getGlobalVisibleRect`, no static per-object TextureView creation, no new CPU capture APIs, no second PassBlur producer.

- [ ] **Step 5: Update PR #54 with the new head, architecture change, test/build run IDs and the explicit note that physical-device alignment/frame pacing still require device validation.
