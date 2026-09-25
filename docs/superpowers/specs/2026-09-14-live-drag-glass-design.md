> [!NOTE]
> **Historical record / 历史记录。** 本文件记录其日期对应的计划、设计或验证快照，不维护为当前实现契约。当前行为请以 [README](../../../README.md)、[FEATURES](../../../FEATURES.md)、[ARCHITECTURE](../../../ARCHITECTURE.md)、[HOOKS](../../../HOOKS.md) 和 [TODO](../../../TODO.md) 为准。

# Live Drag Glass Design

## Goal

Replace the drag-time frozen workspace snapshot with a continuously sampled workspace glass path, matching the successful Security Center/sidebar layering model.

## Constraints

- Keep HyperOS Launcher 4.50 as the authoritative vendor behavior source.
- Do not use Bitmap, PixelCopy, ScreenCapture, CPU readback, or texture re-upload.
- Do not use fixed delays to coordinate drag capture or teardown.
- Fail closed: if the live glass path cannot be established, restore the vendor DragView visual and do not leave the user with an invisible drag object.
- Preserve the existing small-folder preview scaling fix in PR #175.

## Architecture

Launcher remains the lower continuously changing scene. A dedicated non-touchable, non-focusable `TYPE_APPLICATION_PANEL` drag overlay window is attached with the Launcher token and becomes the PassBlur authority. The drag overlay window is excluded from its own PassBlur source, so the producer continuously sees the Launcher workspace beneath it.

The original MIUI DragView remains in the Launcher hierarchy for drag/drop state, hit testing, and vendor lifecycle, but its visual alpha is suppressed while the live overlay is active. The overlay hosts both:

1. the LiquidDock glass output; and
2. a visual mirror of the dragged object.

Because both the mirror and glass live in the excluded upper window, they never contaminate the PassBlur input. The PassBlur producer remains live for the entire drag; there is no frozen backdrop or clean-capture snapshot.

## Frame ownership

Every Choreographer drag frame reads the authoritative DragView transform and publishes the same geometry to the upper-window glass node and the visual mirror. The live PassBlur backend continues ingesting source frames according to configured render quality/FPS, while drag geometry updates are not gated by upper-window pre-draw.

## Presentation lifecycle

- Before live overlay is ready, the original vendor DragView remains visible.
- Once the overlay has a usable output surface and mirror, hide only the original DragView visual; keep the vendor object alive for logic.
- If producer/output/mirror initialization fails, restore the original DragView immediately.
- On drag end, restore vendor visual state before releasing overlay/session resources outside vendor teardown traversal.

## Out of scope

- Replacing MIUI drag/drop logic.
- Reparenting the original DragView object across ViewRoots.
- View-level SurfaceFlinger exclusions; exclusion remains window/SurfaceControl level.
- Changing material appearance parameters unrelated to source layering.
