# iOS 27 Dock optical-contour prototype (2026-10-19)

Base: `main@9de86423a7eafafa27a3f8b196e1a495745728e1`. Reference: the source-verified HTML/WebGL2 shader fitted against the supplied sand wallpaper and iOS screenshot.

The Dock body now enables `PrismalIos27DockShader` after the existing SingleEdge + OS4 transformations. The same rounded-box SDF (`distMask`) and already-calculated derivative normal (`os4SdfGradient`) are used for interior top/bottom high-intensity reflection, corner glints, lateral absorption, and a short outside-only dark line. The extended line fits within the existing 2px raster guard. The edge-refraction lens remains single-field and monotonic; a Dock-specific top/bottom asymmetry is added without changing blur capture.

Only `DockGlassCompositor.drawFrame()` enables this preset. `PrismalRenderer` uploads the new uniform on *each node draw*, including icon overlays, preventing leakage. Android's former foreground `StrokeDrawable` is detached only for `DockLiquidGlassHostView` to avoid two misaligned contours; native and non-glass strokes remain. The broad soft shadow stays with Launcher HotSeats/MiShadow to prevent duplicating the native shadow or adding another surface.

This is a calibrated visual experiment, **not pixel-perfect Apple code**. No PixelCopy, ScreenCapture, Bitmap, framebuffer readback, or alternate producer is introduced. Existing UI blur/light/tint and native shadow settings remain unchanged. Inspect all four corners on a high-contrast wallpaper, verify normal debug and no duplicate foreground border, toggle glass on/off, check 165Hz performance and dock freeze/unfreeze paths before merging.
