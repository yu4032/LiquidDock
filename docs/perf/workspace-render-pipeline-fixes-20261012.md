# Workspace App ↔ HOME rendering: multi-stage fixes (2026-10-12)

Branch: `fix/workspace-render-pipeline-20261012` based on latest `main@8418994c`. This is a functional optimization branch, not a measurement-only branch.

## What changed

### Launcher main-thread / static node lifecycle
- `LauncherGlassSession.setGlassConfig()` uses the current immutable `LiquidDockConfig.Glass` object identity as an idempotence key. N Widget acquisitions with the *same* live config no longer reapply source quality or each post a fresh backdrop request. New config objects (and explicit live config calls) retain their existing behavior.
- `LauncherGlassSceneController.onRootReady()` bootstraps each stable root once, not once for every registered Widget.
- `LauncherGlassSession.registerStaticNode()` adds a new node to the immutable snapshot and requests normal root pre-draw; N registrations coalesce rather than synchronously re-scanning 1..N nodes. Duplicate registrations do nothing.
- `MiuixLauncherStaticGlassHook.scheduleBind()` only checks changed native radius for an existing node; the next root pre-draw observes geometry. No unconditional scene redraw on unchanged binding.
- `LauncherGlassStaticLayer.setSceneVisible()` is idempotent for an unchanged visibility target; subsequent host binds no longer cancel an existing fade or repeatedly write the same TextureView alpha.

### Ordinary App foreground occlusion
- Launcher `onStop()` marks ordinary App coverage and pauses the Workspace source unless a visible Recents capsule uses it. `onResume()` invalidates the old HOME generation and requests the next genuinely fresh frame, respecting existing native HOME/unlock/Recents barriers.
- Unlike a global Producer pause, the session passes the shared source to Recents before its HOME occlusion early return. Workstation has its original keep-live exception.
- The generic backend's consumer-opt-in `needsNormalizedSourceFrame()` rejects residual stopped-App callbacks *before* GPU OES normalization, but still calls `updateTexImage()` to release the input queue slot. Other backend consumers opt in by default, unchanged.

### Prismal shader, allocation and EGL
- Imported #354's tested policy: native-sized output RGBA FBO/texture is allocated only for consumers that actually request texture output; direct-to-EGL Workspace output needs only source and blur textures. Saves a nominal 21.57 MiB at 3008×1880 per affected renderer. Does not shrink EGL buffers.
- Imported #351's exact-zero contribution shader guards for reflected samples and OS4 edge lighting. Nonzero weights retain original expression. GLES compilation failure falls back to the original shader.
- A static output EGL surface tracks whether it has already presented a fully transparent frame. If there are no drawable glass nodes, the first output still clears and swaps; all subsequent empty frames skip full-screen clear/swap until a node reappears. Resize/new output resets the proof. Any active glass node always executes original rendering.
- **No cache-only wallpaper zoom, no resized EGL output, no partial EGL damage/buffer-age reliance and no reduction of active Widget fade fidelity**.

## Known limits / real-device validation

Android JVM tests can verify ownership/fail-closed contracts but cannot prove performance, driver branching uniformity, output GPU contents, or availability of Launcher 4.50 `onStop()` hook on the user's ROM.

Required real-device regression checks on Launcher 4.50 / HyperOS 3, 165Hz:
1. Seven-Widget App→HOME from widget and icon: no background scaling offset, no missing Widget edges, no faded glass stuck transparent.
2. Normal App covering HOME for 10–30 seconds: no stale output when returning, first fresh frame after unocclusion.
3. Recents→HOME and Recents visible while Launcher lifecycle may stop: capsules keep receiving shared source, no black/transparent old output.
4. Folder open/close, unlock, Workstation dock, rotation/resize, Workspace page switching and repeated Widget add/remove.
5. OS4 show-normals, edge reflection/light direction and rounded corners vs latest main; driver fallback if optimized fragment fails to compile.
6. Matched Perfetto A/B (main versus this PR) to verify reduced source callbacks/StaticSwap queue stalls and FrameTimeline jank without slower first HOME reveal.

Tests and CI are a compile/contract gate only. **Do not merge until device passes**. Existing independent diagnostic PR #358 remains available; no attempt is made to invent hardware performance data.
