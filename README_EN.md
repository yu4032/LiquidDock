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

The current 2.x mainline is primarily developed and validated for **HyperOS 3.0.307+** and **`com.miui.home` release-4.50.x.x**.

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

LiquidDock 2.x uses HyperOS native PassBlur together with OES / GLES and Prismal. The legacy 1.x CPU screenshot pipeline is no longer used by the current mainline:

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

- Background frames stay on the GPU path and are not read back into CPU bitmaps.
- The current mainline does not use the legacy real-time screenshot backend.
- PassBlur updates are source-driven rather than requested by a fixed polling loop.
- Local sampling / render quality can be adjusted without changing the launcher-wide coordinate system.

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

## Branches

- **`main`** — current development mainline and zero-copy PassBlur implementation.
- **`archive/1.x`** — archived legacy implementation based on ScreenCapture / bitmap readback.

## Feedback and contributing

Issues and pull requests are welcome. When reporting rendering or compatibility problems, please include at least:

- HyperOS version;
- `com.miui.home` version;
- whether Workstation mode is enabled;
- short, reliable reproduction steps;
- relevant LiquidDock / Launcher logs when available.

Please keep each pull request focused on one issue and avoid unrelated refactoring.

## Risk notice and disclaimer

> [!WARNING]
> LiquidDock hooks the system Launcher and SystemUI through LSPosed / libxposed and calls private HyperOS vendor APIs. An unsupported version combination, ROM update, or unadapted system implementation may cause features to malfunction or Launcher / SystemUI to crash repeatedly. In extreme cases, recovery through LSPosed safe mode, ADB, Recovery, or another recovery path may be required. Use LiquidDock only when you have a workable recovery path and appropriate backups.

LiquidDock is an unofficial community project and is not affiliated with Xiaomi, LSPosed, or any other vendor or project mentioned here. “HyperOS”, “MIUI”, and other names are used only for compatibility descriptions; their trademarks belong to their respective owners.

This software is provided “AS IS”, without any express or implied warranty regarding compatibility, stability, data safety, merchantability, or fitness for a particular purpose. You assume the risk of installing, enabling, modifying, and using this software. To the maximum extent permitted by applicable law, the authors and contributors are not liable for direct, indirect, incidental, special, or consequential damages arising from use of or inability to use the software. The governing terms for licensing, warranty disclaimer, and limitation of liability remain Sections 15 and 16 of the [GPL-3.0](LICENSE), subject to applicable law.

## Credits

- **Prismal** — Liquid Glass optical model and shader parameter reference.
- **LSPosed / libxposed** — hooking API and module runtime.
- **HyperCeiler** — reference for HyperOS module engineering practices and project documentation structure.
- **HyperLight** — reference for the legacy screen-capture implementation.

## License

LiquidDock is open-source software licensed under the [GNU General Public License v3.0](LICENSE). GPL-3.0 permits use, modification, and redistribution under its terms, including commercial use; this project does not add a conflicting non-commercial restriction.
