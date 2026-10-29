# Transient PassBlur root detach ordering (P0)

## Reproduction and evidence

- SHORTCUT_POPUP: 51/51 bindings failed to get detach confirmation.
- DRAG_OVERLAY: 9/9 bindings failed to get detach confirmation.
- OPEN_FOLDER: 123/123 bindings retired cleanly.
- Long-press app → dismiss by tapping empty desktop, repeat 3x:
  EGL mtrack increased ~113,880 KB (~37 MB per popup).
- After ~60 retained sources, EGL mtrack ~2.30 GB and
  `eglCreateContext error=0x3003` (EGL_BAD_ALLOC); shortcut glass fell back.

The earlier folder hypothesis was wrong. This fix deliberately does not touch OPEN_FOLDER
or the detach coordinator's fail-closed policy.

## Actual ownership error

Both `ShortcutPopupGlassCoordinator.releaseLocked` and
`LauncherGlassDragOverlay.releaseDragSource` used:

```text
session.shutdown()      // enqueues work on GPU render thread
sourceOverlay.dispose() // removes WindowManager root immediately
 ...later...
RootPassBlurBackend.shutdown()
PassBlurDetachCoordinator.retire(binding, ...)
Miuix307PassBlurBridge.tryUnbind()
```

Thus the source `SurfaceControl` can be invalidated before the native detach
transaction is even attempted. `PassBlurDetachCoordinator` correctly refuses
to release the producer until committed detach is observed. Retrying three
times cannot restore an already-destroyed root.

## Fix

- Transient overlays stay attached after UI/menu/drag teardown.
- `RootPassBlurBackend.onShutdownComplete` queues an owner callback.
- After compositor-confirmed unbind, `releaseGl()` completes on render thread;
  only then is `overlay.dispose()` posted to main.
- Failure to confirm detach still retains the binding and source window:
  **no timeout-driven GPU free**, no unsafe release-before-commit.
- A shutdown cleanup task no longer passes through the ordinary render task's
  `ensureEglAndSource()` preamble. Otherwise an existing EGL_BAD_ALLOC can
  prevent shutdown from running at all and can allocate new graphics objects
  during teardown.

OPEN_FOLDER still uses its existing explicit one-shot producer retire path and
per-folder shutdown behavior. The module hot-reload policy remains unchanged.

## Regression validation

1. Install the R8 build, restart Launcher, confirm debug log is enabled.
2. Record `pidof com.miui.home`, `dumpsys meminfo com.miui.home`, and
   `PassBlurDetachCoordinator.pendingCount` if available in debug diagnostics.
3. Long-press an icon, then tap empty space to dismiss. Repeat 3, 10, then 60
   times, with time for asynchronous cleanup to complete between operations.
4. Repeat 10 drag-overlay sessions.
5. Verify `PassBlur compositor detach committed` for each transient source and
   no persistent `detach unconfirmed ... retained` entries.
6. Ensure source windows are removed after successful detach, and EGL/GL mtrack
   returns near its stable baseline after idle. The same Launcher PID must survive.
7. Re-run OPEN_FOLDER 50 cycles and a popup→drag→folder interleaving sequence.
8. Test a deliberately unconfirmed/delayed detach: the independent source root
   must not be removed early. Test a failed `eglCreateContext` before or during
   shutdown: teardown must still reach backend shutdown without re-initializing EGL.

**Device outcome:** the original reporter confirmed the transient PassBlur leak
fix on the affected device, and approved integrating it into PR #474. No
post-fix per-domain counts or mtrack measurements were supplied in that
confirmation; retain this checklist for future regression testing.
