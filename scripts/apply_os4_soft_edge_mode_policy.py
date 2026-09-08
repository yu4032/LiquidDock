from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one anchor, found {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


def write_new(path: str, content: str) -> None:
    p = Path(path)
    if p.exists():
        raise SystemExit(f"{path}: expected file to be absent")
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content)


# 1) Persisted mode switch, default ON, registered with the schema.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''        public static final ConfigKey<Integer> PRISMAL_PLAIN_HIGHLIGHT = integer(\n                "liquid_prismal_plain_highlight", 8, 8, 8, 0, 100, ConfigKey.ExportMode.ALWAYS);\n        // OS4 background-driven edge controls. Pixel distances are direct logical output pixels;\n''',
    '''        public static final ConfigKey<Integer> PRISMAL_PLAIN_HIGHLIGHT = integer(\n                "liquid_prismal_plain_highlight", 8, 8, 8, 0, 100, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Boolean> OS4_SOFT_EDGE_ENABLED = bool(\n                "liquid_os4_soft_edge", true, true, true, ConfigKey.ExportMode.ALWAYS);\n        // OS4 background-driven edge controls. Pixel distances are direct logical output pixels;\n''')
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''                Glass.PRISMAL_VIBRANCY, Glass.PRISMAL_PLAIN_HIGHLIGHT,\n                Glass.OS4_EDGE_WIDTH_PX, Glass.OS4_REFLECT_OFFSET_PX,\n''',
    '''                Glass.PRISMAL_VIBRANCY, Glass.PRISMAL_PLAIN_HIGHLIGHT,\n                Glass.OS4_SOFT_EDGE_ENABLED,\n                Glass.OS4_EDGE_WIDTH_PX, Glass.OS4_REFLECT_OFFSET_PX,\n''')

# 2) One policy owns runtime mutual exclusion and the matching UI visibility semantics.
write_new(
    "src/main/java/com/hellovoid/liquiddock/Os4EdgeModePolicy.java",
    '''package com.hellovoid.liquiddock;\n\nimport com.hellovoid.prismal.PrismalHighlightProfile;\n\n/** Mutual exclusion between the OS4 background-driven edge and legacy highlight components. */\nfinal class Os4EdgeModePolicy {\n    private Os4EdgeModePolicy() {}\n\n    static PrismalHighlightProfile effectiveHighlights(\n            boolean os4SoftEdgeEnabled, PrismalHighlightProfile configured) {\n        if (configured == null) configured = PrismalHighlightProfile.ALL_ENABLED;\n        return os4SoftEdgeEnabled ? PrismalHighlightProfile.NONE_ENABLED : configured;\n    }\n\n    static boolean showOs4Controls(boolean os4SoftEdgeEnabled) {\n        return os4SoftEdgeEnabled;\n    }\n\n    static boolean legacyHighlightsEnabled(boolean os4SoftEdgeEnabled) {\n        return !os4SoftEdgeEnabled;\n    }\n}\n''')

# 3) Shared immutable all-off profile avoids mutating saved legacy choices.
replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalHighlightProfile.java",
    '''    public static final PrismalHighlightProfile ALL_ENABLED =\n            new PrismalHighlightProfile(true, true, true, true, true, true, true, true, true);\n\n''',
    '''    public static final PrismalHighlightProfile ALL_ENABLED =\n            new PrismalHighlightProfile(true, true, true, true, true, true, true, true, true);\n    public static final PrismalHighlightProfile NONE_ENABLED =\n            new PrismalHighlightProfile(false, false, false, false, false, false, false, false, false);\n\n''')

# 4) Runtime config resolves the saved legacy profiles, then applies the mutual-exclusion mode.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''        final boolean prismalShowNormals;\n        final PrismalHighlightProfile launcherHighlightProfile, largeSurfaceHighlightProfile;\n''',
    '''        final boolean prismalShowNormals, os4SoftEdgeEnabled;\n        final PrismalHighlightProfile launcherHighlightProfile, largeSurfaceHighlightProfile;\n''')
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''            folderCornerRadiusDp = legacyFolderRadius;\n            launcherHighlightProfile = LauncherHighlightPreferences.read(c);\n            largeSurfaceHighlightProfile = LauncherHighlightPreferences.readLargeSurfaces(c);\n            blur = c.f(ConfigSchema.Glass.BLUR.name(), ConfigSchema.Glass.BLUR.runtimeFallback());\n''',
    '''            folderCornerRadiusDp = legacyFolderRadius;\n            os4SoftEdgeEnabled = c.b(ConfigSchema.Glass.OS4_SOFT_EDGE_ENABLED.name(),\n                    ConfigSchema.Glass.OS4_SOFT_EDGE_ENABLED.runtimeFallback());\n            PrismalHighlightProfile configuredCompact = LauncherHighlightPreferences.read(c);\n            PrismalHighlightProfile configuredLarge = LauncherHighlightPreferences.readLargeSurfaces(c);\n            launcherHighlightProfile = Os4EdgeModePolicy.effectiveHighlights(\n                    os4SoftEdgeEnabled, configuredCompact);\n            largeSurfaceHighlightProfile = Os4EdgeModePolicy.effectiveHighlights(\n                    os4SoftEdgeEnabled, configuredLarge);\n            blur = c.f(ConfigSchema.Glass.BLUR.name(), ConfigSchema.Glass.BLUR.runtimeFallback());\n''')

# 5) GUI: OS4 controls are a conditional group; legacy highlight entry/page is disabled in OS4 mode.
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''private val liquidSpecs = listOf(\n''',
    '''private val os4EdgeSpecs = listOf(\n    IntSpec(ConfigSchema.Glass.OS4_EDGE_WIDTH_PX, "OS4 边缘带宽度", "px"),\n    IntSpec(ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX, "OS4 反射偏移", "px"),\n    IntSpec(ConfigSchema.Glass.OS4_REFLECTION_STRENGTH, "OS4 反射强度", "%"),\n    IntSpec(ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN, "OS4 暗部补光", "%"),\n    IntSpec(ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE, "OS4 方向光角度范围", "%π"),\n    IntSpec(ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY, "OS4 主方向光强度", "%"),\n    IntSpec(ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY, "OS4 反向补光强度", "%"),\n)\nprivate val liquidSpecs = listOf(\n''')
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''    IntSpec(ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT, "基础高光", "%"),\n    IntSpec(ConfigSchema.Glass.OS4_EDGE_WIDTH_PX, "OS4 边缘带宽度", "px"),\n    IntSpec(ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX, "OS4 反射偏移", "px"),\n    IntSpec(ConfigSchema.Glass.OS4_REFLECTION_STRENGTH, "OS4 反射强度", "%"),\n    IntSpec(ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN, "OS4 暗部补光", "%"),\n    IntSpec(ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE, "OS4 方向光角度范围", "%π"),\n    IntSpec(ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY, "OS4 主方向光强度", "%"),\n    IntSpec(ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY, "OS4 反向补光强度", "%"),\n    IntSpec(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X, "光源 X", "%"),\n''',
    '''    IntSpec(ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT, "基础高光", "%"),\n    IntSpec(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X, "光源 X", "%"),\n''')
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''    var largeFolderGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.LARGE_FOLDER_GLASS.name(), ConfigSchema.Glass.LARGE_FOLDER_GLASS.uiDefault())) }\n    SettingsList(\n''',
    '''    var largeFolderGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.LARGE_FOLDER_GLASS.name(), ConfigSchema.Glass.LARGE_FOLDER_GLASS.uiDefault())) }\n    var os4SoftEdge by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.OS4_SOFT_EDGE_ENABLED.name(), ConfigSchema.Glass.OS4_SOFT_EDGE_ENABLED.uiDefault())) }\n    SettingsList(\n''')
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''        IntSetting(prefs, largeFolderSizeOffsetSpec, masterEnabled && liquidGlass && largeFolderGlass)\n        IntSetting(prefs, largeFolderCornerRadiusSpec, masterEnabled && liquidGlass && largeFolderGlass)\n        ArrowPreference(\n            stringResource(R.string.launcher_highlights_entry),\n            summary = stringResource(R.string.launcher_highlights_entry_summary),\n            enabled = masterEnabled && liquidGlass,\n            onClick = openLauncherHighlights,\n        )\n''',
    '''        IntSetting(prefs, largeFolderSizeOffsetSpec, masterEnabled && liquidGlass && largeFolderGlass)\n        IntSetting(prefs, largeFolderCornerRadiusSpec, masterEnabled && liquidGlass && largeFolderGlass)\n        BooleanSetting(\n            prefs,\n            ConfigSchema.Glass.OS4_SOFT_EDGE_ENABLED,\n            "OS4 柔光边缘",\n            "开启后使用背景驱动的 OS4 柔光边缘，并在运行时关闭旧九层高光；关闭后恢复原高光配置",\n            masterEnabled && liquidGlass,\n        ) { os4SoftEdge = it }\n        if (Os4EdgeModePolicy.showOs4Controls(os4SoftEdge)) {\n            SmallTitle("OS4 柔光边缘参数")\n            os4EdgeSpecs.forEach { IntSetting(prefs, it, masterEnabled && liquidGlass) }\n        }\n        ArrowPreference(\n            stringResource(R.string.launcher_highlights_entry),\n            summary = if (os4SoftEdge) "OS4 柔光边缘开启时旧高光组件由运行时统一关闭；关闭 OS4 后恢复这里保存的配置"\n                    else stringResource(R.string.launcher_highlights_entry_summary),\n            enabled = masterEnabled && liquidGlass && Os4EdgeModePolicy.legacyHighlightsEnabled(os4SoftEdge),\n            onClick = openLauncherHighlights,\n        )\n''')
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''    val liquidEnabled = prefs.getBoolean(ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault())\n    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {\n''',
    '''    val liquidEnabled = prefs.getBoolean(ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault())\n    val os4SoftEdge = prefs.getBoolean(ConfigSchema.Glass.OS4_SOFT_EDGE_ENABLED.name(), ConfigSchema.Glass.OS4_SOFT_EDGE_ENABLED.uiDefault())\n    val legacyHighlightsEnabled = Os4EdgeModePolicy.legacyHighlightsEnabled(os4SoftEdge)\n    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {\n''')
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''                        masterEnabled && liquidEnabled,\n                    )\n''',
    '''                        masterEnabled && liquidEnabled && legacyHighlightsEnabled,\n                    )\n''')
# The same exact toggle block appears a second time for large surfaces.
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''                        masterEnabled && liquidEnabled,\n                    )\n''',
    '''                        masterEnabled && liquidEnabled && legacyHighlightsEnabled,\n                    )\n''')

print("OS4 soft-edge mode policy patch applied")
