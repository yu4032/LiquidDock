# Dock Legacy Renderer Rollback

Date: 2026-08-23
Status: authoritative override for Dock only

This note supersedes the Dock-specific sections of `2026-08-23-glass-scene-architecture-design.md`.
Workspace Scene Architecture remains in force; Dock does not participate in it.

## Authoritative Dock behavior

Dock is restored to the proven implementation from commit `fbc3d6a96ef46932b4e418af128a1bfa0ebddadc`.

- Dock owns one independent `Miuix307PassBlurTextureView` / EGL output layer.
- Dock owns its own PassBlur producer and keeps it continuous/realtime.
- Dock does not use `LauncherGlassSession`, `LauncherGlassSceneController`, Workspace generation, or Workspace freshness gates.
- Dock does not use `DockGlassCompositor`, `DockGlassItemNode`, `DockGlassItemRegistry`, or `DockGlassSceneSnapshot`.
- Workspace `ShortcutIcon` discovery must never register icons into a Dock registry.
- No per-Dock-icon glass surfaces or Workspace static nodes are created.
- Rotation/rebind behavior follows the restored legacy Dock implementation rather than the Scheme A Dock generation/batch design.
- The normal GPU-only constraint remains: no PixelCopy, Bitmap capture, `glReadPixels`, MediaProjection, or screen recording in the successful Dock path.

## Workspace boundary

Workspace keeps the new shared static scene architecture for icons, widgets, small folders, and large folders. Its component styles apply only to Workspace/drag scene nodes. They must not be interpreted as ownership of Dock icon rendering.

## Device regression that caused this override

The Scheme A Dock item compositor produced incorrect device behavior: Dock backdrop/content semantics regressed and Dock item glass did not behave as the proven independent Dock layer. The user explicitly required a complete Dock rollback instead of further incremental repair.

## Verification

The rollback is accepted only when:

1. `Miuix307PassBlurTextureView.java` and `Miuix307ZeroCopyRenderer.java` match the `fbc3d6a96e...` versions.
2. The four Scheme A `DockGlass*` production classes are absent.
3. Dock remains a continuous independent TextureView/EGL renderer.
4. Workspace static glass remains a separate output domain.
5. Full unit tests and `assembleDebug` pass.
