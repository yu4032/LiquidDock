# Open-folder glass: wallpaper-only backdrop (October 16, 2026)

## Source and visibility

`OPEN_FOLDER` uses a separate transparent, non-interactive PassBlur capture authority. It excludes its own source window, the verified Launcher root surface, DockAssistantView, **the actual bound Dock SurfaceControl root(s) obtained from the active Dock material and producer binding**, and system chrome. The first GPU-prepared wallpaper frame is frozen for both opening and closing; no `ScreenCapture`, `PixelCopy` or software `Bitmap` is used.

Instead of beginning capture at `FolderIcon.ACTION_DOWN`, the coordinator keeps only its session identity and starts the separate PassBlur window **two render frames after native `FolderCling.open()`**. This gives the OEM Dock FolderState alpha transaction time to reach SurfaceFlinger before the producer can emit its first image. The Dock exclusions include both the names seen at touch and at the new bind, because native reparenting can change its exact root. Because Launcher and Dock are excluded, the wallpaper-only source may complete *after* native `FolderCling.open()` begins without recursively sampling the folder. Native Launcher remains the sole owner of Workspace/Dock z-order, alpha and touch modality; LiquidDock must never bypass the OEM FolderState listeners.

The **separate Workspace glass TextureView** is hard-hidden as the native folder opens. It is not parented under the OEM-faded Workspace and otherwise retains a stale source-folder lens at the original icon position when its renderer freezes. The cached GL input is retained, but the UI surface is revealed only after the folder closes and a new Workspace frame is committed. This does not affect the accepted Dock producer-drain/generation-guarded thaw implementation.

## Geometry and optics

Native FolderAnimController spring data drives the lens in both directions. If the exact native animation-spec hook is not called, the transformed animation View bounds are used to estimate the visual progress. If the folder session is not ready or a material cannot be safely replaced, keep the OEM plate instead of hiding content. A stable sibling lens renders under FolderCling; native folder text and icons remain OEM-owned.

The four folder-only optics controls remain under Appearance → Folders (blur 0–60 px, IOR 100–200, thickness 1–60 dp, tint alpha 0–160). Defaults remain 6 px, 1.55, 18 dp, and 35/255; they do not change other surfaces.

## Regression boundaries

- The Workspace glass cache may not remain visible at the source folder coordinates after opening.
- No recursive source-folder or FolderCling pixels in the wallpaper-only snapshot.
- Native workspace/Dock modal input, native open/close geometry, and post-close material restoration remain intact.
- Native fallback remains available when PassBlur startup fails, including ultra-fast/programmatic opens.
- No change to shortcut popup and non-folder PassBlur domains.
- Verify ordinary/rapid 1×1 and 2×2 opens, close, app launch from folder, and Dock/Workspace post-close recovery on actual Launcher 4.50 hardware.

Focused tests: `LauncherGlassSceneControllerTest`, `PassBlurBindPolicyTest`, `OpenFolderGlassStateTest`, `OpenFolderMotionStateTest`, `OpenFolderGlassGeometryTest`. CI compilation is not a substitute for device acceptance.
