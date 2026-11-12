# Gboard Frozen-Sampling HWUI Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Draw frozen-sampling Gboard floating-keyboard glass in Gboard's ViewRoot so its geometry is submitted in the same frame as the keyboard.

**Architecture:** Keep `RootPassBlurBackend` and `PrismalRenderer` as the capture/preparation pipeline. For frozen sampling only, export the prepared raw and blurred textures once per existing capture event into immutable HardwareBitmap inputs and draw the current lens through an AGSL `View`. Retain the existing TextureView output as a hidden fallback and leave real-time sampling plus the handwriting capsule on the existing GLES route.

**Tech Stack:** Android Java, GLES20/EGL14, `HardwareBuffer`, `Bitmap.wrapHardwareBuffer`, Android `RuntimeShader`/AGSL, existing Prismal and Gboard lifecycle hooks.

**Spec:** `docs/superpowers/specs/2026-11-01-gboard-frozen-hwui-glass-design.md`

## Global Constraints

- Enable the pilot only for floating Gboard glass when `realtimeBackgroundSampling == false`.
- Keep real-time sampling, handwriting toolbar glass, and PassBlur capture ownership/exclusions unchanged.
- Preserve the Gboard-root exclusion so the glass sink is never sampled into its own backdrop.
- Do not allocate or export HardwareBuffers on geometry-only frames; export only after existing frozen-capture events.
- Do not recycle HardwareBuffers/Bitmaps while HWUI may still be drawing them.
- Do not add or run unit tests; verify with a debug build and connected-device behavior, as requested by the workspace rules.

## Files and Responsibilities

- Create `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassHwuiView.java`: AGSL-backed full-root glass drawing, immutable backdrop binding, geometry invalidation, and first-draw/failure callbacks.
- Modify `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassSession.java`: frozen-mode selection, one-shot prepared-texture exports, geometry-only HWUI invalidation, and GLES fallback control.
- Modify `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassView.java`: retain the TextureView fallback and host/switch the frozen-mode HWUI view without changing real-time behavior.
- Modify `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassCoordinator.java`: publish pre-draw geometry to the active output, preserve z-order, and hide stock Gboard visuals only after the selected output draws.
- Modify `src/main/java/com/hellovoid/liquiddock/RootPassBlurBackend.java` only to generalize the existing one-shot HardwareBitmap export documentation if needed; keep its buffer-copy and fence behavior unchanged.
- Reuse `LauncherGlassHwuiProbeView.agslSource(...)`, `LauncherGlassSession.setHwuiProbeMaterialUniforms(...)`, and `LauncherGlassSession.setHwuiProbeDecorationUniforms(...)` so the pilot does not fork the Prismal material or uniform mapping.

## Review Focus

- Initial backdrop export or AGSL setup failure: stock background stays visible until the TextureView fallback presents.
- Runtime-enabled real-time sampling: continues through the existing TextureView route with no HWUI exports.
- Frozen motion-start/settle refresh: new snapshots replace the old pair, but geometry-only pre-draws do not copy textures.
- Gboard root capture exclusion: sink remains absent from its own backdrop on first presentation and subsequent refreshes.
- Detach/shutdown during an in-flight HWUI draw: references are dropped safely without premature recycling.

## Tasks

### Task 1: Add the frozen-mode HWUI drawing view

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassHwuiView.java`

**Interfaces:**
- `setBackdrops(Bitmap raw, Bitmap blurred, int logicalWidth, int logicalHeight)` binds the immutable pair with linear-filtered `BitmapShader` inputs and initializes the existing Prismal AGSL source.
- `setScene(GboardFloatingGlassGeometry geometry, PrismalParams params, PrismalHighlightProfile highlights, PrismalEdgeDecoration decoration, float density)` publishes the current material and lens bounds for the next Gboard draw.
- `clearBackdrops()` drops RuntimeShader and BitmapShader references without recycling in-flight HardwareBuffers.
- `Listener.onPresented()` fires only after a successful glass draw; `Listener.onFailure(Throwable)` lets the host select the GL fallback.

- [x] Create the View and bind raw/blur `BitmapShader`s using `BitmapShader.FILTER_MODE_LINEAR`.
- [x] Reuse the current decorated Prismal AGSL source and material/edge uniform setters; set `u_resolution`, `uProbeBounds`, `u_glassSize`, corner radii, `uProbeRawScale`, `uProbeBlurScale`, and `uProbeWallpaperScale=1f` from the supplied snapshot.
- [x] Draw the lens plus the existing edge guard in root coordinates; invalidate on `setScene` without requesting a backdrop capture.
- [x] Call `onPresented` after a successful `Canvas.drawRect`; route initialization/draw errors to `onFailure`.
- [x] Review-focus check: verify crop and orientation uniform mapping against `GboardFloatingGlassGeometry` and the existing TextureView output coordinates.

### Task 2: Export frozen backdrops and keep geometry updates on HWUI

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassSession.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/RootPassBlurBackend.java` only if its export API documentation needs generalizing.

**Interfaces:**
- `GboardFloatingGlassSession` attaches one `GboardFloatingGlassHwuiView` for the frozen/fullscreen pilot.
- The session sends each prepared raw/blur pair plus logical dimensions to the view on the main thread; the view owns shader binding and draw invalidation.
- `updateGeometry(...)` sends scene state to HWUI only after the snapshot dimensions match the current logical frame.

- [x] On each existing frozen capture event, prepare Prismal once, export `preparedSourceTexture()` using `preparedSourceWidth()/preparedSourceHeight()`, and export `preparedBlurTexture()` using `preparedBlurWidth()/preparedBlurHeight()`.
- [x] Post the immutable pair to the HWUI view; on failure, activate the existing TextureView path and request one GL render from the current prepared backdrop.
- [x] When HWUI is active, skip per-geometry `drawGlass`/TextureView swaps; update only the HWUI scene.
- [x] Keep `onFreshFrame` and motion-start/settle freeze transitions authoritative; never copy textures on `updateGeometry` alone.
- [x] Leave the real-time and cropped/handwriting constructors on the existing GL render/swap path.
- [x] Review-focus check: verify stale root dimensions reject a snapshot instead of displaying it on a mismatched frame; dimension retries use one-shot frozen captures.

### Task 3: Integrate first-frame handoff, visibility, and fallback

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassCoordinator.java`

**Interfaces:**
- The existing Gboard sink hosts the TextureView fallback and optional HWUI child below keyboard content.
- `showHwuiOutput()` and `showTextureFallback()` switch output visibility while preserving child order beneath the native keyboard subtree.
- The coordinator's `syncGeometry` publishes the same captured snapshot to the active Gboard output before the current traversal draws.

- [x] Keep the TextureView output available but hidden while the frozen HWUI path is preparing its first backdrop.
- [x] Reveal HWUI only after a valid snapshot is bound; hide the TextureView sibling while HWUI draws.
- [x] Hide stock Gboard visuals only after the HWUI view reports its first successful draw; on HWUI failure, show the TextureView fallback and wait for its existing `onSurfaceTextureUpdated` signal.
- [x] On hide/detach, clear HWUI references and release the session through the existing lifecycle path; keep PassBlur root exclusions unchanged.
- [x] Review-focus check: verify the frozen path cannot signal presentation from the hidden, blank TextureView.

### Task 4: Build, install, and inspect the pilot

**Files:**
- No additional source files.

- [x] Run `gradlew.bat assembleDebug -PworkspaceGlassHwuiProbe=true` from the clean PR worktree.
- [x] Install the resulting APK. The device reports that LSPosed needs a reboot before the updated system-scoped hook takes effect.
- [ ] After reboot, inspect initial presentation, floating move/resize, motion-start refresh, settle refresh, hide/detach, and fallback; confirm same-frame glass geometry and no self-capture.
- [ ] With real-time sampling enabled, confirm the existing GLES/TextureView path and visual behavior remain unchanged.
- [x] Run `git diff --check`; keep only the Gboard pilot and its design/plan documents in the PR change set.
