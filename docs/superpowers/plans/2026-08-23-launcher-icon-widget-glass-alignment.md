# Launcher Icon/Widget Glass Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the residual root wallpaper-sampling offset using ViewRoot surface insets and extend the one-surface Launcher static compositor to widgets and app icons, with glass visible beneath transparent icon pixels but not under labels.

**Architecture:** Keep one PassBlur/OES producer, one root backdrop cache and one root-wide static TextureView. Correct producer normalization from surface-buffer coordinates into Decor content coordinates. Generalize lightweight static nodes to FOLDER/WIDGET/ICON and discover concrete widget/icon hosts through per-class constructor hooks.

**Tech Stack:** Android/Java/Kotlin, libxposed API101, OpenGL ES 2, SurfaceTexture/OES, Prismal, Compose settings, JUnit source/contract tests.

**Spec:** `docs/superpowers/specs/2026-08-23-launcher-icon-widget-glass-alignment-design.md`

## Global Constraints

- GPU-only wallpaper source; no CPU/readback capture.
- One shared static root Surface only; no per-object static TextureView/Surface/EGLSurface.
- No hard-coded alignment pixel correction.
- Dock ownership and Prismal optics remain independent.
- Folder press/open/drag lifecycle must not regress.
- Widget and icon foreground drawing/interaction remain owned by MIUI Launcher.

---

### Task 1: Lock Alignment and Generic-Node Behavior with RED Contracts

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherGlassSurfaceContentRectTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherGlassProducerContentRectContractTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherGlassGenericStaticNodeContractTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherGlassIconGeometryTest.java`

**Interfaces:**
- Consumes current Session/static compositor implementation.
- Produces exact expected contracts for the remaining tasks.

- [ ] **Step 1: Add pure mapping tests** for zero insets and asymmetric left/top/right/bottom insets. The expected UV rect uses left and bottom origins as documented in the spec.
- [ ] **Step 2: Add Session source contract** requiring reflective `mWindowAttributes` / `surfaceInsets`, a stored content rect, and removal of the literal full-buffer `uBackdropRect` upload in `renderNormalizationRoot()`.
- [ ] **Step 3: Add static-node/hook contracts** requiring FOLDER/WIDGET/ICON kinds, concrete `ShortcutIcon`, `LauncherAppWidgetHostView`, and `MaMlHostView` discovery, with no global `View.onAttachedToWindow` hook and no static per-object Surface.
- [ ] **Step 4: Add icon fallback geometry tests** requiring a centered icon visual rectangle that is shorter than a label-bearing host and never spans the label area.
- [ ] **Step 5: Run `./gradlew testDebugUnitTest --stacktrace`** and confirm only the new contracts fail for missing behavior.
- [ ] **Step 6: Commit the intentional RED tests.**

---

### Task 2: Correct Surface-Buffer to Root-Content Mapping

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSurfaceContentRect.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Test: Task 1 alignment tests.

**Interfaces:**
- Produces `LauncherGlassSurfaceContentRect.resolve(int surfaceWidth, int surfaceHeight, int left, int top, int right, int bottom)` returning normalized left/bottom/width/height.
- `ProducerGeometry` carries this rect to `renderNormalizationRoot()`.

- [ ] **Step 1: Implement the pure rect helper** with finite/clamped normalized values and full-buffer fallback when content would become empty.
- [ ] **Step 2: Extend `ProducerGeometry`** with surface inset values/content rect and include content-rect changes in geometry-change detection.
- [ ] **Step 3: Read ViewRoot `mWindowAttributes.surfaceInsets` reflectively** inside `readSurfaceGeometry()`, defaulting to zero insets when unavailable.
- [ ] **Step 4: Upload the content rect in `renderNormalizationRoot()`** instead of literal `(0,0,1,1)` for `uBackdropRect`; leave texture-matrix and rotation logic unchanged.
- [ ] **Step 5: Log surface size, buffer size and measured insets when producer geometry binds/changes** for hardware diagnosis.
- [ ] **Step 6: Run focused alignment tests.**

---

### Task 3: Generalize Lightweight Static Nodes

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java`
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassIconGeometry.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java`
- Test: generic-node/icon tests plus existing folder lifecycle tests.

**Interfaces:**
- `LauncherGlassStaticNode.attachToMaterial(View, LauncherGlassDragState.Kind, float, LiquidDockConfig.Glass)`.
- Existing folder path passes `Kind.FOLDER`.
- ICON nodes resolve a local visual rect through `LauncherGlassIconGeometry`; WIDGET/FOLDER use full host bounds.

- [ ] **Step 1: Add node kind while preserving current weak ownership/lifecycle/interaction behavior.**
- [ ] **Step 2: Implement `LauncherGlassIconGeometry`**. Prefer a `TextView` top compound drawable's actual bounds/intrinsic size and its drawable content placement; use a conservative centered-square fallback that excludes the label area.
- [ ] **Step 3: Make icon `captureGeometry()` map the visual sub-rect corners through the same full matrix chain as other nodes.**
- [ ] **Step 4: Update folder attachment to explicitly register `Kind.FOLDER`.**
- [ ] **Step 5: Run focused existing folder + new generic-node tests.**

---

### Task 4: Discover Static Widget and Icon Hosts

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Test: `LauncherGlassGenericStaticNodeContractTest.java`

**Interfaces:**
- Hook installs concrete-class declared constructors only.
- Per-instance attach listeners bind a static node after layout.

- [ ] **Step 1: Resolve and hook declared constructors** for `ShortcutIcon`, `LauncherAppWidgetHostView`, and `maml.MaMlHostView`; missing optional classes must not abort Launcher startup.
- [ ] **Step 2: Install one weak per-instance attach observer** and schedule binding on animation when attached and laid out.
- [ ] **Step 3: Register `Kind.ICON` / `Kind.WIDGET` with the shared static-node path.** Do not modify widget/icon drawable/background transparency.
- [ ] **Step 4: Resolve native widget radius when available; otherwise use a proportional widget fallback. Resolve icon radius from its icon visual dimensions.
- [ ] **Step 5: Install the hook from `ModuleMain` without changing Dock ownership.**
- [ ] **Step 6: Run focused hook/static-node contracts.**

---

### Task 5: Extend Drag Suppression to Widget and Icon Static Nodes

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherDragOverlayHook.java`
- Test: existing drag contracts + generic static-node contract.

**Interfaces:**
- Drag metadata resolves the original static host/material for all FOLDER/WIDGET/ICON kinds.
- `DragRecord` retains one weak static-node reference.

- [ ] **Step 1: Remove folder-only install gating** and enable the drag hook whenever any Launcher static-glass kind is enabled under master Liquid Glass.
- [ ] **Step 2: Resolve widget/icon static nodes from metadata/source Views at drag start.**
- [ ] **Step 3: Suppress the matching static node while the existing single drag overlay is active and restore it on removal.**
- [ ] **Step 4: Run all drag-focused tests.**

---

### Task 6: Add Widget/Icon Configuration and UI

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java`
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Modify: `src/main/res/xml/preferences.xml`
- Modify: `src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java`
- Add/modify UI contract test as needed.

**Interfaces:**
- `Glass.WIDGET_GLASS` => `liquid_widget_glass`, default true, ALWAYS.
- `Glass.ICON_GLASS` => `liquid_icon_glass`, default true, ALWAYS.
- Runtime fields `widgetEnabled`, `iconEnabled`.

- [ ] **Step 1: Add both schema keys and runtime fields.**
- [ ] **Step 2: Add Compose toggles** with summaries describing shared underlay/transparent-region behavior.
- [ ] **Step 3: Add legacy Preference toggles** under Launcher Liquid Glass.
- [ ] **Step 4: Update complete-default export count from 130 to 132 and assert both new keys export `true`.**
- [ ] **Step 5: Run config/UI tests.**

---

### Task 7: Full Verification and Artifact

**Files:**
- Update PR #54 body after verification.

- [ ] **Step 1: Run `./gradlew testDebugUnitTest --stacktrace`** and require zero failures.
- [ ] **Step 2: Run `./gradlew assembleDebug --stacktrace`** and require BUILD SUCCESSFUL.
- [ ] **Step 3: Verify standard `.github/workflows/api101-build.yml` is restored and no temporary workflow/script remains.**
- [ ] **Step 4: Record Actions run/job, artifact ID, size and SHA-256.**
- [ ] **Step 5: Update PR #54 body** with surface-inset alignment correction, widget/icon shared static glass, icon visual-subrect semantics, tests/build artifact, and hardware-validation caveat.
- [ ] **Step 6: Keep PR draft; do not merge without explicit user request.**
