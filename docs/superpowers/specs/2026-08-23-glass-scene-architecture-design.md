# Glass Scene Architecture Design

Date: 2026-08-23
Base branch: `feat/folder-press-glow`
Base commit: `fbc3d6a96ef46932b4e418af128a1bfa0ebddadc`
Status: approved architecture, implementation not started

This document is the authoritative design for the next Launcher glass refactor. Where it conflicts with `2026-08-23-launcher-static-page-glass-compositor-design.md` or `2026-08-23-launcher-icon-widget-glass-alignment-design.md`, this document supersedes them. The earlier documents remain useful background for root-wide static composition, surface-inset mapping, icon visual bounds, and the shared backdrop cache.

## 1. Goal

Replace the current collection of lifecycle fixes with one explicit **Glass Scene Architecture** that owns visibility, freshness, geometry generation, component classification, and GPU output domains.

The selected design is **Scheme A: Workspace / Dock dual-domain composition**, with Drag as a transient third output domain:

```text
Launcher
├─ Workspace
│  ├─ native icons/widgets/folders
│  └─ one shared WorkspaceGlassLayer
│       ├─ ICON nodes
│       ├─ WIDGET nodes
│       ├─ SMALL_FOLDER nodes
│       └─ LARGE_FOLDER nodes
│
├─ Drag overlay                 # active drag only
│
└─ Dock window
   └─ one Dock compositor
        ├─ Dock body
        ├─ Dock ICON nodes
        └─ Dock folder/item nodes as supported
```

The architecture must preserve the GPU-only wallpaper path. It must not introduce screen recording, `PixelCopy`, `ImageReader`, bitmap wallpaper capture/readback, `glReadPixels`, or a CPU copy of the PassBlur output.

## 2. Why Scheme A

Three designs were considered.

### A. Workspace / Dock dual-domain compositor — selected

Workspace content shares one root-wide static output and one root-space backdrop cache. Dock body and Dock items share one separate continuous compositor in the Dock window. Drag remains one temporary overlay.

This matches the actual Android ownership boundaries: Workspace and Dock do not share one stable local coordinate system or one lifecycle. It also allows Dock to remain continuous while Workspace stays caller-managed/static between meaningful refreshes.

### B. One global Launcher + Dock compositor — rejected

A single compositor spanning Workspace and Dock would couple separate roots/windows and require synthetic coordinate synchronization across independent surfaces. Rotation, Workstation motion, alpha, and Dock translation would then need cross-window mirroring. This is less reliable than drawing Dock items in the Dock's own local domain.

### C. Per-object TextureView/Surface — rejected

This makes lifecycle locally simple but reintroduces the already observed scaling problem: object count increases `TextureView`, `Surface`, EGL window surface, and output swap count. It also makes page motion and rotation synchronization harder rather than easier.

## 3. Non-negotiable output contract

There are at most three LiquidDock GPU output domains:

1. **Workspace static glass layer** — all enabled desktop icons, widgets, small folders, and large folders.
2. **Drag overlay** — only the active dragged object.
3. **Dock glass layer** — Dock body plus supported Dock items in the same compositor.

For N Workspace objects and M Dock items:

```text
Workspace TextureView / Surface / EGLSurface = 1 / 1 / 1
Dock      TextureView / Surface / EGLSurface = 1 / 1 / 1
Drag      TextureView / Surface / EGLSurface = 0 or 1 / 0 or 1 / 0 or 1
```

Increasing N or M must not increase physical output-surface count.

## 4. New ownership boundary: LauncherGlassSceneController

Introduce `LauncherGlassSceneController` as the sole authority for the Workspace scene lifecycle. `LauncherGlassStaticLayer` becomes a passive output owned by the controller/session pair. The layer must never infer that it should be visible merely because it is attached and has nodes.

The controller owns:

- Launcher/root bootstrap and reconciliation;
- Workspace scene state;
- scene generation;
- HOME/COVERED transitions;
- Recents coverage;
- folder-open coverage;
- desktop edit / other full Workspace overlay coverage;
- rotation generation invalidation;
- drag/drop refresh handoff;
- first valid producer-frame barrier;
- fresh-frame-before-reveal behavior;
- producer suspension/resume requests;
- startup scan of already-existing Workspace children.

`LauncherGlassSession` remains the GPU owner: render thread, EGL context, PassBlur binding, OES input, normalization, prepared backdrop, Workspace output EGLSurface, and optional drag output. The controller commands session freshness and scene visibility but does not own GL resources.

`LauncherGlassStaticLayer` only exposes controller-driven operations such as `setSceneVisible(boolean)` and surface attach/resize/detach. It does not acquire itself from `registerStaticNode()` and does not reveal itself as a side effect of a node registration.

## 5. Workspace scene state machine

Use an explicit state machine. Exact enum names may vary, but behavior must map to these states:

```text
DETACHED
  ↓ root ready
BOOTSTRAPPING
  ↓ all existing nodes reconciled + vendor suppression settled
HOME_WAITING_FRESH_FRAME
  ↓ current-generation OES consumed + backdrop rebuilt
HOME_VISIBLE

HOME_VISIBLE ── covered ──> COVERED
COVERED ── home restored ──> HOME_WAITING_FRESH_FRAME

any attached state ── generation change ──> HOME_WAITING_FRESH_FRAME
                                       or COVERED if still covered
```

Coverage is authoritative. In `COVERED`:

- Workspace glass layer is `INVISIBLE` rather than visually stale;
- Workspace producer requests are suspended except a transition that is preparing HOME;
- no old backdrop is revealed behind Recents, an opened folder, or an overlay edit surface;
- node-level visibility does not override scene coverage.

When HOME returns:

```text
HOME signal
→ keep WorkspaceGlassLayer hidden
→ request current-generation PassBlur burst
→ receive current-generation OES frame
→ normalize current producer geometry
→ rebuild prepared backdrop
→ present Workspace scene
→ reveal WorkspaceGlassLayer
```

A cached old backdrop may still exist internally, but it is never a reveal credential after a cover transition or generation change.

## 6. Bootstrap barrier and startup reconciliation

The current Workspace PassBlur path is caller-managed: bind, update for a short burst, then pause. Constructor/attach hooks are asynchronous and can register nodes after that producer burst has ended. Startup therefore cannot depend on whichever hook happened to fire first.

`LauncherGlassSceneController` implements a bootstrap barrier:

```text
stable Launcher root + Workspace ready
        ↓
enter BOOTSTRAPPING; layer hidden
        ↓
scan current Workspace pages / CellLayouts
        ↓
classify and register all existing supported hosts
        ↓
apply type-specific vendor material suppression
        ↓
finish reconciliation barrier
        ↓
request fresh producer burst for current generation
        ↓
consume current-generation OES
        ↓
rebuild backdrop
        ↓
first Workspace render + reveal
```

The scan must be idempotent and use weak object ownership. Existing constructor/attach hooks remain installed for objects created after bootstrap; they feed the same classifier/registry instead of creating their own scene lifecycle.

The old folder-specific multi-frame startup recovery becomes fallback discovery only. It must not be the mechanism that makes an otherwise initialized scene fresh.

A press event changes only interaction state. It must never bind a missing producer, recover startup nodes, refresh a stale backdrop, or make a hidden scene visible as a side effect.

## 7. Node model and component classification

Introduce a glass-scene node category independent from drag transport, for example:

```java
enum LauncherGlassNodeKind {
    ICON,
    WIDGET,
    SMALL_FOLDER,
    LARGE_FOLDER
}
```

Do not continue overloading the current three-value `LauncherGlassDragState.Kind` for static scene classification. Drag code may map scene kinds to the smaller set of drag rendering policies where appropriate.

Each Workspace node remains lightweight and owns no View subclass, `TextureView`, `Surface`, EGLSurface, FBO, or PassBlur producer. A node contains weak host/material references, resolved style, geometry for a generation, interaction state, and suppression flags.

Dock item nodes are separate lightweight objects owned by the Dock compositor. A Dock icon must never be registered as a Workspace static node.

## 8. Effective visibility

Node visibility must follow the native Launcher hierarchy rather than only the leaf View.

For every scene synchronization, compute **effective visibility** by walking from the material host toward the relevant scene root and accumulating:

- `View.getVisibility() == VISIBLE`;
- attached/shown state;
- finite alpha;
- cumulative alpha greater than a small visibility epsilon;
- any controller-level suppression such as drag-source suppression.

Conceptually:

```text
effectiveAlpha = node.alpha × parent.alpha × ... × sceneRoot.alpha
```

Any `INVISIBLE`/`GONE` ancestor makes the node not renderable. This allows MIUI's own Workspace/page fade animations to remove glass naturally even if the leaf icon/widget remains `VISIBLE`.

The controller's `COVERED` state is still stronger than this calculation: no Workspace nodes render while the scene is covered.

## 9. Workspace generation model

Add a monotonically increasing `sceneGeneration`. The following data must be tagged or validated against the same generation before publication:

- logical root width/height;
- ViewRoot surface width/height and surface insets/content rect;
- root `SurfaceControl` identity;
- configuration rotation;
- PassBlur binding/input producer;
- OES-consumed frame freshness;
- normalized backdrop;
- prepared Prismal backdrop;
- static node geometry snapshots.

A frame is publishable only when producer geometry, OES frame, prepared backdrop, node geometry, and scene state all belong to the current generation.

### Rotation sequence

When root logical orientation, ViewRoot surface orientation, root surface identity, or relevant configuration rotation changes:

1. increment the Workspace generation;
2. hide the Workspace glass layer immediately;
3. reject render work queued for an older generation;
4. clear `hasConsumedFrame` / fresh-frame credential for the new generation;
5. update producer buffer dimensions using the existing verified 90°/270° swap rules;
6. refresh/rebind producer state as required by Workspace caller-managed ownership;
7. request a new PassBlur burst;
8. resolve node geometry directly in the new orientation;
9. bypass the ordinary two-frame geometry-stability gate for this generation transition;
10. consume a current-generation OES frame and rebuild the backdrop;
11. present and reveal only if the scene is HOME and all generation gates pass.

Do not change the existing validated 90°/270° optical/buffer-swap mapping merely to fix lifecycle generation.

The current RED `LauncherGlassRotationGenerationTest` remains the first production gate: `LauncherGlassProducerGeometryGate` must reject opposite-orientation producer geometry before any stale frame can become publishable.

## 10. Workspace producer freshness API

Separate these concepts in `LauncherGlassSession`:

- **interaction/geometry redraw**: reuse a valid prepared backdrop;
- **fresh producer request**: resume/request caller-managed PassBlur updates;
- **generation invalidation**: old frame cannot satisfy current generation;
- **backdrop rebuild**: normalize newly consumed OES and call `prepareBackdrop()`;
- **scene present**: draw all eligible nodes and perform one Workspace swap.

`requestLifecycleRefresh()` must no longer ambiguously mean all of the above. Scene code needs explicit methods/events such as:

```text
invalidateGeneration(...)
requestFreshBackdrop(generation)
onFreshOesConsumed(generation)
requestSceneRedraw()
suspendWorkspaceProducer()
```

Names may change, but the behavioral separation is mandatory.

## 11. Dock remains a separate continuous compositor

Dock behavior is intentionally not copied from Workspace.

The Dock producer remains **continuous** because the Dock glass is expected to react dynamically to the wallpaper/background while Dock moves, scales, rises, sinks, rotates, and changes Workstation layout.

Today a rotation can update the old input `SurfaceTexture` buffer size and clear frame state without replacing the old BufferQueue/PassBlur producer binding. The new Dock generation policy is:

```text
Dock root/surface/rotation generation changed
→ stop publishing old generation
→ destroy/release old input Surface + SurfaceTexture producer
→ create new OES SurfaceTexture + Surface
→ apply current buffer geometry
→ call SetPassBlurSurface / equivalent binding again
→ restore continuous producer updates
→ consume new-generation OES
→ resume Dock presentation
```

A simple `setDefaultBufferSize(newWidth,newHeight)` on the old producer is not sufficient for a Dock generation change.

Dock must not be converted to Workspace-style static caching or a four-frame caller-managed lifetime.

## 12. Dock body and Dock items share one compositor

Extend the existing Dock zero-copy renderer into a batch compositor:

```text
continuous wallpaper PassBlur
        ↓
normalize / prepareBackdrop once when source changes
        ↓
beginGlassFrame
        ↓
draw Dock body
draw Dock icon 1
draw Dock icon 2
draw Dock folder/item ...
        ↓
ONE Dock TextureView
ONE eglSwapBuffers
```

All Dock item geometry is resolved in the same Dock-local coordinate domain as the Dock body. Dock translation, scale, alpha, reveal/hide animation, orientation animation, and Workstation motion therefore require no cross-root synchronization with `WorkspaceGlassLayer`.

The icon style is shared with Workspace icons at configuration level, not at compositor ownership level. Disabling icon glass removes icon nodes in both domains. Enabling it creates Workspace icon nodes in Workspace and Dock icon nodes in Dock.

A Dock with 0, 6, or 20 icons still owns one Dock output Surface/EGLSurface.

## 13. Style model and configuration

Replace runtime component booleans/radius fields with four independent styles:

```java
GlassComponentStyle iconStyle;
GlassComponentStyle widgetStyle;
GlassComponentStyle smallFolderStyle;
GlassComponentStyle largeFolderStyle;
```

Each style contains:

```text
enabled: boolean
sizeOffsetDp: float
cornerRadiusDp: float   # 0 = Auto; >0 = forced radius
```

`sizeOffsetDp` is a symmetric edge expansion, not four independent offsets:

```text
-20 dp → inset every edge by 20 dp
  0 dp → native resolved bounds
+12 dp → expand every edge by 12 dp
```

For widgets this changes the outer bounds by the same amount on every edge and therefore does not introduce independent X/Y scaling controls or distort the widget's underlying content layout.

### Canonical keys

Use canonical keys with explicit component ownership:

```text
liquid_icon_glass
liquid_icon_size_offset
liquid_icon_corner_radius

liquid_widget_glass
liquid_widget_size_offset
liquid_widget_corner_radius

liquid_small_folder_glass
liquid_small_folder_size_offset
liquid_small_folder_corner_radius

liquid_large_folder_glass
liquid_large_folder_size_offset
liquid_large_folder_corner_radius
```

The schema is the authority for ranges/defaults. `corner_radius == 0` means Auto. Negative forced radii are invalid/clamped to Auto rather than interpreted as geometry.

### Backward compatibility

Existing JSON must continue to load:

```text
liquid_folder_glass
  → fallback default for smallFolderStyle.enabled
  → fallback default for largeFolderStyle.enabled

liquid_folder_corner_radius
  → fallback for smallFolderStyle.cornerRadiusDp
  → fallback for largeFolderStyle.cornerRadiusDp
```

Existing `liquid_icon_glass` and `liquid_widget_glass` remain canonical enable keys. If new per-style size/radius keys are absent, they use schema defaults.

Migration must preserve explicitly supplied new keys over legacy fallbacks. Export/import and presets must write the new canonical style fields while still reading old files.

The settings GUI exposes four rows/groups: Icon, Widget, Small Folder, Large Folder. Each group has Enable, Size Offset, and Corner Radius/Auto. The Icon group explicitly states that it controls both Workspace and Dock icon glass.

## 14. Geometry application of Size Offset

Apply `sizeOffsetDp` after resolving the native visual bounds but before root/Dock-local geometry normalization.

For resolved local bounds `(l,t,r,b)` and `o = sizeOffsetDp × density`:

```text
l' = l - o
t' = t - o
r' = r + o
b' = b + o
```

Clamp only to keep width/height positive and numerically valid. Do not clamp the glass shape to the host View merely because a positive offset extends outside the host; the compositor/root determines actual output clipping.

Corner radius is resolved against the final geometry. A forced dp radius is capped at half the final minimum dimension.

## 15. GPU-safe icon Auto Shape Resolver

Add a testable `LauncherGlassIconShapeResolver` that derives a rounded-rectangle-compatible radius without pixel readback.

Resolution order:

1. obtain the actual icon `Drawable` used by the host, using the existing `LauncherGlassIconGeometry` visual bounds path;
2. if the drawable/outline is circular, use approximately half the resolved icon diameter;
3. if the drawable exposes a rounded-rect outline, use its native/system outline radius scaled to the final icon bounds;
4. for `AdaptiveIconDrawable`, inspect the framework mask/outline geometry and recognize the rounded-rect/circle cases needed by system adaptive icons;
5. if the shape cannot be represented safely as a rounded rect, use the current proportional fallback.

Phase one explicitly does **not** implement arbitrary alpha-contour extraction, bitmap inspection, arbitrary `Path` stenciling, star/icon silhouette shaders, or CPU/GPU pixel readback. Prismal's core glass shape remains rounded-rectangle compatible.

Acceptance for Auto is at minimum correct circle and normal system adaptive rounded-square behavior.

## 16. Type-specific vendor material suppression

Vendor suppression becomes classification-driven and must remove only the MIUI material that LiquidDock replaces.

### SMALL_FOLDER

- target the `FolderIcon1x1` material/background path or equivalent concrete material View;
- remove/suppress only vendor blur/material plate;
- preserve the miniature app-preview children/drawables, including the 4×4-style preview content where MIUI supplies it;
- never hide the whole FolderIcon host.

### LARGE_FOLDER

- target `mIconImageView` / the concrete advanced-material image/blur owner used by the larger folder class;
- keep folder preview/content children intact;
- keep existing open/close and press interaction behavior, but scene visibility is controlled centrally.

### WIDGET

- support `LauncherAppWidgetHostView` and `MaMlHostView` ownership;
- suppress only vendor background/blur material known to conflict with LiquidDock;
- preserve `RemoteViews`, MAML children, touch handling, and host visibility.

### ICON

- never replace or clear the actual icon drawable;
- icon glass is a material behind the native icon visual;
- label and native icon rendering remain native.

Forbidden suppression techniques for these hosts include:

```text
host.setAlpha(0)
host.setVisibility(GONE)
removeAllViews()
clearing the widget/preview content tree
```

A transparent replacement drawable is allowed only on the exact vendor material plate that LiquidDock owns, not on a content drawable.

## 17. Folder open, Recents, editing, and coverage signals

Existing concrete Launcher hooks remain valuable as **signals**, but they no longer individually decide whether a static node or layer should be visible.

Examples:

- Folder `onOpen`/close completion updates the scene controller's folder coverage state;
- Recents transition hooks update scene coverage;
- desktop edit/overlay state hooks update scene coverage;
- drag source hooks update node suppression and drag-overlay ownership;
- native ancestor alpha/visibility supplies effective node visibility.

Multiple coverage causes compose as a set/count rather than a single fragile boolean. Workspace glass can reveal only when all active coverage reasons are cleared and a fresh current-generation frame has been consumed.

## 18. Drag handoff

Keep the existing single `LauncherGlassDragOverlay` output. It does not become part of the Workspace static batch because it needs independent z-order and moving-object timing.

On drag start:

1. resolve the originating scene node;
2. suppress only that Workspace node;
3. configure the one drag output from the node's component style and visual bounds;
4. continue using the shared GPU wallpaper/backdrop path where valid.

On drop/end:

1. stop the drag output;
2. reconcile the destination scene node and vendor suppression;
3. if the drop changes scene/root generation or producer freshness requirements, request a fresh backdrop;
4. reveal the static destination only under the normal scene freshness rules.

Press interaction and drag recovery stay independent.

## 19. Existing classes and intended responsibilities

The implementation should evolve the current code rather than duplicate it:

- `LauncherGlassSession`: GPU resource owner; generation-aware Workspace producer/backdrop/present API.
- `LauncherGlassStaticLayer`: passive one-per-root Workspace output; visibility controlled externally.
- `LauncherGlassStaticNode`: lightweight Workspace node; migrate to `LauncherGlassNodeKind`, component style, generation geometry, effective visibility.
- **new `LauncherGlassSceneController`**: Workspace scene state, bootstrap, reconciliation, cover/reveal, generation orchestration.
- **new `LauncherGlassSceneRegistry` or equivalent helper only if needed**: idempotent host classification/reconciliation; do not create a second lifecycle authority.
- `MiuixLauncherStaticGlassHook`: constructor/attach discovery for icons/widgets; route through controller reconciliation.
- `MiuixFolderGlassHook`: folder discovery/press/open signals and classified vendor suppression; remove folder-specific startup freshness ownership.
- `MiuixLauncherDragOverlayHook`: single drag handoff across all supported kinds.
- `LauncherGlassIconGeometry`: retains icon visual bounds responsibility.
- **new `LauncherGlassIconShapeResolver`**: Auto radius classification from Drawable/Outline/AdaptiveIcon.
- `Miuix307ZeroCopyRenderer` / Dock renderer path: evolve into one Dock body+items batch compositor and generation-aware continuous producer rebind.
- `MiuixGlassHook`: Dock host/vendor shell installation and Dock scene discovery/signals.
- `LiquidDockConfig.Glass`, `ConfigSchema`, migrations/presets/settings UI: four-style configuration.

Avoid placing scene-state policy directly into Xposed hook callbacks. Hooks should translate MIUI lifecycle events into controller/compositor operations.

## 20. Error handling and fail-safe behavior

A scene must fail closed visually rather than showing known-stale glass.

- If current-generation producer geometry is incoherent, keep the Workspace layer hidden and retry a bounded fresh producer burst.
- If fresh OES is not consumed, do not reveal an old backdrop as current.
- If reflection cannot resolve optional vendor material fields, preserve native content rather than hiding the whole host.
- If Auto icon shape cannot be recognized, use the safe proportional radius fallback.
- If Dock rebind fails after a generation change, do not continue presenting the old-generation Dock frame as if it were live; keep the previous native/failure-protection shell behavior already defined by the Dock path and retry through existing bounded activation rules.
- Root/session detach invalidates callbacks queued for that root via generation/session identity.

## 21. TDD sequence

Implementation begins from the existing RED rotation behavior test and proceeds by contracts rather than device-only fixes.

### Phase 1 — generation/freshness core

Add/fix tests for:

- `LauncherGlassProducerGeometryGate` accepts coherent portrait/landscape geometry and rejects opposite-orientation stale surfaces;
- old generation cannot publish after root/surface/config generation change;
- rotation bypasses ordinary geometry stability for the first new-generation snapshot;
- fresh-frame credential is generation-specific;
- layer reveal requires current-generation backdrop readiness.

### Phase 2 — scene controller/bootstrap/coverage

Tests for:

- static layer does not self-reveal on node registration;
- startup reconciliation discovers already-attached supported hosts before first reveal;
- bootstrap requests a fresh producer burst after reconciliation;
- Recents/folder/edit coverage hides the layer and suspends Workspace production;
- HOME return remains hidden until fresh OES/backdrop;
- multiple coverage reasons must all clear;
- effective visibility follows ancestor visibility and cumulative alpha;
- press changes interaction only.

### Phase 3 — four component styles and suppression

Tests for:

- independent enable/size/radius values for all four kinds;
- `0` radius = Auto;
- symmetric size-offset math;
- old folder keys populate both small/large fallbacks;
- new keys override legacy values;
- settings/export/import/presets preserve new fields;
- small vs large folder classification;
- widget/icon content is never globally hidden/cleared;
- vendor suppression targets material owners only.

### Phase 4 — Auto icon shape

Tests for:

- circular outline → half-diameter radius;
- rounded-rect outline → native scaled radius;
- supported adaptive-icon round/rounded-square cases;
- unrecognized path → proportional fallback;
- no bitmap/pixel-readback API appears in the implementation path.

### Phase 5 — Dock generation and batch items

Tests/contracts for:

- Dock generation change releases old producer Surface/SurfaceTexture and creates a new one;
- PassBlur is rebound to the new Dock producer;
- continuous updates resume after rotation;
- Dock body and all Dock item draws happen before one output swap;
- Dock icon enable/style comes from `iconStyle` but no Dock icon is registered in Workspace;
- Dock item count does not affect physical output-surface count.

### Phase 6 — full regression

Run the existing Launcher glass, folder lifecycle, drag overlay, Dock, Prismal, config/migration, and geometry suites plus complete unit tests and `assembleDebug`. No old regression test may be deleted merely because architecture changed; update only tests whose old ownership contract is intentionally superseded by this spec.

## 22. Device acceptance matrix

The feature is not complete until device validation covers all of these:

1. Restart Launcher with enabled icons, widgets, small folders, and large folders already on pages. Correct glass appears without any press or drag.
2. Enter Recents: native Workspace and Workspace glass disappear together. Return HOME: no stale-glass flash; fresh background appears before reveal.
3. Open/close a folder and enter/exit overlay desktop edit state: Workspace glass follows coverage correctly and refreshes before return.
4. Rotate landscape → portrait → landscape: every Workspace material has correct position, size, corner behavior, and wallpaper sampling. No opposite-orientation old frame is visible.
5. Rotate Dock: Dock continues dynamic background response after rotation rather than freezing the pre-rotation frame.
6. Verify small folder, large folder, and widgets do not double-stack MIUI advanced material with LiquidDock, while preview/widget content remains intact.
7. Verify each of the four GUI styles independently changes Enable, Size Offset, and Corner Radius/Auto.
8. Verify Auto icon radius with at least a circular icon and a normal system adaptive rounded-square icon.
9. Enable icon glass and verify both Workspace icons and Dock icons receive the same optical style while remaining in their respective compositors.
10. Exercise Dock rise/sink, translation, scale, alpha, orientation animation, and Workstation changes; Dock item glass remains locked to the Dock body.
11. Compare GPU output ownership with 0, 6, and 20 Dock icons and with increasing Workspace object count. Surface/EGLSurface count remains constant per output domain.
12. Confirm no screen recording, PixelCopy, bitmap readback, or `glReadPixels` path has been introduced.

## 23. Completion criteria

This refactor is complete only when all automated gates are green, `assembleDebug` succeeds, and the device acceptance matrix passes without requiring a press, drag, or other accidental interaction to repair glass state.

The architectural invariant is simple: **nodes describe material; controllers/compositors own scene state; only the current generation may be shown.**
