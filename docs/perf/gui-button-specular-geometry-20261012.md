# GUI button highlight — prebaked Prismal resource (2026-10-12)

## Why

The main-branch setting button rim used a polar center-angle `Brush.sweepGradient`, which gives visually uneven brightness along long straight segments and continuous corners. The earlier PR #356 revision moved buttons to a clipped, normal-lit native Prismal specular `GraphicsLayer`; while source-faithful, the user requested **all highlight computation before build, shipped as static resources**.

## Source and geometry

Pinned upstream [PrismalAGSL v1.0.4](https://github.com/styropyr0/PrismalAGSL/tree/v1.0.4), MIT copyright Saurav Sajeev:

- `PrismalContinuousCurvatureCornerBuilder.kt` and `PrismalRoundedRectangleOutline.kt` define three Bezier segments per corner, dependent on width/height ratios.
- `rounded_rect_sdf.agsl` and `default_specular.agsl` compute `pow(abs(dot(gradSdRoundRect, (cos(45°),sin(45°)))),1)` on each pixel.
- `PrismalSpecularModifier.kt`: clipped stroke of `2 * ceil(0.5dp*density)` pixels, blur radius 0.25dp, white alpha .5, `BlendMode.Plus`.

The app's action buttons and +/- stepper controls have **36dp fixed height**, radius 18dp. Upstream continuous corner profiles vary with width below about 72dp. Above 72dp, the width-normalized `tW` saturates at 1, so the *corner* curve no longer changes.

## Offline generator and output

`scripts/generate_gui_button_specular.mjs` uses only Node.js built-ins; run once from repository root:

```sh
node scripts/generate_gui_button_specular.mjs
```

It generates `src/main/res/drawable-nodpi/gui_button_specular_atlas.png`:

- 37 source rows: **every integer width from 36 through 72dp**, each 36dp high, computed at 3px/dp.
- Original continuous corner Bezier geometry, source normal field, the clipped uniform-width stroke, plus a small separable 3-tap offline blur approximation.
- The file encodes **transparent white + baked alpha**, rather than themes/colors. The final device draw uses `BlendMode.Plus` over already fixed color fills. The *direction-dependent lighting* is finished offline.
- Dimensions: **216×3996 px**, indexed PNG with alpha palette, ~0.87MB pre-APK resource. One instance decoded in the scaffold (~3.45MB RGBA). No handmade Android `.9.png` format.

On-device `guiBakedPrismalButtonSpecular` only selects an atlas row and draws it. At widths above 72dp, it uses three source fragments: fixed left 34dp corner, 4dp stretchable straight middle, fixed right 34dp corner. This does **not** stretch the rounded corners or recompute lighting. 36–72dp uses nearest 1dp variant (at most 0.5dp width mismatch).

## Preserved and removed

Preserve the original separate `guiNativePrismalDepthShadow` (BlurMaskFilter, cached GraphicsLayer), fixed theme fills, and existing button gestures. Original small switch/slider handles and ordinary Cells are **not** changed.

Delete the previous button-specific `PrismalShader`, AGSL source, `GuiButtonSpecularNode`, per-button blur/GraphicsLayer and uniform updates. The runtime button highlight adds no `GraphicsLayer`, AGSL, Gaussian blur, or background sampling. It still requires normal GPU image sampling and `Plus` blending; it is not a literally zero-work render.

The atlas is optional. If PNG decoding fails, `GuiSolidChrome` logs a warning and draws no extra highlight instead of crashing the settings Activity, as occurred during a previous required shadow-resource experiment.

## Verification

CI verifies PNG dimensions, palette transparency and the decoded 36dp/72dp corner specular, source generator presence, resource fail-open, no runtime button shader, and unchanged existing Prismal shadow. Real-device comparison should check both light/dark buttons, the red danger action, 36dp steppers, seam-free wide pills, idle switch/slider and dense-list frame time. This approximates upstream native alpha/blur, not a pixel-exact captured native image.

**PR #356 is Draft until tested on device.** Do not merge based solely on CI.

## Disabled reset and stepper shadow fix

The on-device screenshot of the **disabled** reset action showed a faint rectangular gray block behind a properly rounded white specular border. This matches the ordering of `.alpha(if (enabled) 1f else 0.66f)` **outside** `guiNativePrismalDepthShadow` in `ModernSettingsUi.Button`: when reset is already at its default value, `SettingsControls` passes `enabled=false`. The offscreen alpha layer then clips its blurred shadow to the button's rectangular measured bounds. The +/- steppers had the same pattern with alpha 0.45f when at their range limits.

Fix: create native Prismal depth shadows **only for enabled actions/steppers**, move alpha after the shadow modifier so it applies solely to material, baked glint and content, and retain the original shape clip and layout. No changes to PNG atlas, cached thumbnail, native enabled shadows, background sampling or stepper gesture handling. Disabled elements are now deliberately shadow-free, consistent with their inactive state.

Architecture tests assert the original default-value disabled condition, conditional shadow, alpha modifier ordering and continued use of the offline baked button highlight.
