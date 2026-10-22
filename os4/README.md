# LiquidDock4 / HyperOS 4 — controlled rewrite

This branch intentionally does **not** incorporate the research repository's
`port-source/` or ship its native AOT hooks. It uses
`yu4032/liquiddock-os4-research` strictly as observations and test evidence.
Previous prototype code has been removed from this branch's file tree and
history; no broad copy remains.

## Baseline and isolation

- Based on LiquidDock `main` commit `c0ffef235`.
- Active target: Xiaomi 13 (`fuxi`) on Android 17 / HyperOS
  `OS4.0.27.0.XEOCNXM`, with `com.miui.home`
  `RELEASE-8.01.02.8583-260918-10161026-R`.
- The exact target ELF hashes and tablet-mode reproduction are recorded in
  [`docs/os4-device-contract-fuxi-8583.md`](../docs/os4-device-contract-fuxi-8583.md).
- Original Java Launcher 4.50 path is unchanged.
- `Os4LauncherIdentityPolicy` is the first conservative eligibility gate;
  its positive result **cannot** authorize native hooks by itself.
- The legacy Launcher 4.50 Java backend is bypassed for every Android 16+ AOT
  Launcher, including unknown builds. OS4 settings pages remain available, but
  unsupported native capabilities fail closed.
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

## Offline target attestation

`os4/tools/attest_elf.py` is a new, source-independent verifier for a
**locally extracted** ARM64 `libapp.so`. It refuses mismatched full ELF hashes,
unmapped or non-executable target ranges, and mismatched **whole-function**
SHA-256 fingerprints. It deliberately does not generate or guess a trust
profile: a target profile must be independently established from the exact
vendor build. No fingerprints are promoted from research notes without
reverification. Passing disk attestation is necessary but not sufficient for
runtime load-bias verification and calling-convention proofs.

Example format (values intentionally omitted):

```json
{
  "elf_sha256": "<exact 64-hex SHA-256>",
  "targets": [
    {
      "name": "independently-verified-function",
      "vaddr": "0x...",
      "size": 128,
      "sha256": "<exact whole-function SHA-256>"
    }
  ]
}
```

## Test

```sh
cc -std=c11 -D_GNU_SOURCE -Wall -Wextra -Werror -Ios4/native \
   os4/native/process_gate.c os4/tests/process_gate_test.c \
   -o /tmp/os4-process-gate-test
/tmp/os4-process-gate-test
python3 -m unittest discover -s os4/tests -p 'test_*.py' -v
./gradlew testDebugUnitTest assembleDebug
```

Optional Android NDK read-only bootstrap build:

```sh
ANDROID_NDK_HOME=/path/to/android-ndk bash os4/native/build.sh
```

This does **not** produce an installable OS4 release APK yet. No research
binary, device secrets, or private debug artifacts are committed.
