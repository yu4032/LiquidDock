# LiquidDock

<p align="center">
  <a href="./README.md">简体中文</a> · <strong>English</strong>
</p>

<p align="center">
  <a href="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml"><img alt="Build" src="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml/badge.svg"></a>
  <a href="https://github.com/yu4032/LiquidDock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/yu4032/LiquidDock"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/yu4032/LiquidDock"></a>
</p>

LiquidDock is an LSPosed module for HyperOS 3 tablets. It lets you customize the home screen, Dock, and Recents, and brings Liquid Glass styling to the launcher, system UI, and selected third-party apps.

<p align="center">
  <img width="3008" height="1880" alt="LiquidDock on HyperOS Launcher" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" />
</p>

## Main features

### Liquid Glass

With the LiquidDock and Liquid Glass master switches enabled, LiquidDock can add a consistent glass appearance to the following surfaces. Icons, widgets, folders, Recents buttons, and other integrations also require their own feature switches:

- Dock;
- home-screen icons and Dock function icons;
- widgets;
- small and large folders;
- icons, widgets, and folders while dragging;
- the shortcut menu shown when long-pressing a home-screen icon;
- the “Clear All” and device-interconnect buttons in Recents;
- launcher uninstall, remove, and second-confirmation dialogs;
- the app's top menu for split-screen, floating windows, and related controls;
- Security Center sidebars;
- the MIUI System Search main screen;
- the Gboard floating keyboard and related toolbars.

Blur, refraction, chromatic dispersion, tint, brightness, shadows, highlights, corner radii, and different highlight layers can be adjusted separately.

Most appearance changes take effect **immediately** when the relevant hook is already running. Installing a hook for the first time, or changing structural settings such as the grid, may still require restarting the corresponding scope.

<p align="center">
  <img width="704" height="440" alt="LiquidDock glass example" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" />
</p>

### Home screen layout

Enable “Free Home Screen Grid” to choose **2–10 columns and 2–6 rows** in landscape. Portrait swaps the dimensions, with icon positions remembered separately for each orientation. Landscape and portrait have independent controls for:

- horizontal spacing;
- top and bottom spacing;
- row gaps;
- page-indicator position;
- widget sizing.

When reducing the number of rows or columns, the settings app checks whether an existing **4×2 widget** would no longer fit. It warns you instead of saving an incompatible grid. Restart Launcher after changing the grid.

### Dock

Enable Dock customization to change its dimensions, position, and icon spacing. Outline, shadow, and divider settings are also available:

- width and height;
- distance from the bottom edge;
- icon spacing;
- blur and corner radius;
- outline;
- whole-Dock shadow and outline shadow;
- divider.

The phone-interconnect shortcut can be hidden without disabling the system interconnect feature or affecting its transfer icon.

A recent-app blacklist can prevent selected apps from appearing in the Dock's recent-app suggestions.

### Widgets and folders

Widgets can use glass backgrounds, with dark-content adaptation for some widgets. In **Widget Component Management**, you can load the widgets currently on the home screen and select individual parts:

- **Hide background parts:** Select internal widget background areas to hide. These rules can be backed up and restored separately.
- **Whiten parts for dark themes:** Turn selected RemoteViews text white, or display images as white shapes while preserving their original transparency.
- **Avoid conflicting rules:** The same exact part cannot be both hidden and whitened. Whitening selections are stored separately and are not included in hiding-rule backups.

Part-level whitening currently supports **RemoteViews TextView / ImageView**. Internally drawn MAML script elements are not supported. Restart Launcher after changing these selections.

Small and large folders have separate glass switches, sizes, and corner-radius settings.

### Shortcut menus and dialogs

The shortcut menu shown when long-pressing a home-screen icon can use a glass background, with an optional white-text-and-icon mode for dark themes.

Launcher uninstall, remove, and second-confirmation dialogs can also use glass backgrounds, with controls for:

- keeping or removing background dimming;
- native dark mode;
- independent tint;
- independent blur;
- restoring inheritance from the global glass appearance.

Dialogs also have an **All Parameters** page for finer optical settings such as refraction and highlights, independently of the global configuration.

### Recents and Workstation

Recents supports:

- adjustable wallpaper blur;
- disabling wallpaper dimming;
- glass backgrounds for the “Clear All” and device-interconnect buttons.

Workstation mode provides a **vertical offset for Dock icons**, Dock icon glass corner radius, home-screen horizontal positioning, and All Apps spacing for both orientations. Workstation Dock icon settings require Workstation customization to be enabled; the divider has its own switch.

### System UI and Security Center

Optional system integrations include:

- the app's top menu for split-screen, floating windows, and related controls;
- supported Security Center sidebars, including Game Toolbox, Video Toolbox, Global Dock, and All Apps, with support for the slide-and-hold gesture.

Both glass integrations require the LiquidDock master switch, the Liquid Glass master switch, and their respective feature switches. They also depend on compatibility with the installed system components.

### MIUI System Search and Gboard

MIUI System Search can replace its main-screen background with Liquid Glass. Tint and blur can be set independently, while **All Parameters** offers separate refraction, highlight, and other optical settings. Parameters without local overrides inherit the global settings. The module, glass, and corresponding third-party glass switches must be enabled.

Gboard supports glass for the floating keyboard and related toolbars, with independent tint and blur. **All Parameters** provides additional optical settings and a one-tap reset to global inheritance. The module, glass, and corresponding third-party glass switches must also be enabled. Whether dragging the bottom handle automatically enters resize mode is controlled separately and does not depend on the glass switch; the keyboard's bottom handle must be recognized for this to work.

### Configuration, animation, and backups

Animation timing can be adjusted for workspace visibility, Dock icon reveal, press feedback, Dock resizing, and settings-page transitions. An optional **Disable wallpaper zoom** switch constrains Launcher's wallpaper zoom target during app and Recents transitions without changing workspace animation; first-time installation requires restarting Launcher. Effectiveness depends on the vendor Launcher implementation.

The settings app uses MIUIX and Prismal glass styling, with options grouped by feature. Sliders support continuous dragging, live values, and spring animations on release. You can also tap a value to enter it directly or use the plus, minus, and reset buttons. Settings dialogs have transition animations, and the bottom glass capsule supports swipe navigation with moving icon and text highlights.

The **iOS Glass Effect** switch controls the settings app's appearance. The Restart Scopes dialog supports scrolling and multiple selections, independently of the desktop glass switches.

The settings app supports:

- restoring the built-in default configuration;
- exporting the current configuration as JSON;
- importing a JSON configuration;
- separately backing up and restoring widget hiding rules.

New installations use the project's built-in defaults. Upgrading does not overwrite saved settings with the entire default preset.

## Compatibility

Main development and verification environment:

| Item | Current range |
| --- | --- |
| HyperOS | Mainly tablet builds based on 3.0.307 and newer; **HyperOS 4 is not supported** |
| System Launcher | `com.miui.home` release-4.50.x.x |
| LSPosed | A version supporting libxposed API 101 |

Updates to System Launcher, SystemUI, Security Center, Gboard, or System Search may require the corresponding integration to be adapted again.

## Installation

1. Download the latest APK from [GitHub Releases](https://github.com/yu4032/LiquidDock/releases).
2. Install it and enable LiquidDock in LSPosed.
3. Select the scopes you need:

| Scope | Purpose |
| --- | --- |
| `com.miui.home` | Required: core home-screen, Dock, Recents, folder, and widget features |
| `com.android.systemui` | Required: desktop transitions and optional app-top-menu glass |
| `com.miui.securitycenter` | Only needed for Security Center sidebars and gestures |
| `com.google.android.inputmethod.latin` | Only needed for Gboard integration |
| `com.android.quicksearchbox` | Only needed for MIUI System Search glass |
| `system` (System Framework) | Only needed for the optional wallpaper GPU rendering switch; reboot the device after enabling |

4. Restart the relevant processes, or reboot the device.
5. Open LiquidDock and enable or adjust the features you want.

Most glass appearance parameters update immediately when their hooks are already loaded. Structural layout changes, newly enabled integrations, and some hook installation options still require restarting Launcher, SystemUI, or the relevant app. Use **Restart Scopes** in the settings app to select the processes to restart.

## Reporting issues

When opening an issue, please include:

- HyperOS version;
- System Launcher version;
- the version of any other app involved;
- the LiquidDock options that were enabled;
- reproducible steps;
- relevant logs, if needed.

## Documentation

- [FEATURES.md](FEATURES.md) — current features and settings
- [ARCHITECTURE.md](ARCHITECTURE.md) — runtime architecture
- [HOOKS.md](HOOKS.md) — integration entry points and boundaries
- [CONTRIBUTING.md](CONTRIBUTING.md) — development conventions
- [TODO.md](TODO.md) — unfinished work
- [CHANGELOG.md](CHANGELOG.md) — version changes
- [DIVIDER.md](DIVIDER.md) — Workstation divider
- [docs/release-signing.md](docs/release-signing.md) — Release builds, isolated signing, and security boundaries

The plans, specs, and verification records under `docs/superpowers/` are historical development records, not descriptions of the current implementation.

## Risk notice

> [!WARNING]
> LiquidDock changes how System Launcher, SystemUI, Security Center, and selected third-party apps are displayed. Updates to system components or apps may cause compatibility issues. Keep a working recovery method available before updating.

LiquidDock is a community project and is not affiliated with Xiaomi, LSPosed, or other related projects.

## Credits

- [Compose Miuix](https://github.com/compose-miuix-ui/miuix) — MIUIX Compose UI framework
- [PrismalAGSL](https://github.com/styropyr0/PrismalAGSL) — Liquid Glass components for the settings UI
- [AndroidX / Jetpack](https://source.android.com/docs/setup/about/licenses) — Android UI and foundation components
- [LSPosed / libxposed](https://github.com/LSPosed/LSPosed) — module hooking API and runtime
- [HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) — HyperOS module development reference
- [Prismal](https://github.com/styropyr0/Prismal) — Liquid Glass optical model reference

## License

[GNU General Public License v3.0](LICENSE)
