# LiquidDock4

<p align="center">
  <a href="./README.md">简体中文</a> · <strong>English</strong>
</p>

<p align="center">
  <a href="https://github.com/yu4032/LiquidDock/actions/workflows/build.yml"><img alt="Build" src="https://github.com/yu4032/LiquidDock/actions/workflows/build.yml/badge.svg"></a>
  <a href="https://github.com/yu4032/LiquidDock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/yu4032/LiquidDock"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/yu4032/LiquidDock"></a>
</p>

An LSPosed module for **HyperOS 4 / HyperOS 3** tablets that customizes the home screen, Dock, and Recents, bringing native Soft Glass and Liquid Glass styling alongside flexible grid, folder, widget, and Workstation layouts.

- **HyperOS 4 (`v3.0+` / `main`)**: Combines native-layer adaptation for the `hyos_spawner`-based System Launcher with independent-process LSPosed hooks.
- **HyperOS 3 (legacy launcher)**: Supports `com.miui.home release-4.50.x.x` (the legacy archive is preserved on the [`archive/2.7.9`](https://github.com/yu4032/LiquidDock/tree/archive/2.7.9) branch).

<p align="center">
  <a href="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6"><img width="680" alt="Home-screen overview" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" /></a>
</p>

<details>
<summary><strong>More Screenshots</strong></summary>

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

## Key Features

### HyperOS 4 (OS4)

- **Native Soft Glass**: Customize the launcher's native Soft Glass optical parameters (edge bands, reflections, dark-area fill light, directional light), component glass, drag materials, adaptive icon corner radii, and MAML widget materials.
- **Home Grid & Large Folders**: Configure custom grid dimensions and independent landscape/portrait margins and page-indicator offsets; supports tablet large folders, custom inner folder grids, 4×2 symmetric preview insets, icon alignment, and title controls.
- **Widget Enhancements**: Quick **1×1 ↔ 2×2** and **2×1 ↔ 4×2** size switching via the long-press menu, widget stretch prevention, and horizontal offset tuning.
- **Dock & Workstation**: Customize Dock geometry, position, and divider styling; supports Workstation Taskbar mode with floating overhang, Dock/icon vertical offsets, independent landscape/portrait All Apps margins, and background blur.
- **Independent-Process Glass & Safety**: Extends Liquid Glass to Recents buttons, app top menus, Security Center sidebars, System Search, and Gboard; unlocks up to 6 split-screen stages; includes a live Hook Monitor and **automatic safe mode** (triggers if Launcher crashes 8 times within 20 seconds).

### HyperOS 3 (OS3)

- **Full-Component Liquid Glass**: Applies unified Liquid Glass across the Dock, icons, widgets, small/large folders, shortcut menus, dialogs, drag states, and system surfaces, with global inheritance or per-component overrides for blur, refraction, dispersion, highlights, outlines, and shadows.
- **Free Home Grid**: Supports **2–10 columns and 2–6 rows** in landscape with independent portrait layouts, removes rotation mapping and 2×1/2×2/4×2 placement restrictions, and adds 80%–120% home-icon scaling.
- **Dock & Behavior**: Customize size, spacing, corner radius, shadows, and dividers; hide the device-interconnect entry or filter recent apps with a blacklist.
- **RemoteViews & Folder Details**: Node-level RemoteViews background hiding and precise tinting/whitening, true 2×2 large folders, and independent Liquid Glass backgrounds for expanded folders.
- **System Integration & Animations**: Hide the gesture pill, disable wallpaper zoom, and tune workspace fade-in and transition speeds.

> See [FEATURES.md](FEATURES.md) for the full list of options and detailed parameter explanations.

## Compatibility

| Item | Requirement / Scope | Verified Environment |
| --- | --- | --- |
| HyperOS | **HyperOS 4 (OS4)** / **HyperOS 3 (3.0.307+)** | `OS4.0.3.0.XAOCNXM` |
| Android | Android 13+ (`minSdk = 33`) | Android 17 (SDK `37`) |
| Architecture | `arm64-v8a` (16KB page-aligned) | `arm64-v8a` |
| System Launcher | • **OS4**: Flutter/Dart + Rust AOT<br>• **OS3**: `release-4.50.x.x` | `RELEASE-8.01.02.8583` / `8602` / `9239` |
| Framework & Root | LSPosed (libxposed API 102) + Root (`su`) | LSPosed `v2.2.1` + Zygisk Next `1.5.3` |

## Quick Start

1. Download and install the latest APK from [GitHub Releases](https://github.com/yu4032/LiquidDock/releases).
2. Enable **LiquidDock4** in **LSPosed** and select the required scopes:

| Scope | Purpose |
| --- | --- |
| `com.miui.home` | **Required**: Home grid, Dock, folders, widgets, Workstation mode, and Soft Glass |
| `system` | **Required**: Widget layout constraints and system animation sync (reboot required on change) |
| `com.android.systemui` | Optional: Gesture pill hiding, split-screen/handle menus, and system transitions |
| `com.miui.securitycenter` | Optional: Security Center sidebar glass |
| `com.google.android.inputmethod.latin` | Optional: Gboard floating keyboard glass |
| `com.android.quicksearchbox` | Optional: MIUI System Search glass |

3. Restart the selected scopes or reboot your device.
4. Open **LiquidDock4** to enable and customize features.

> **Tip**: After updating or clearing Launcher data, tap **Restart Launcher** in the app to re-sync native settings. If safe mode is triggered, you can exit it manually from the app.

## Documentation

- [FEATURES.md](FEATURES.md) — Full feature and settings reference
- [CHANGELOG.md](CHANGELOG.md) — Release history
- [CONTRIBUTING.md](CONTRIBUTING.md) — Development guidelines
- [docs/release-signing.md](docs/release-signing.md) — Release build and signing guide

## Credits

- [Compose Miuix](https://github.com/compose-miuix-ui/miuix) — MIUIX Compose UI components
- [PrismalAGSL](https://github.com/styropyr0/PrismalAGSL) / [Prismal](https://github.com/styropyr0/Prismal) — Liquid Glass UI components and optical model reference
- [LSPosed / libxposed](https://github.com/LSPosed/LSPosed) — Hooking framework and API
- [HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) — HyperOS module development reference

## License

[GNU General Public License v3.0](LICENSE)
