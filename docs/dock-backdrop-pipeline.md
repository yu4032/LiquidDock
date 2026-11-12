# Dock backdrop preparation

Dock continues using the vendor PassBlur producer in Home, applications and Recents.
The captured image is scene content, not a wallpaper-only cache. Producer callbacks,
refresh requests, valid coverage, mirrored overscan and prepared-backdrop invalidation
remain owned by the existing Dock session.

## One producer conversion instead of two

Previously each fresh background followed this chain:

```
PassBlur OES -> normalized RGBA8 intermediate -> Prismal source (Y flip)
            -> existing two-pass blur -> existing glass -> existing output crop
```

Now the normalization draw writes directly into Prismal's owned source target.
`PRISMAL_SOURCE_VERTEX` supplies the same Y flip before the existing normalization
fragment executes. Other consumers retain the conventional normalization vertex.
`PrismalRenderer.prepareBackdropRendered` owns allocation and invokes the producer's
draw callback synchronously on the current GL thread. No producer texture is borrowed
across frames, and context teardown still releases all source/blur/output resources.

This removes an RGBA8 texture/FBO and a full-image copy on every backdrop rebuild.
For the observed 1249 x 555 capture, the removed texture uses 2,772,780 bytes
(2.64 MiB), excluding driver bookkeeping. Capture scale, logical material resolution,
blur kernel, optical material, highlights, opacity and final composition are unchanged.
Scene-only icon animation still reuses the prepared source/blur until its inputs change.

## Compact material output at native pixel density

Dock previously allocated a material output texture for the entire logical sampling domain,
although only the visible Dock crop was copied to the TextureView. `beginGlassFrameCropped`
now reuses the renderer's auxiliary output target for the visible crop plus at most one
padding pixel per axis. The viewport retains the full logical resolution, translated by
the crop's even source origin. Per-node scissor rectangles translate and intersect with
the compact texture bounds. Material shaders and background coordinates are unchanged.

The optional padding preserves the original framebuffer's 2 x 2 derivative grouping for
`dFdx`/`dFdy`/`fwidth`, including the optical edge normal. A direct-to-window prototype
changed this grouping for odd source origins and failed the pixel comparison; it is not
part of the implementation. The final existing copy removes the 0/1 padding pixels at
1:1 density and retains the original partial-coverage scissor and alpha behavior.

The session chooses/clears the compact target; `DockGlassCompositor.drawNodes` appends the
same body and icon nodes. There is no persistent full-sized material texture requiring a
previous/current dirty-region union. The compact target is fully cleared each frame, resized
only when its dimensions change, and released with the renderer's existing targets.

For the observed logical 2498 x 1110 / visible 1578 x 328 geometry with one padding row:

| GPU texture storage | Previous Dock optimization | Compact output |
| --- | ---: | ---: |
| Material output | 11,091,120 bytes | 2,076,648 bytes |
| Sharp + two blur targets (1249 x 555 capture) | 4,155,564 bytes | 4,155,564 bytes |
| Total owned intermediate textures | 15,246,684 bytes | 6,232,212 bytes |

This is an 81.3% reduction in the material target and a 59.1% reduction in Dock-owned
intermediate texture storage versus the previous optimization. Including removal of the
old raw intermediate, the cumulative reduction versus main is 65.4% for this geometry.
These are RGBA8 allocation sizes, excluding driver bookkeeping, EGL window buffers,
PassBlur producer resources and other Launcher allocations.

## Reference implementations

- [BlurView RenderNodeBlurController](https://github.com/Dimezis/BlurView/blob/master/library/src/main/java/eightbitlab/com/blurview/RenderNodeBlurController.java):
  persistent rendering resources and separation of backdrop recording from view transforms.
- [Google GLSLImageProcessor](https://github.com/android/renderscript-samples/blob/main/RenderScriptMigrationSample/app/src/main/java/com/android/example/rsmigration/GLSLImageProcessor.kt):
  GPU image ownership and explicit intermediate/output resources. Its CPU synchronization
  and copying are not adopted into Dock's frame path.
- [Arm's open source GPU optimization guide](https://arm-software.github.io/magog/docs/03_mobile_gpus/03_tile_based_renderers/):
  render-target working sets, tile storage and bandwidth considerations. The compact output
  reduces allocation size and avoids preserving a large partially cleared material target.

The pass fusion is specific to LiquidDock's existing producer/Prismal orientation contract;
no shader or material from those projects replaces Prismal.

## Verification

```
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
adb shell am instrument -w -e case dockBackdrop com.hellovoid.liquiddock.test/com.hellovoid.liquiddock.SettingsSearchInstrumentation
```

`DockBackdropCheck` compares the old two-pass path and the direct-source path in a real
GLES2 pbuffer. The synthetic asymmetric gradient/checker uses the production normalization
fragment with a 2D sampler substituted for the external producer. It checks four rotations,
signed crop compensation and partial coverage/mirrored guards, reading back source, blur
and final glass images at 1535 x 257 physical pixels. On the connected Mali device:

| Image | Maximum channel difference (0-255) | Mean absolute channel difference |
| --- | ---: | ---: |
| Sharp source | 1 | 0.001215 |
| Blur | 1 | 0.000811 |
| Final glass | 2 | 0.000508 |

Removing the intermediate filtered RGBA8 copy changes rounding slightly. Tests bound both
maximum and mean error; they do not claim bit-identical output.

Eight alternating 40-rebuild blocks include GPU completion at each block boundary.
Four independent runs measured original/direct background preparation in milliseconds:
0.301/0.258, 0.400/0.328, 0.386/0.359 and 0.403/0.349 (approximately 7-18% reduction).
These measurements include CPU submission and GPU completion for background preparation,
not whole-Launcher frame time or PassBlur capture latency. No glFinish/readback is added
to production rendering.

The full JVM suite completed with 971 tests, zero failures/errors and one skip.
The installed build was also exercised on Home, over Settings and in Recents; the Dock
sampled application content over Settings and the expected underlying scene in Recents.
Actual external-OES integration is covered by this device smoke check, rather than by
the synthetic pbuffer sampler test.

### Compact material validation

`DockSurfaceCheck` additionally compares the original full material texture + copy against
compact material texture + copy with the same body and ten icon nodes. It covers all four
X/Y origin parity combinations, baseline/decorated optics, three node positions, full/partial/
empty coverage, target resizing and compact/full/compact switching. Reflection verifies
that compact-only rendering never allocates the full logical output texture and that the
auxiliary texture dimensions include the expected parity padding.
The expanded pixel comparison completed with a maximum channel difference of 1/255 and
a mean absolute channel difference of 0.0000249/255 for the compact output stage.

Six alternating 8 x 30-frame runs measured CPU submission plus GPU completion for
material rendering and final copy (prepared background reused):

| Material | Full output (ms/frame) | Compact output (ms/frame) |
| --- | --- | --- |
| Baseline | 6.847, 6.636, 6.501, 6.741, 5.772, 5.503 | 1.857, 1.828, 1.939, 1.883, 1.821, 1.851 |
| Decorated | 7.539, 7.593, 7.572, 7.630, 6.852, 6.728 | 1.959, 2.073, 2.144, 2.094, 2.036, 2.086 |

This is approximately 66-74% lower material-frame elapsed time in the isolated device
fixture. Both paths use identical nodes, optics and a final copy; the baseline retains
its previous/current dirty-region union. Programs are warmed and copy uniform locations
cached before timing. These results are not whole-Launcher CPU utilization, GPU utilization,
presented-frame FPS or end-to-end capture latency.
