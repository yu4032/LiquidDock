# Launcher 4.50 Dock Transition, Prismal Boundary, and Widget Controls Plan

## Scope

Implement the device-reported follow-up fixes on `fix/450-workstation-stroke-widget-backgrounds`, using the saved HyperOS Launcher 4.50 JADX output as the behavioral authority.

### 1. Workstation -> normal stroke transition

Launcher 4.50 drives `HotSeatsListContentBlurBackground2.mCornerRadius` with `mViewRadiusAnimator` during `updateBackgroundSize(...)`. LiquidDock currently publishes `workstationMode=false` before the vendor `LaptopStateManager.onLaptopModeChanged(false)` body runs, so 307 geometry hooks can treat transitional radius callbacks as ordinary-mode geometry. The existing `MainHook.animating(View)` guard calls a nonexistent `HotSeatsListContentBlurBackground2.isAnimating()` and therefore always falls through to `false` on 4.50.

Implementation:

- Add an explicit Dock visual transition state independent of the logical workstation flag.
- Entering workstation immediately retires ordinary/native stroke ownership.
- Leaving workstation enters `EXITING_WORKSTATION`; transitional vendor radius callbacks may update the glass body but may not commit a new custom stroke radius.
- Observe the real Launcher 4.50 `mViewRadiusAnimator` / `animatorSet` state. Never use a guessed radius or fixed millisecond delay.
- Bind settlement to a transition generation so a canceled/replaced animation cannot complete an older transition.
- When the current radius animation truly settles, read the final vendor `mCornerRadius`, leave the transition state, and commit the final Prismal-host stroke once.
- Replace the dead `isAnimating()` reflection with the actual 4.50 animator fields for legacy/non-307 guards too.

### 2. Prismal as the single visible Dock boundary

Commit `35939d6` correctly removed the second Android `dispatchDraw()` clip because it multiplied two independent antialiasing masks and caused the broken bottom highlight. The follow-up black corner arcs are caused by the remaining geometry mismatch: `DockShapePath` uses a `.5px` inset while Prismal's SDF zero contour uses the full glass extent.

Implementation:

- Keep `DockLiquidGlassHostView.dispatchDraw()` unclipped.
- Keep `DockShapePath` unchanged for stroke/pixel-center consumers.
- Add a dedicated Prismal outer-boundary path for View outline/native shadow geometry using `[0,width] x [0,height]` and the same radius basis as Prismal.
- Make `DockLiquidGlassHostView` use that path only for its outline/shadow.
- Do not add a second alpha mask or clip the TextureView.

### 3. User-selectable widget background controls

Keep the bundled rule engine as safe defaults, but stop requiring new hard-coded product rules for future widgets.

Discovery transport:

- Add an API101 Remote Preferences group dedicated to widget discovery. It is written by the injected Launcher process and read by the settings app; it is not mirrored into the normal `config` group.
- MAML discovery uses Launcher 4.50 `ScreenElementRoot.mElements` and records actual `elementName:type` values together with widget identity.
- RemoteViews discovery only records stable, named resource-ID Views that currently own a non-null background; it does not expose arbitrary reflection actions or anonymous descendants.

User configuration:

- Add a `Widget background hiding` subpage under Liquid Glass.
- Show discovered widget identities and their discovered controls/elements.
- Allow per-element selection. Persist selections in normal config as deterministic user rules.
- Add a separate switch for bundled/default rules.
- Exact user selections outrank bundled rules.
- MAML selections hide via `ScreenElement.show(false)` while preserving/restoring original `mShow`.
- RemoteViews selections clear only the exact selected View background while preserving/restoring the original Drawable.
- Unknown/missing targets fail closed and do not partially mutate a widget.

## TDD / verification sequence

1. Add source-contract regression tests for Launcher 4.50 exit animation ownership and Prismal boundary geometry.
2. Add pure codec/matching tests for user widget selections and discovery snapshots.
3. Push tests first and confirm PR CI fails in `testDebugUnitTest`.
4. Implement transition state and real animator settlement.
5. Implement dedicated Prismal outline boundary.
6. Implement discovery store, user-selection resolver, MAML/RemoteViews executors, config schema, and Compose subpage.
7. Run the complete unit suite and `assembleDebug` through PR CI.
8. Inspect the generated API101 debug artifact and update PR #83 with the new root cause and verification evidence.

No merge to `main` is part of this plan.