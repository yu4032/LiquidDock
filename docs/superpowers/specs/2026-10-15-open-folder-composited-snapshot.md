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


### October 16: native close sampling and material ownership
- Keep the frozen wallpaper source throughout the reverse spring; do not change Gaussian kernel to Workspace blur during close. The old 2-pass reblur could overtake geometry/swap on the EGL thread and make the endpoint flash.
- On closing frames, use the actual transformed `FolderAnimIcon` child image's root-space bounds as the Prismal lens rectangle and wallpaper sample position. Use the measured bounds to derive the backdrop fade; retain the native-spec interpolation only when no safe live plate is available.
- Submit every changed closing geometry to the coalescing output queue; no 11ms render-pacing gate during a 165Hz native reverse spring.
- Native advanced-material ownership extends to the actual animated FolderIcon container in addition to its image View; track that plate's background drawable and suppress its RenderNode blur only while the expanded Prismal presentation is active. Preserve native content and OEM restoration on exit.

### Close Gaussian continuity
The frozen wallpaper source is normalized once; Prismal's folder session opts into two precomputed GPU Gaussian blur textures: OPEN_FOLDER radius and the current Workspace radius, both generated only at capture/live-config update. Native reverse-spring progress blends the two in the same optical fragment shader, so the final close frame already matches Workspace Gaussian blur. No per-frame blur kernels, no second PassBlur capture, and no modification to other Prismal clients.

The full-screen wallpaper blur and the refractive lens both blend folder/workspace Gaussian textures with the same close progress. A folder-only composite shader samples both prepared textures in one pass; the existing non-folder compositing shader is unchanged.

### October 16 video regression: close geometry source and final output
The video demonstrates a drifting lens while OEM folder contents contract normally. The previous direct `bounds(getFolderAnimIcon().getIconImageView())` path had an independent local origin/pivot and was then center-offset once; it is not a valid geometry source. Both directions now interpolate strictly in the native desktop-icon to Folder.mBackgroundView root-space endpoints; reverse Folme translation/scale properties supply only normalized progress. Only channels demonstrably active at close initialization participate, avoiding a stationary translation freezing an independently advancing scale. The native content and background-completion gate queues the exact terminal icon geometry / 100% Workspace blur and waits for its final GL swap plus one UI frame before freeing the TextureView. This is not a fixed animation timer. Existing OEM native background and Modal/Dock ownership remains untouched.
