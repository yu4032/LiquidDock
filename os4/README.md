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

## OS4 liquid glass phase 1 — native material gate

The existing `liquid_glass` preference is now published as the optional
`glass_enabled=1` in the same CE/DE launcher snapshot. This works without
enabling custom grids, dock geometry or icon sizing. With the preference off
the module leaves vendor material capability checks entirely untouched.
On enable, a Launcher restart is required.

The first stage hooks only the Dart capability predicates
`BlurUtilities.isGlassMaterialSupported`,
`BlurUtilities.isHyperMaterialSupported`, and
`BlurUtilities.isMaterialDisabled`. Unlike the archived research prototype,
the other reactive glass predicates are deliberately not overridden because
constant returns could discard Rx dependency registration.

The 8583 AOT symbols are located in the XZ-compressed `.gnu_debugdata`
mini ELF, **not in the outer ELF's `.symtab`**. The LiquidDock settings
process extracts the three uniquely named functions from the installed
vendor APK using XZ for Java, and publishes `glass_targets` in the existing
atomic native snapshot. The Launcher startup path never runs XZ decoding.
Whole-ELF SHA-256 must first match the recorded fuxi profile; the entire
runtime function must match the mapped, immutable vendor ELF bytes, and its
entry must match the Dart ABI before inline hooking. An absent/ambiguous
symbol or changed code disables the override. All three hooks must install
and mlock-pin their code pages before the override flag becomes active.
Until then wrappers tail-call the vendor functions unchanged.

After installing, open LiquidDock settings (or toggle liquid_glass) so the
module process publishes the new `glass_targets` field, then restart
Launcher. No legacy constant addresses are used.

Device log markers: `OS4 glass gate enabled`,
`OS4 glass gate unavailable`, and `OS4 glass gate calls=`.
This phase only unlocks the **vendor's native glass**. It does not yet install
the Prismal meniscus, refraction, chromatic dispersion or optical shader,
and must not be described as the completed LiquidDock glass port. A full
end-to-end device visual / hit-region check is still required.

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
