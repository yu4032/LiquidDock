from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if new in text:
        print(f'already patched: {path}')
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected one anchor, got {count}: {old[:80]!r}')
    p.write_text(text.replace(old, new, 1))

# Config schema declarations.
replace_once(
    'src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java',
    '''        public static final ConfigKey<Boolean> WIDGET_ADAPTATION = bool(\n                "grid_widget_adaptation", false, false, false, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Boolean> MARGINS_DP = bool(''',
    '''        public static final ConfigKey<Boolean> WIDGET_ADAPTATION = bool(\n                "grid_widget_adaptation", false, false, false, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Boolean> ICON_SIZE_ENABLED = bool(\n                "launcher450_icon_size_enabled", false, false, false, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> ICON_SIZE_PERCENT = integer(\n                "launcher450_icon_size_percent", 100, 100, 100, 80, 120,\n                ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Boolean> MARGINS_DP = bool(''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java',
    '''        add(keys, Grid.ENABLED, Grid.PROFILE, Grid.WIDGET_ADAPTATION,\n                Grid.MARGINS_DP, Grid.MARGINS_OFFSET,''',
    '''        add(keys, Grid.ENABLED, Grid.PROFILE, Grid.WIDGET_ADAPTATION,\n                Grid.ICON_SIZE_ENABLED, Grid.ICON_SIZE_PERCENT,\n                Grid.MARGINS_DP, Grid.MARGINS_OFFSET,''')

# Runtime config.
replace_once(
    'src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java',
    '''    static final class Grid {\n        final boolean enabled, widgetAdaptation, dp, offsets;\n        final float landscapeHorizontal, landscapeTop, landscapeBottom, landscapeRowGap;''',
    '''    static final class Grid {\n        final boolean enabled, widgetAdaptation, iconSizeEnabled, dp, offsets;\n        final int iconSizePercent;\n        final float landscapeHorizontal, landscapeTop, landscapeBottom, landscapeRowGap;''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java',
    '''            widgetAdaptation = c.b(ConfigSchema.Grid.WIDGET_ADAPTATION.name(),\n                    ConfigSchema.Grid.WIDGET_ADAPTATION.runtimeFallback());\n            dp = c.b(ConfigSchema.Grid.MARGINS_DP.name(),''',
    '''            widgetAdaptation = c.b(ConfigSchema.Grid.WIDGET_ADAPTATION.name(),\n                    ConfigSchema.Grid.WIDGET_ADAPTATION.runtimeFallback());\n            iconSizeEnabled = c.b(ConfigSchema.Grid.ICON_SIZE_ENABLED.name(),\n                    ConfigSchema.Grid.ICON_SIZE_ENABLED.runtimeFallback());\n            iconSizePercent = Math.max(Launcher450IconSizePolicy.MIN_PERCENT,\n                    Math.min(Launcher450IconSizePolicy.MAX_PERCENT, c.i(\n                            ConfigSchema.Grid.ICON_SIZE_PERCENT.name(),\n                            ConfigSchema.Grid.ICON_SIZE_PERCENT.runtimeFallback())));\n            dp = c.b(ConfigSchema.Grid.MARGINS_DP.name(),''')

# Runtime installation.
replace_once(
    'src/main/java/com/hellovoid/liquiddock/ModuleMain.java',
    '''            DockMirrorShortcutHook.install(classLoader);\n            DockNativeShadowBridge.install(classLoader, runtimeConfig.dock);\n            new MainHook().install(classLoader);''',
    '''            DockMirrorShortcutHook.install(classLoader);\n            DockNativeShadowBridge.install(classLoader, runtimeConfig.dock);\n            Launcher450IconSizeHook.install(classLoader,\n                    runtimeConfig.enabled && runtimeConfig.grid.iconSizeEnabled,\n                    runtimeConfig.grid.iconSizePercent);\n            new MainHook().install(classLoader);''')

# Compose spec.
replace_once(
    'src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt',
    '''private val gridSpecs = listOf(''',
    '''private val launcher450IconSizeSpec = IntSpec(\n    ConfigSchema.Grid.ICON_SIZE_PERCENT,\n    "图标大小",\n    "%",\n    summary = "Launcher 4.50：工作区、Dock 与小文件夹共用；100% 为系统默认",\n)\n\nprivate val gridSpecs = listOf(''')
replace_once(
    'src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt',
    '''private fun GridPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {\n    var customGrid by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Grid.ENABLED.name(), ConfigSchema.Grid.ENABLED.uiDefault())) }\n    val profileLabels = stringArrayResource(R.array.home_grid_profile_entries)''',
    '''private fun GridPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {\n    var customGrid by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Grid.ENABLED.name(), ConfigSchema.Grid.ENABLED.uiDefault())) }\n    var launcher450IconSizeEnabled by remember {\n        mutableStateOf(prefs.getBoolean(\n            ConfigSchema.Grid.ICON_SIZE_ENABLED.name(),\n            ConfigSchema.Grid.ICON_SIZE_ENABLED.uiDefault(),\n        ))\n    }\n    val profileLabels = stringArrayResource(R.array.home_grid_profile_entries)''')
replace_once(
    'src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt',
    '''        item { SmallTitle(stringResource(R.string.category_landscape)) }\n        item { SettingsCard { gridSpecs.filter''',
    '''        item { SmallTitle("图标大小 · Launcher 4.50") }\n        item {\n            SettingsCard {\n                BooleanSetting(\n                    prefs,\n                    ConfigSchema.Grid.ICON_SIZE_ENABLED,\n                    "自定义图标大小",\n                    "仅作用于工作区、Dock 与小文件夹；重启桌面后生效",\n                    masterEnabled,\n                ) { launcher450IconSizeEnabled = it }\n                IntSetting(\n                    prefs,\n                    launcher450IconSizeSpec,\n                    masterEnabled && launcher450IconSizeEnabled,\n                )\n            }\n        }\n        item { SmallTitle(stringResource(R.string.category_landscape)) }\n        item { SettingsCard { gridSpecs.filter''')

# Static architecture test allowlist.
replace_once(
    'src/test/java/com/hellovoid/liquiddock/RuntimeBehaviorTestPolicyContractTest.java',
    '''            "LauncherGlassStaticBoundaryTest.java",\n            "LauncherGlassVendorMaterialSuppressionContractTest.java",''',
    '''            "Launcher450IconSizeContractTest.java",\n            "LauncherGlassStaticBoundaryTest.java",\n            "LauncherGlassVendorMaterialSuppressionContractTest.java",''')
