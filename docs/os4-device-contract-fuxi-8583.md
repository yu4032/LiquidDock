# OS4 device contract — Xiaomi 13 (`fuxi`)

Recorded: 2026-10-22

## Identity

| Field | Value |
|---|---|
| Product | `fuxi` |
| Model | `2211133C` |
| Android SDK | `37` |
| HyperOS | `OS4.0.27.0.XEOCNXM` |
| Build fingerprint | `Xiaomi/fuxi/fuxi:17/CP2A.260605.016/OS4.0.27.0.XEOCNXM:user/release-keys` |
| Launcher package | `com.miui.home` |
| Launcher version | `RELEASE-8.01.02.8583-260918-10161026-R` |
| Launcher version code | `801028583` |
| Launcher code | AOT, no `FLAG_HAS_CODE`, no `classes*.dex` |
| ABI | `arm64-v8a` |

## Vendor binary hashes

Extracted from `/product/priv-app/MiuiHome/MiuiHome.apk` on the target device:

```text
MiuiHome.apk
  size   58,061,613
  sha256 62277B6C54E84E0FAE6375B69BF47C02E327CCAC8E18B97102684D8522AAFB83

lib/arm64-v8a/libapp.so
  size   29,983,600
  sha256 6F5274F435CC07AAB165CE5315D1D35AFAB559A659AFD6200379D309C9D08B91

lib/arm64-v8a/libapp_launcher.so
  size   16,242,592
  sha256 F70A2D15505ABC83BBC2F03CBE9E90B635A8ADD6D89AE0FEF764100AEA1C6D8B

lib/arm64-v8a/libresources_frb.so
  size    1,451,664
  sha256 DE94B5B4D3A47A9C1BB9D809D95FF77CFBDF94CCD9A8B17F2FE06AB2283C193C

lib/arm64-v8a/libnative_widget_sdk.so
  size    2,689,096
  sha256 0D939D8AC09C3F42EBA34E0647C3DF8951D453A1BC30B3048B852E3F71C781C9
```

These hashes identify a local vendor build. The binaries themselves are not
committed. Native targeting must refuse this profile when the installed ELF
hash differs.

## Tablet mode

The device is a phone whose stock value is:

```text
ro.build.characteristics=nosdcard
```

The reversible `os4pad` KernelSU module sets the property in `post-fs-data`:

```sh
resetprop ro.build.characteristics tablet
```

After a full reboot:

```text
ro.build.characteristics=tablet
sys.boot_completed=1
com.miui.home PID remains alive
current focus is com.miui.home/.launcher.Launcher
```

The launcher then creates/uses `launcher_rotatable4x6.db`, and the rendered home
screen uses the PAD Dock structure on the phone display.

This spoof is debug infrastructure, not a production LiquidDock capability.
It must remain a separate KernelSU module and must never be performed from
`native_init`.

## Rollback

The module directory is:

```text
/data/adb/modules/os4pad
```

Disable or remove it and reboot to return to `ro.build.characteristics=nosdcard`.
Before enabling tablet mode, the Launcher databases, SharedPreferences and
LSPosed configuration were copied to:

```text
/data/local/tmp/fuxi-pre-tablet-20261022-205758/
```

That directory is device-local recovery data and is not committed.

## Native grid acceptance

The 8583 symbol table resolves the relevant count authority to:

```text
GridConfig.calGridSize                       0x199b6a4 / 7372
GridController._filterActiveGridConfig       0x8d26c0  / 728
PadRowColHandler.handle                      0x1a88188 / 344
CellLayoutGetxController.changeCellCount     0xa523cc  / 932
```

The native backend pins the complete 344-byte `PadRowColHandler.handle` body and
hooks its `+0x80` row/column substitution window. On this portrait display the
observed mapping was:

```text
config 7x4   -> raw(cols=4,rows=6) -> final(cols=10,rows=4) when 4x10 is set
config 10x10 -> raw(cols=4,rows=6) -> final(cols=10,rows=10)
```

Final verified full-reboot log:

```text
20.344 native_init enter pid=5844
20.345 grid config 10x10 source=/data/user_de/.../liquiddock-native-early.conf
20.352 grid repack before load 10x10 moved=0
20.695 PadRowColHandler count authority installed
21.354 PadRowCol calls=32 raw(cols=4,rows=6) final(cols=10,rows=10)
```

`sys.boot_completed=1`, `ro.build.characteristics=tablet`, Launcher focus and a
stable PID were confirmed. The device owner accepted the resulting 10x10
layout. No tombstone was produced.

The configured range is `2..10` columns by `2..10` rows. Rows above 10 fail
closed in both the GUI publisher and native parser.

## Guardrail

Identity matching authorizes further verification only. It does not authorize
native hooks. Each hook still requires the exact ELF digest, mapped executable
range, whole-function fingerprint and runtime calling-convention validation.
