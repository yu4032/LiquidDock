# GUI fixed canvas and baked Cell material palette — 2026-10-11

## Background

PR #352 replaced expensive Prismal Cell lenses, pressed shader ripples and per-instance optical layers. The user measured a **significant improvement**, requested solid fills, then requested that the **highlight outlines and fill colors be predetermined from the actual GUI background**, rather than independently selected colors or per-Cell blends.

MIUIX uses `ThemeController(ColorSchemeMode.MonetSystem)`, so the previous `MiuixTheme.colorScheme.background` depended on system/wallpaper colors. Literal build-time precomputation requires locking only the **settings GUI canvas** to a specific light/dark pair. This does not alter the rest of the MIUIX color scheme, typography, text foregrounds, or Launcher/Widget material.

## Precomputed assets

The checked-in `src/main/res/values/gui_preset_material_colors.xml` and `values-night/gui_preset_material_colors.xml` contain the complete colors. No color interpolation is needed for the glass-ON Cell chrome.

| Palette component | Light | Dark |
|---|---|---|
| Canvas | `#F5F7FA` | `#1B2028` |
| Glass-OFF canvas | `#E6E8EB` | `#191E26` |
| Cell fill | `#EDF1F6` | `#343B46` |
| Button, +/- and idle thumb fill | `#E5EAF1` | `#454E5B` |
| Cell outer highlight | `#FFFFFF` | `#6E7C90` |
| Cell under-rim | `#C9D2DF` | `#46505E` |
| Button highlight | `#FDFEFF` | `#8694A6` |
| Button under-rim | `#BFCAD8` | `#596474` |
| Destructive fill | `#D73333` | `#D73333` |

The Cell/pill colors are coordinated with their canvas instead of being determined individually by the current Monet theme. The glass-OFF canvas is the precomputed equivalent of its old small darkening. The two outline colors are likewise literal XML color resources.

## Runtime path

- `rememberGuiPresetChromePalette()` reads the resource palette **once at the settings Scaffold**, memoizes it, and exposes it through `LocalGuiPresetChromePalette`.
- `ModernSettingsScaffold` uses `palette.canvas` (or `palette.canvasWithoutGlass`) for the single opaque background.
- `ModernSurface` uses the exact precomputed Card fill and two simple rounded outlines; `Button`, `GuiStaticRepeatStepButton` and idle slider/toggle thumbs reuse Pill/Destructive resource colors.
- `Modifier.guiSolidChrome` draws one flat fill, a 1dp perimeter outline and a 0.5dp fixed-color rim. Both colors come from Android resources; no runtime specular/refraction/light computation, gradient shader, `Canvas` drawable, per-Cell bitmap, or extra material child.
- The disabled GUI glass fallback retains the existing MIUIX-style cell colors/strokes, but runs its material mixing **only when glass is OFF**.
- Gesture-time slider/toggle Prismal, dynamic top header and bottom navigation remain unchanged, as requested.

These are *precomputed color resources*, not pre-rendered 9-patch border bitmaps; Android/Compose still rasterizes the simple round-rect outline for the actual measured cell dimensions. This is intentional, since Cell sizes vary and replacing the efficient solid modifier with a per-Cell Drawable/Canvas pipeline would reintroduce overhead.

## Verification

1. Compare light/dark Cell visibility, fixed highlight outlines and color harmony with the now-matching plain canvas.
2. Confirm no gradients, per-Cell optical shaders or double layered background; keep click and text interaction intact.
3. Check stepper +/- tap, press/repeat/release, reset, restart confirmation, slider/toggle idle/drag transitions.
4. Verify header and bottom glass still capture their original merged background and page contents without hue regressions.
5. Benchmark dense-page scroll with matched pages against the previous solid candidate. A CI pass does not establish real GPU performance.

The entire change remains in PR #352 Draft pending user device acceptance. PR #340 and main remain untouched.


## User correction: spatial directional white reflection, not a uniform bright border

The previous "1dp white rim" implementation was **visually incorrect**: a bright single-color outline has identical intensity everywhere and does not convey a light source. The user explicitly requires *light-caused white specular highlights*, while retaining the performance improvement from all non-interactive Cells using fixed backgrounds.

### Precomputed light maps

Six transparent Android NinePatch assets are checked in:

- `drawable-xxhdpi/gui_specular_{card,pill,thumb}.9.png` for light theme.
- `drawable-night-xxhdpi/gui_specular_{card,pill,thumb}.9.png` for dark theme.

The distributions are baked *offline*, not computed at each Cell draw. A fixed upper-left key light produces a white upper-left hotspot. Its luminance tapers along the top and left edges, and the bottom/right sides keep a weak, darker definition. This is a **spatially asymmetric alpha/light map**, unlike a uniformly colored Compose border. NinePatch stretch regions are located away from corners so large Cards, medium Capsules and small 40×24dp Handles retain their corner geometry as their actual widths/heights change.

### Runtime path

- `GuiPresetChromePalette` retains the fixed opaque Cell, Pill, Thumb, Danger and Canvas fill colors. The earlier `*_rim` and `*_outline` flat color keys were removed.
- In `rememberGuiPresetChromePalette()`, three NinePatchDrawables are selected through Android's drawable night qualifier and loaded **once at the Scaffold**. Each Cell shares the existing drawable objects.
- `Modifier.guiSolidChrome`: one `background(fill, shape)` plus a transparent overlay `drawWithContent` whose NinePatch bitmap is drawn over existing content. No per-Cell gradient calculation, shader, lighting, backdrop blur, sample layer or new composable node.
- `Modifier.guiThumbHighlight` overlays **only** the dedicated thumb's precomputed light map during touch-time Prismal rendering. The live Prismal thumb's existing movement, source alignment and spring are untouched.
- The dynamic top header / bottom navigation still use their original Prismal sources; no broad renderer changes.

### Performance and visual bounds

This deliberately substitutes a tiny, stretchable precomputed bitmap edge for real-time specular shading. It does add a NinePatch draw call to each Cell, so its exact GPU cost must be checked against the prior pure-color version; however, it avoids recomputing refraction or specular illumination and creates no extra composable. The files are xxhdpi with guide-marked 9-patch stretch bars; AAPT2 compiles the guide borders.

Unit tests assert that *actual baked pixel alpha near the upper-left* exceeds bottom-right alpha, that all six theme/shape resources exist, and that no live per-Cell optical pipeline was reintroduced. This verifies nonuniform distribution only; it does not replace on-device visual comparison.

**Important**: Do not merge #352 until the user confirms that the source direction, edge strength, press transition, and scroll performance look correct.
