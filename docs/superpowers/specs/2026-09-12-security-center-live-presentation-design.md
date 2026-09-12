# Security Center Live Presentation Design

## Problem

Security Center's shader path currently uses `SecurityCenterFramePipelineState` as both the first-presentation safety barrier and the steady-state rendering gate. The pipeline intentionally accepts only the first source plus one post-handoff refresh for a generation. After that, additional PassBlur source frames are rejected even though the native producer remains continuous. The result is a finite-frame material rather than a truly live backdrop.

The failure is distinct from producer activation. Lock/unlock and material-handoff fixes can produce valid source frames, yet the consumer can still stop repainting once the generation's strict presentation sequence finishes.

## Goal

Keep the existing fail-closed first-presentation protocol, then allow same-generation PassBlur frames to continuously repaint already-authorized Security Center sinks at the configured render FPS.

## Design

Use two presentation phases for the shader-backed Video / Global Sidebar path:

1. **Barrier phase.** Geometry or generation changes create a `FrameRequest`. A source frame must pass the existing serial/generation checks, render through Prismal, be swapped into every required TextureView output, and receive real `onSurfaceTextureUpdated` acknowledgements before custom ownership is considered presented.
2. **Live phase.** The first accepted presentation still causes the material handoff and producer rebind. The pipeline-owned post-handoff refresh then completes through the same TextureView acknowledgement barrier. Only after that second current-generation presentation may the session enter LIVE. In LIVE, later source frames of that same generation render the latest authorized frame directly into current outputs without reopening ownership or presentation-barrier state.

## State boundary

Add an Android-free `SecurityCenterLivePresentationState` with these externally visible semantics:

- `onStrictPresentation(generation, requestedFollowUp=true)` records that the first current-generation presentation completed but LIVE is not yet safe.
- `onStrictPresentation(generation, requestedFollowUp=false)` enters LIVE only when the same generation is awaiting its rebound presentation.
- `isLive(generation)` is true only for the established live generation.
- `invalidate()` exits LIVE and clears the awaiting state.

The session must invalidate this state before any new logical `FrameRequest`, source rebind/recovery, output attach/resize/detach, or shutdown. A new generation therefore always returns to the strict barrier.

## Rendering rules

`SecurityCenterGlassSession.onFreshFrame` checks LIVE before calling `SecurityCenterFramePipelineState.onFreshSource`. A LIVE frame is accepted only when:

- the source generation equals the live generation and the latest `FrameRequest` generation;
- the root remains attached and source logical dimensions match the latest presentation geometry;
- every latest sink still maps to the same valid EGL output.

It then reuses the existing Prismal `prepareBackdrop -> beginGlassFrame -> drawGlass -> presentTarget` path for all current nodes. It does not arm TextureView presentation serials, does not call the listener's ownership callback, and does not create a new frame-pipeline request. Output mutation invalidates LIVE first and returns to the strict path.

## Lifecycle rules

- Video and Global Dock may use LIVE because their transition policy selects `CUSTOM_SHADER`.
- Game remains vendor-only and never creates this session.
- Material handoff, window-visibility recovery, source-authority rollover, endpoint rebind, panel hide/release, and shutdown all invalidate LIVE before recovery proceeds.
- No `postDelayed`, ScreenCapture, PixelCopy, Bitmap capture, stale screenshot, or CPU backdrop copy is introduced.
- No hardcoded vendor-obfuscated symbols are introduced.

## Acceptance

API101 must pass zero-copy/no-delay audit, all unit tests, and `assembleDebug`. Unit coverage must prove that LIVE cannot start after only the first ACK, starts after the post-handoff strict presentation, accepts only its own generation, and is revoked by invalidation/new request. Static integration coverage must prove that the session has a direct same-generation live-render path while the existing strict TextureView ACK path remains intact.
