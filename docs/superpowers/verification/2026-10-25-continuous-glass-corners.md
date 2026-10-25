# Continuous glass corners verification

Base: GitHub origin/main db25c746. Local branch: codex/ipados-glass-corners.

## Automated checks

- `./gradlew testDebugUnitTest assembleDebug --console=plain`: BUILD SUCCESSFUL.
- Application: 794 tests, zero failures/errors/skips.
- Prismal: 57 tests, zero failures/errors/skips, including generated-program GLSL validation.
- `scripts/verify_continuous_corners.sh`: all three live fragment programs compile/link
  on NVIDIA RTX 5070 Ti Laptop GPU, OpenGL ES 3.2.
- 664 CPU contour vertices agree with the GPU field within the 0.04px tolerance
  (distance readback quantized to 1/64px). Covers rectangles, circles, long Docks,
  cards and nearly round shapes.
- 64 quadrant samples confirm independent radii use the correct corner.
- Asymmetric body-depth regression: before the fix, TR=40/BR=0 at the horizontal
  center axis produced encoded depths 94.76562 versus 67.78125. After the fix both
  sides produce 67.78125. The GPU harness retains this regression check.
- `git diff --check`: clean.

APK: `build/outputs/apk/debug/LiquidDock-debug.apk` (fixed project debug signing key).

## Scope and remaining validation

The common optical adapter reaches all live Prismal programs, including the
baseline fallback, decorated glass and experimental reconstructed optics. Component
sizes/radii are unchanged. Glass Dock shadow/foreground paths use the same contour;
native blur retains its legacy path. Legacy smoothing now adjusts height-normal
sample spacing instead of changing the continuous silhouette. Existing border-style
controls remain, with clarified text.

This is a community Apple-like approximation. The Debug APK was installed on the selected turner device via ADB and the user
accepted its appearance. No device performance measurements were taken. Verify icon/folder/widget edges, search and
third-party surfaces, Dock borders, pressed states and resize animations on device.

## Follow-up switch verification

- Global `liquid_continuous_corners` switch on the Liquid Glass page, default off.
- 851 unit tests pass; Debug APK builds successfully.
- GLES test retains continuous contour/asymmetric-depth checks, and 3,485 samples
  of switch-off distance agree with unmodified upstream GLSL (including smoothing).
- On/off values reach portable renderer params and survive builder copies.
