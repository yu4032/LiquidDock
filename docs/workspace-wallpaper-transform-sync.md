# Workspace wallpaper transform synchronization

## Problem and evidence

The HWUI Workspace backdrop is an immutable capture, while HyperOS scales the real
wallpaper independently. Tracking command targets does not track that animation.
On the test device, `startAnim` requested scale 1.14, followed by a command returning
to 1.0. A later capture was still labelled 1.14 by the Launcher observer. Replacing
the backdrop then changed the sampled image even though object geometry was stable.

Device framework inspection located the actual animation in system_server:
`MiuiWallpaperSurfaceAnimation.updateAnim()` applies its SurfaceControl transaction,
then calls `updateParams()`. Observe the fields after the whole `updateAnim()` call,
not through a separate hook on the tiny internal helper. Completion passes through
`MiuiWallpaperAnimationManager.setWallpaperScale()` before resetting animation state.
The native pivot can differ from the former hardcoded center (notably Y = 0.4).

## Shared mechanism

Observe applied native scale and pivot, rather than synthesizing another animation.
The existing Launcher wallpaper command carries a callback Binder to WMS. WMS sends
applied samples asynchronously; Launcher coalesces redraw invalidations on its UI thread.
This applies to every scene using that wallpaper animation, without per-scene curves.

Each replacement backdrop is tagged using its SurfaceTexture monotonic timestamp.
Only replacement queries the authoritative WMS history synchronously. A short barrier
waits for any native update currently being applied; no store monitor spans vendor/WMS
calls. Queries time out after 50 ms and reject unknown/expired metadata. This avoids
depending on delivery order between source frames and asynchronous callbacks.

First connection also gates publication: unknown native state is never labelled neutral.
The first valid callback resumes pending backgrounds. Before connection, missing metadata
does not initiate an idle capture retry loop. If the device lacks the native hook contract,
Workspace HWUI backgrounds remain pending and the observer reports its incompatibility;
there is no guessed native transform fallback for published images.

Drawing maps current screen coordinates through the inverse current native transform,
then the transform baked into the capture. The same affine UV mapping is applied to
sharp and blurred inputs. Glass geometry, material details, resolution and capture
exclusion remain unchanged. Other users of the shared shader bind an identity mapping.

## Validation and limits

Unit coverage includes timestamp lookup, ring eviction, changing pivots, continuity when
reanchoring captures, delayed callback ordering, startup without native history, invalid
timestamps, and update timeout. Device shader checks cover Workspace, Gboard and shortcut
popup bindings. The existing Dock GPU equivalence check remains applicable.

Installing an APK cannot replace hooks already owned by system_server. A device reboot
is required to load this observer before visual validation. Code/build checks alone do
not establish that jumping is fixed: verify connection logs, App enter/exit, folder
open/close, edit mode, unlock and interrupted/reversed animations after reboot.

The applied sample timestamp follows transaction submission, not SurfaceFlinger latch.
This removes target/producer-callback mislabelling but does not claim exact compositor
frame synchronization. If a residual mismatch remains, measure that boundary directly.

## Device run, 2026-11-02

The debug APK and instrumentation APK were installed, then the device was rebooted
with user approval. WMS logged observer installation, and Launcher logged native
connection on display 0 before its first backdrop export. No observer/query failure
was logged during the smoke run. Recorded App enter/exit (including immediate reversal),
folder open/close, Recents, edit-mode exit and screen off/unlock. Glass remained present
after returning Home; sampled recordings showed no obvious background reset. This
is a smoke check, not a per-frame displacement measurement or exhaustive scene acceptance.

Build and JVM verification: 979 tests, zero failures/errors, one skipped. Device
`workspaceShaderReuse` and `dockBackdrop` checks passed. Dock GPU comparison retained
maximum final-image difference 2/255 for source fusion and 1/255 for compact output.

## Follow-up: Dock revealed inside an App, then return Home

The first smoke check missed a repeatable discontinuity in this exact path. Temporary
per-draw diagnostics showed only scale endpoints `1.0 -> 1.14 -> 1.0`, with zero
intermediate samples, even though the native spring was running. A video frame at
5.5266 seconds showed a 13.26/255 mean adjacent-frame RGB difference in the widget
inspection region after geometry had settled. Captured source images registered to
approximately identity (about half a physical source pixel of translation), so a
large source-image zoom did not explain that discontinuity.

The separate `updateParams()` hook was bypassed by compiled/inlined native callers.
Moved observation to the existing `updateAnim()` hook after the original call returns,
inside the same update barrier. This removes one hook and observes both the completed
SurfaceControl submission and final fields directly. No new interpolation or animation
curve was introduced.

After rebuilding, installation and reboot, the same diagnostic assertion changed from
zero intermediate samples (failure) to 19 visible intermediate samples (pass). The
recorded return-Home path showed continuous sampling instead of the previous large
step. Temporary logs and CPU image exports were removed from the final source.
The clean package was rebuilt and installed, its Launcher process restarted, and
the exact App/Dock/Home sequence repeated twice without the previous large step.
The selected text-free widget background patch's maximum adjacent-frame difference
after geometry settled was 8.661/255 before and 0.489/255 in the diagnostic fixed run;
this is a local recording metric, not a global positioning-error measurement.
The final JVM build retained 979 tests with zero failures/errors and one skipped;
the device Workspace shader reuse check passed again. Narrow code review found no
blocking callback/barrier issue.
