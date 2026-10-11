# GUI settings Cell pure-color rendering — 2026-10-11

## Motivation

The user confirmed a **significant improvement** from replacing GUI Cell Prismal refraction with static material, then requested an even simpler *single solid color*. The previous PR #352 candidate was not yet merged and still used five layer-list XML drawables with multiple gradients/strokes plus a separate `GuiStaticGlassChrome` child and native canvas call for each Cell. Those embellishments are unnecessary with the GUI's fixed background.

## Final implementation in PR #352

- `GuiSolidChrome.kt`: one opaque `Color` for each theme and kind, applied directly through `Modifier.background(color, shape)`. Palette:
  - Cards: light `#F0F2F5`, dark `#343944`.
  - Ordinary buttons, stepper +/- and idle thumbs: light `#E5E9EE`, dark `#4C5260`.
  - Destructive actions: `#D73333` in both themes.
- `ModernSurface` now draws the plain fill on its **own Column modifier** rather than creating a Box and a background child. Cells retain the 24dp corner shape, independent text/value updates and Compose click feedback.
- `Button` uses a plain fill directly on its Box modifier, without a nested material child. Existing disabled alpha and action callbacks remain. Reset and dialog confirmation use the same treatment.
- `GuiStaticRepeatStepButton` applies a fill on its original outer Box. Its 430ms long-press threshold, 90ms repeat and pressed feedback remain intact.
- `GuiOnTouchPrismalSlider` and `GuiOnTouchPrismalToggle` idle thumbs draw a plain-color rounded surface while preserving the original spring transform. During active touch the existing Prismal pipeline runs unchanged.
- Deleted `GuiStaticGlassChrome.kt` and the **five** `gui_static_glass_*.xml` gradient resources. No XML parsing/inflation, native Canvas draw, specular/gradient pass, or per-Cell backdrop capture is needed.

## Intentionally unchanged

The always-live top header/app-bar actions and bottom navigation still use Prismal's merged `backgroundLayer` + `screenLayer` captured backdrop. They can still require extra GPU composition when content scrolls. This patch only simplifies static Cell chrome; it does not make unsupported claims about GPU time beyond the improvement reported on device for the previous candidate.

The GUI glass OFF mode retains its existing theme-aware solid fallback/borders for contrast. No Launcher/Dock/Widget renderer, preferences, settings logic, or source-capture geometry changed.

## Acceptance

1. Inspect light/dark cards, action pills and destructive buttons: **flat solid fill, no highlight/gradient/refraction**. Corners and contrast must remain reasonable.
2. Verify dense numeric pages, +/- single tap and long press, reset and reboot dialog enabled/disabled states.
3. Verify slider/toggle drag/release springs and live sampling; compare the idle thumb against live on-touch appearance.
4. Verify top header and bottom navigation blur still samples scrolling text correctly.
5. Recheck actual scroll GPU frame times and RenderTarget cache pressure on the same device. CI confirms build/contracts, not visual fidelity or measured FPS.

Remain Draft until user has checked this pure-color candidate on device.
