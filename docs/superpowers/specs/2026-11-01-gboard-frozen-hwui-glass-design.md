# Gboard frozen-sampling HWUI glass pilot

## Intent

Reduce floating-keyboard glass geometry lag by drawing Prismal through a hardware-accelerated View in Gboard's existing ViewRoot. The keyboard and glass geometry will then be submitted in the same app frame.

## Current behavior

`GboardFloatingGlassSession` prepares the backdrop and glass with GLES, then presents through a full-root `TextureView`. Geometry is observed from Gboard pre-draw, but the resulting glass frame waits for a separate TextureView buffer. The workspace HWUI path shows that same-ViewRoot AGSL drawing can remove this geometry handoff.

Gboard's default real-time sampling mode receives repeated PassBlur frames. `RootPassBlurBackend.copyTextureToHardwareBitmap()` allocates an immutable HardwareBuffer and calls `glFinish()`, so using that one-shot export for every live frame is unsuitable. The existing PassBlur binding excludes the Gboard root surface; this exclusion must remain intact so the module's sink is not captured into its own backdrop.

## Goals

- Pilot HWUI/AGSL only when floating Gboard glass uses frozen background sampling.
- Keep the cached wallpaper raw/blur pair immutable for each capture and update only the live glass geometry in Gboard's ViewRoot.
- Preserve the existing first-frame handoff: do not hide Gboard's stock background until the HWUI glass has drawn.
- Retain the current GLES/TextureView path as the fallback if export or shader setup fails.
- Preserve PassBlur root exclusions, GlassEdgeEffects, appearance settings, geometry transforms, and cleanup on hide/detach.

## Non-goals and failure conditions

- Do not change real-time sampling behavior or add per-frame HardwareBuffer allocations in this pilot.
- Do not migrate the handwriting toolbar capsule yet; it currently constructs the shared session in real-time/cropped mode.
- Do not change PassBlur capture ownership or exclusions.
- Treat the pilot as failed if it captures the Gboard sink, flashes the stock layer during handoff, changes backdrop orientation/cropping, or increases motion jank materially.

## Approaches considered

1. Keep GLES and tune its material work. This is lowest risk, but geometry still passes through the TextureView buffer queue.
2. Add a frozen-mode HWUI/AGSL output with the existing GLES path as fallback. This validates same-frame geometry without exporting a new buffer every live frame. **Selected.**
3. Migrate all Gboard modes to HWUI with a fenced multi-buffer pool. This preserves real-time sampling, but requires explicit producer/consumer ownership and fence handling beyond the one-shot workspace export.

## Selected behavior

When `realtimeBackgroundSampling == false`, export the prepared raw and blurred Prismal textures after the existing frozen-capture events, then draw the glass through a Gboard-root View using the same Prismal-derived AGSL material and current `GboardFloatingGlassGeometry`. New geometry invalidates the View for the next Gboard frame and does not trigger a new background export. When the setting is enabled, keep the existing GLES/TextureView path unchanged.

If HWUI initialization or drawing fails, restore the existing GLES/TextureView output before releasing the stock background. Detach and shutdown must drop Bitmap/RuntimeShader references without recycling HardwareBuffers that may still be in-flight.

## Acceptance checks

- Frozen-mode floating keyboard glass tracks keyboard move/resize on the same Gboard frame without the extra TextureView presentation delay.
- Initial presentation, motion-start refresh, settle refresh, hide/detach, and fallback preserve current behavior.
- The captured wallpaper orientation and crop remain correct, and the Gboard glass never appears in its own PassBlur backdrop.
- Real-time sampling continues to use the existing GLES/TextureView route.
- Build and install on the connected device; visually inspect move/resize and capture/handoff. No unit tests are added for this pilot.
