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

LiquidDock is an LSPosed / libxposed API 101 module for the **HyperOS 3 tablet launcher**. It adds a customizable Liquid Glass rendering pipeline to the system launcher and extends controls for the Dock, workspace grid, widgets, folders, Recents, and Workstation layouts.

LiquidDock is primarily developed and validated for **HyperOS 3.0.307+** and **`com.miui.home` release-4.50.x.x**.

<p align="center">
  <img width="3008" height="1880" alt="LiquidDock on HyperOS Launcher" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" />
</p>

## Features

### Liquid Glass

- GPU-rendered Liquid Glass for the Dock, supported widgets, folders, and workspace icons.
- Refraction, dispersion, blur, specular lighting, highlights, caustics, and directional light based on the Prismal optical model.
- Configurable glass size, corner radius, sampling area, blur, highlight, and other visual parameters.
- Workspace icon glass support when used with a transparent icon theme.
- Support for dynamic and video wallpapers.

### Launcher / Dock customization

- Additional **8×4** and **10×6** workspace layouts.
- Independent portrait / landscape margins, spacing, and page-indicator position controls.
- Configurable Dock width, height, bottom offset, icon spacing, corner radius, and blur parameters.
- Configurable Dock stroke, opacity, shadow, and divider appearance.
- Widget background hiding / adaptation rules.
- Workstation layout customization, including 8×4 workspace support.
- Configurable Recents background blur.

## Rendering architecture

LiquidDock uses HyperOS native PassBlur together with OES / GLES and Prismal in a zero-copy rendering pipeline:

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

Current implementation properties:

- Background frames stay on the GPU path.
- PassBlur updates are source-driven.

<p align="center">
  <img width="704" height="440" alt="LiquidDock glass example" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" />
</p>

## Current support range

| Item | Requirement |
| --- | --- |
| ROM | HyperOS 3.0.307+ |
| Launcher | `com.miui.home` `release-4.50.x.x` |
| Hook environment | LSPosed / libxposed API 101 |
| Build JDK | JDK 17 |
| Android SDK | compileSdk 37 |

### Before using

LiquidDock directly depends on internal HyperOS Launcher / SystemUI behavior as well as vendor-private classes and hidden `SurfaceControl.Transaction` PassBlur APIs. A ROM, Launcher, or SystemUI update can change these private interfaces and temporarily break related features until LiquidDock is updated.

Heavily modified third-party ROMs, heavily modified Launcher / SystemUI builds, and environments outside the support range above are not guaranteed to work. Non-glass features also depend on the corresponding HyperOS Launcher classes and methods being present.

### LSPosed scope

```text
com.miui.home
com.android.systemui
```

`com.miui.home` is the primary injection target. `com.android.systemui` is used for Launcher / SystemUI transition and state coordination.

## Installation

1. Download the latest APK from [GitHub Releases](https://github.com/yu4032/LiquidDock/releases).
2. Install the APK on a device with a working LSPosed / libxposed environment.
3. Enable **LiquidDock** in LSPosed.
4. Enable the following scopes:
   - System Launcher — `com.miui.home`
   - System UI — `com.android.systemui`
5. Restart the affected processes or reboot the device.
6. Open LiquidDock and enable / configure the features you want to use.

Before reporting a compatibility issue, please confirm that your HyperOS and `com.miui.home` versions are still within the supported range.

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

## Feedback and contributing

Issues and pull requests are welcome. When reporting rendering or compatibility problems, please include at least:

- HyperOS version;
- `com.miui.home` version;
- whether Workstation mode is enabled;
- short, reliable reproduction steps;
- relevant LiquidDock / Launcher logs when available.

Please keep each pull request focused on one issue and avoid unrelated refactoring.

## Risk notice

> [!WARNING]
> LiquidDock relies on private HyperOS interfaces and hooks the system Launcher and SystemUI through LSPosed / libxposed. System, Launcher, or SystemUI updates may introduce compatibility issues. Keep appropriate backups and make sure you have a working recovery method before use.

LiquidDock is an unofficial community project and is not affiliated with Xiaomi, LSPosed, or other related vendors or projects. “HyperOS” and “MIUI” are mentioned only for compatibility purposes.

This project is provided “AS IS” and is used at your own risk. Licensing and liability terms are governed by the [GPL-3.0](LICENSE).

## Credits

- **Prismal** — Liquid Glass optical model and shader parameter reference.
- **LSPosed / libxposed** — hooking API and module runtime.
- **HyperCeiler** — reference for HyperOS module engineering practices and project documentation structure.

## License

LiquidDock is open-source software licensed under the [GNU General Public License v3.0](LICENSE).
