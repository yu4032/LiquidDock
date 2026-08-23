# Glass Scene Architecture — Implementation Status

Date: 2026-08-23
Applies to: `feat/folder-press-glow`
Status: implementation materialized; clean CI and hardware validation remain the release gates.

This note records the implementation correction discovered during device validation and supersedes conflicting operational details in `2026-08-23-glass-scene-architecture-design.md`.

## Dock: main lifecycle is authoritative

The existing `main` Dock zero-copy pipeline is the proven realtime baseline and must not be replaced by a generic scene producer lifecycle.

Keep these `main` behaviors unchanged:

- one `Miuix307PassBlurTextureView` owned as a child of the live vendor Dock material;
- one Dock EGL output and one output swap;
- `Miuix307PassBlurBridge.bind()` enables PassBlur continuously with `setUpdateTextureFlag(..., TRUE, scale)` once at binding time;
- incoming OES frames drive rendering through `OnFrameAvailableListener`;
- no per-vsync `SurfaceControl.Transaction` producer pump;
- Dock translation, scale, reveal/hide and other whole-Dock motion are inherited from the native parent hierarchy rather than mirrored through Workspace scene geometry;
- existing buffer/config rotation mapping remains unchanged;
- rebind/recreation is used only at the existing proven lifecycle boundaries such as root-surface identity loss/replacement; ordinary geometry refresh does not invent a new producer lifetime.

Scheme A extends this pipeline only at the Prismal draw stage:

```text
main continuous PassBlur/OES producer
        ↓
normalize once for the current source frame
        ↓
prepareBackdrop
        ↓
beginGlassFrame
        ↓
draw Dock body
draw Dock-local icon nodes
        ↓
outputTexture
        ↓
one Dock composite / one TextureView / one EGL swap
```

Dock icon geometry is Dock-local. A Dock icon must never be a Workspace static node. Increasing Dock icon count must not create additional TextureViews, Surfaces, EGLSurfaces or PassBlur producers.

## Workspace and Recents ownership

Workspace remains the Scheme A caller-managed domain. Icons, widgets, small folders and large folders are eligible only when their actual ancestry classifies them as `WORKSPACE`.

The classifier is mutually exclusive:

- `WORKSPACE` → Workspace static compositor;
- `DOCK` → Dock-local item compositor;
- `OTHER` → no static glass ownership.

Recents/Overview is Workspace coverage. Reused Launcher `FolderIcon` or `ShortcutIcon` views in Recents must not retain Workspace or Dock glass merely because their concrete Java class matches a hooked class.

## Runtime glass disable and power contract

`liquid_glass=false` is a runtime resource boundary, not just a visual visibility flag. A true→false transition must:

- remove Workspace attach/pre-draw observers;
- release the drag output;
- shut down all `LauncherGlassSession` instances and their EGL threads;
- unbind Dock PassBlur and shut down the Dock EGL thread;
- remove the injected Dock glass host;
- restore vendor Dock/folder material drawables that were made transparent while LiquidDock owned them;
- clear Dock item registrations;
- stop pending folder recovery;
- prevent constructor/attach hooks from recreating observers or GPU resources while glass remains disabled.

No LiquidDock glass path may use PixelCopy, ImageReader, bitmap wallpaper capture/readback, `glReadPixels`, MediaProjection, or screen recording.

A separate distinction is intentional: disabling Liquid Glass does not disable unrelated LiquidDock features such as grid changes or non-glass Dock customization. Therefore device power diagnosis must compare both runtime glass teardown and a fresh Launcher start with glass disabled before attributing any remaining load to the glass architecture.

## Verification state

The dynamically patched implementation was reproduced twice with 384 unit tests passing and `assembleDebug` succeeding, then materialized directly onto the feature branch. All temporary glass patch/prep scripts were removed and the standard `api101-build.yml` workflow restored.

The final completion gate is a fresh standard CI run from the materialized source, followed by target-device checks for Dock realtime sampling, Dock-local item alignment, Recents isolation, runtime-off power, fresh-start power with glass disabled, startup freshness, rotation/root-surface recovery, and drag/folder/widget/icon behavior.
