# Security Center Material-Lifetime Glass Design

## Goal

Replace Security Center's native advanced-material presentation with LiquidDock glass by inheriting the lifetime of the actual vendor material carrier Views instead of maintaining a parallel page/sidebar lifecycle.

## Reverse-engineered authority model

The OS4 Security Center implementation has one reusable `TurboLayout` host, but the hosted material subtree is rebuilt on each panel presentation:

- `ob.e0` reuses the wrapper/TurboLayout, calls `TurboLayout.removeAllViews()`, invokes `TurboLayout.V(...)`, then `c0()` / `a0(...)` to rebuild panel children.
- Dock material carrier: `com.miui.gamebooster.windowmanager.newbox.o0` (`TurboLayout.getDockLayout()`).
- Game material carrier: `com.miui.gamebooster.windowmanager.newbox.y1` (`TurboLayout.getGameTurboLayout().getMainView()`). `y1` itself owns the advanced background material.
- Game internal pages are children of `y1`; `z1` translates the main/second/third content containers. These transitions are content-only and must not create glass scene transitions.
- All Apps material carrier: `com.miui.dock.allapps.w` (`TurboLayout.getAppsLayout()`). It is dynamically added to/removed from the same `TurboLayout` and owns its own advanced material.

Therefore the correct lifetime boundary is the identity/attach lifetime of the actual material carrier View, not `TurboLayout` identity, Activity identity, sidebar show/hide callbacks, or inferred page scenes.

## Architecture

Keep one root-wide `SecurityCenterGlassSession` and the existing zero-copy `RootPassBlurBackend`. The producer/session survives material-subtree replacement as long as the Android root remains valid.

Introduce a material-subtree epoch at every resolved `TurboLayout.V(...)` configure call. The epoch refresh resolves the current Dock and optional Game/Video material carriers after the vendor has rebuilt them. Replacing a carrier disposes only that carrier's old sink and creates a sink adjacent to the new carrier.

Each carrier is independent:

- Dock carrier attach -> Dock sink exists.
- Game/Video carrier attach -> Box sink exists.
- All Apps `w` attach -> All Apps sink exists.
- Carrier detach/removal/replacement -> only its sink is retired.

Vendor material suppression is re-applied against the currently live carriers after a current-frame presentation acknowledgment. A new material subtree always forces a fresh handoff even if the root session and `TurboLayout` objects are reused.

## Lifecycle rules

1. `TurboLayout.V(...)` starts a new subtree epoch. It does not tear down the root PassBlur session.
2. The post-configure readiness hook resolves current Dock/Game/Video carriers and calls the coordinator with the new epoch/carriers.
3. A carrier identity change invalidates the current presentation handoff for that carrier set, disposes stale sinks, and waits for a fresh frame on the new sinks.
4. `com.miui.dock.allapps.w` lifetime is driven only by its actual attach/remove lifecycle. No synthetic `TRANSITIONING`, fixed settle delay, or inferred target scene is required.
5. `z1` game page transitions are ignored by glass lifecycle. The `y1` shell remains the fixed glass material owner while inner containers translate.
6. Root detach/runtime disable remains the authority for full session teardown.
7. Vendor sidebar show/hide/terminal hooks are observational only unless they coincide with actual material/root detach.

## Presentation composition

A frame contains every currently attached, presentation-ready material carrier that should be visible:

- Dock is required.
- Box is included when the current Game/Video material carrier is attached and visible.
- All Apps is included when its material carrier is attached and visible.

For Game + All Apps, the Game material carrier may still be visible while vendor content animates out. Glass composition follows the vendor material carriers themselves; do not use internal page-container translation to move the Game glass shell.

## Geometry authority

Each `SecurityCenterGlassSinkView` mirrors its own material carrier only. It may mirror the carrier's geometry/alpha/scale, but never the transforms of nested Game page containers. Existing root-space geometry capture remains valid.

## Vendor material ownership

`SecurityCenterVendorMaterialState` remains the stable View-API interception layer. Claims are associated with current carrier View identities under the stable TurboLayout owner. On subtree replacement, stale carriers are restored/released and new carriers are claimed after their first current-frame presentation acknowledgment.

## PassBlur boundary

This refactor does not change:

- the root-wide `RootPassBlurBackend`;
- the continuous frame request pipeline;
- the explicit Security Center PassBlur output-surface authority;
- the current full-size caller-owned producer geometry.

Do not reintroduce Security Center's vendor-owned `0.25` scale onto LiquidDock's full-size producer.

## Error handling

Fail closed per carrier. If a required Dock carrier or sink is not ready, keep vendor material visible and do not claim custom ownership for that subtree. If an optional Box/All Apps carrier is absent, compose the remaining valid carriers. Full session teardown is reserved for root detach, runtime disable, or terminal source/render failure.

## Device-validation boundary

This is an agile device-validation branch. CI continues to build the debug APK and retain the zero-copy audit; stale unit tests for the previous scene-based architecture are not authoritative until the device path is accepted.

Success criteria:

- first Game pull: fixed-position Game glass shell, no page-induced glass displacement;
- first All Apps open: Dock and All Apps remain live; no shared freeze;
- close/reopen Game Toolbox: new `o0/y1` carriers receive new sinks and custom replacement on the second pull;
- repeated All Apps open/close: `w` attach/remove repeatedly creates/retires only the All Apps sink;
- no fixed-delay or polling workaround is introduced.
