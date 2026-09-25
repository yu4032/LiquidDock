> [!NOTE]
> **Historical record / 历史记录。** 本文件记录其日期对应的计划、设计或验证快照，不维护为当前实现契约。当前行为请以 [README](../../../README.md)、[FEATURES](../../../FEATURES.md)、[ARCHITECTURE](../../../ARCHITECTURE.md)、[HOOKS](../../../HOOKS.md) 和 [TODO](../../../TODO.md) 为准。

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

`LauncherWallpaperFreshnessHook` mirrors the concrete HyperOS 4.50 Launcher refresh transaction proven by the canonical OS3 Launcher Pad decompilation:

1. `DesktopWallpaperManager.MiuiWallpaperManagerCallbackStub.onWallpaperChanged(...)` unconditionally calls `DesktopWallpaperManager.updateWallpaperInfo()`.
2. `updateWallpaperInfo()` removes any previously queued `WallpaperInfoUpdateTask`, reinitializes it, and enqueues the latest task on `Executors.BACKGROUND_EXECUTOR`.
3. `WallpaperInfoUpdateTask.run()` rereads `getWallpaperColors(1)`, desktop wallpaper info/type/scrollability and then invokes `DesktopWallpaperManager.onDarkModeChange()`.
4. `onDarkModeChange()` posts `ColorModeRefreshTask` to Workspace. Once Launcher is not loading, that task calls `notifyWallpaperColorChanged()`.
5. LiquidDock advances wallpaper content generation at `updateWallpaperInfo()` and requests the fresh PassBlur pulse only after `notifyWallpaperColorChanged()` returns, so all vendor wallpaper listener fan-out for that transaction has completed.

The Launcher callback stub's `onWallpaperFirstFrameRendered(int)` and `onDrawFrameEnd()` bodies are empty in this build, so they are not used as Workspace wallpaper-content completion authority. `onDrawFrameEnd()` remains separately consumed by the Recents-return wallpaper-settle implementation.

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

Canonical decompilation authority model (OS3 Launcher Pad 4.50.0.1204, analysis identity `46532f3bdcce8939...`):

- transaction start: `DesktopWallpaperManager.updateWallpaperInfo()`;
- transaction completion for Launcher wallpaper-derived UI: `DesktopWallpaperManager.notifyWallpaperColorChanged()`;
- registration source: `WallpaperManagerCompatVT.initMiuiWallpaperManager(...)` registers the callback through `MiuiWallpaperManager.registerWallpaperChangeListener(callback, 1)`;
- Workspace is explicitly added to `DesktopWallpaperManager`'s wallpaper-color listener list in `Launcher.setupViews()`;
- no framework `WallpaperManager.getWallpaperId()`, generic wallpaper broadcast, or fixed-delay retry is used by LiquidDock as content authority.

The path remains event-driven and GPU-only: no polling loop, screenshot capture, PixelCopy, Bitmap wallpaper readback, or fixed timing fallback is permitted.

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
