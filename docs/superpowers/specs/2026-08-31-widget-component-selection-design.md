# Widget Component Selection Design

LiquidDock discovers the actual component structure of widgets already present on the Workspace and lets the user choose which visual components should be suppressed behind LiquidDock glass.

## Stable selectors

RemoteViews uses provider component + resource entry name as its default selector. Class name is optional validation and hierarchy path is diagnostic only. MAML uses productId + element name, with class name as optional validation.

## Discovery and configuration

The injected Launcher process writes discovered descriptors to a dedicated API101 Remote Preferences group named `widget_components`. The settings UI reads this catalog. User selections are stored in normal LiquidDock config SharedPreferences as `widget_hidden_components`; the existing app-to-API101 synchronization makes those selections visible to Launcher runtime.

## Runtime ownership

RemoteViews selected nodes are set to `INVISIBLE` so provider layout geometry is preserved. Their original visibility is restored when LiquidDock releases widget material. MAML selected ScreenElements use `show(false)` and retain/restore their original `mShow`. Missing targets fail open.

Bundled MAML XML rules remain as default compatibility behavior and are not replaced by user rules.
