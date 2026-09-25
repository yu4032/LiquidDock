# LiquidDock

<p align="center">
  <a href="./README.md">简体中文</a> · <strong>English</strong>
</p>

<p align="center">
  <a href="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml"><img alt="Build" src="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml/badge.svg"></a>
  <a href="https://github.com/yu4032/LiquidDock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/yu4032/LiquidDock"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/yu4032/LiquidDock"></a>
</p>

LiquidDock is an LSPosed module for the **HyperOS tablet launcher**, focused on desktop layout, Dock customization, and Liquid Glass effects.

<p align="center">
  <img width="3008" height="1880" alt="LiquidDock on HyperOS Launcher" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" />
</p>

## What it can do

### Liquid Glass

Add a consistent Liquid Glass look to the Dock, workspace icons, widgets, and folders. Blur, refraction, highlights, dispersion, corner radius, and other visual details can be adjusted from the settings app.

Glass also follows dragged icons and widgets. The long-press shortcut menu can use a glass background as well, with an optional white text and icon mode for dark backgrounds.

<p align="center">
  <img width="704" height="440" alt="LiquidDock glass example" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" />
</p>

### Home screen layout

Choose between **8×4** and **10×6** workspace grids, with separate spacing and positioning controls for portrait and landscape.

On Launcher 4.50, icon size can also be adjusted for the workspace, Dock, folders, and related launcher views without changing normal All Apps or Search sizing.

### Dock

Adjust Dock width, height, bottom position, icon spacing, corner radius, and blur. Stroke, shadow, and Workstation divider options are available too.

The phone-interconnect shortcut can be hidden from the Dock without turning off the underlying system feature.

### Widgets and folders

Widgets can use glass backgrounds with an optional dark-content mode. Selected background parts of supported widgets can also be hidden from the settings app.

Small and large folders can be configured separately, including their glass effect, size, and corner radius.

### Recents and Workstation

Recents background blur can be adjusted. LiquidDock also includes several layout options for HyperOS Workstation / Laptop mode, including Dock, workspace, and app-page positioning.

Workstation support is still being refined, so changing its options one at a time is recommended.

### Security Center sidebar

LiquidDock can also add Liquid Glass to supported HyperOS 4 Security Center sidebar pages, including Game Toolbox, Video Toolbox, Global Dock, and All Apps.

Security Center varies significantly between system versions. If a build is not compatible, LiquidDock leaves the original system interface in place.

## Compatibility

The main development and test baseline is:

| Item | Recommended version |
| --- | --- |
| HyperOS | 3.0.307 or newer |
| System Launcher | `com.miui.home` release-4.50.x.x |
| LSPosed | A version with libxposed API 101 support |

Launcher and Security Center updates may temporarily affect compatibility. If something stops working after an update, check the installed launcher version first.

## Installation

1. Download the latest APK from [GitHub Releases](https://github.com/yu4032/LiquidDock/releases).
2. Install it and enable LiquidDock in LSPosed.
3. Enable these scopes:
   - System Launcher — `com.miui.home`
   - System UI — `com.android.systemui`
   - Security Center — `com.miui.securitycenter`
4. Restart the affected processes or reboot the device.
5. Open LiquidDock and enable the features you want.

Some layout options require a launcher restart. The settings app indicates this where possible.

## Reporting issues

When opening an issue, please include:

- HyperOS version;
- System Launcher version;
- the LiquidDock options that were enabled;
- short, reliable reproduction steps;
- relevant logs when available.

For Workstation or Security Center problems, also mention the page or mode where the issue occurs.

## More documentation

For a complete feature list, see [FEATURES.md](FEATURES.md).

Development and implementation notes are kept in:

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [HOOKS.md](HOOKS.md)
- [CONTRIBUTING.md](CONTRIBUTING.md)
- [TODO.md](TODO.md)
- [CHANGELOG.md](CHANGELOG.md)

## Risk notice

> [!WARNING]
> LiquidDock changes parts of the system Launcher, System UI, and Security Center. System component updates may introduce compatibility issues, so keep a working recovery method available before updating.

LiquidDock is a community project and is not affiliated with Xiaomi, LSPosed, or related projects.

## Credits

- **Prismal** — Liquid Glass visual reference
- **LSPosed / libxposed** — module runtime
- **HyperCeiler** — HyperOS module development reference

## License

[GNU General Public License v3.0](LICENSE)
