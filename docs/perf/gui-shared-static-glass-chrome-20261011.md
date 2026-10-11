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
