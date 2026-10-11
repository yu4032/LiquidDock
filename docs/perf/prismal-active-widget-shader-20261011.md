# Prismal active-Widget shader cleanup — 2026-10-11

Base: latest `main` at branch creation. Draft PR #348.

Perfetto on the reported widget-heavy page shows the dominant problem while seven Widget glass nodes are visible, not while the scene is empty. This candidate does **not** alter animation timing, frame cadence, resolution, blur amount, wallpaper sampling or refraction geometry.

The renderer already replaces legacy Prismal edge families with OS4 edge/reflection on every draw through `withOs4EdgeReplacingLegacyEdge()`. Before this patch, legacy specular/rim arithmetic still executed per fragment and only the final color additions were gated off. The patch gates whole optional blocks so those discarded calculations are skipped.

It then moves five configuration-only expressions out of fragment pixels and uploads equivalent uniforms once per immutable `PrismalParams`: primary light vector, 2D light direction, OS4 3D light vector, Fresnel R0 and shadow extent.

Unit tests verify source-transform contracts and numerical equivalence. CI cannot measure Adreno GPU time or guarantee visual parity. Device acceptance remains: identical seven-Widget App→HOME gestures at 165Hz, compare `StaticDraw`, `StaticSwap`, FrameTimeline buffer stuffing and visual OS4 direction/highlight/normal output. Keep draft until measured.
