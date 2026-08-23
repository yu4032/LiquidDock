# Workspace Wallpaper Content Generation Design

## Problem

Workspace glass caches the last consumed PassBlur OES frame and then pauses the Workspace producer. The cache is invalidated for scene coverage, producer geometry, rotation, and Surface generation changes, but not when the wallpaper content changes while all producer geometry remains stable. HyperOS 4.50 therefore updates Launcher wallpaper state while LiquidDock can continue drawing the previous wallpaper backdrop indefinitely.

## Goal

Model wallpaper content freshness explicitly so a same-size, same-rotation, same-Surface wallpaper replacement produces a bounded event-driven Workspace PassBlur refresh without enabling continuous producer updates.

## Constraints

- GPU-only: no PixelCopy, ImageReader, Bitmap readback, glReadPixels, screen recording, or CPU wallpaper capture.
- Keep Workspace producer demand-driven and paused after a consumed fresh frame.
- No fixed-delay timers or polling loops.
- Do not conflate wallpaper content changes with Surface/geometry generation changes.
- Preserve current App→HOME, rotation, Surface generation, producer endpoint rollover, Dock, folder, widget, and icon behavior.
- Stale events/frames from an older wallpaper content generation must not commit a newer generation.

## Architecture

Add a dedicated `LauncherWallpaperFreshnessHook` and Android-free `LauncherWallpaperContentState`.

`LauncherWallpaperFreshnessHook` observes HyperOS 4.50 wallpaper lifecycle boundaries and routes them to the active Workspace root:

1. `DesktopWallpaperManager` wallpaper-change boundary increments wallpaper content generation.
2. `Workspace.onWallpaperColorChanged()` may issue one candidate refresh for the current pending generation.
3. `onWallpaperFirstFrameRendered(int)` and `onDrawFrameEnd()` act as compositor-ready boundaries and may issue one authoritative refresh for the current generation.

The state machine coalesces duplicate notifications and exposes tokens describing whether a candidate or authoritative pulse should be requested. An authoritative boundary is allowed to request a second pulse even if a candidate pulse already happened, because the candidate may have captured the final old wallpaper frame.

`LauncherGlassSceneController` remains the root router. Wallpaper content invalidation does not change scene visibility or increment scene generation; it forwards the wallpaper token to the matching `LauncherGlassSession`.

`LauncherGlassSession` tracks requested and consumed wallpaper content generations alongside existing scene generation. A wallpaper refresh clears the cached backdrop/frame state and requests exactly one producer pulse. A consumed OES frame records the wallpaper generation associated with that pulse. Old wallpaper generations cannot mark a newer wallpaper content generation committed. After consumption the existing `pauseUpdates(binding)` behavior remains unchanged.

## State Model

For wallpaper generation `N`:

- `changed(N)`: content version changed and is pending.
- `candidateRequested(N)`: at most one early pulse has been requested.
- `authoritativeRequested(N)`: at most one compositor-ready pulse has been requested; it may follow the candidate pulse.
- `committed(N)`: a frame associated with the latest authoritative generation has been consumed and rendered.

Rapid transitions `A → B → C` produce monotonically increasing generations. Events and frame commits for B are ignored once C is pending.

## Hook Strategy

Primary vendor events:

- `com.miui.home.launcher.wallpaper.DesktopWallpaperManager` wallpaper change callback path.
- `com.miui.home.launcher.Workspace.onWallpaperColorChanged()` candidate UI notification.
- vendor callback methods `onWallpaperFirstFrameRendered(int)` / `onDrawFrameEnd()` when present.

Hook installation must be reflective and version-tolerant: missing optional callbacks are logged and do not disable the rest of the pipeline. No broadcast receiver or timer is used as the primary mechanism.

## Testing

RED tests must prove the current branch lacks wallpaper content freshness, then GREEN must cover:

- same Surface/geometry/rotation wallpaper changes still advance wallpaper content generation;
- duplicate candidate notifications coalesce;
- authoritative notification can request a second pulse after a candidate;
- duplicate authoritative notifications coalesce;
- stale generation events and stale frame commits cannot commit the latest generation;
- wallpaper invalidation does not alter scene visibility/state generation;
- Workspace producer remains paused after consuming the requested fresh frame;
- ordinary static node redraw does not advance wallpaper content generation;
- current Surface/geometry lifecycle tests remain green.
