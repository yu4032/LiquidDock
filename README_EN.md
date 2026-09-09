# LiquidDock

<p align="center">
  <a href="./README.md">简体中文</a> · <strong>English</strong>
</p>

<p align="center">
  <a href="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml"><img alt="Build" src="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml/badge.svg"></a>
  <a href="https://github.com/yu4032/LiquidDock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/yu4032/LiquidDock"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/yu4032/LiquidDock"></a>
  <a href="https://github.com/libxposed/api"><img alt="libxposed API 101" src="https://img.shields.io/badge/libxposed-API%20101-6f42c1"></a>
</p>

LiquidDock is an LSPosed / libxposed API 101 module for the HyperOS 3 tablet launcher. It brings a customizable liquid-glass rendering pipeline to the launcher and provides additional controls for the Dock, workspace grid, widgets, folders, Recents, and Workstation layouts.

The current 2.x implementation is primarily developed for **HyperOS 3.0.307+** and **`com.miui.home` release-4.50.x.x**.

<p align="center">
  <img width="3008" height="1880" alt="LiquidDock on HyperOS Launcher" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" />
</p>

## Features

### Liquid Glass

- GPU-rendered liquid glass for the Dock, supported widgets, folders, and workspace icons.
- Refraction, dispersion, blur, specular lighting, caustics, directional light, and configurable highlight effects based on the Prismal optical model.
- Configurable glass size, corner radius, sampling area, and visual parameters.
- Workspace icon glass support when used with a transparent icon theme.
- Support for dynamic and video wallpapers.

### Launcher customization

- Additional **8×4** and **10×6** workspace layouts.
- Independent portrait / landscape margins and spacing controls.
- Configurable page-indicator position.
- Dock width, height, bottom offset, icon spacing, corner radius, and blur controls.
- Configurable Dock stroke, opacity, shadow, and divider appearance.
- Widget background hiding / adaptation rules.
- Workstation layout customization, including 8×4 workspace support.
- Configurable Recents background blur.

## Rendering architecture

LiquidDock 2.x uses the HyperOS native PassBlur path together with OES / GLES and Prismal instead of the legacy CPU screen-capture pipeline:

```text
HyperOS SurfaceFlinger PassBlur
        ↓
external OES texture
        ↓
GLES normalization / local sampling
        ↓
Prismal blur + optical rendering
        ↓
TextureView composition
```

Key properties:

- Background frames stay on the GPU; LiquidDock does not read them back into CPU bitmaps.
- The current mainline does not use the old real-time screenshot backend.
- PassBlur updates are source-driven, so static content does not require a fixed polling loop.
- Local sampling / render quality can be adjusted without changing the launcher-wide coordinate system.

<p align="center">
  <img width="704" height="440" alt="LiquidDock glass example" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" />
</p>

## Requirements and compatibility

| Item | Requirement |
| --- | --- |
| ROM | HyperOS 3.0.307+ |
| Launcher | `com.miui.home` `release-4.50.x.x` |
| Hook framework | LSPosed / libxposed API 101 environment |
| Build JDK | JDK 17 |
| Android SDK | compileSdk 37 |

The liquid-glass backend depends on HyperOS vendor classes and hidden `SurfaceControl.Transaction` PassBlur APIs. A ROM or Launcher update may change these private interfaces and break glass rendering until LiquidDock is updated.

Non-glass features also depend on the corresponding HyperOS Launcher classes and methods being present.

### Module scope

```text
com.miui.home
com.android.systemui
```

`com.miui.home` is the primary injection target. `com.android.systemui` is used for Launcher / SystemUI transition coordination.

## Installation

1. Download the latest APK from [GitHub Releases](https://github.com/yu4032/LiquidDock/releases).
2. Install the APK on a device with a working LSPosed / libxposed environment capable of hooking system applications.
3. Enable **LiquidDock** in LSPosed.
4. Enable the recommended scopes:
   - System Launcher — `com.miui.home`
   - System UI — `com.android.systemui`
5. Restart the affected processes or reboot the device.
6. Open LiquidDock and configure the features you want to use.

Because LiquidDock hooks private HyperOS implementation details, make sure your current ROM / Launcher version is within the supported compatibility range before reporting a bug.

## Build from source

Requirements:

- Android SDK / compileSdk 37
- JDK 17
- libxposed API 101
- Gradle dependencies for `io.github.libxposed:api` and `service`

### Debug

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

### Release

```bash
ANDROID_HOME=/path/to/Android ./gradlew assembleRelease --no-daemon
```

APK outputs are written under:

```text
build/outputs/apk/
```

Both Debug and Release builds use the Android Gradle Plugin optimization / shrinker path configured by the project.

## Branches

- **`main`** — current development branch and zero-copy PassBlur implementation.
- **`archive/1.x`** — archived legacy implementation based on screen capture / bitmap readback.

## Contributing

Bug reports and pull requests are welcome. When reporting rendering or compatibility issues, please include at least:

- HyperOS version
- `com.miui.home` version
- whether Workstation mode is enabled
- a short reproduction description
- relevant LiquidDock / Launcher logs when available

Please keep changes focused and avoid mixing unrelated fixes into the same pull request.

## Credits

- **Prismal** — optical model and shader parameter reference for Liquid Glass.
- **LSPosed / libxposed** — hooking API and module runtime.
- **HyperCeiler** — reference for HyperOS module engineering practices.
- **HyperLight** — reference for the legacy screen-capture implementation.

## Disclaimer

LiquidDock is an unofficial community project and is not affiliated with Xiaomi. “HyperOS” and “MIUI” are trademarks of their respective owners and are mentioned only for compatibility purposes.

This project modifies system applications through runtime hooks and private vendor APIs. Use it at your own risk and keep a recovery path available when testing on unsupported ROM or Launcher versions.

## License

LiquidDock is licensed under the [GNU General Public License v3.0](LICENSE).
