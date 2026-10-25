# Fuxi HyperOS 4 Launcher 8.01.02.8584 contract

Validated from the connected Xiaomi 13 (`fuxi`) device after its system-app
Launcher update.

| Property | Value |
| --- | --- |
| Android | 17 / SDK 37 |
| OS build | `OS4.0.27.0.XEOCNXM` |
| Launcher | `RELEASE-8.01.02.8584-260918-10231931-R` (`801028584`) |
| Active APK | `/data/app/.../com.miui.home-<id>/base.apk` |
| Factory APK | `/product/priv-app/MiuiHome/MiuiHome.apk` (`8.01.02.8583`) |

The Launcher process maps the updated `base.apk`, not the factory APK name. The
native resolver therefore selects `com.miui.home-` when present in
`/proc/self/maps`, then falls back to `MiuiHome.apk` for factory installs.

## Runtime libraries

| Entry | Size | SHA-256 |
| --- | ---: | --- |
| `lib/arm64-v8a/libapp.so` | 29,980,112 | `93e511989202a70d38b8231c3133167b830ca7609b61ba6ad133898737d0e0ef` |
| `lib/arm64-v8a/libapp_launcher.so` | 16,242,592 | `f70a2d15505abc83bbc2f03cbe9e90b635a8add6d89ae0fef764100aea1c6d8b` |

`os4/profiles/fuxi-8.01.02.8584.json` pins the complete AOT ELF and the 11
function bodies used by the layout pipeline. The Rust orientation guard remains
byte-identical to the 8583 build and is pinned separately in
`os4/profiles/fuxi-8584-rust-layout.json`.

The vendor APK and extracted shared libraries are local device evidence only;
they are not checked into the repository.
