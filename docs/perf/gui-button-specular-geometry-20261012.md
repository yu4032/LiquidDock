# GUI button edge: Prismal source geometry (2026-10-12)

## Problem

The accepted GUI uses a light-weight `GuiSolidChrome.guiStaticSpecularRim` with a `Brush.sweepGradient` around the element center. For elongated action buttons, polar angle is unrelated to the **local boundary normal**. The gradient crosses straight edges and curved corners at inconsistent rates, making sections of the single stroke appear heavier or thinner. Increasing stroke width or adding a second contrasting outline does not solve the error and previously caused stacked outlines.

## Source audit

Pinned upstream: [PrismalAGSL v1.0.4](https://github.com/styropyr0/PrismalAGSL/tree/v1.0.4).

- `PrismalSpecularModifier.kt`: draw a **single Stroke** of width `2 * ceil(0.5.dp.toPx())`, clip it to the exact Shape outline, apply a 0.25dp blur, and draw the cached layer with `BlendMode.Plus`.
- `default_specular.agsl` + `rounded_rect_sdf.agsl`: compute intensity using `pow(abs(dot(gradSdRoundRect, (cos(45°),sin(45°)))),1)`; **direction modulates light, never geometric stroke width**.
- `PrismalRoundedRectangle` defaults to **Continuous** corner interpolation rather than Compose's `RoundedCornerShape`.

## Change

Added `GuiPrismalButtonSpecular.kt` as an isolated adaptation of upstream's internal specular node and Default shader, scoped exclusively to regular/destructive action buttons and numeric +/- buttons. The static fill, external native `DepthShadow`, text, tap and repeat-on-hold logic remain untouched. Switch numeric steppers to `PrismalRoundedRectangle(18.dp)`.

A native `GraphicsLayer` is created and released with its modifier node. The true clipped outline, normal-based shader and blur are recorded when Shape, size, layout direction or density changes; ordinary scrolling only composites the cached layer. If AGSL compilation fails on a device, the renderer gracefully draws a plain white native-width rim. No full `drawPrismalGlass`, screenshot, backdrop sampling, per-Cell lens shader or new drawable resource.

The ordinary setting Card and idle toggle/slider thumb **still use the accepted inexpensive cached sweep rim**. If they later need similar correction, review their visual defects independently rather than expanding GPU work to all Cells without measurements.

## Acceptance

Run CI, verify consistent apparent border width and corner continuity on wide buttons, red destructive actions and 36dp +/- buttons in both themes, ensure no double rim and confirm touch repeat behavior. Perfetto comparison should track both GPU render target memory and dense-page frame times before merging. Native specular creates an additional offscreen GraphicsLayer **per visible button**, so performance is not guaranteed identical.

PrismalAGSL v1.0.4 MIT copyright/license already retained under `THIRD_PARTY_NOTICES.md` and `docs/licenses/PrismalAGSL-MIT.txt`.
