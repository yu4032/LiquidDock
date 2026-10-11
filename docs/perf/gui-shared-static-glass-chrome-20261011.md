# GUI body material: baked final Prismal composite — 2026-10-11

Status: **PR #352 DRAFT**. Not merged. The user confirmed a significant improvement from eliminating all live Prismal Cell optics, but rejected two successive attempts to emulate the original specular appearance. This replaces the incorrect raw-specular overlay with an **offline-baked final RGB material**.

## Source evidence (PrismalAGSL v1.0.4, pinned)

Source: https://github.com/styropyr0/PrismalAGSL/tree/v1.0.4

- `default_specular.agsl`: `intensity = pow(abs(dot(prismalGradSdRoundRect(...), vec2(cos(angle),sin(angle)))), falloff)`; default angle 45°, falloff 1.
- `PrismalSpecularStyle.Default`: **white alpha 0.5 + `BlendMode.Plus`**. This is additive compositing, not a transparent white `SrcOver` bitmap.
- `PrismalSpecularModifier`: shape clipping; a stroke width of `2 * ceil(width.toPx())` with `PrismalSpecular.width = 0.5dp` and blur radius `width/2`. Applies the light Shader to the *stroke*, not to the entire Cell.
- `PrismalRoundedRectangle(24.dp)`: original GUI Cell uses the upstream **Continuous** corner style, not Compose's simpler `RoundedCornerShape`.
- The shader obtains its **normal field** with `gradRadius = min(cornerRadius * 1.5, halfWidth, halfHeight)`. This differs from the outline radius used for shape clipping; blindly using 24dp for both gives different lighting.
- `PrismalGlassSurface` composes backdrop effects/tint/solid surface in addition to its independent specular and depth-shadow nodes. Lens/refraction of a **uniform fixed backdrop** contributes little in the Cell interior, whereas the shape clip, plus-blended white edge, and fill color remain visible.
- `PrismalGlassSlider` / `PrismalGlassToggle` use `PrismalSpecular.Ambient.copy(alpha = pressProgress)`. Their surface explicitly paints `Color.White.copy(alpha = 1f - progress)` when released. **The idle thumb is white and has no active Ambient specular**. Baking a dark-left/white-right Ambient highlight into it at rest was incorrect.

## Root causes of our prior visual mismatch

1. The first arbitrary image faded only from the upper-left; this was not the upstream normal model.
2. The next implementation baked `Default` source alpha and overlaid it with regular **SrcOver**, even though the original uses **Plus**. Over near-white material, Plus frequently saturates the thin stroke to pure white; SrcOver only gently interpolates towards white. Baking the raw shader separately loses the bright, continuous edge in the provided reference.
3. The alpha overlay was drawn with `drawWithContent { drawContent(); draw(overlay) }` — above text and icons. The real static backing belongs **behind content**.
4. We had changed the original `PrismalRoundedRectangle(24.dp)` clip to `RoundedCornerShape(24.dp)`, which changes how continuous corners look.
5. The screenshot's pale-lavender Cell fill differs from the earlier guessed gray-blue palette. Sampling representative non-text regions yields approximately **canvas #F0F1F6; Cell #F4F4FC**; the display and JPEG compression introduce some noise, so these are fixed representative values rather than claims of exact original XML hex codes.

## Replacement design

The main UI now uses baked **composite PNG images**, not a solid fill plus a semi-transparent specular overlay:

- A **single opaque color fill** is the material's interior.
- The offline baker computes the source 45° normal dot, approximates the clipped specular stroke/blur near the shape edge, applies the *actual additive Plus equation* `C_out = min(255, C_fill + 255 * 0.5 * intensity * edgeWeight)` to the final fixed-color RGB values, then stores that RGB directly in the resource. On light Cells, this creates a narrow saturated white perimeter with faint directional differences; on dark Cells, more of the two-lobed shape remains visible.
- Baked corner pixels approximate the original **Continuous** outline with a superellipse. The `ModernSurface` and action buttons once again clip with `PrismalRoundedRectangle`, so the drawn outline aligns with the real upstream geometry. The clip approximation remains a declared fidelity limit, not a pixel-exact AGSL recreation.
- Four `drawable[-night]-nodpi/gui_material_{card,pill}.png` ordinary **8-bit RGBA PNGs** encode the final composite. Hand-generated `.9.png` files repeatedly failed native AAPT2 resource compilation; an audit later also found **binary size mismatches in earlier uploaded blobs**, so those AAPT2 failures cannot safely be attributed to the format alone. The current committed ordinary PNG blobs were independently verified to match the expected uncompressed byte lengths. `drawable/gui_material_thumb.xml` is the original flat-white idle handle; `drawable/gui_material_danger.xml` is the static red action. Source slice guides remain in the PNGs, and the renderer crops them at runtime.
- `GuiSolidChrome` resolves four drawable instances (two PNG, two XML) **once at the Scaffold**. `Modifier.guiSolidChrome` draws PNGs behind Cell content using a **deterministic nine-region stretch**: it keeps source corners at their 24dp/18dp physical radius and stretches only straight sections. This uses up to nine ordinary Canvas draw operations per Card/Pill, not a single native NinePatch draw. No runtime optical shader, refraction, backdrop capture, offscreen GraphicsLayer or per-Cell color math is reintroduced. Thumb/Danger XML are single draws.
- **Idle thumb:** uses a white precomposited resource in both themes. **Active thumb:** continues using original Prismal Ambient, pressProgress, white-fill crossfade, and gesture/spring animations. No second baked reflection is overlaid on touch.

A fully baked resource is the appropriate approximation **because the GUI canvas and Cell fill are intentionally fixed**. This would not be valid for arbitrary dynamic wallpapers.

## Reproducibility

`scripts/bake_gui_materials.py` regenerates the four Card/Pill ordinary RGBA PNGs using only Python 3's standard library, including source slicing guides and truecolor PNG encoding. The idle-thumb and destructive-action XML shapes are independently maintained. It documents the source math, the approximate stroke falloff, the fixed background colors and the clipping approximation. Keep the script and assets in sync when changing colors or shapes.

## Device acceptance

- On the light GUI, compare the original screenshot: near-white/lavender cell, **continuous thin white rim**, small directional modulation rather than isolated white bulbs, consistent rounding.
- Compare long navigation Cells and shorter status/action cards. Ensure the nine manually stretched source regions leave no seams or duplicated corners.
- Verify the idle white thumb XML, drag-time original Prismal Ambient, no double specular, and smooth release spring.
- Dense-list scroll and graphics memory usage **must be measured again**: nine cropped PNG draws per Cell are not free, even though the per-Cell Prismal shaders and render targets have been removed. CI cannot establish a frame-time improvement.
- Verify GUI glass OFF solid fallback, light/dark contrasts, top/header/footer sampling. **Do not merge on CI success alone.**

Unrelated PR #340 remains independent; `main` remains untouched.
