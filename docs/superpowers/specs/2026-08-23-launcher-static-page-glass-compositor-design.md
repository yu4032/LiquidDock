# Launcher Static Page Glass Compositor Design

Date: 2026-08-23
Base branch: `feat/folder-press-glow`
Base commit: `49f55c76f935e0aecbb0ee2408904353e940a1f9`

## Goal

Replace per-folder static `TextureView` outputs with one shared static Launcher glass compositor per stable Launcher root, while fixing the remaining static wallpaper misalignment and keeping drag glass, Dock glass, and the GPU-only wallpaper source isolated.

## Root causes addressed

1. Static folder geometry currently comes from `getGlobalVisibleRect()`. That rectangle is already clipped by ancestors. Treating the clipped rectangle as the complete output geometry can stretch a partial wallpaper sample across the full material and therefore appears as both offset and scale error. Drag glass avoids most of those ancestor clips, which explains why drag can align while the dropped static folder does not.
2. The current static architecture is one object = one `LauncherGlassSinkView` = one `TextureView`/`Surface`/`EGLSurface`. Rendering then performs one crop draw and `eglSwapBuffers` per object. This scales with folder/icon count and is already noticeable with four folders.
3. Workspace scrolling changes every object's root-space geometry. The current atlas may therefore be rebuilt even though the wallpaper source itself has not changed.

## Output architecture

### Static desktop

There is exactly one physical static glass output `TextureView` for each stable Launcher root. It is inserted at the bottom of the stable root (`DecorView`) so all normal Launcher content remains above it. The view is transparent outside glass shapes.

Static folders no longer create sibling `TextureView`s. Each material is represented by a lightweight `LauncherGlassStaticNode` containing only:

- weak material reference;
- geometry/lifecycle state;
- folder-open and drag suppression state;
- corner radius;
- press/glow interaction state.

Nodes are grouped logically by their Workspace/CellLayout ancestry for future icon/widget expansion, but all static nodes are batch-rendered into the same physical root output. This is stricter than one surface per page: one or two visible Workspace pages during a swipe still require only one static output swap.

### Drag

The existing single drag overlay remains separate because it must render above Workspace content and cross page boundaries. It reuses the same `LauncherGlassSession`, OES producer, normalized backdrop and blurred backdrop. During a drag the process owns at most:

- one static root output;
- one drag output.

No second wallpaper producer is introduced.

## Geometry model

Static nodes must never call `getGlobalVisibleRect()` for sampling geometry.

For each material, capture the four complete local corners `(0,0)`, `(w,0)`, `(0,h)`, `(w,h)`. Use Android View matrices to transform those corners from material local space to global space and then through the inverse stable-root global matrix into root-local space. Parent clipping is intentionally not applied.

The resulting root-local axis-aligned bounds drive Prismal geometry. Corner radius is scaled by the minimum transformed X/Y basis length. Workspace scroll is contained in the ancestor matrix, so root-space bounds update immediately while Recents/root transforms may continue to use the existing two-frame stability guard.

The Android compositor remains responsible for actual visual clipping; sampling geometry is never pre-clipped.

## Backdrop cache

The Launcher session changes from per-node atlas backdrop preparation to one root-sized normalized backdrop cache:

1. PassBlur produces wallpaper-only OES data for the stable Launcher root.
2. When a new OES frame, producer geometry change, root size change, or optical configuration requiring blur changes occurs, normalize the full root once and call `PrismalRenderer.prepareBackdrop()` once.
3. Static node geometry/interaction changes only call `beginGlassFrame()` + batched `drawGlass()` + one static output swap. They must not call `prepareBackdrop()`.
4. Workspace scrolling therefore moves/recomputes glass shapes against the cached root backdrop without repeating blur preparation.
5. Drag output is rendered from the same prepared backdrop and performs its own crop/present only while dragging.

The static cost is allowed to grow with actual covered glass pixels and draw count. Surface count, backdrop preparation count, and output swap count must not grow with static object count.

## Lifecycle and ownership

`LauncherGlassSession` remains the sole owner of:

- PassBlur binding;
- OES `SurfaceTexture`;
- EGL context/render thread;
- normalized root texture;
- Prismal backdrop cache;
- static output EGLSurface;
- optional drag output EGLSurface.

`LauncherGlassStaticLayer` is one weakly registered output view per stable root. Root detach disposes it with the session. Static nodes do not own EGL or Surfaces.

Material reparenting only updates the node's material ancestry/matrix. It cannot destroy or recreate the static output surface.

## Folder behavior preserved

The migration must preserve:

- Folder Glass enable/disable semantics;
- native/Auto and forced folder corner radius;
- press glow coordinates and animation;
- folder-open suppression until close animation completion;
- drag suppression and handoff to the shared drag overlay;
- permanent transparency of MIUI's replaced folder material;
- startup recovery after Launcher restart.

`MiuixLauncherDragOverlayHook` must discover the lightweight static node directly instead of scanning a folder parent for a `LauncherGlassSinkView`.

## Performance contract

For N static glass objects on the desktop:

- static `TextureView` count = 1 per stable Launcher root;
- static `Surface` count = 1;
- static EGL window surface count = 1;
- static `eglSwapBuffers` count per rendered scene refresh = 1;
- backdrop blur preparation on pure geometry/page-scroll changes = 0;
- no PixelCopy, ImageReader, Bitmap readback, `glReadPixels`, screen recording, or CPU wallpaper capture.

This is the required base for later folder + widget + icon glass expansion.

## Testing

Add contracts for:

- unclipped transformed bounds use full local corners and never `getGlobalVisibleRect()`;
- static node is not a `View`/`TextureView` and owns no `Surface`;
- only one static layer exists per stable root;
- Folder hook claims `LauncherGlassStaticNode`, not static `LauncherGlassSinkView`;
- drag suppression resolves the static node directly while drag still uses its one sink;
- root backdrop preparation is separated from geometry-only static redraw;
- one static output is presented once after batching all static nodes;
- existing drag, Dock ownership, wallpaper-only source, folder lifecycle, config and Prismal tests remain green.

## Device acceptance

CI cannot prove frame pacing or visual alignment. On-device acceptance after the build is:

1. Static folder wallpaper aligns immediately after Launcher start and immediately after drop.
2. Repeated drag/drop does not change the static sampling offset.
3. Four folders on one page no longer produce the previous multi-surface slowdown.
4. Workspace swipes remain smooth with several folders and do not show frozen/lagging glass.
5. Folder press, open/close and drag handoff remain correct.
