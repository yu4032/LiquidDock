## 2026-10-19 Dock external Prismal shadow output viewport

Root cause: the original Dock renderer correctly drew optional exterior
shadow into a larger normalized sample/Prismal FBO but then
`renderCompositePass` cropped to the **native Dock body UV** and wrote
only to the native-size TextureView. Those exterior pixels could therefore
never appear onscreen even though the SDF/scissor guard was correct.

When Dock scope has a nonzero configured GPU cast shadow, extend the existing
**single** TextureView via negative FrameLayout margins (at most 96px per
side, based on `PrismalEdgeDecoration.extraGuardPx`). Preserve the original
native Dock/material host dimensions, corner radius, foreground stroke and
PassBlur root binding. Temporarily disable ViewGroup clipping at the material
and immediate parent boundary while the shadow viewport is expanded, restoring
the saved values when the glass host is cleared or the shadow is disabled.

`Miuix307PassBlurTextureView.updateBackdropMapping` now calculates:
- Native **Dock body** UV from the actual Dock view's screen rectangle relative
  to the enlarged TextureView, including sample insets.
- Full **output surface** UV from the enlarged TextureView's own rectangle,
  also including the original sample insets.

The shader geometry and Dock icon projection use native coordinates inside
the unchanged logical sample frame, while the last composite samples the
full output UV, keeping alpha in the true exterior shadow band. A zero-margin
configuration produces the original pixel-exact crop. This does NOT expand
the SF PassBlur producer, allocate another Surface/GL pass, or reconstruct
the backdrop.

The extra output pixels do increase the normalized sample size and fragment
work only for a Dock with cast shadow enabled. Margin changes on live settings
and size changes use the current geometry observer without a new producer
binding. **Hardware QA remains required** for HotSeats ancestor clipping,
drag/resize orientation and GPU frame time; only the new policy's geometry
math has been unit tested.

The `GlassEdgeSettingsPage` now exposes GPU shadow settings for Dock and
all other glass scopes (old native-shadow exemptions removed), and separates
LazyColumn cards by 8dp to match other LiquidDock settings pages.
