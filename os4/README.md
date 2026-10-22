# LiquidDock4 / HyperOS 4 — controlled rewrite

This branch intentionally does **not** incorporate the research repository's
`port-source/` or ship its native AOT hooks. It uses
`yu4032/liquiddock-os4-research` strictly as observations and test evidence.
Previous prototype code has been removed from this branch's file tree and
history; no broad copy remains.

## Baseline and isolation

- Based on LiquidDock `main` commit `c0ffef235`.
- Android 16 / OS4 AOT Launcher under investigation:
  `com.miui.home` `RELEASE-8.01.02.7751-260904-10100945-R`.
- Original Java Launcher 4.50 path is unchanged.
- `Os4LauncherIdentityPolicy` is the first conservative eligibility gate;
  its positive result **cannot** authorize native hooks by itself.
- `os4/native` builds a standalone **read-only** native injection bootstrap.
  It neither hooks functions nor writes Launcher state.
- No device-mode spoofing, root configuration publishing, or launcher DB rewrites
  have been adopted. OS4 capability activation is postponed until compatibility
  and rollback checks exist.

## Rejected approaches from the research history

1. Treating the minimum `MiuiHome.apk` memory mapping as the Dart ELF load
   base, or using `mapping start - file offset` for every PT_LOAD.
2. Trusting one AArch64 prologue word or a symbol name as proof of binary
   compatibility; the eventual target manifest must match the entire ELF digest
   and sufficiently long, validated function signatures.
3. Calling native trampolines through ordinary AAPCS64 wrappers without
   preserving Dart's x15 stack and caller-owned argument window.
4. Changing `_usesNoVacantMode` or global occupancy to simulate free placement.
5. Running experimental device spoofing from `native_init` before process and
   feature gating.
6. Publishing root configuration across mismatched mount namespaces or keeping
   stale early-boot configuration when main configuration is invalid.
7. Mutating `launcher*.db` merely because a new grid size was selected. Any
   migration must have explicit dry-run plans, backup, schema guards, transaction
   rollback, and verified timing.
8. Claiming glass is complete from hit counts on five widget-material gates.
   Dock glass needs final-pixel verification and shader/UBO identity checks.

## Implementation order

1. **Platform contract**: derive and independently test the OS4 launcher
   identity, ELF build hashes, ZIP entry mappings, and Rust/Dart calling
   conventions using real target binaries. Reject mismatches.
2. **Native injection**: confirm the read-only bootstrap on the target device;
   add minimal, individually validated hook points with a native kill switch.
3. **Configuration authority**: design a versioned, atomic, validated config
   snapshot and native reader. Verify source ownership and mount namespaces;
   no shell interpolation of user values.
4. **Grid**: port LiquidDock's layout invariants from its real Java policy and
   prove the dry-run planner, rotation state, overflow behavior, and database
   transaction in isolation before enabling modifications.
5. **Dock/glass**: reuse OS4's native compositor where possible. Develop
   optical parameter mapping only against shader fingerprints and pixel A/B
   evidence; no broad Vulkan interception as a first step.
6. **Feature parity**: folder, widgets, animations, Recents and settings one
   capability at a time; preserve stock presentation when unsupported.

## Test

```sh
cc -std=c11 -D_GNU_SOURCE -Wall -Wextra -Werror -Ios4/native \
   os4/native/process_gate.c os4/tests/process_gate_test.c \
   -o /tmp/os4-process-gate-test
/tmp/os4-process-gate-test
./gradlew testDebugUnitTest assembleDebug
```

Optional Android NDK read-only bootstrap build:

```sh
ANDROID_NDK_HOME=/path/to/android-ndk bash os4/native/build.sh
```

This does **not** produce an installable OS4 release APK yet. No research
binary, device secrets, or private debug artifacts are committed.
