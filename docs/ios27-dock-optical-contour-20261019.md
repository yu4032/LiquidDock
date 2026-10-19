# iOS 27 shared Liquid Glass optical-contour experiment (2026-10-19)

Branch: `feature/ios27-dock-optical-contour-20261019` based on `main@9de86423`; prior Dock-only implementation in PR #429 is now generalized.

## Scope

All surfaces rendered using `PrismalRenderer` share this optical model: Dock body and icons; workspace widgets, small/large folders and drag overlays; expanded folder; recents capsule; shortcut popup; search box; floating Gboard; Security Center and SystemUI menu surfaces. They already use the exact same GLSL program, so the extension is applied once at program creation, not copied across rendering sessions.

The shared style uses the existing `distMask`, `os4SdfGradient`, cached 2D light uniform, and single monotonic edge lens. It adds size-aware meniscus asymmetry, a primary/opposite rim sheen, corner highlights, inner lateral absorption, and a thin **outside-only** dark line modulated by the opposite light direction. Per-node tint, blur, refraction controls, Fresnel/OS4 settings, shape, crop and geometry remain owned by each existing client.

## Ownership and performance

The shader has no new backdrop, framebuffer, PixelCopy, ScreenCapture or Bitmap. Most interior fragments bypass the additional optical shading. The former independently drawn Dock `StrokeDrawable` stays suppressed for `DockLiquidGlassHostView`; this must not suppress the native fallback when glass is disabled. Other native and vendor borders are unchanged. External shadow authority remains with existing native owners such as MiShadow. No additional shadow layers are created. There is no Dock-only uniform and no new per-draw state.

The Compose settings UI uses the separate upstream PrismalAGSL graphics pipeline for a small number of interactive controls, and cached static corner assets elsewhere; modifying this OpenGL shader must not be represented as changing those distinct materials. Do not replace Compose static material resources or add new recording layers for parity without a separate performance audit.

## Verification before merge

1. Test tiny icons/capsules and wide folders for smooth corners and no oversized dark outline.
2. Check Dock shadow/outline ownership across dock edit, workstation mode and runtime glass toggles.
3. Verify opening/closing folders, large widget rendering, drag paths, recents, popup/search and external surfaces.
4. Measure frame time / shader load on a glass-heavy page at 165Hz, and ensure no black-border clipping at the raster guard.
5. Confirm the user-selected light direction and highlight strength remain perceptually consistent.

CI passing is necessary but not a substitute for on-device comparison with the reference screenshot.
