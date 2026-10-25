# Component corner radius options

## Behavior

Dialog, shortcut-popup and app-caption handle-menu pages each expose an independent
radius slider. Missing values default to -1dp (OEM geometry); 0dp is square; positive
values use tenth-dp storage up to 160dp. Radii fit half the shortest component side.
The continuous-corner switch changes the curve independently of this radius.
Reopening a component applies its saved radius. Existing colors, blur, dimensions,
content layout and capture behavior are preserved.

## Implementation

Three IF_PRESENT schema keys feed the component configuration. A pure resolver
handles automatic fallback, density and size bounds. Dialog and shortcut overrides
resolve before their existing animation transforms. Dialog radius is read at actual
attachment because Launcher may preload dialog objects before settings change.
Handle-menu rendering uses the current output size and supports live updates.

HyperOS also clips the caption-menu SurfaceControl: a device dump showed 74.5px
rounding for a 758x147 menu. Capture its native radius from the observed addWindow
arguments (height/(extended mode ? 4 : 2)+1). Once Prismal presents, the custom
radius updates that exact container as well as the shader. An owned lease restores
the exact native radius on automatic mode, fallback or release, without changing
window crop, position, scale or animation. Default mode issues no override.

## Verification

- 862 JVM tests pass: 805 application and 57 Prismal; zero failures/errors/skips.
- Tests cover native defaults, independent fractional settings, malformed inputs,
  density conversion, square corners, bounds, scoped inheritance and live routing.
- Surface lease tests cover compositor writes, restoration, duplicate suppression
  and failed-write ownership.
- Debug APK builds; existing real-GLES corner checks pass.
- Independent reviews found no remaining issue.
- ADB installation succeeded on turner; SystemUI restarted for the compositor fix.
  The user confirmed that the corrected menu radius works.
