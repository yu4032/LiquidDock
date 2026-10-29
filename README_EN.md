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

<details>
<summary><strong>Screenshots</strong></summary>

<p align="center">Select an image to view the original.</p>

<p align="center">
<strong>Home-screen overview</strong><br />
<a href="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6"><img width="640" alt="Home-screen overview" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" /></a>
</p>

<table align="center">
<tr>
<td width="50%" align="center" valign="top">
<p><strong>Shortcut menu</strong></p>
<a href="https://github.com/user-attachments/assets/4a8104b4-d4de-4319-b6d8-99b3d9603b20"><img width="300" alt="Shortcut menu" src="https://github.com/user-attachments/assets/4a8104b4-d4de-4319-b6d8-99b3d9603b20" /></a>
</td>
<td width="50%" align="center" valign="top">
<p><strong>Launcher dialog</strong></p>
<a href="https://github.com/user-attachments/assets/7b4a152b-bb7a-4b36-a8de-841ec0ea2618"><img width="300" alt="Launcher dialog" src="https://github.com/user-attachments/assets/7b4a152b-bb7a-4b36-a8de-841ec0ea2618" /></a>
</td>
</tr>
<tr>
<td width="50%" align="center" valign="top">
<p><strong>Security Center sidebar</strong></p>
<a href="https://github.com/user-attachments/assets/ec773cda-b8cf-4ac8-9a78-afce2437519c"><img height="280" alt="Security Center sidebar" src="https://github.com/user-attachments/assets/ec773cda-b8cf-4ac8-9a78-afce2437519c" /></a>
</td>
<td width="50%" align="center" valign="top">
<p><strong>System Search</strong></p>
<a href="https://github.com/user-attachments/assets/41c5e987-bc6d-45d3-ac2f-c14b44d61057"><img width="300" alt="System Search" src="https://github.com/user-attachments/assets/41c5e987-bc6d-45d3-ac2f-c14b44d61057" /></a>
</td>
</tr>
</table>

<p align="center">
<strong>Glass details</strong><br />
<a href="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4"><img width="440" alt="Glass details" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" /></a>
</p>

<p align="center">
<strong>App control menu</strong><br />
<a href="https://github.com/user-attachments/assets/9f39ca0c-3fd3-4cc4-ba6c-d8af01354788"><img width="640" alt="App control menu" src="https://github.com/user-attachments/assets/9f39ca0c-3fd3-4cc4-ba6c-d8af01354788" /></a>
</p>

<p align="center">
<strong>Recents action buttons</strong><br />
<a href="https://github.com/user-attachments/assets/badcd56c-b013-4a12-a018-bc505ee943f2"><img width="640" alt="Recents action buttons" src="https://github.com/user-attachments/assets/badcd56c-b013-4a12-a018-bc505ee943f2" /></a>
</p>

</details>

## What's new in v2.7.6

- **Separate tint and solid fill:** Tint adjusts the background color while retaining texture; solid fill uses separate RGB and 0–255 opacity controls. Fill defaults to transparent, and expanded folders can override it.
- **Glass-shaped icon clipping:** Preprocessed display assets preserve the glass outline during home-screen rendering and app-return animations.
- **Settings organization and search:** Global, folder, menu, keyboard, and grid settings use feature-based groups. Search navigates to individual controls.
- **API 102 and graphics cleanup:** libxposed API 102 provides atomic hook callback replacement; transient shortcut and drag glass follow compositor-aware EGL resource teardown.
- **Readable widget part titles:** The widget component picker displays readable labels for background rules.

See [CHANGELOG.md](CHANGELOG.md) for the full history.

## Main features

### Liquid Glass

**Two independent color effects:** **Tint** multiplies the sampled backdrop color while retaining texture. **Solid fill** overlays the body with chosen RGB at 0–255 opacity (default 0). Full fill opacity covers the sampled texture; output transmittance and animation opacity remain separate.


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

Glass materials support blur, refraction, dispersion, tint, solid fill, brightness, lighting, highlights, shadows, and corner radii. Outlines and soft shadows can inherit global parameters or use per-component settings.

Loaded glass hooks support live material adjustments. Restart the relevant scope after first installing a hook or changing structural settings such as the grid.

### Home screen layout

Enable “Free Home Screen Grid” to choose **2–10 columns and 2–6 rows** in landscape, with the dimensions swapped in portrait. Rotation-based position mapping is disabled, so icon and widget positions are saved independently in landscape and portrait. Each orientation has its own controls for:

- horizontal spacing;
- top and bottom spacing;
- row gaps;
- page-indicator position;
- widget sizing.

The previous placement restrictions on **2×1, 2×2, and 4×2 widgets** have also been removed, allowing these widgets to be placed at any grid position.

When shrinking the grid, the app checks whether existing 4×2 widgets fit and prompts for adjustments when necessary. Restart Launcher after grid changes.

A separate **80%–120% icon-size control** adjusts supported home-screen, Dock, folder-preview, and Workstation All Apps icons; the ordinary app drawer and search results keep their native sizes.

### Dock

Enable Dock customization to change its dimensions, position, and icon spacing. Outline, shadow, and divider settings are also available:

- width and height;
- distance from the bottom edge;
- icon spacing;
- blur and corner radius;
- outline;
- whole-Dock shadow and outline shadow;
- divider.

Hide the Dock's phone-interconnect shortcut independently of the system's interconnect and transfer features.

A recent-app blacklist can prevent selected apps from appearing in the Dock's recent-app suggestions. **Dock → Behavior → Hide Dock recent-task icons** can instead hide the entire group of suggested recent-task icons. It is **off by default**, does not remove pinned Dock icons or change system Recents, and requires restarting Launcher.

**Native Dock Shadow** applies only with Dock customization enabled and Liquid Glass disabled. When Liquid Glass is enabled, a separate **Liquid Glass Shadow** page controls Prismal outer shadows and light-responsive outlines.

### Widgets and folders

Widgets support glass backgrounds and dark-content adaptation. In **Widget Component Management**, identify individual desktop widget parts by their **readable titles**:

- **Hide background parts:** Select internal widget background areas to hide. These rules can be backed up and restored separately.
- **Whiten parts for dark themes:** Turn selected RemoteViews text white, or display images as white shapes while preserving their original transparency.
- **Rule management:** Switch an individual component between hiding and whitening; each rule type is stored separately.

Precise whitening supports **RemoteViews TextView / ImageView**. MAML widgets use background-hiding and general material adaptation. Restart Launcher after modifying part rules.

Small and large folders have separate glass switches, sizes, and corner-radius settings.

Expanded folders support independent Liquid Glass backgrounds with native opening and closing animations. Under **Appearance → Glass Components → Folders**, configure blur, refraction, thickness, tint, and **solid-fill RGB and opacity**. Set a fill channel to **−1** to inherit the global value or 0–255 to override it. Native blur and the fade duration of other glass elements can also be adjusted. Restart Launcher when first enabling expanded-folder glass.

On Launcher 4.50, four-preview and nine-preview large folders support a real **2×2 home-screen footprint**. Enable real large folders and titles per folder in the launcher's native folder settings. Under **Appearance → Folders**, adjust preview icon size (**60%–110%, 100% by default**) and toggle the native outline. Preview icon sizing works independently of the glass switch.

A separate modified Mingou Launcher compatibility switch (off by default) restores marked 2×2 large-folder footprints.

### Shortcut menus and dialogs

The long-press shortcut menu supports a glass background, dark-theme text adaptation, and independent tint, solid fill, blur, and optical parameters. Global inheritance can be restored with one action.

Launcher uninstall, remove, and second-confirmation dialogs can also use glass backgrounds, with controls for:

- keeping or removing background dimming;
- native dark mode;
- independent tint;
- independent blur;
- restoring inheritance from the global glass appearance.

The dialog's **All Parameters** page includes refraction, highlights, and solid fill, with a one-tap reset to global inheritance.

### Recents and Workstation

Recents supports:

- adjustable wallpaper blur;
- disabling wallpaper dimming;
- glass backgrounds for the “Clear All” and device-interconnect buttons.

**Workstation taskbar mode** extends the Dock background to the left, right, and bottom screen edges, removing the corresponding rounded corners and optical highlights. It works with both Liquid Glass and the native Dock background while preserving the vendor fly-in/fly-out animation. Workstation mode also provides a **vertical offset for the entire Dock including its background**, separate **vertical icon offsets for portrait and landscape**, and Dock icon glass corner radius.

Besides separate All Apps horizontal and top/bottom spacing for both orientations, Workstation offers **independent portrait and landscape All Apps page-indicator vertical offsets** (-160 to 160 dp; positive moves down). They do not affect the ordinary home-screen indicator. Workstation Dock icon settings require Workstation customization to be enabled; the divider has its own switch.

### System UI and Security Center

Optional system integrations include:

- the app's top menu for split-screen, floating windows, and related controls;
- supported Security Center sidebars, including Game Toolbox, Video Toolbox, Global Dock, and All Apps, with support for the slide-and-hold gesture.

The app-top control menu has separate glass, blur, tint, and optical settings; it need not match the global glass appearance.

Both glass integrations require the LiquidDock master switch, the Liquid Glass master switch, and their respective feature switches. They also depend on compatibility with the installed system components.

### MIUI System Search and Gboard

MIUI System Search supports an independent glass background, tint, and blur. **All Parameters** adds refraction, highlights, and solid-fill controls; unset parameters inherit the global configuration.

Gboard supports floating-keyboard and toolbar glass with independent tint, solid fill, blur, and optical settings. **All Parameters** restores global inheritance. A separate setting controls automatic resize after dragging the bottom handle.

### Configuration, animation, and backups

Animation timing can be adjusted for workspace visibility, **unlock-time workspace-glass reveal**, Dock icon reveal, the fade of other glass elements when opening folders, press feedback, Dock resizing, and settings-page transitions. Dock icon glass also fades in during the final part of the native app-to-home return animation. An optional **Disable wallpaper zoom** switch constrains Launcher's wallpaper zoom target during app and Recents transitions without changing workspace animation; first-time installation requires restarting Launcher. Effectiveness depends on the vendor Launcher implementation.

An optional **Hide gesture handle on Home/Recents** setting (off by default) hides the system gesture bar while those screens are shown, returning control to the system upon leaving. Its first-time hook installation requires restarting SystemUI.

The settings app uses MIUIX and Prismal styling with feature-based groups. **Pull down on any of the four top-level pages to search** for tint, solid fill, corners, folders, grid, outlines, and shadows, then navigate to the corresponding settings cell. Sliders support live dragging, numeric entry, plus/minus, and reset actions; the bottom glass capsule supports swipe navigation.

The **iOS Glass Effect** switch controls the settings app's appearance. The Restart Scopes dialog supports scrolling and multiple selections, independently of the desktop glass switches.

The settings app supports:

- restoring the built-in default configuration;
- exporting the current configuration as JSON;
- importing a JSON configuration;
- separately backing up and restoring widget hiding rules.

New installations load the built-in defaults; upgrades retain saved user settings.

## Compatibility

Compatibility:

| Item | Current range |
| --- | --- |
| HyperOS | HyperOS 3 tablet builds (3.0.307 and newer); HyperOS 4 integration is developed on a separate branch |
| System Launcher | `com.miui.home` release-4.50.x.x |
| LSPosed | A version supporting libxposed API 102 |

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

## Usage notes

LiquidDock uses LSPosed to customize Launcher, SystemUI, and selected applications. Back up the configuration and keep a recovery method available when updating.

LiquidDock is independently maintained by its community.

## Credits

- [Compose Miuix](https://github.com/compose-miuix-ui/miuix) — MIUIX Compose UI framework
- [PrismalAGSL](https://github.com/styropyr0/PrismalAGSL) — Liquid Glass components for the settings UI
- [AndroidX / Jetpack](https://source.android.com/docs/setup/about/licenses) — Android UI and foundation components
- [LSPosed / libxposed](https://github.com/LSPosed/LSPosed) — module hooking API and runtime
- [HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) — HyperOS module development reference
- [Prismal](https://github.com/styropyr0/Prismal) — Liquid Glass optical model reference

## License

[GNU General Public License v3.0](LICENSE)
