# LiquidDock

<p align="center">
  <a href="./README.md">简体中文</a> · <strong>English</strong>
</p>

<p align="center">
  <a href="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml"><img alt="Build" src="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml/badge.svg"></a>
  <a href="https://github.com/yu4032/LiquidDock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/yu4032/LiquidDock"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/yu4032/LiquidDock"></a>
</p>

LiquidDock is an LSPosed module for HyperOS 3 tablets. Customize the workspace and Dock, and bring Liquid Glass to Recents, folders, widgets, system surfaces, and selected apps.

> **Version note:** This README describes the current `main` branch (2.7.0 prepared for release). APKs on [GitHub Releases](https://github.com/yu4032/LiquidDock/releases) may not include these changes yet; check the [changelog](CHANGELOG.md) for the version you installed.

<p align="center">
  <img width="3008" height="1880" alt="LiquidDock on HyperOS Launcher" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" />
</p>

## Main features

### Modern settings UI

The settings app uses a **MIUIX + Prismal** glass design, organized into workspace, Dock, Liquid Glass, app integrations, animations, and other feature pages. The glass header and bottom navigation remain visually distinct, including in dark mode and when settings glass is disabled.

- **Sliders:** smooth, continuous dragging with the nearest valid value shown live. Safe settings follow the displayed value; grid changes that could invalidate widget placement are checked before saving. Release springs remain animated.
- **Direct entry:** tap a number to type it, or use increment, decrement, and reset controls without label-width jumps.
- **Dialogs:** animated numeric input, defaults, grid warnings, and restart-scope dialogs. Restart scopes support a scrollable multi-select list.
- **Bottom navigation:** draggable glass capsule with icon and label highlights that track selection.
- **iOS Glass Effect:** controls the settings app's glass appearance separately from Launcher glass options.

### Liquid Glass

With the LiquidDock and Liquid Glass master switches enabled, LiquidDock can add a consistent Liquid Glass appearance to the surfaces below. Icons, widgets, folders, Recents buttons and additional integrations also require their corresponding feature switches:

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

Blur, refraction, dispersion, tint, brightness, shadow, highlights, corner radius, and individual highlight layers can be adjusted. **IOR** changes the lens bending at glass edges; the separate lens-refraction multiplier has a maximum of 8. Lower backdrop capture resolution is designed to retain sharp glass contours and highlights.

Most appearance changes update **already-installed hooks** live. Enabling a hook that has not been installed yet, or changing structural settings such as the grid, may still require restarting the relevant scope.

<p align="center">
  <img width="704" height="440" alt="LiquidDock glass example" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" />
</p>

### Home screen layout

Enable the free workspace grid to choose **2–10 columns and 2–6 rows** in landscape. Portrait swaps the dimensions, with separate orientation layout memory. While this switch is enabled, portrait and landscape can be adjusted independently for:

- horizontal spacing;
- top and bottom spacing;
- row gaps;
- page-indicator position;
- widget sizing adaptation.

When reducing rows or columns, LiquidDock checks whether an existing **4×2 widget** would exceed the target grid. It warns you and blocks the unsafe change. Restart Launcher after changing the grid.

Custom icon size has its own switch and does not require the free grid. One shared scale applies to workspace, Dock, small-folder, folder-content and Workstation app-page icons. The sidebar app page and search page do not follow this scaling.

### Dock

Dock customization enables dimension, position and icon-spacing adjustments. Dock settings also include stroke, shadows and the divider; stroke and the divider have their own switches:

- width and height;
- bottom position;
- icon spacing;
- blur and corner radius;
- stroke;
- continuous/squircle shape;
- whole-Dock shadow and stroke shadow;
- Workstation divider.

The phone-interconnect shortcut can be hidden without disabling the underlying system feature.

Whole-Dock shadow requires both Dock customization and shadow; stroke shadow requires both stroke and stroke shadow. Hiding the interconnect shortcut, recent-app filtering and the divider do not require Dock dimension customization.

A recent-app blacklist prevents selected apps from appearing in Dock recent-app recommendations.

If wallpaper flickers, try the optional wallpaper GPU rendering switch. It requires both module and glass master switches, the System Framework (`system`) scope and a device reboot.

### Widgets and folders

With widget glass enabled, widgets can use glass backgrounds; dark-content adaptation has its own switch. In **Widget Component Management**, load widgets currently on the workspace and choose precise components:

- **Hide individual backgrounds:** select internal widget background regions; hiding rules have their own backup and restore.
- **Whiten components for dark themes:** turn selected RemoteViews text white or display selected images as white silhouettes while retaining transparency, without tinting an entire widget.
- **Avoid conflicts:** hide and whiten cannot both target the same exact component. Whiten selections are stored separately and **are not included in hiding-rule backups**.

Precise whitening currently works for **RemoteViews TextView / ImageView**, not script-drawn MAML elements. Restart Launcher after changing per-component rules.

Small and large folders have separate glass, size, and corner-radius controls.

### Shortcut menu and dialogs

The long-press shortcut menu can use a glass background with an optional white-text-and-icon mode for dark backgrounds.

These are separate switches, both requiring the module and glass master switches. White text and icons do not require shortcut-menu glass.

Launcher uninstall, remove, and second-confirmation dialogs additionally require dialog glass after the master switches are enabled, with controls for:

- background dimming;
- native dark mode;
- local tint;
- local blur;
- restoring inheritance from the global glass appearance.

Launcher dialogs also have their own **All Parameters** page for finer optics such as refraction and highlights. Unset values inherit the global glass profile, and you can reset all overrides at once.

### Recents and Workstation

Recents supports:

- adjustable wallpaper blur;
- optional wallpaper-dimming suppression;
- Liquid Glass for the Clear All and device-interconnect action buttons.

Recents background blur and wallpaper-dimming suppression do not require Liquid Glass. Glass on its action buttons requires the glass master and corresponding feature switch. When a Prismal frame is not ready, the enabled buttons retain a transparent interim background instead of falling back to an inconsistent native blur; turning the feature off restores the stock background.

Workstation mode offers **Dock icon vertical offset**, glass corner radius, workspace horizontal positioning, and independent All Apps spacing in portrait and landscape. Workstation Dock icon adjustments require Workstation customization; the divider has its own switch. Ineffective Dock length-offset and icon-bottom-spacing controls have been removed from the settings UI.

### System UI and Security Center

Optional system integrations include:

- the app-caption menu containing split-screen, floating-window, and related controls;
- supported Security Center Game Toolbox, Video Toolbox, Global Dock, and All Apps surfaces.

Both glass integrations require the module master, glass master and corresponding feature switches, and a compatible target-system build. Security Center currently uses one glass feature switch; **separate blur and tint controls for Global Dock, All Apps, Game Toolbox, and Video Toolbox are not yet part of main**.

### MIUI system search and Gboard

MIUI system search can replace its main background with Liquid Glass, with local tint and blur as well as an **All Parameters** page for additional independent refraction and highlight options. Unset values inherit global glass settings. This requires the module master, glass master and the relevant app-glass switch.

Gboard supports glass for the floating keyboard and related toolbar surfaces, with independent tint and blur. Its **All Parameters** page supports additional optical overrides and a one-step reset to global inheritance. Glass requires the module master, glass master and the relevant app switch. Resize-after-handle-drag is independent of glass and requires successful identification of the keyboard bottom handle.

### Configuration, animation, and backup

Animation timing can be adjusted for workspace visibility, Dock icon return, press feedback, Dock size changes, and settings-page transitions.

The settings app supports:

- restoring the built-in default configuration;
- exporting the current configuration to JSON;
- importing a JSON configuration;
- separate backup and restore for widget-component hiding rules.

Fresh installs use the current built-in defaults; upgrades do not replace the whole current configuration with the default preset. **However, starting with 2.7.0, legacy preference keys are no longer migrated.** Settings saved only under retired keys fall back to current defaults. LiquidDock JSON backups and Launcher layout backups are separate.

## Compatibility

Main development and verification baseline:

| Item | Current range |
| --- | --- |
| HyperOS | Primarily tablet builds based on 3.0.307 and newer |
| System Launcher | `com.miui.home` release-4.50.x.x |
| LSPosed | A version with libxposed API 101 support |

Updates to Launcher, System UI, Security Center, Gboard, or MIUI Search may temporarily affect the corresponding integration.

## Installation

**Back up both your LiquidDock JSON configuration and Launcher layout** before upgrading. They are different backups. In particular, 2.7.0 does not migrate retired preference keys; keep a restorable workspace layout before changing the grid.

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
| `system` (System Framework) | Only required for the optional wallpaper GPU rendering switch; reboot after enabling |

4. Restart the affected processes or reboot the device.
5. Open LiquidDock and configure the features you want.

Most appearance settings update live when their hooks are already installed. Structural layout changes and newly enabled integrations may still require restarting Launcher, System UI, or an app. Use **Restart Scopes** in the settings UI to select affected processes.

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
- [docs/release-signing.md](docs/release-signing.md) — isolated Release build/signing flow and security boundaries

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
