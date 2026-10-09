# GUI Prismal layered-compositing performance pilot (2026-10-09)

## Baseline / device evidence
The user reports significantly faster scrolling with GUI glass off. In the uploaded glass-on `dumpsys gfxinfo` snapshot, the app reports 1,452 frames, 50th/90th overall frame-duration percentiles of 18/36 ms, GPU 50th/90th/95th percentiles of 8/13/17 ms, Skia (Vulkan) GPU cache ~764.42 MiB, including ~645.55 MiB of `GrVkTextureRenderTarget` scratch resources (582 entries). These are **cache allocations**, not confirmed peak process GPU memory nor a controlled before/after comparison. Modern Android jank and legacy-jank counts disagree and must not be summarized as a stable jank rate.

## Source mechanism
- `ModernSettingsScaffold` has independent full-screen background and content Prismal capture layers; header and bottom tabs sample their merged output. Keep these sources to avoid reintroducing previously fixed background-capture regressions.
- Each `ModernSurface` uses a 12dp Prismal blur and lens with 0.28 chromatic aberration, creating an offscreen render layer. Upstream v1.0.4 blur uses `BlurEffect` and aberration selects the dispersion shader with up to seven samples at lens edges.
- Dense parameter pages previously used one individually rendered `SettingsCard` per integer setting. The '图标与控件' mixed page put all three toggles, two sliders/steppers, and an entry into one unusually tall glass surface inside one LazyColumn item.
- Prismal sliders and steppers keep their native effect chain; no changes to the upstream library or to user-selected UI effects.

## Conservative pilot in this branch
1. `groupedIntSettings` makes small, keyed batches of three integer controls per glass card, with simple non-glass separators. Applied only to LiquidSpec, DockGeometry and portrait/landscape grid pages. All original preference keys, callback and enable semantics preserved.
2. The mixed `GlassIconsPage` becomes a single lazy scrolling list with three bounded Prismal groups: toggle controls, two numeric controls, and the highlight entry. Parent glass backgrounds move with the content; each group is independently disposed offscreen.
3. Do **not** alter the 12dp blur, lens, chromatic aberration, capture hierarchy, global tabs, or FPS. No claim of speedup before device measurement.

## Evaluation and revert gates
- Install pilot CI APK and compare it to the merged main version on the **same device**, refresh rate, screen orientation and content. Reset gfxinfo immediately before each equal-duration trial: glass-on page scroll on '图标与控件', dense Liquid Material parameter scroll, numeric-slider drag, then glass-off for context.
- Compare GPU p50/p90/p95, CPU/total frame p50/p90, `GrVkTextureRenderTarget` scratch count/bytes, and visible jank. Stronger evidence requires Perfetto GPU FrameTimeline.
- Check toggles, dependent slider availability, numeric entry, reset/stepper, page-entry arrow, long-list border scrolling, bottom-tab gestures, header blur and dark mode. If a group causes local overdraw or a large visual regression, revert that group only.
- Keep this isolated branch/PR unmerged pending on-device A/B confirmation.
