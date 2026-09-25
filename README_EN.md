# LiquidDock

<p align="center">
  <a href="./README.md">简体中文</a> · <strong>English</strong>
</p>

<p align="center">
  <a href="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml"><img alt="Build" src="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml/badge.svg"></a>
  <a href="https://github.com/yu4032/LiquidDock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/yu4032/LiquidDock"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/yu4032/LiquidDock"></a>
</p>

LiquidDock is an LSPosed module for HyperOS tablets. It customizes the home screen, Dock, Recents, and extends Liquid Glass styling to additional system surfaces and selected third-party apps.

<p align="center">
  <img width="3008" height="1880" alt="LiquidDock on HyperOS Launcher" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" />
</p>

## Main features

### Liquid Glass

LiquidDock can add a consistent Liquid Glass appearance to:

- the Dock;
- workspace icons and Dock utility icons;
- widgets;
- small and large folders;
- dragged icons, widgets, and folders;
- the long-press shortcut menu;
- the Recents action buttons;
- launcher uninstall, remove, and second-confirmation dialogs;
- the app-caption menu with split-screen and floating-window controls;
- supported Security Center sidebars;
- the MIUI system search main background;
- the Gboard floating keyboard and related toolbar surfaces.

Blur, refraction, dispersion, tint, brightness, shadow, highlights, corner radius, and individual highlight layers can be adjusted.

<p align="center">
  <img width="704" height="440" alt="LiquidDock glass example" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" />
</p>

### Home screen layout

Choose between **8×4 / 4×8** and **10×6 / 6×10** workspace profiles. Portrait and landscape can be adjusted independently for:

- horizontal spacing;
- top and bottom spacing;
- row gaps;
- page-indicator position;
- widget sizing adaptation.

Launcher 4.50 also supports independent icon-size control for the workspace, Dock, folders, and related launcher surfaces.

### Dock

Available Dock controls include:

- width and height;
- bottom position;
- icon spacing;
- blur and corner radius;
- stroke;
- continuous/squircle shape;
- whole-Dock shadow and stroke shadow;
- Workstation divider.

The phone-interconnect shortcut can be hidden without disabling the underlying system feature.

### Widgets and folders

Widgets can use glass backgrounds with optional dark-content adaptation. Supported widgets can also be scanned from the current workspace so individual internal background components can be hidden. These hiding rules can be backed up and restored separately.

Small and large folders have separate glass, size, and corner-radius controls.

### Shortcut menu and dialogs

The long-press shortcut menu can use a glass background with an optional white-text-and-icon mode for dark backgrounds.

Launcher uninstall, remove, and second-confirmation dialogs can use their own glass appearance, with controls for:

- background dimming;
- native dark mode;
- local tint;
- local blur;
- restoring inheritance from the global glass appearance.

### Recents and Workstation

Recents supports:

- adjustable wallpaper blur;
- optional wallpaper-dimming suppression;
- Liquid Glass for the Clear All and device-interconnect action buttons.

Workstation mode has separate controls for Dock length, icon position, icon corner radius, workspace horizontal position, All Apps spacing, and the divider.

### System UI and Security Center

Optional system integrations include:

- the app-caption menu containing split-screen, floating-window, and related controls;
- supported Security Center Game Toolbox, Video Toolbox, Global Dock, and All Apps surfaces.

These two higher-risk system integrations are not automatically enabled by the built-in default configuration.

### MIUI system search and Gboard

MIUI system search can replace its main background with Liquid Glass and can use independent tint and blur values.

Gboard supports glass for the floating keyboard and related toolbar surfaces, independent tint and blur, and a setting that controls whether dragging the bottom handle automatically enters resize mode.

### Configuration, animation, and backup

Animation timing can be adjusted for workspace visibility, Dock icon return, press feedback, Dock size changes, and settings-page transitions.

The settings app supports:

- restoring the built-in default configuration;
- exporting the current configuration to JSON;
- importing a JSON configuration;
- separate backup and restore for widget-component hiding rules.

A fresh install is seeded with the current built-in default configuration. Existing user settings are not replaced by the default profile during upgrades.

## Compatibility

Main development and verification baseline:

| Item | Current range |
| --- | --- |
| HyperOS | Primarily tablet builds based on 3.0.307 and newer |
| System Launcher | `com.miui.home` release-4.50.x.x |
| LSPosed | A version with libxposed API 101 support |

Updates to Launcher, System UI, Security Center, Gboard, or MIUI Search may temporarily affect the corresponding integration.

## Installation

1. Download the latest APK from [GitHub Releases](https://github.com/yu4032/LiquidDock/releases).
2. Install it and enable LiquidDock in LSPosed.
3. Enable the scopes you need:

| Scope | Used for |
| --- | --- |
| `com.miui.home` | Required for the core Launcher, Dock, Recents, folder, and widget features |
| `com.android.systemui` | Recommended for launcher transitions and required for the optional app-caption menu glass |
| `com.miui.securitycenter` | Only required for Security Center glass |
| `com.google.android.inputmethod.latin` | Only required for Gboard integration |
| `com.android.quicksearchbox` | Only required for MIUI Search glass |

4. Restart the affected processes or reboot the device.
5. Open LiquidDock and configure the features you want.

Some structural settings require a Launcher, System UI, or app restart. The settings app indicates this where possible.

## Reporting issues

When opening an issue, include:

- HyperOS version;
- System Launcher version;
- the app version related to the affected integration;
- enabled LiquidDock options;
- reliable reproduction steps;
- relevant logs when needed.

## Documentation

- [FEATURES.md](FEATURES.md) — current features and settings
- [ARCHITECTURE.md](ARCHITECTURE.md) — current runtime architecture
- [HOOKS.md](HOOKS.md) — current integration boundaries
- [CONTRIBUTING.md](CONTRIBUTING.md) — development rules
- [TODO.md](TODO.md) — active unfinished work
- [CHANGELOG.md](CHANGELOG.md) — release history
- [DIVIDER.md](DIVIDER.md) — Workstation divider behavior

Files under `docs/superpowers/` are historical plans, specifications, and verification records. They do not describe the current implementation unless explicitly stated otherwise.

## Risk notice

> [!WARNING]
> LiquidDock changes the presentation of System Launcher, System UI, Security Center, and selected third-party apps. System or app updates may introduce compatibility issues, so keep a working recovery method available before updating.

LiquidDock is a community project and is not affiliated with Xiaomi, LSPosed, or related projects.

## Credits

- **Prismal** — Liquid Glass visual reference
- **LSPosed / libxposed** — module runtime
- **HyperCeiler** — HyperOS module development reference

## License

[GNU General Public License v3.0](LICENSE)
