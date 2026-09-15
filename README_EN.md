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

LiquidDock is an LSPosed / libxposed API 101 module for the HyperOS tablet launcher. Current `main` is **2.4.1**, developed primarily against **HyperOS 3.0.307+ / `com.miui.home` release-4.50.x.x**, with a separate capability-driven Liquid Glass integration for the Security Center sidebar process.

<p align="center">
  <img width="3008" height="1880" alt="LiquidDock on HyperOS Launcher" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" />
</p>

## Current features

### Liquid Glass

- Prismal Liquid Glass for the Dock, workspace icons, selected Dock system actions, supported widgets, and small/large folders.
- Workspace static items share one root-wide PassBlur source/session instead of creating one producer per item.
- Workspace dragging uses a dedicated upper window with **live** Workspace sampling and a draw-only mirror of MIUI's DragView; it is not a frozen drag-start screenshot.
- Shortcut menus can use an independent glass background and an optional dark-mode adaptation. Text becomes white; standalone icons are tinted only when a one-time scan classifies them as near-black, low-chroma line art, so colorful third-party app icons remain unchanged.
- Widgets support dark-content adaptation, background ownership handling, and user-selectable component hiding.
- Security Center `:ui` uses capability-driven Game / Video / Global Dock / All Apps glass contracts instead of treating obfuscated field names as compatibility authorities.
- Prismal optics expose refraction, dispersion, blur, thickness, IOR, normals, specular/rim/caustics, directional lighting, and highlight profiles.

### Launcher / Dock

- Custom **8×4** and **10×6** workspace profiles with orientation-specific geometry and placement memory.
- Launcher 4.50 icon scaling from 80% to 120% for Workspace, Dock, small-folder previews, open-folder contents, and the Workstation app page while leaving normal All Apps/Search on vendor sizing.
- Dock width, height, bottom offset, icon spacing, blur, corner geometry, squircle, and Fill-Diff controls.
- Dock stroke, stroke shadow, whole-Dock shadow, and Workstation Divider controls.
- Optional hiding of the phone-interconnect Dock entry without changing the underlying connection feature.
- Experimental Workstation/Laptop Dock, Grid, All Apps, and icon-position adjustments.
- Configurable Recents background blur.
- Configurable timing for Workspace visibility, Dock-icon reveal, press in/out, Dock resize, and settings-page transitions.

## Rendering architecture

Launcher Liquid Glass uses a GPU zero-copy path:

```text
HyperOS MiuiX PassBlur
        ↓
Surface / SurfaceTexture
        ↓
GL_TEXTURE_EXTERNAL_OES
        ↓
GPU normalization / overscan
        ↓
Prismal optical renderer
        ↓
Dock / Launcher / popup / drag / Security Center output
```

Important invariants:

- The active Liquid Glass backdrop pipeline does not use ScreenCapture, PixelCopy, CPU bitmap readback, or texture re-upload as a fallback.
- Native PassBlur geometry remains authoritative; Workspace quality scaling happens after OES normalization.
- Source draining is separated from expensive Prismal/output rendering, so an FPS cap never blocks `SurfaceTexture.updateTexImage()`.
- Scene, wallpaper, and producer freshness use generations and fresh-frame barriers; returning HOME never authorizes a stale frame merely because a View became visible.
- Missing vendor/private capabilities fail closed and preserve or restore vendor presentation.

> `ShortcutMenuDarkModeController` renders a menu icon once into a tiny 20×20 bitmap for color classification. That bitmap is UI icon analysis only and is not part of the glass backdrop capture path.

<p align="center">
  <img width="704" height="440" alt="LiquidDock glass example" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" />
</p>

## Compatibility boundary

| Item | Current boundary |
| --- | --- |
| LiquidDock | `main` / 2.4.1 |
| Android | minSdk 33, targetSdk / compileSdk 37 |
| Launcher | HyperOS 3.0.307+, `com.miui.home` release-4.50.x.x is the primary validated baseline |
| Hook runtime | libxposed API 101 |
| Build JDK | JDK 17 |
| Security Center | `com.miui.securitycenter:ui`; runtime semantic/resource capabilities are validated before any material ownership is claimed |

LiquidDock relies on private HyperOS Launcher, SystemUI, Security Center, PassBlur, and `SurfaceControl` behavior. A component update may break a feature even when the package name stays the same.

### Xposed scope

```text
com.miui.home
com.android.systemui
com.miui.securitycenter
```

- `com.miui.home`: primary Launcher functionality and Liquid Glass.
- `com.android.systemui`: read-only HOME/keyguard transition timing authority; it is not the Launcher glass renderer.
- `com.miui.securitycenter`: Security Center initialization proceeds only in the exact `com.miui.securitycenter:ui` process.

## Installation

1. Download the APK from [GitHub Releases](https://github.com/yu4032/LiquidDock/releases).
2. Install it on an LSPosed environment supporting libxposed API 101.
3. Enable LiquidDock and the three scopes above.
4. Restart the affected processes or reboot.
5. Enable the features you want in LiquidDock settings.

Structural hooks such as Grid layout and some Dock/Workstation installation choices remain restart-bound. A number of visual ownership switches can release state at runtime; the settings UI documents the intended boundary where practical.

## Build from source

Requirements: Android SDK 37, JDK 17, Gradle 9.6.1, libxposed API 101.

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Release:

```bash
ANDROID_HOME=/path/to/Android ./gradlew assembleRelease --no-daemon
```

Both Debug and Release use Android optimization / R8. CI debug APKs therefore exercise shrinker behavior as well.

## R8 and reflection rules

Project-owned LiquidDock classes must not access one another through string reflection; use typed Java/package-private APIs. Reflection is reserved for Android/HyperOS vendor boundaries where needed.

Cross-ClassLoader type names also require care. Dock spacing resolves `RecyclerView$State` through the Launcher ClassLoader, so the project uses targeted `-keepnames` for `RecyclerView` and `$State` to prevent R8 from adapting those strings to LiquidDock's own obfuscated binary names. Broad keep rules are not a substitute for removing project self-reflection.

## Documentation

- [FEATURES.md](FEATURES.md) — user-visible features, settings, and restart/live boundaries
- [ARCHITECTURE.md](ARCHITECTURE.md) — runtime architecture, ownership, and freshness
- [HOOKS.md](HOOKS.md) — current hooks, listeners, and reflection boundaries
- [CONTRIBUTING.md](CONTRIBUTING.md) — development, testing, R8, and compatibility rules
- [DIVIDER.md](DIVIDER.md) — Workstation Divider ownership
- [TODO.md](TODO.md) — active engineering debt
- [CHANGELOG.md](CHANGELOG.md) — release history and current-main changes

`docs/superpowers/plans` and `docs/superpowers/specs` are historical design/implementation records. Current behavior is defined by production source and the root documentation above.

## Feedback

For compatibility reports, include the HyperOS version, Launcher version, Workstation state, relevant LiquidDock settings, reliable reproduction steps, and `[DC]` logs. For Security Center issues, also include the installed Security Center version and whether the failing path is Game, Video, Global Dock, or All Apps.

## Risk notice

> [!WARNING]
> LiquidDock hooks private system Launcher, SystemUI, and Security Center behavior. Keep a working recovery method before updating system components. LiquidDock is an unofficial community project and is not affiliated with Xiaomi, LSPosed, or related vendors/projects.

The project is provided “AS IS” under [GPL-3.0](LICENSE).

## Credits

- **Prismal** — Liquid Glass optical model and shader parameter reference
- **LSPosed / libxposed** — hooking API and module runtime
- **HyperCeiler** — HyperOS module engineering and settings-structure reference

## License

[GNU General Public License v3.0](LICENSE)
