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

## Experiment B — turn off LIST CELL edge-lens (after grouped pilot)

- Only `SettingsCard` and `ModernFeatureCard` set `ModernSurface(edgeRefraction = false)`. Dialogs and unrelated `ModernSurface` callers keep the original lens default.
- Both the static/ripple and interactive render paths set their lens height **and** amount to zero for those cells, so PrismalAGSL `applyPrismalGlassEffects` does not add `prismalLens`; chromatic aberration is also set to zero on that branch.
- The cell still uses the native Prismal blur, vibrancy, tint, contour/specular, round shape and click ripple. Glass sliders, toggles, steppers, buttons, header, and bottom navigation are **unchanged**.
- Compare experiment A (grouped only, CI #7664) to experiment B (grouped + lensless list cells, next CI), on the same mixed/dense page and identical gesture, recording GPU p50/p90/p95 and cached render targets, and inspecting the visual rim. The savings may be limited because the 12dp blur and source compositing remain.

## Experiment C — top/bottom live Prismal, all body chrome static with touch-gated controls

- Retain the existing `backgroundLayer` and `screenLayer` recorders because the Prismal header/capsule must continue sampling page content. Body `LocalPrismalSurfaceBackdrop` is deliberately null; `ModernSurface`, ordinary actions, list cell panels, steppers and dialogs use cheap gradients, theme-aware rims and native press indications. No live blur/refraction from idle content controls.
- Provide `LocalTouchPrismalBackdrop` sourced from the **existing** backgroundLayer to keep optics consistent without another recorder. Local PrismalAGSL v1.0.4 adaptations `GuiOnTouchPrismalSlider` and `GuiOnTouchPrismalToggle` preserve the existing spring-gesture modifier/track and thumb geometry across recompositions. The thumb's GPU `drawPrismalGlass` and `prismalGlassLayer(trackBackdrop)` activate only while dragged/pressed and detach when released; idle thumb stays visually opaque without shader.
- Slider track taps trigger a separate onPress sampling gate, and slider drag events gate sampling directly; toggle press/drag sets the gate. No whole-control swaps during active gestures; enabled state and app preference callbacks remain in the existing stable callback wrappers. Both controls still have animated progress and thumb movement when idle (no glass pass).
- Other controls keep a lightweight touch highlight via Compose clickable/MIUIX, not animated per-control GPU glass. Top-bar Back and action buttons retain their native Prismal effects; bottom capsule stays unchanged.
- Device acceptance: verify slider track tap + thumb drag, toggle tap+drag and nested scroll, long-press, quick repeated input, numeric entry, disabled state, dark/light rim, restart scope selection, header/footer blur over all list content. Compare GPU p50/p90/p95 and Vulkan scratch textures against the grouped pilot. Do not merge until tested.

## Experiment D — retained Prismal, one-shot material cache (supersedes B/C)

User rejected removing effects or substituting a plain white/gradient UI. The accepted strategy is to compute the **original Prismal effect once per stable geometry/theme**, then reuse the GPU `GraphicsLayer` while the cell scrolls. This supersedes experiment B's removed lens and C's flat static fill. The old experiment records are historical only.

- `GuiFrozenPrismalChrome` renders the actual `PrismalGlassSurface` with its original blur, lens, chromatic dispersion, specular, tint and round geometry into a dedicated layer using `layer.record`; subsequent draws use `drawLayer` while geometry/style matches. Changes of layout size or explicit theme/color/style parameters invalidate that record. The cache draws **only the optical backing**, with text, values, controls and clickable press indication outside, so they continue to update.
- `ModernSurface` settings cards/dialogs and ordinary GUI action backgrounds reuse this frozen material. No live cell-specific shader calculation during a normal scroll; header and bottom tabs keep their native live merged source. `SettingsCard` / `ModernFeatureCard` restore the Prismal lens rather than zeroing parameters.
- Slider/toggle idle thumbs are likewise frozen Prismal materials (not flat white). Their underlying spring gestures remain attached across render-mode changes; during touch, the unmodified PrismalAGSL v1.0.4-derived live track/glass pipeline activates, then returns to a frozen layer on release. The existing two full-screen source layers serve the header/footer; no bitmap, screenshot or third recorder is introduced.
- A GPU display-list record is not the same as a measured texture lifetime. Runtime/per-device verification is still needed to establish how often RenderEffect is actually evaluated inside HWUI and whether the GPU surface cache pressure improves. Do not claim one-time GPU kernel dispatch or a measured speedup from static source review alone.

## Workstation Dock GUI truth-in-settings cleanup

- The user reports `workstation_dock_icon_bottom_offset` and `workstation_dock_width_offset` have no visible effect. Source shows a `Rect.bottom` item decoration modification and a separate DockContainer LayoutParams width attempt; neither guarantees an actual visual change in the OEM layout. Remove their sliders from the GUI while retaining the existing schema, JSON import/export, and runtime read paths for compatibility.
- Rename the working `workstation_dock_icon_top_offset` slider to **工作台 Dock 图标垂直偏移** and clarify the summary. It still writes the original top-offset key, so existing settings are unchanged.
- Retest after any future Launcher/vendor update before re-exposing width/bottom controls; do not silently claim those runtime hooks are absent.

CI validation guards the frozen chrome and trimmed GUI. On-device gates remain: cached cell lens/highlight appearance during scroll, after color/theme changes, after rotation, idle and dragged thumbs, top/bottom blur, Dock scope dialog selections, and same-trial GPU p50/p90/p95 and Vulkan texture cache comparison.
