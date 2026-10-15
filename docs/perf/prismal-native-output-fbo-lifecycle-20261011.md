# Prismal native RGBA output FBO allocation audit — 2026-10-11

Independent draft performance fix, based on main `48ca068e`; no device results yet.

## Proven unnecessary allocation

`LauncherGlassSession` calls `PrismalRenderer.prepareBackdrop()` and then `beginGlassFrameOnSurface()` for the root-wide Workspace StaticLayer. The latter renders **directly into EGL framebuffer zero**. Nevertheless the previous `ensureTargets()` eagerly created `outputTexture` + `outputFramebuffer` at native logical root resolution.

This native FBO is not consumed by the static direct-to-window route. At 3008×1880 and RGBA8888 it is 5,655,040 pixels × 4 bytes ≈ 21.57 MiB of additional texture storage per active renderer (before alignment/compression and any driver overhead).

## Exact change

- `ensureTargets()` keeps source normalization and blur target allocations but no longer preallocates `outputTexture/outputFramebuffer`.
- `beginGlassFrameRegion()` initializes the native RGBA scene output texture/FBO **on demand**, so Dock, popups, drag, legacy `render()` and any caller requiring a texture output retain their old behavior.
- Same-geometry cache validity uses source/blur framebuffer handles, **not outputTexture**, preventing a costly realloc/release loop during every static Widget source frame.
- `beginGlassFrameOnSurface()` and EGL Surface dimensions / coordinate mapping are unchanged. Texture outputs still receive an initial full transparent clear.
- Driver FBO creation failure releases its new texture before propagating the existing render error.

## Scope and verification

This is an **allocation/VRAM-pressure reduction**, not a reduction of the fullscreen EGL swap size or draw bandwidth, and does not establish FPS improvement. No resolution, shader, blur, refraction, buffer age or wallpaper/return animation change. Unit tests enforce lazy target construction, initial clear and texture-output compatibility.

Validate native Workspace static rendering, then drag and Dock/folder glass texture paths (including alternating direct EGL and internal FBO frames), app→HOME, Recents→HOME, rotation/resize and native wallpaper endpoint rebind; compare GPU memory usage and frame pacing where measurable.

A separate experiment PR #353 adds Perfetto EGL/HWUI metrics and conservative stable-scene TextureView cropping. Keep the findings independent until each is device verified.
