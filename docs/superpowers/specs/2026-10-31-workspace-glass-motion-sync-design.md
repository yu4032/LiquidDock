# Workspace glass motion synchronization

> Historical design snapshot dated 2026-10-31. It records the implementation decision for this change and is not the current runtime contract.

## Intent

Preserve the original Workspace glass lifecycle for every node: when it appears, hides,
fades, changes scale, transfers to a drag/launch proxy, and returns to its Workspace View.
The user reports that the Workspace moves while its glass stays visually unchanged. The
current HWUI route already reads live View geometry; the missing part is making current
geometry and opacity reach the drawn HWUI frame without changing the existing ownership
and scene visibility rules.

## Goals

- Cover Workspace icons, widgets, and small and large folders.
- Follow native View transforms and visibility alpha, plus LiquidDock's per-node drag and
  return fades.
- Preserve attach/detach, native visibility, launch-proxy handoff, folder return, scene
  freshness, coverage, and HOME recovery timing.
- Keep the glass and Launcher content synchronized through the existing root ViewRoot.
- Stop requesting extra frames once the motion settles.
- Preserve the existing moving DragView and floating launch-proxy ownership rules.

## Non-goals

- Replace the PassBlur source/sampling path or change backdrop orientation, Prismal optics, or glass styling. Workspace capture scheduling may change to support idle pauses and active motion/drag leases.
- Translate or freeze the root-coordinate backdrop to imitate node motion.
- Add a new Surface/BufferQueue or replace the existing drag and launch proxies.

## Current behavior and constraint

`LauncherGlassStaticNode.captureGeometry()` maps a node's transformed corners into root
coordinates, so it can already represent scale and translation. `LauncherGlassSession`
observes nodes from the root pre-draw listener. However, only Workspace scroll explicitly
invalidates `LauncherGlassHwuiProbeView`. Node fades and launch-proxy updates call
`requestStaticRedraw()`, while `renderOutputs()` skips static GL rendering when the HWUI
probe is active. That leaves the probe showing its previous frame. The probe also sets
paint alpha from LiquidDock's custom visibility value only; it does not include native
alpha between a node and the Workspace. Workspace-wide alpha is already applied once by
`LauncherGlassStaticLayer` and must not be multiplied again per node.

## Approaches considered

1. **Workspace pre-draw synchronization (selected).** Use the existing per-frame View
   observations to detect changed node geometry and alpha, then invalidate the HWUI glass
   before the current root draw. Route LiquidDock-owned fade/proxy updates through the same
   presentation invalidation. This covers all registered nodes with one authority and
   retains the existing idle behavior.
2. **Hook each MIUI animation.** Mirror edit-mode, drag, app-launch, and return progress
   through separate hooks. This gives path-specific control but duplicates Launcher
   animation state and relies on private, version-sensitive entry points.
3. **Continuously sample with Choreographer.** Sample and redraw every node while Workspace
   is attached. This catches changes that do not invalidate the View tree, but adds a
   polling loop and frame cost when no transition is active.

The user selected approach 1. Existing DragView and floating launch-proxy hooks remain the
geometry authorities for those moving objects; their changes will invalidate the same HWUI
presentation path.

## Design

- During root pre-draw, compare each node's live geometry and visual alpha inputs. When a
  transform, visibility, or alpha changes, invalidate the probe in time for the same
  ViewRoot traversal to draw current geometry. Geometry changes continue using the existing
  `WorkspaceTransitionFrameSyncState` lease, which requests another root frame while motion
  is observed and releases after its stable-frame boundary.
- Compose node opacity from LiquidDock's `visibilityAlpha` and `homeReturnAlpha` with the
  native alpha from the material View up to, but excluding, the Workspace root. Keep
  Workspace/root alpha and scene transitions in the existing glass-layer alpha path so
  these factors are applied once.
- When custom fade or launch-proxy state changes outside an ordinary property-animation
  traversal, request a probe frame through the same presentation-sync entry point instead
  of relying only on a static GL redraw.
- Keep the existing native WindowElement lifecycle and whole-root Surface transition authority.
  Those transitions move the glass layer and Launcher content together; explicit proxy
  geometry updates remain responsible for motion that transfers one node outside Workspace.
- Continue drawing the root-coordinate backdrop in place. Only node geometry and opacity
  follow Launcher motion.
- End motion-driven invalidation after the observed state is stable; do not add an always-on
  frame callback.

## Lifecycle ownership table

| Lifecycle | Existing authority to preserve | Glass behavior |
| --- | --- | --- |
| Ordinary Workspace node | Attached material View and its visibility/alpha ancestry | Show only while the source is visible; follow its current root-space transform and effective alpha. |
| Long-press drag | MIUI DragView and `setSuppressedByDrag` | Fade the source glass with its source lifecycle; let the existing drag overlay follow DragView; do not leave both visible. |
| App launch proxy | FloatingIconView2 / FloatingIconLayer2 geometry and visibility rules | Keep the source slot hidden until the proxy is drawable, then follow the proxy's actual rectangle and visible alpha; return ownership when the source View is restored. |
| App-to-HOME return | Existing CLOSE_TO_HOME progress and `LauncherGlassReturnFade` | Preserve the native-progress fade and hidden/visible proxy handoff; restore the Workspace node at the existing endpoint. |
| Folder return | Saved Workspace geometry and folder-return owner | Keep the saved geometry through the existing return handoff even while the source View is temporarily hidden. |
| Scene-level lifecycle | `LauncherGlassSceneController` and `LauncherGlassStaticLayer` | Preserve current bootstrap/fresh-frame, folder/Recents/rotation/unlock gates and scene fade timing. |

Opacity must be composed from the current visual owner exactly once. Ordinary nodes use
their native source ancestry plus LiquidDock's node fade; proxy-owned nodes use the proxy
visibility/opacity contract and the existing return-progress fade; scene-level alpha stays
owned by the static layer/controller. A proxy must not cause unrelated Workspace nodes to
remain visible after their native scene has faded.

## Acceptance criteria

- Every registered Workspace glass node follows native scale/translation and alpha during
  edit-mode transitions, Workspace zoom, app open/return, and native visibility changes.
- Long-press drag fades the source node with the existing moving glass overlay and does not
  leave a stale or duplicated source glass image.
- Existing drag and launch-proxy visibility, geometry, and return ownership remain intact.
- Hidden/detached nodes, folder return, scene coverage, and HOME recovery retain their
  existing show/hide timing.
- Workspace-level fades are applied once; node-level alpha is neither omitted nor doubled.
- The root-coordinate backdrop does not move with glass geometry, and idle Workspace does
  not acquire continuous frame sampling.

## Implementation boundary

Expected changes include `LauncherGlassSession`, `LauncherGlassStaticLayer`,
`LauncherGlassStaticNode`, the HWUI probe view, and the Workspace producer scheduling path.
Keep existing shader optics and PassBlur sampling intact. The opt-in probe exports an immutable
backdrop pair when size or rotation changes; same-size later content is not re-exported, so the
probe is currently bounded to static backdrops. Build the debug APK with the probe flag enabled
and review the named transitions before treating the work as complete.
