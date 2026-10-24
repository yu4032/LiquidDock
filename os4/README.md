# LiquidDock4 / HyperOS 4 native layout

The active device is Xiaomi 13 (`fuxi`), Android 17 / HyperOS
`OS4.0.27.0.XEOCNXM`, Launcher `RELEASE-8.01.02.8583-260918-10161026-R`.
Launcher runs under `hyos_spawner` without ART; Java layout drafts are not the
runtime implementation. The legacy Launcher Java backend remains separate.

## Layout behavior

- Portrait and landscape remember independent HOME coordinates and share item
  identity, size and page membership. Square grids use the actual orientation
  enum rather than inferring direction from row/column counts.
- Snapshots are scoped to the configured grid. Changing grids archives both
  directions and restores that grid's saved profile when selected again.
- Restore the complete ItemInfo DTO batch before classification, then reuse the
  vendor's existing orientation transaction, occupancy rebuild and database save.
  No active Launcher SQLite connection or direct database rewrite is used.
- Capture drag, squeeze, resize, folder transitions and deletion at the native
  serialization boundary. Startup writes cannot replace saved page membership
  before the DTO load has been prepared.
- Widgets can start on odd rows/columns. The rectangular vendor drop rule retains
  its automatic squeeze, bounds and collision handling; only its lattice parity
  predicate is bypassed for custom HOME grids.
- OS4 settings temporarily bypass widget preflight, allowing configurations below
  four rows/columns. Footprints must still fit the actual grid.
- The Rust pre-load orientation conversion assumes stock geometry and can panic
  with `index out of bounds: len is 3 but index is 3` on a three-column restart.
  The attested Rust guard delegates this conversion to the DTO planner only when
  the complete native layout pipeline is installed; otherwise it calls original.

## Main-screen options

The attested backend also applies the existing icon-size percentage, widget
horizontal stretch and independent full-screen portrait/landscape horizontal,
top, bottom, row-gap and indicator-Y settings. Fractional offsets use Flutter
logical dp; preference `_tenths` values take precedence. Restart Launcher after
changing these settings. Split-layout tuning is not connected in this stage.

Icon sizing works independently of custom grid and affects the shared icon
record consumed by the workspace, Dock and folders. Geometry is applied once
when each fresh grid configuration is computed. Bottom distance and row gap
change workspace row stride while preserving the shared inset used by Dock.
Impossible viewports retain the complete original geometry.

With widget stretch disabled, native widget content keeps its original width
and stays centered. Enabled multi-column widgets fill their allocated grid
width; one-column widgets retain native sizing. A bounded cache stores only
primitive before/after dimensions for each configuration variant. The widget
hook reuses the vendor's existing Double allocation path and GC stack root.

Indicator Y changes the fresh Transform's Matrix4 translation, including its
hit testing. The earlier scale-origin Offset does not move an identity scale
and is left untouched. All these geometry adjustments reject split configs.

## OS4 native glass controls

The OS4 liquid-glass page exposes 39 native material controls, each independently
switchable between inherited and customized values. Every control supports a
slider, decimal input and restoration to the native value. Restart Launcher to
apply changes. No optical preset is forced when the override bundle is empty.

Controls cover edge width/falloff, thickness, reflection offset, IOR, light XYZ,
main/opposite intensity and angular range, edge lighting, reflection strength and
lighten, inner bottom glow, RGBA, opacity, tint blend, gamma, independent refract
and background blur, darkening and its luminance range, foreground/background
saturation and brightness, inner color mix/white, luminance amount and four
Bezier control points, and burn. Blur controls remain separate because changing
both original blur inputs to one value changes the native backdrop appearance.
Shader-internal fixed light width and refract-blur constants are not exposed.

Only explicit overrides are serialized as `glass_native_<field>=<decimal>` lines.
The native parser validates names, ranges and duplicates. The app stores the
sparse bundle in the exported `liquid_os4_native_overrides` preference; old
Prismal/blur preferences do not activate native overrides.

There is no shader replacement or custom backdrop capture. A full-body-attested
`_ImageFilter.__initGlassV7` observer runs immediately after native construction.
It identifies the newly allocated C++ `DlGlassImageFilter` via the saved native
receiver and verified vtable, then changes selected float fields and owned vector
components before the filter is cached or drawn. Dart heap objects and HDR/
geometry/capture-coordinate fields are untouched. The exact engine file SHA-256
and live V7 dispatcher body must match before any material write. Unknown engines,
invalid vectors or invalid light directions preserve the original material.

The earlier capability hooks still opt into the native material on the supported
8583 Launcher build. Native boot logs report engine verification and how many
explicit fields were applied. Visual acceptance of individual controls remains
part of device testing.

## Native safety and compatibility

Every hook verifies its complete function body against the exact local vendor
ELF. ZIP entry mappings and PT_LOAD ranges determine runtime addresses; mapping
verification accepts adjacent executable VMAs created by earlier hooks. Unknown
bodies are refused. The entire layout pipeline must be available before layout
mutation or Rust conversion delegation is enabled.

Successful inline hooks pin their patch pages with `mlock`. File-backed private
code-page discard can restore the APK bytes and silently remove every hook;
this was observed in a long-running process whose rotation callbacks stopped.
The original layout hooks occupy 40 KiB on the tested 4 KiB-page device; the
main-screen option hooks add three patch sites. A pin failure
also disables custom counts and placement, retaining a coherent stock pipeline.
The regression harness reproduces patch loss under `MADV_DONTNEED`, then verifies
that locked pages retain the patch across a page boundary. The original caller
that cleared the live process's pages has not been identified.

Dart observers preserve all integer/vector registers, flags, Dart x15 and native
SP. They only write verified numeric fields and never call Dart from C or retain
heap pointers beyond the callback. Rust uses an ordinary AAPCS64 wrapper.

The versioned `liquiddock-layout-v3.bin` sidecar uses checksums, validation and
atomic fsync/rename. Impossible same-page placement does not publish a partial
layout. The planner first preserves remembered positions, then retries packing
when a new footprint needs space. It does not invent pages during rotation.

## Reproduce and validate

No vendor APK/SO or private device dump belongs in the repository. With locally
extracted vendor files:

```sh
python3 os4/tools/generate_layout_fingerprints.py /path/to/lib/arm64-v8a
python3 os4/tools/attest_elf.py /path/to/libapp.so os4/profiles/fuxi-8.01.02.8583.json
python3 os4/tests/test_attest_elf.py
cc -std=c11 -D_GNU_SOURCE -Wall -Wextra -Werror -Ios4/native \
  os4/native/process_gate.c os4/tests/process_gate_test.c -o /tmp/ld-process-test
/tmp/ld-process-test
cc -std=c11 -D_GNU_SOURCE -Wall -Wextra -Werror -Ios4/native \
  os4/tests/grid_conf_test.c -o /tmp/ld-grid-test
/tmp/ld-grid-test
for t in layout_memory layout_changes layout_load executable_range layout_runtime hook_page_pin; do
  cc -std=c11 -D_GNU_SOURCE -Wall -Wextra -Werror os4/tests/${t}_test.c -o /tmp/ld-test
  /tmp/ld-test
done
ANDROID_NDK_HOME=/path/to/ndk bash os4/native/build.sh
./gradlew testDebugUnitTest assembleDebug
```

`dart_observer_device_test.c`, `free_place_device_test.c` and
`widget_lattice_device_test.c` are ARM64 executable harnesses for the actual
assembly wrappers. Compile them with the NDK and run them on the device.

Use `os4/tools/package_native.py` to insert the SO and native_init entry into the
Gradle APK, then zipalign to 16 KiB and sign with alignment preservation enabled.
Verify both the final APK signature and its final ZIP alignment after signing.

Device acceptance also checks every visible page item and its on-screen bounds;
database positions alone cannot detect clipped or stale UI after a lost hook.
Device acceptance compares item IDs, pages, coordinates and spans across
rotation, Launcher restart and full reboot. A successful build or Java test is
not a device acceptance result. Occasional blank stock widgets on page one are
retained at the user's request.

The integrated audit retains CE configuration authority after unlock, whole-ELF
SHA-256 attestation for both Dart and Rust entries before hooking, process gating
before diagnostics, serialized hook installation, and atomic configuration
publication. Sidecar profile switching occurs only after the native layout
pipeline has been verified and installed. The obsolete direct SQLite repack is
not part of the active layout path.

## Icon and MAML widget materials

The OS4 component pages expose native glass switches for desktop/Dock app icons
and loaded MAML widgets. Icon background size and corner radius are configurable;
zero radius inherits the vendor shape. Restart Launcher after changes.

The widget component page performs discovery only when manually requested. Native
widget initialization records loaded source archive identities without traversing
XML. Manual discovery lists render nodes with exact source-tree selectors. Selected
nodes are hidden in immutable shadow archives; the original widget resources are
never modified. Clearing selections or disabling glass restores original content
on the next Launcher load. Component hiding works independently of glass.

Widget glass uses the vendor MaterialToken/GlassConfig with rustPassWindow enabled.
It preserves native background capture and does not replace shaders. Only loaded
MAML archives are supported in this stage; RemoteViews component handling and icon
transition animation coverage are not included. Native code and SDK fingerprints
must match the attested Launcher build before hooks are installed.

Dock bottom offset adjusts the native hotseat bottom reference in logical dp:
positive values move Dock upward, negative values move it downward, and zero
retains the vendor placement. Background, icons and hit regions move together;
the page indicator follows the vendor's same bottom reference. HOME grid
coordinates and stored layout profiles remain unchanged.

Manual MAML discovery also records source-tree render order and depth. Area
ratios are calculated only when XML dimensions and aliases can be resolved;
runtime-dependent dimensions and unavailable Z values remain unknown. Existing
background ranking uses these fields without changing exact node selectors.

Shadow archive publication assigns the directory and files to the Launcher UID
and restores their SELinux contexts before publishing the mapping. Upgrading
invalidates the prior publish cache so existing root-owned directories are
repaired automatically. Read or hash failures retain the original resource and
are recorded in the native boot log.
