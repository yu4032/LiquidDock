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

## Follow-up after the user reported little improvement

The earlier change only replaced `GuiFrozenPrismalChrome` for standard Cells and idle slider/toggle thumbs. An actual source audit identified three unmodified per-Cell or dialog Prismal paths:

1. `ModernGlassStepper` always instantiated `PrismalGlassStepper` whenever the GUI glass setting was enabled. This is present in every dense numeric Cell, often multiple times on a single page.
2. The numeric reset action explicitly requested `Button(prismalNumericAction = true)`, routing around the static material path.
3. The restart dialog still instantiated `PrismalGlassButton` for its destructive action. `ModernSurface` also retained a dead/conditionally active body glass branch through `GuiStaticPressPrismalSurface` and `PrismalGlassSurface`.

The second pass closes these paths: `ModernSurface` has **one resource-based chrome implementation** (plus solid fallback for GUI glass OFF), `Button` has **only static/danger resource rendering**, restart actions share that resource, and `ModernGlassStepper` uses `GuiStaticRepeatStepButton`. Its pointer input preserves tap-to-step, disabled range behavior, a pressed overlay, and repeat-on-hold with a 430ms initial delay and 90ms cadence. Standard navigation click feedback uses Compose click indication instead of an AGSL ripple. Deleted the unreachable `GuiStaticPressPrismalSurface` renderer.

The rule is now explicit: **inside GUI Cell chrome, only the switch and slider may instantiate interactive Prismal sampling**. The top header/app-bar actions, bottom tabs and their merged screen capture are separate floating navigation surfaces and intentionally remain live. Those global layers could still dominate GPU bandwidth, so an unmeasured whole-screen speedup is not claimed.

### Additional acceptance checks

- Many 3-control numeric Cell groups: ensure there is no Prismal layer for their +/- or Reset actions; test tapping, rapid tapping, long-press repeat, range boundaries and disabled state.
- Folder/navigation Cell press: Compose click indication remains; no 1.0×→1.05× scale or moving Cell during press.
- Restart dialog: red button remains visible and disabled when no scope is selected; toggles still animate and sample only while touching.
- If GPU time barely improves again, measure the **remaining header/bottom backdrop recapture** independently, not by reintroducing shader effects into Cell chrome.
