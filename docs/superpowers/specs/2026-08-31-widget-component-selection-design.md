# Widget Component Selection Design

LiquidDock discovers the actual component structure of widgets already present on the Workspace and lets the user choose which visual components should be suppressed behind LiquidDock glass.

## Stable selectors

RemoteViews uses provider component + resource entry name as its default selector. Class name is an exact safety validation and hierarchy path is diagnostic only. MAML uses productId + element name, with class name as exact safety validation.

## Discovery upstream

The hooked Launcher process treats API101 Remote Preferences as read-only. Discovery therefore does **not** write a Remote Preferences group from MIUI Home.

The module app generates a random `widget_discovery_token` in its ordinary config preferences. Existing API101 config synchronization makes that token readable by Launcher. When a new descriptor is discovered, Launcher sends an explicit component-targeted broadcast to `WidgetDiscoveryReceiver` containing the descriptor and token. The receiver compares the token with `MessageDigest.isEqual` and persists accepted descriptors in the module app's private `widget_components` SharedPreferences file under `catalog`.

The GUI reads this local catalog directly. RemoteViews publication is de-duplicated by resource name + class within a scanned content tree; the direct RemoteViews content root is diagnostic-only and is never offered as selectable.

## Selection downstream

User selections are stored in ordinary LiquidDock default preferences as the StringSet `widget_hidden_components`. The existing app-to-API101 config synchronization sends those selectors down to Launcher runtime. No new mutable runtime store is introduced in the target process.

## Runtime ownership

RemoteViews selected nodes are set to `INVISIBLE` so provider layout geometry is preserved. Their original visibility is restored when LiquidDock releases widget material. MAML selected ScreenElements use `show(false)` and retain/restore their original `mShow`. Missing targets or class mismatches fail open.

Bundled MAML XML rules remain as compatibility defaults in this version. User claims are applied after bundled claims and restored before bundled claims so original provider state remains recoverable.
