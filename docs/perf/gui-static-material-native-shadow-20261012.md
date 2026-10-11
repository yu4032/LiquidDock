# GUI static chrome and native shadow (2026-10-12)

## Accepted implementation

The settings body uses fixed light/dark palette colors instead of Prismal backdrop capture and per-Cell refraction. `GuiSolidChrome.kt` draws the real continuous Prismal Shape as a solid background with **one** cached white directional specular rim, rather than stacking multiple bitmap and runtime outlines. This keeps the layout and rounded corners consistent at any Cell size.

Idle slider and toggle thumbs use the retained white XML shape, a single cached edge highlight, and the independent compact native shadow. The existing live-on-touch Prismal sampling, Ambient specular, depth inset, spring animation and their original active depth shadow remain unchanged. The thumb drawable is optional: loading failure degrades safely to a white `GradientDrawable`, never aborting settings composition.

## Native Prismal depth shadow

`GuiPrismalNativeDepthShadow.kt` is an isolated adaptation of PrismalAGSL v1.0.4's internal `PrismalDepthShadowModifier.kt`. It records a **filled shape** blurred using `BlurMaskFilter(NORMAL)`, clears the interior with `BlendMode.Clear`, and draws the cached offscreen `GraphicsLayer` behind the clipped material.

- Standard Card/action/stepper: Prismal `DepthShadow.Default`, 24dp radius, Y offset 4dp, black at 10% alpha.
- Idle slider/toggle thumb: 4dp radius, 5% black, following their existing active shadow settings.
- The cached outline depends on shape, size, direction and density. Resizing or material changes may re-record the blurred shadow; normal scrolling with stable sizes should reuse it.
- No standalone `drawPrismalGlass` and no new per-Cell background sampling, lens shader, PixelCopy, screenshot, or shadow bitmap resources.

The shadow does allocate one offscreen `GraphicsLayer` per live node and may increase GPU memory compared with the previous simulated outline. That cost must be assessed independently from the documented static-color performance improvement.

## Cleanup

Removed the unused baked Card/Pill PNGs, generated color/lighting scripts, manual nine-region bitmap stretching, destructive-button bitmap/XML, old frozen glass components, and the superseded three-stroke fake shadow. Kept only directly used GUI code, the white idle-thumb XML shape, theme palette, focused tests and required licensing.

## Provenance and validation

User accepted the final look and startup behavior. CI must pass on the clean branch before merging. Device performance may still vary for pages with many visible shadow layers.

PrismalAGSL v1.0.4 by Saurav Sajeev, MIT license: `docs/licenses/PrismalAGSL-MIT.txt` and `THIRD_PARTY_NOTICES.md`.
