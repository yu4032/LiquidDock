# Gboard SurfaceControl Drag Glass Design

Date: 2026-09-16
Branch: `perf/gboard-workspace-frame-ownership`
PR: #201

## 1. Problem statement

Device logs show that the current Gboard floating-keyboard glass path computes geometry and Prismal output at roughly the display cadence, but the `TextureView` consumer presents at roughly half that rate during an actual drag.

Observed on the 120 Hz device:

- display refresh rate request: about 120 Hz;
- `GEOMETRY_CAPTURE`: about every 8 ms;
- `RENDER_CROP`: about every 8 ms;
- `TEXTURE_PRESENTED`: about every 16 ms during drag.

Therefore the remaining lag is a presentation-layer problem, not a geometry-authority or Prismal-computation problem.

## 2. Goals

1. Remove the Gboard drag glass output from the `TextureView` / `SurfaceTexture` consumer path.
2. Preserve the already validated drag architecture:
   - Gboard vendor hierarchy remains the only geometry authority;
   - geometry is sampled from `Choreographer` frames;
   - drag start latches a prepared full-root GPU backdrop;
   - Prismal locally recomputes the glass at the current keyboard geometry;
   - no CPU readback, Bitmap, PixelCopy, ImageReader, or `glReadPixels` fallback.
3. Present glass through an application-owned `SurfaceControl` child attached to the Gboard root surface.
4. Keep keyboard keys/text above the glass without introducing a second content copy.
5. Fail closed: if the independent surface cannot be created or attached, retain the current TextureView path or restore stock background rather than showing a blank region.

## 3. Platform API choice

The implementation will use public Android APIs only.

- `View.getRootSurfaceControl()` provides the `AttachedSurfaceControl` for the Gboard ViewRoot.
- `AttachedSurfaceControl.buildReparentTransaction(child)` reparents an app-created `SurfaceControl` into that root surface hierarchy.
- A `Surface` created from the child `SurfaceControl` becomes the EGL output target for Prismal presentation.
- `SurfaceControl.Transaction` owns child position, crop, layer, visibility, alpha, and preferred frame rate.

The project minSdk is 33, while `AttachedSurfaceControl` and `View.getRootSurfaceControl()` are available from API 31, so no compatibility reflection is required.

## 4. Composition model

The independent glass layer is a child of the Gboard root surface and uses a negative relative layer so that the Gboard root buffer remains above it.

Conceptually:

```text
Gboard root surface
├─ parent/root buffer: keys, labels, icons, handle          [above]
└─ LiquidDock glass SurfaceControl child                    [below]
```

The existing stock-background suppression remains responsible for making the keyboard background region transparent. Keys and other foreground content continue to be rendered by Gboard itself in the parent/root buffer, so they remain above the glass child.

The child must never be placed above the Gboard content buffer, because that would cover keys/text and create a second compositing problem.

## 5. New component boundary

Introduce a dedicated presentation abstraction rather than embedding SurfaceControl logic directly in the session.

### `GboardFloatingGlassOutput`

Interface-level responsibilities:

- expose a `Surface` suitable for the existing EGL presentation code;
- receive authoritative glass geometry each frame;
- receive output size changes;
- expose readiness / first-present lifecycle;
- release all surface resources deterministically.

Two implementations:

### `GboardSurfaceControlGlassOutput` — preferred

Owns:

- root `AttachedSurfaceControl` reference;
- child `SurfaceControl`;
- child `Surface`;
- reusable `SurfaceControl.Transaction` state where appropriate;
- last published root-space geometry and size;
- first-visible/commit state.

### `GboardTextureViewGlassOutput` — compatibility fallback

Wraps the current `GboardFloatingGlassView` behavior. It remains available only when the preferred SurfaceControl path cannot be established safely.

This keeps the session and Prismal renderer independent from the presentation mechanism.

## 6. Creation and attachment lifecycle

When a floating Gboard session is created:

1. Resolve the authoritative Gboard root View.
2. On the UI thread, call `root.getRootSurfaceControl()`.
3. If it is null or the root is not attached, do not create the independent path yet.
4. Create a named child `SurfaceControl` configured for a translucent buffer source.
5. Use `AttachedSurfaceControl.buildReparentTransaction(child)` and apply the returned transaction.
6. Keep the child hidden until the first valid Prismal buffer has been swapped successfully.
7. Create a `Surface` from the child and attach it to the existing EGL output path.
8. Set preferred frame rate from the current display refresh rate using `SurfaceControl.Transaction.setFrameRate(...)` or the equivalent surface frame-rate API.

Any exception, invalid SurfaceControl, null root attachment, failed reparent, or failed Surface creation must release partial resources and fall back without suppressing stock visuals prematurely.

## 7. Geometry ownership and per-frame transaction

`GboardFloatingGlassCoordinator` remains the geometry authority.

For each Choreographer frame:

1. Capture the current root-space `GboardFloatingGlassGeometry`.
2. Publish the geometry to the session/renderer as today.
3. Publish the same root-space geometry to the preferred output layer.
4. The output layer issues one SurfaceControl transaction containing only compositor properties:
   - `setPosition(child, glassX, glassY)`;
   - crop / window crop matching current output dimensions;
   - visibility when ready;
   - frame-rate hint where required.
5. Do not perform producer reconciliation or backdrop capture because of geometry movement.

The SurfaceControl transaction is geometry-only. Prismal rendering remains a separate GPU operation using the frozen drag backdrop.

## 8. Output buffer coordinates

Do not render a full-screen final glass output buffer.

The existing `presentCropped(...)` behavior is retained:

- Prismal works in full-root coordinates against the frozen full-root backdrop;
- the current glass region is recomputed for the current keyboard position;
- the final EGL target remains keyboard-local in size, e.g. roughly `1232 x 978`;
- SurfaceControl positions that local buffer at the current root-space `glassX/glassY`.

This avoids a full-screen output swap on every frame and keeps fill/bandwidth bounded to the keyboard area.

## 9. Drag snapshot lifecycle

The existing zero-copy drag snapshot semantics stay intact.

### LIVE

- root PassBlur source updates normally;
- `prepareBackdrop` accepts fresh root frames.

### DRAG_SNAPSHOT

At the first true drag movement:

- require an already prepared full-root backdrop;
- pause live source updates;
- reject subsequent live backdrop preparation;
- retain the prepared full-root GPU backdrop;
- continue geometry sampling and Prismal local recomputation every frame;
- independent SurfaceControl output follows current geometry.

### WAITING_FOR_FRESH_LIVE

On drag release:

- resume source updates and producer reconciliation;
- request a fresh live backdrop;
- continue displaying the last valid snapshot-backed glass until a new live backdrop has actually been prepared and a new output buffer has been successfully swapped;
- only then return authority fully to LIVE.

This explicitly closes the current lifecycle gap where logical drag mode ends before a fresh live frame is known to be ready.

## 10. First-frame and stock-background authority

Stock Gboard background must not be hidden merely because the SurfaceControl exists.

Preferred sequence:

1. child SurfaceControl exists but is hidden;
2. Prismal renders a valid buffer;
3. EGL swap succeeds;
4. transaction positions/crops/shows the child;
5. transaction-committed callback confirms SurfaceFlinger accepted the visibility transaction;
6. only then suppress stock keyboard background.

If any stage fails, keep or restore the stock background.

On fallback to TextureView, preserve the existing first-present contract.

## 11. Release lifecycle

Release must be idempotent and ordered:

1. stop Choreographer callbacks / output geometry publishing;
2. stop accepting render work for the output;
3. detach EGL output Surface;
4. hide and reparent/remove the child SurfaceControl from the root hierarchy;
5. release `Surface`;
6. release `SurfaceControl`;
7. restore stock background if no valid replacement remains.

No transaction or render callback may reference the child after release.

## 12. Rotation, resize, and root replacement

The output layer is bound to a specific Gboard root attachment.

If any of these occur:

- root View changes;
- root `AttachedSurfaceControl` changes or becomes unavailable;
- orientation/display transform changes;
- output dimensions change;
- floating keyboard session is rebuilt;

then the independent output must be recreated instead of carrying a stale SurfaceControl into the new root hierarchy.

No fixed delay is used. Re-establishment happens only from authoritative lifecycle/attachment signals.

## 13. Diagnostics

Keep concise diagnostics while this architecture is validated on device:

```text
SC_OUTPUT_CREATE
SC_OUTPUT_REPARENTED
SC_OUTPUT_SURFACE_READY
SC_GEOMETRY serial=N x=... y=... w=... h=...
SC_SWAP serial=N
SC_TX_APPLIED serial=N
SC_TX_COMMITTED serial=N
SC_FIRST_VISIBLE
SC_OUTPUT_RELEASE reason=...
SC_FALLBACK reason=...
```

The existing geometry and drag diagnostics remain available during validation.

The decisive device metric is no longer `TextureView.onSurfaceTextureUpdated`. Instead compare:

- geometry sample cadence;
- Prismal render/swap cadence;
- SurfaceControl transaction commit cadence;
- SurfaceFlinger jank/present behavior if necessary.

## 14. TDD strategy

Before production changes:

1. Add typed lifecycle/state tests for preferred-output selection and fail-closed fallback.
2. Add tests for `LIVE -> DRAG_SNAPSHOT -> WAITING_FOR_FRESH_LIVE -> LIVE` so a release cannot switch authority before fresh live content is prepared and swapped.
3. Add contract coverage that Gboard preferred output uses public `AttachedSurfaceControl` / `SurfaceControl` APIs and does not use reflection, hidden ViewRoot fields, fixed resource IDs, Bitmap, PixelCopy, or CPU readback.
4. Add geometry-output policy tests verifying movement changes compositor position without triggering producer reconciliation or backdrop preparation.
5. Run a RED CI and verify failures correspond to missing new production types/behavior.
6. Implement minimal production code to green.
7. Run full unit + debug assembly + zero-copy audit.

Device validation remains authoritative for the 120 Hz presentation result.

## 15. Device acceptance criteria

A build is considered successful only if device testing shows all of the following:

1. during drag, glass visually remains aligned with Gboard with no obvious one-frame/two-frame trailing behavior;
2. the content inside the glass changes according to the new keyboard coordinates over the drag-start full-root snapshot, rather than moving an old local glass image;
3. keys/text/handle remain above the glass and are never covered by the independent surface;
4. no blank/black background appears on creation, drag release, resize, or teardown;
5. release transitions back to a fresh live backdrop without flashing to stale content;
6. logs show compositor transaction cadence tracking the display/geometry cadence closely enough that the previous ~60 Hz TextureView bottleneck is absent;
7. zero-copy constraints remain intact.

## 16. Non-goals

This change does not:

- redesign Prismal shaders;
- change Gboard hook discovery or use obfuscated classes/methods;
- add CPU screenshot capture;
- change appearance settings;
- merge PR #201 automatically;
- claim smoothness based on CI alone.
