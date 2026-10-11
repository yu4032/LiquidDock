# LiquidDock

<p align="center">
  <a href="./README.md">简体中文</a> · <strong>English</strong>
</p>

<p align="center">
  <a href="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml"><img alt="Build" src="https://github.com/yu4032/LiquidDock/actions/workflows/api101-build.yml/badge.svg"></a>
  <a href="https://github.com/yu4032/LiquidDock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/yu4032/LiquidDock"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/yu4032/LiquidDock"></a>
</p>

LiquidDock is an LSPosed module for HyperOS 3 tablets, with customization for the home screen, Dock, Recents, and Liquid Glass appearance.

<p align="center">
  <img width="3008" height="1880" alt="LiquidDock on the home screen" src="https://github.com/user-attachments/assets/cca03437-d897-45ed-adcc-149d07f1c7f6" />
</p>

## Features

### Home screen layout

- Set a custom home grid with 2–10 columns and 2–6 rows in landscape. Portrait swaps the dimensions and keeps a separate icon layout.
- Adjust horizontal spacing, top and bottom margins, row gaps, and page indicator position for each orientation, plus split-screen offsets.
- Change launcher and Dock icon sizes. Multi-column widgets can stretch with the grid or stay centered at their original width.
- Check for existing 4×2 widgets before saving a grid too small to fit them.

### Dock and Workstation

- Adjust Dock width, height, position, icon spacing, corners, and background blur.
- Customize the outline, squircle shape, Dock shadow, outline shadow, and divider.
- Change Dock resizing animation, hide the device interconnect shortcut, and exclude selected apps from the recent-app suggestions.
- In Workstation mode, adjust Dock icon positioning, home-screen horizontal offset, and All Apps spacing independently in landscape and portrait.

### Liquid Glass

Liquid Glass can be applied separately to the Dock, home-screen icons, Dock function icons, widgets, small and large folders, dragged items, shortcut menus, and Recents action buttons.

Adjust blur, thickness, refraction, chromatic dispersion, tint, opacity, lighting, highlights, shadows, and corners. Icons and small folders have their own highlight switches, separate from widgets and large folders. Backdrop sampling resolution and rendering frame rate are also adjustable.

<p align="center">
  <img width="704" height="440" alt="LiquidDock Liquid Glass" src="https://github.com/user-attachments/assets/caf50253-187d-4dbe-acfb-08ebc70769c4" />
</p>

### Widgets and folders

- Add glass backgrounds to widgets and adapt some dark widget content.
- Select individual widget parts to hide or turn white, including text and image elements. Hidden-part rules can be backed up separately.
- Configure glass, sizing, and corners independently for small and large folders.

### Recents and system surfaces

- Adjust Recents wallpaper blur, disable wallpaper dimming, and add glass to Clear All and device interconnect buttons.
- Hide the gesture navigation handle on the home screen and in Recents.
- Add glass to launcher uninstall/removal confirmation dialogs and the top app menu for split-screen and floating windows.
- Apply glass to supported Security Center sidebars. The two-stage edge gesture can also open the mini Dock, with an adjustable trigger distance.

### System Search and Gboard

- **MIUI System Search:** Glass background for the main search screen, with independent tint, blur, and optical settings.
- **Gboard:** Glass for the floating keyboard and related toolbars, with its own appearance settings and floating-keyboard handle behavior.

### Animation and settings

Adjust workspace visibility, Dock icon reveal, glass press feedback, menu dismissal, and settings page transitions. Most appearance changes can update live when the relevant hooks are loaded.

The settings app includes an iOS-style glass toggle, JSON configuration import/export, a default preset, and scope restart controls.
