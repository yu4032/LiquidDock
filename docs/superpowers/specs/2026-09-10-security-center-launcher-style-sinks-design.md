# Security Center Launcher-Style Glass Sinks Design

## Goal

Replace the Security Center single union-sized glass output with Launcher-style per-node glass sinks that inherit vendor animation transforms from the real View hierarchy, while expanding custom material ownership to include TurboLayout itself.

## Problem

Two runtime defects remain after semantic compatibility was restored:

1. Native Security Center background material can remain visible below the custom glass because current custom ownership clears Dock / Toolbox / All Apps targets but does not clear TurboLayout's own MIUI blur/material state.
2. During Folme transitions, the current single `SecurityCenterGlassOutputView` is repeatedly resized and repositioned from reconstructed union geometry. Parent and child vendor transforms are not naturally inherited, so animated shapes can diverge from the actual vendor Views.

## Architecture

Keep exactly one `RootPassBlurBackend` and one `SecurityCenterGlassSession` per attached Security Center root. Replace the single union output surface with one `SecurityCenterGlassSinkView` per live material node: Dock, optional Game/Video toolbox material, and optional All Apps.

Each sink is inserted immediately behind the corresponding vendor material View in the same parent. The sink keeps a stable local size based on the material View's untransformed width/height, copies the material View's local `x/y`, pivot, scale, rotation, alpha and visibility every pre-draw, and therefore inherits any ancestor Folme transforms automatically from the View hierarchy. No custom animation curve, fixed delay, debounce or frame cap is introduced.

`SecurityCenterGlassSession` continues to prepare the shared root backdrop once per fresh source frame. It renders each node geometry into its own sink EGL surface using that sink's root-relative geometry. Node surfaces are consumers only; they do not create additional PassBlur producers.

## Material Ownership

When a fresh custom frame is authorized, `SecurityCenterVendorMaterialBridge.claimCustom(...)` must clear vendor material from:

- TurboLayout itself;
- Dock material View;
- optional Game/Video material View;
- optional All Apps material View.

TurboLayout state is restored through the existing validated `finalBackground()` vendor method. Game and Video restoration continue through their existing validated vendor restore methods. Saved drawable restoration remains limited to targets whose drawable is explicitly captured before mutation.

## Animation Authority

The vendor View tree is the only animation authority. Security Center glass must not recreate Folme spring/ease parameters. On every relevant pre-draw:

- sink local size remains tied to the material View's base width/height rather than animated screen bounds;
- sink copies local transform properties from its material peer;
- ancestor transforms are inherited because sink and material share the same parent;
- root-relative geometry is recaptured for rendering only when source/crop geometry changes, not by resizing the TextureView for every transformed frame.

This mirrors the existing Launcher sink strategy rather than hooking MIUIX animation internals.

## Lifecycle and Freshness

The existing scene-generation/fresh-frame gate remains authoritative. A sink is revealed only after the current generation has a fresh rendered frame and custom ownership succeeds. During Dock ↔ All Apps transition, already-authorized custom ownership remains active; new synchronous vendor nodes are stripped immediately and receive sinks without falling back to native material.

Closing through validated `d2`/`f2`, root detach, runtime disable or terminal producer failure disposes every sink, releases their EGL window surfaces, restores vendor material, and shuts down the one shared session.

## Constraints

- Preserve root SurfaceControl → SetPassBlurSurface → SurfaceTexture/external OES → normalization → Prismal zero-copy path.
- No `ScreenCapture`, `Bitmap`, `PixelCopy`, CPU screenshot path, fixed delay, stale fallback or second PassBlur producer.
- Do not hook Folme/MIUIX animation implementation classes.
- Keep compatibility discovery fail-closed and do not reintroduce version-specific obfuscated class literals.
- Device-side ADB verification remains waived by user; CI and source/decompile contract verification are required.

## Tests

Add source-contract RED tests that fail on the current implementation and require:

1. TurboLayout itself is reset/cleared in `claimCustom` before the custom layer is revealed.
2. Security Center uses per-node sink outputs, and each sink mirrors `x/y`, pivot, scale, rotation, alpha and visibility from its material peer.
3. Animation sync does not resize sink `LayoutParams` from transformed/global bounds on every frame.
4. `SecurityCenterGlassSession` still owns exactly one `RootPassBlurBackend` and supports multiple sink output surfaces.
5. Existing semantic compatibility, generation freshness and zero-copy contract tests remain green.
