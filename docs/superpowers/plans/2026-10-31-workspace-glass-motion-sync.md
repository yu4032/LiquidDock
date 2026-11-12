# Workspace Glass Motion Synchronization Implementation Plan

> Historical implementation plan dated 2026-10-31. It records the work for this change and is not the current runtime contract.

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the full Workspace glass lifecycle and make every glass node follow its current visual owner's geometry and opacity during Launcher motion.

**Architecture:** Capture per-node presentation alpha alongside the existing root-space geometry snapshot. Keep the Workspace scene alpha on the root glass layer when no launch proxy owns a node; while a proxy is active, apply Workspace alpha to ordinary nodes individually and apply proxy alpha only to the proxy-owned node. Route node-state changes to the HWUI probe when it owns static output, while retaining the existing GL path as fallback.

**Tech Stack:** Android Java, View/ViewTreeObserver, Canvas/RuntimeShader HWUI, existing EGL/Prismal static renderer, MIUI Xposed hooks.

**Spec:** `docs/superpowers/specs/2026-10-31-workspace-glass-motion-sync-design.md`

## Global Constraints

- Cover Workspace icons, widgets, and small and large folders.
- Preserve attach/detach, native visibility, launch-proxy handoff, folder return, scene freshness, coverage, and HOME recovery timing.
- Keep the existing PassBlur source and sampling path; adjust only capture scheduling for idle Workspace, active motion, and live drag. Preserve backdrop orientation, Prismal optics, and glass styling.
- Do not translate or freeze the root-coordinate backdrop to imitate node motion.
- Do not add a new Surface/BufferQueue or replace existing drag and launch proxies.
- Do not add an always-on frame callback.

## Review Focus

1. Native alpha on a material or an intermediate Workspace child changes without geometry change: the glass must match it without multiplying Workspace alpha twice. Inspect both ordinary and probe-enabled frame alpha composition.
2. A launch proxy becomes visible while the Workspace fades: only the proxy-owned node follows proxy alpha; other Workspace nodes follow the Workspace's current effective alpha.
3. The proxy exists before it draws, or stops drawing during a transition: the source node remains hidden until ownership returns, with no stale proxy geometry.
4. Folder return uses saved geometry while the source View is temporarily hidden: keep the existing return geometry and handoff timing.
5. Long-press drag fades the source while DragView owns the moving glass: avoid both a frozen source image and duplicate source/overlay glass.

## File Map

- `LauncherGlassVisibility.java`: Add an ancestor-bounded alpha read so node alpha can exclude Workspace alpha when the layer already applies it.
- `LauncherGlassVisualOwnerState.java`: Store proxy opacity alongside proxy ownership and rectangle.
- `LauncherGlassStaticNode.java`: Compose custom, source, folder-return, and proxy opacity while preserving current geometry ownership.
- `MiuixLauncherStaticGlassHook.java`: Publish the final consumer's proxy opacity with its geometry and existing visibility decision.
- `LauncherGlassSession.java`: Carry node opacity in immutable static geometry frames; publish alpha-only changes; invalidate the HWUI probe for static presentation changes; use the frame alpha in both static render paths.
- `LauncherGlassStaticLayer.java`: Expose one probe invalidation entry point and retain the current Workspace alpha behavior for ordinary scenes and folder freezes.
- `LauncherGlassHwuiProbeView.java`: Only change if needed to consume the session's alpha-aware frame; no independent animation loop.

---

### Task 1: Track opacity for each visual owner

**Files:**

- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassVisibility.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassVisualOwnerState.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java`

**Interfaces:**

- Add `LauncherGlassVisibility.alphaBelowAncestor(View host, View ancestor, View sceneRoot) -> float`; it multiplies visible alpha from `host` up to, but excluding, `ancestor`, returns `0f` if the ancestry is invalid/invisible, and does not include `sceneRoot` alpha.
- Add `LauncherGlassVisualOwnerState.updateLaunchProxyRect(float[] rect, float alpha) -> boolean` and `alpha() -> float`; preserve the existing one-argument method as a full-opacity overload.
- Add `LauncherGlassStaticNode.presentationAlpha(View root, View workspace, float workspaceAlpha, boolean workspaceAlphaBypassed) -> float` for the current frame's composed node alpha.

- [x] **Step 1: Add bounded native-alpha sampling.** Implement `alphaBelowAncestor` with the same attachment, visibility, finite-alpha, and epsilon rules as `LauncherGlassVisibility`; do not include either the ancestor's or root's alpha.
- [x] **Step 2: Extend proxy ownership with opacity.** Store a clamped `[0, 1]` alpha with the proxy rectangle; hidden ownership clears geometry and opacity to zero, and ending ownership clears the proxy alpha back to one. Keep the existing one-argument update method delegating to full opacity for callers that have no alpha source.
- [x] **Step 3: Compose node presentation alpha.** Ordinary nodes use `visibilityAlpha()` times native alpha below Workspace. When Workspace alpha is bypassed because a proxy owns a node, ordinary nodes also multiply the effective Workspace alpha. A proxy-owned node uses its stored proxy alpha and existing `visibilityAlpha()`/HOME-return fade; an active folder-return snapshot ignores the temporarily hidden source View alpha.
- [x] **Step 4: Publish vendor proxy opacity.** In `MiuixLauncherStaticGlassHook`, keep the existing View2 `alpha > 0.1` and Layer2 `alpha > 0` visibility contracts. Store View2 as its actual binary visible opacity and Layer2 as the supplied SurfaceControl alpha. For CLOSE_TO_HOME, preserve `LauncherGlassReturnFade` as the opacity authority and do not multiply the same native progress twice.
- [x] **Step 5: Build the debug variant.** Run `.\gradlew.bat assembleDebug -PworkspaceGlassHwuiProbe=true`; expected result: `BUILD SUCCESSFUL`.

### Task 2: Publish and draw alpha-aware frames in sync

**Files:**

- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticLayer.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassHwuiProbeView.java` only if session frame access requires a small forwarding change.

**Interfaces:**

- Extend `StaticGeometryFrame` with immutable `float outputAlpha`; retain its geometry and Workspace scroll anchor fields.
- Add `LauncherGlassStaticLayer.invalidateHwuiProbe(View root) -> boolean`; return `true` when the active probe was invalidated, otherwise `false` so callers can retain the GL fallback.
- Change `syncWorkspacePresentation` to return the effective Workspace alpha used for the current frame while retaining its existing scene/folder policy.

- [x] **Step 1: Publish alpha-only changes.** In `syncSceneOnUiThreadInternal`, calculate each node's frame alpha after the aggregate launch-proxy ownership is known. If geometry is unchanged but alpha changes, replace that node's `StaticGeometryFrame`, publish the new scene snapshot, and mark static presentation dirty.
- [x] **Step 2: Draw the published alpha.** In `renderStaticScene` and `drawHwuiProbe`, use each published frame's `outputAlpha` rather than reading only `node.visibilityAlpha()`. Skip zero-alpha frames. Preserve live root-space geometry capture for the HWUI route and the existing backdrop coordinates.
- [x] **Step 3: Invalidate only the output owner.** When the probe is active, `requestStaticRedraw()` invalidates it and does not queue a redundant static GL render. On pre-draw geometry or alpha changes, invalidate the probe before returning from that traversal. When the probe is inactive, preserve the current GL scheduling path. If drag output also changed, keep scheduling that output independently.
- [x] **Step 4: Keep scene and ownership transitions intact.** Confirm the existing root layer still owns scene alpha, folder-freeze policy, bootstrap/fresh-frame visibility, Recents/rotation/unlock coverage, and native WindowElement frame sync. Proxy activation must not keep unrelated nodes opaque.
- [x] **Step 5: Build the debug variant.** Run `.\gradlew.bat assembleDebug -PworkspaceGlassHwuiProbe=true`; expected result: `BUILD SUCCESSFUL`.

### Task 3: Review the transition matrix on device

**Files:**

- Review: changed Workspace glass implementation files and the generated debug APK.

- [ ] **Step 1: Verify ordinary nodes.** On device, inspect icon, widget, small-folder, and large-folder glass during edit-mode enter/exit and any Workspace zoom/scale transition. Expected: bounds and corner scale follow the native node; native alpha is matched once.
- [ ] **Step 2: Verify ownership handoffs.** Inspect long-press drag, app launch, and app-to-HOME return. Expected: source glass fades/hides at the native handoff, drag/launch geometry follows the actual proxy, and ownership returns without a stale frame or duplicate glass.
- [ ] **Step 3: Verify scene gates and idle behavior.** Inspect folder return, Recents/coverage, unlock, and settled Workspace. Expected: existing show/hide timing remains; no extra frame loop runs once stable.
- [ ] **Step 4: Record build and device observations.** Report the build result and any transition that still misses the native geometry or opacity. Automated tests are not added or run in this plan.
