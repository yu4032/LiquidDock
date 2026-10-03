# LiquidDock

[简体中文](README.md)

LiquidDock is an LSPosed module for HyperOS tablets, providing workspace grids, Dock customization, Recents backgrounds and Liquid Glass integrations. Features require their corresponding switches and scopes; entering a value does not enable its feature.

## Workspace layout

With the LiquidDock master and free-workspace-grid switches enabled, landscape supports 2–10 columns and 2–6 rows; portrait swaps the dimensions. Custom grid geometry, widget sizing adaptation and orientation layout memory belong to this path. Grid values do not enable it while the free grid is disabled.

Current main keeps the stock Pad squeeze planner for 1×1 icons and uses the generic rectangular planner for multi-cell items.

Custom icon size is independent: it requires the master and its own switch, without requiring the free grid. Workspace, Dock, small-folder, folder-content and Workstation app-page icons share one scale percentage, rather than having separate size controls. Ordinary All Apps and search are outside this shared scaling path.

## Dock and Recents

All entries below require the LiquidDock master switch, but use different feature switches.

| Setting | Conditions and scope |
| --- | --- |
| Dock dimensions, position, icon spacing and related parameters | Dock customization enabled; a supported glass background that owns the Dock uses its glass rendering path |
| Dock stroke | Stroke enabled; independent of Dock dimension customization |
| Whole-Dock shadow | Both Dock customization and shadow enabled |
| Stroke shadow | Both stroke and stroke shadow enabled |
| Custom divider | Divider enabled; independent of Dock dimension customization |
| Hide phone-interconnect shortcut | Its own switch; hides the entry without changing system connectivity |
| Recent-app blacklist | Filters system Dock recommendations without hiding workspace icons; independent of Dock customization |
| Workstation Dock dimensions and icon geometry | Workstation customization enabled, with corresponding surfaces in system Workstation mode |
| Recents background blur | Master switch; Liquid Glass is not required |
| Suppress Recents wallpaper dimming | Its own option; Liquid Glass is not required, and system blur and transitions remain |

Dock glass and Dock dimension customization have different switches. Recents background adjustments are also separate from glass on its action buttons.

## Liquid Glass

Launcher glass requires both the LiquidDock and Liquid Glass master switches. Workspace icons, Dock utility icons, widgets, small folders, large folders and Recents action buttons also have their own switches. Dragged glass respects the corresponding item-type switches rather than applying automatically to every dragged object.

Blur, refraction, dispersion, tint and highlight values style enabled glass surfaces; changing values does not enable a feature. Surfaces with local appearance settings select global or local values according to their configuration.

Shortcut-menu glass and white text/icons have separate switches. Both require the two master switches, but text mode does not require shortcut-menu glass.

Uninstall, remove and second-confirmation dialogs additionally require dialog glass. Native dark styling also requires its dark-mode switch and an available theme bridge in the target launcher.

Widget internal-background hiding rules are separate from glass appearance. Actual workspace widgets can be scanned to select background targets, with separate rule import/export.

### Additional surfaces

| Glass feature | Conditions |
| --- | --- |
| App-caption window-control menu | Master + glass master + caption-glass switch; supported system animation integration |
| Security Center sidebar | Master + glass master + Security Center glass; supported `:ui` process and material integration |
| MIUI Search main background | Master + glass master + corresponding third-party glass configuration enabled |
| Gboard floating keyboard and supported toolbars | Master + glass master + corresponding third-party glass configuration enabled |

These integrations target specific surfaces rather than replacing every background in an app.

Gboard's resize-after-handle-drag behavior uses an independent setting. Its touch policy does not read glass switches and requires successful bottom-handle identification and adapter installation. Disabling automatic resize preserves dragging and prevents release from entering the resize interface.

Wallpaper GPU rendering integration requires the master, glass master and wallpaper-flicker-fix switches together, plus the System Framework (`system`) scope and a device reboot.

## Installation and applying changes

Download an official APK from [GitHub Releases](https://github.com/yu4032/LiquidDock/releases), install it and enable it in LSPosed with libxposed API 101 support. The code primarily targets HyperOS 3.0.307+ tablet paths and `com.miui.home` release-4.50.x.x. Other versions require compatible target structures.

| Scope | Purpose |
| --- | --- |
| `com.miui.home` | Launcher, Dock, grid, Recents and launcher glass |
| `com.android.systemui` | Unlock transition notifications and optional app-caption glass |
| `com.miui.securitycenter` | Security Center sidebar integration |
| `com.google.android.inputmethod.latin` | Gboard integration |
| `com.android.quicksearchbox` | MIUI Search integration |
| `system` (System Framework) | Optional wallpaper GPU rendering integration |

A scope permits the module to run in that process; it does not enable feature switches. Reboot after installation. Layout, icon size and other settings read at initialization require restarting the affected process. Some visual states update at runtime, but not every parameter applies immediately.

Back up the system launcher layout before upgrading or changing the grid. LiquidDock JSON import/export saves module configuration, not launcher layout. Widget-background hiding rules have separate backups. Empty configurations receive built-in defaults; ordinary upgrades do not replace existing settings with defaults. Manually restoring defaults changes settings.

## Feedback and documentation

Include system and target-app versions, the switches actually enabled and reproduction steps. Enable diagnostic logging when needed.

- [Feature reference](FEATURES.md)
- [Architecture](ARCHITECTURE.md) and [Hook map](HOOKS.md)
- [Development rules](CONTRIBUTING.md) and [signing workflow](docs/release-signing.md)
- [Changelog](CHANGELOG.md)

Main may contain unreleased fixes; consult the source matching the downloaded release. Historical plans and verification records reflect their original development state.

LiquidDock is a community project, unaffiliated with Xiaomi or LSPosed. Visual references include Prismal; the runtime uses LSPosed / libxposed, with HyperOS integration references from HyperCeiler.

[GNU General Public License v3.0](LICENSE)
