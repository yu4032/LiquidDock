# GUI fixed-background shared glass resources — 2026-10-11

## Intent

The settings GUI has a stable, opaque theme background. Each list card and idle control chrome previously instantiated `GuiFrozenPrismalChrome`, which recorded a separate full Prismal blur/lens/specular render list in an individual GPU `GraphicsLayer`. A recorded display list is **not a precomputed image**; it still costs native compositing/offscreen GPU resources. Multiple cards repeated the same glass optics against effectively the same static backdrop.

The user specifically requests shared static background optics, rather than recalculating refraction/lighting for each instance.

## Change

- Four reusable Android XML drawable resources: `gui_static_glass_{card,pill}_{light,dark}.xml`. These precompose fixed neutral fill, directional highlight, outer rim and thin inner edge using drawable primitives.
- `GuiStaticGlassChrome` loads the selected resource for the current UI theme and draws it directly into the Compose canvas without `PrismalBackdrop`, `RenderEffect`, `GraphicsLayer`, AGSL refraction or per-item captures.
- All noninteractive/fixed list `ModernSurface` cards and ordinary neutral `Button` backgrounds use the shared assets. Text, values, clicks, dialog actions and content composition stay separate.
- Static thumbs in `GuiOnTouchPrismalSlider` and `GuiOnTouchPrismalToggle` use the shared pill asset while retaining their existing drag spring transforms. During touch the original live Prismal pipeline resumes unchanged.
- Retain the Prismal merged sources, dynamic header, top actions and bottom tabs; retain real Prismal for numeric +/- and reset (the user explicitly kept those touch affordances).
- Do **not** change the global Prismal renderer, user settings, backend, blur radius, wallpaper, or Launcher glass. The old `GuiFrozenPrismalChrome` has been deleted so no per-item frozen GPU material survives as dead code.

## Visual trade-off and bounds

A shared vector-like XML gradient cannot contain a physically correct refraction of **different** content beneath every individual card: this intentionally approximates the fixed neutral background, with shaped light edge and theme-appropriate depth. It is *not* byte-for-byte identical to a per-cell Prismal glass pass. This is acceptable only if the user confirms the resulting cells look appropriate on-device. Card corners remain 24dp, pills use capped corner radii, and variants are theme-specific. Dynamic top/bottom blur and interactive thumb optics are unmodified.

## Verification matrix

1. Light and dark UI, long/dense pages and grouped cards: verify geometry, border highlight, transparency, contrast, legibility and smooth scroll.
2. Numeric slider dragging/release, toggle press/drag, stepper +/- and reset modal: ensure untouched Prismal tactile/animation behavior.
3. Global GUI glass OFF: existing solid cells remain unchanged.
4. Top bar background capture of text, bottom capsule capture over moving cells and header action buttons remain functional, with no purple hue regression.
5. Measure equal-length `dumpsys gfxinfo` runs before/after on same device; especially GPU p50/p90/p95, `GrVkTextureRenderTarget` cache bytes, frame misses and opening dense list. A final conclusion about GPU improvement requires on-device data; CI alone cannot establish actual perf gains.

Regression gate: no `rememberGraphicsLayer`, `layer.record`, `PrismalGlassSurface` or `RenderEffect` in the static resource path. Do not merge before device checks.
