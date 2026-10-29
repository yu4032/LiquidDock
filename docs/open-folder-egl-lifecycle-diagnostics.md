# OPEN_FOLDER EGL lifetime diagnostics (isolated from PR #474)

## Reported symptom

After approximately 45 repeated open/close cycles, the Launcher folder's glass
output falls back to the native material. Device report: eglCreateContext error=0x3003,
with EGL mtrack increasing from ~226 MB to ~2.3 GB. This is an existing
OPEN_FOLDER resource lifetime problem, not part of the API 102 hook replacement.

## Lifecycle under test

1. `OpenFolderGlassCoordinator.startWallpaperCapture` allocates a
   `ShortcutPopupGlassSession` and a `RootPassBlurBackend`.
2. `retireFrozenFolderProducer` explicitly retains EGL, normalized texture and
   presentation output while only retiring the source producer. **This alone is not
   an EGL leak**: the glass must remain visible until folder close finishes.
3. `OpenFolderGlassCoordinator.releaseAll` calls
   `ShortcutPopupGlassSession.shutdown`, which queues output GL release and calls
   `RootPassBlurBackend.shutdown`.
4. After the compositor confirms native detach,
   `scheduleShutdownGlCleanup` calls `releaseGl` on the render thread.

The device evidence so far shows one-shot producer retirement, **not** that steps
3-4 were completed for every session.

## Instrumentation

This diagnostic branch changes **only** `RootPassBlurBackend.java` and
`ShortcutPopupGlassSession.java` and adds this guide. No EGL lifecycle
semantics, resource ownership, hooks or hot-reload policy are changed.

When the existing debug-log preference is enabled, search for `[EGL-LIFE]`.
Each line includes a backend ID, a lifecycle event, and cumulative successful
creation/destruction counts for **OPEN_FOLDER only**:

- `contexts=created/destroyed`
- `pbuffers=created/destroyed`
- `windows=created/destroyed`
- `pendingDetach` and `shuttingDown`

Events: `backend-created`, `context-create`, `pbuffer-create`,
`window-create`, `window-destroy`, `session-shutdown-queue`,
`session-shutdown-cleanup-running`, `backend-shutdown-request`,
`backend-shutdown-detach-confirmed`, `releaseGl-begin`,
`pbuffer-destroy`, `context-destroy`, `releaseGl-end`.
Failed destroys have a `-failed` suffix; exceptions have a `-exception` suffix.

**The counts represent successful EGL API calls, not bytes or GPU allocation
sizes**. These are process-wide OPEN_FOLDER counters; compare the final counts
after all async cleanup has had time to finish. A missing
`session-shutdown-cleanup-running` after `session-shutdown-queue` indicates
the queued guarded render task did not reach the callback. The session's
`postToRenderThread` wrapper can itself fail during EGL setup and skip the
cleanup callback, which must be distinguished from failure to start shutdown.

## One-build device run

1. Build and install the diagnostic **R8 release** on the same device and
   framework configuration, reboot/restart Launcher, enable LiquidDock debug
   logging. Record PID and `dumpsys meminfo` baseline.
2. Capture logcat with a file on the host:

   ```sh
   adb logcat -c
   adb logcat -b main,system,crash -v threadtime > folder-egl-lifetime.log
   ```

3. Open/close the same expanded folder **10 times**, allow the closing animation
   and deferred detach/GL cleanup to complete between cycles.
4. Record new PID and `dumpsys meminfo com.miui.home`; filter the log for
   `EGL-LIFE`, `OpenFolderGlass`, `RootPassBlur`, `PassBlurRetire`.
5. After an additional idle period, record the same counters/meminfo again.
   If the EGL totals balance but mtrack grows, investigate outstanding
   SurfaceControl/TextureView/compositor buffers and their ownership instead.

## Interpretation

- `context-create` without matching `context-destroy` for a backend, plus
  missing `backend-shutdown-request`: folder/session close did not reach backend.
- `session-shutdown-queue` but no `session-shutdown-cleanup-running`:
  render queue task may be skipped or fail in its guarded EGL preamble.
- `backend-shutdown-request` but no `backend-shutdown-detach-confirmed`:
  native detach callback/continuation has not completed.
- `releaseGl-begin` but no `releaseGl-end`: GL cleanup was interrupted.
- `*-destroy-failed`: EGL API did not acknowledge destruction.
- Balanced EGL counts with rising mtrack: retained GL textures, producer buffers
  or compositor-facing surfaces may remain; counters alone do not establish
  those resources' ownership.

Do **not** replace the one-shot retirement with early `releaseGl`: it would
invalidate the frozen lens that is still rendered during native folder close.
