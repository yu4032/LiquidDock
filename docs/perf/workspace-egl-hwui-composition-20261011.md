# Workspace EGL / transparent HWUI composition audit — 2026-10-11

**Base:** main `48ca068e`. **Status:** experimental / unmerged. No claim of on-device FPS improvement.

## Actual output topology (from production code)

- `LauncherGlassStaticLayer` is exactly one root-wide, `MATCH_PARENT`, transparent `TextureView` attached below Workspace content in the Launcher View hierarchy. It owns a `SurfaceTexture` exported as an Android `Surface`.
- `RootPassBlurBackend` creates an **RGBA8888 EGL window surface** from it, with a GLES2 context; its native PassBlur input is a separate OES SurfaceTexture.
- Every consumed source frame goes through OES normalize, reduced-density backdrop blur, then `PrismalRenderer.beginGlassFrameOnSurface()`, which clears the whole root-sized RGBA back buffer. All static glass nodes shade only their small scissored regions; finally the backend `eglSwapBuffers()` queues the entire root-sized window surface.
- The output is presented via TextureView/HWUI. It is **not safe to label the TextureView buffer itself as an independent SurfaceFlinger layer** without inspecting the actual layer tree: HWUI may sample the texture into the Launcher application surface. The Launcher actual SurfaceFrame and SurfaceFlinger display frames in FrameTimeline show final deadlines, not a unique causal owner.

At user-reported 3008×1880 pixels, one 8-bit four-channel full-screen window buffer has 5,655,040 pixels ≈21.57MiB. A single full-resolution read+write pass would move ≈43.14MiB before bandwidth compression/caching; a 165Hz complete pass therefore has multi-GiB/s *theoretical* traffic. These are dimensional estimates, **not GPU bus measurements**, and they do not establish the real hardware composition path.

## Why shader arithmetic alone may show little improvement

GL `eglSwapBuffers` wall time includes rendering queue pressure and the producer/consumer buffer wait. It does not mean a shader took exactly that CPU time. The previous supplied recordings showed many Launcher `BUFFER_STUFFING` FrameTimeline bits while seven Widget glass nodes were visible; an individual bit is correlation, not attribution.

Scissoring the per-node fragment shader reduces expensive shading but does not shrink the output window surface nor automatically reduce HWUI sampling of the transparent full-root TextureView.

## Narrow experimental optimization

Apply a **View.clipBounds** only to the HWUI compositor's root-space visible area when ALL conditions hold:

1. The Workspace static node snapshot is complete with finite geometry and a valid root transform.
2. Two consecutive OnPreDraw observations confirm no root, widget or Workspace scroll motion; reject while rotation settles or vendor Recents covers the home.
3. The union of all Widget/other static-node bounds, expanded by a **20px per-edge guard**, covers at most 85% of the root. Near-fullscreen or ambiguous scenes keep full-area composition.
4. Scroll events, node registration/removal, SurfaceTexture attach/detach/size, geometry motion and reset immediately restore an unrestricted TextureView.

**Never** modify `TextureView.setTransform()`, parent translation, root-wide Surface dimensions, EGL config, buffer format, native wallpaper coordinate/zoom, or PassBlur frame cadence. The EGL window is still full native resolution. This may only reduce HWUI sampling/composition of **transparent regions outside a conservative geometric union**, not the number of queued full-screen EGL buffers.

## Measurement added

Trace sections when Perfetto atrace is enabled, no synchronous `debugLogging` needed:

- `LD.Workspace.SurfaceClear`: root RGBA clear command submission
- `LD.Workspace.StaticDraw`: draw-call submissions for static nodes
- `LD.Workspace.StaticSwap`: EGL buffer exchange
- `LD.Workspace.EglSwap`: lower-level backend exchange, also for drag surfaces
- `LD.Workspace.StaticSurfacePixels`: full EGL output size (should never change)
- `LD.Workspace.HwuiClipPixels`: requested **visible** region size, not measured SF damage or GPU physical bandwidth
- `LD.Workspace.DrawnWidgets`: Widgets submitted on the GL thread
- `LD.Workspace.EglBufferAge`: 1/60 sampled EGL_EXT_buffer_age value **only when supported**. Query is diagnostic only. Never imply buffer-preservation guarantees based on this attribute alone.

### What is needed for attribution

Run identical multi-Widget app→HOME returns 5–10 times, main vs this experimental branch, same device orientation, capture GL/atrace app slices, `gfx` FrameTimeline data for Launcher and SF, GPU frequency/busy/stall where accessible. Record WindowManager layers/CompositionType/TextureView integration where possible. Segment periods where `HwuiClipPixels` actually falls below `StaticSurfacePixels`; no benefit should be claimed when both are equal.

Mandatory visual regression checks: slow/fast workspace scroll, page edge, Widget fling, App→HOME animation, Recents→HOME, native wallpaper zoom, 0→many widgets, Widgets that span the viewport edge, pinching, folder and drag, landscape/portrait, icon dark/light surfaces, glass fade, first buffer after rebind. Fail closed to full viewport on any crop artifacts.

Future investigation, **not implemented**: EGL buffer age + damage-region rendering and buffer reuse. Partial root clears are unsafe without correct age tracking and per-buffer damage history; the Android GLES2/EGL14 path currently swaps full buffers. Never introduce `eglSwapBuffersWithDamage`, EGL buffer preservation or surface-window dynamic resizing blindly.
