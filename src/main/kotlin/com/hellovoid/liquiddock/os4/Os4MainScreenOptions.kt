package com.hellovoid.liquiddock.os4

import com.hellovoid.liquiddock.config.ConfigKey
import com.hellovoid.liquiddock.config.ConfigSchema
import com.hellovoid.liquiddock.WidgetComponentStore

data class Os4GridOffsets(
    val horizontal: Double = 0.0,
    val top: Double = 0.0,
    val bottom: Double = 0.0,
    val rowGap: Double = 0.0,
    val indicatorY: Double = 0.0,
) {
    internal fun isValid(): Boolean =
        listOf(horizontal, top, bottom).all { it.isFinite() && it in -600.0..600.0 } &&
            rowGap.isFinite() && rowGap in -200.0..400.0 &&
            indicatorY.isFinite() && indicatorY in -160.0..160.0
}

data class Os4DockOptions(
    val enabled: Boolean = false,
    val width: Double = 0.0,
    val height: Double = 0.0,
    val spacing: Double = 0.0,
    val bottom: Double = 0.0,
    val corner: Double = 0.0,
) {
    internal fun isValid(): Boolean =
        width.isFinite() && width in -80.0..80.0 &&
            height.isFinite() && height in -80.0..80.0 &&
            spacing.isFinite() && spacing in -8.0..12.0 &&
            bottom.isFinite() && bottom in -30.0..40.0 &&
            corner.isFinite() && corner in -50.0..100.0
}

data class Os4DividerOptions(
    val enabled: Boolean = false,
    val widthDp: Int = 10,
    val heightScale: Int = 60,
    val yOffsetDp: Int = 0,
    val colorR: Int = 255,
    val colorG: Int = 255,
    val colorB: Int = 255,
    val alpha: Int = 128,
) {
    internal fun isValid(): Boolean =
        widthDp in 0..160 && heightScale in 0..200 && yOffsetDp in -80..80 &&
            listOf(colorR, colorG, colorB, alpha).all { it in 0..255 }
}

/** Pure preference extraction, independent from Android/root and launcher identity checks. */
data class Os4MainScreenOptions(
    val iconSizeEnabled: Boolean = false,
    val bigFolderEnabled: Boolean = false,
    val widget1x1To2x2Enabled: Boolean = false,
    val iconSizePercent: Int = 100,
    val landscape: Os4GridOffsets = Os4GridOffsets(),
    val portrait: Os4GridOffsets = Os4GridOffsets(),
    val dock: Os4DockOptions = Os4DockOptions(),
    val divider: Os4DividerOptions = Os4DividerOptions(),
    val glassEnabled: Boolean = false,
    val glassNativeOverrides: Map<String, Double> = emptyMap(),
    val glassDockOverrides: Map<String, Double> = emptyMap(),
    val glassIconOverrides: Map<String, Double> = emptyMap(),
    val glassWidgetOverrides: Map<String, Double> = emptyMap(),
    val glassLargeFolderOverrides: Map<String, Double> = emptyMap(),
    val glassIcons: Boolean = false,
    val glassWidgets: Boolean = false,
    val glassLargeFolder: Boolean = true,
    val glassLargeFolderCorner: Double = 0.0,
    val glassLargeFolderNativeOutline: Boolean = true,
    val widgetOverrides: Boolean = false,
    val hookSafetyEnabled: Boolean = false,
    val glassIconSize: Double = 0.0,
    val glassIconCorner: Double = 0.0,
    val recentsBlurPercent: Int = 100,
) {
    companion object {
        val offsetKeys = listOf(
            ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE,
            ConfigSchema.Grid.LANDSCAPE_TOP_DISTANCE,
            ConfigSchema.Grid.LANDSCAPE_BOTTOM_DISTANCE,
            ConfigSchema.Grid.LANDSCAPE_ROW_GAP,
            ConfigSchema.Grid.LANDSCAPE_INDICATOR_Y,
            ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE,
            ConfigSchema.Grid.PORTRAIT_TOP_DISTANCE,
            ConfigSchema.Grid.PORTRAIT_BOTTOM_DISTANCE,
            ConfigSchema.Grid.PORTRAIT_ROW_GAP,
            ConfigSchema.Grid.PORTRAIT_INDICATOR_Y,
        )
        private val dockKeys = listOf(
            ConfigSchema.Dock.WIDTH_OFFSET,
            ConfigSchema.Dock.HEIGHT_OFFSET,
            ConfigSchema.Dock.SPACING,
            ConfigSchema.Dock.BOTTOM_OFFSET,
            ConfigSchema.Dock.BLUR_CORNER_OFFSET,
        )
        val watchedKeys: Set<String> = buildSet {
            addAll(listOf(ConfigSchema.Core.ENABLED, ConfigSchema.Grid.ENABLED,
                ConfigSchema.Grid.NATIVE_HOOK_SAFETY,
                ConfigSchema.Grid.COLUMNS, ConfigSchema.Grid.ROWS,
                ConfigSchema.Grid.ICON_SIZE_ENABLED, ConfigSchema.Grid.ICON_SIZE_PERCENT,
                ConfigSchema.Grid.BIG_FOLDER_ENABLED, ConfigSchema.Grid.WIDGET_1X1_TO_2X2_ENABLED).map { it.name() })
            offsetKeys.forEach { add(it.name()); add("${it.name()}_tenths") }
            add(ConfigSchema.Glass.ENABLED.name())
            add(ConfigSchema.Glass.OS4_NATIVE_OVERRIDES.name())
            add(ConfigSchema.Glass.OS4_DOCK_GLASS_CUSTOM.name())
            add(ConfigSchema.Glass.OS4_DOCK_GLASS_OVERRIDES.name())
            add(ConfigSchema.Glass.OS4_ICON_GLASS_CUSTOM.name())
            add(ConfigSchema.Glass.OS4_ICON_GLASS_OVERRIDES.name())
            add(ConfigSchema.Glass.OS4_WIDGET_GLASS_CUSTOM.name())
            add(ConfigSchema.Glass.OS4_WIDGET_GLASS_OVERRIDES.name())
            add(ConfigSchema.Glass.OS4_LARGE_FOLDER_GLASS_CUSTOM.name())
            add(ConfigSchema.Glass.OS4_LARGE_FOLDER_GLASS_OVERRIDES.name())
            add(ConfigSchema.Glass.ICON_GLASS.name())
            add(ConfigSchema.Glass.WIDGET_GLASS.name())
            add(ConfigSchema.Glass.LARGE_FOLDER_GLASS.name())
            add(ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.name())
            listOf(
                ConfigSchema.Glass.ICON_SIZE_OFFSET,
                ConfigSchema.Glass.ICON_CORNER_RADIUS,
                ConfigSchema.Glass.LARGE_FOLDER_CORNER_RADIUS,
            ).forEach { add(it.name()); add("${it.name()}_tenths") }
            add(WidgetComponentStore.SELECTION_KEY)
            add(ConfigSchema.Dock.ENABLED.name())
            dockKeys.forEach { add(it.name()); add("${it.name()}_tenths") }
            add(ConfigSchema.Divider.ENABLED.name())
            listOf(
                ConfigSchema.Divider.WIDTH_DP, ConfigSchema.Divider.HEIGHT_SCALE,
                ConfigSchema.Divider.Y_OFFSET_DP, ConfigSchema.Divider.COLOR_RED,
                ConfigSchema.Divider.COLOR_GREEN, ConfigSchema.Divider.COLOR_BLUE,
                ConfigSchema.Divider.ALPHA,
            ).forEach { add(it.name()); add("${it.name()}_tenths") }
            add(ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT.name())
        }

        /** Empty means disabled; null means corrupt active settings and must clear stale state. */
        fun fromPreferences(values: Map<String, *>): String? = try {
            fun boolean(key: ConfigKey<Boolean>): Boolean =
                if (values.containsKey(key.name())) values[key.name()] as Boolean else key.uiDefault()
            fun integer(key: ConfigKey<Int>): Int =
                if (values.containsKey(key.name())) values[key.name()] as Int else key.uiDefault()
            fun string(key: ConfigKey<String>): String =
                if (values.containsKey(key.name())) values[key.name()] as String else key.uiDefault()
            /** divider 的 dp×10 值：优先 _tenths；旧键值本身即 dp×10；否则用 DP_TENTHS 默认。 */
            fun dp10(key: ConfigKey<Int>): Int {
                val tenths = "${key.name()}_tenths"
                return when {
                    values.containsKey(tenths) -> values[tenths] as Int
                    values.containsKey(key.name()) -> values[key.name()] as Int
                    else -> key.uiDefault()
                }
            }
            fun dp(key: ConfigKey<Int>): Double {
                val tenths = "${key.name()}_tenths"
                val value = if (values.containsKey(tenths)) (values[tenths] as Int) / 10.0
                    else if (values.containsKey(key.name())) (values[key.name()] as Int).toDouble()
                    else key.uiDefaultDp().toDouble()
                require(value.isFinite() && value >= key.minInt() && value <= key.maxInt())
                return value
            }
            if (!boolean(ConfigSchema.Core.ENABLED)) ""
            else {
                val grid = boolean(ConfigSchema.Grid.ENABLED)
                val icons = boolean(ConfigSchema.Grid.ICON_SIZE_ENABLED)
                val bigFolder = boolean(ConfigSchema.Grid.BIG_FOLDER_ENABLED)
                val widget1x1To2x2 = boolean(ConfigSchema.Grid.WIDGET_1X1_TO_2X2_ENABLED)
                val dockEnabled = boolean(ConfigSchema.Dock.ENABLED)
                val dividerEnabled = boolean(ConfigSchema.Divider.ENABLED)
                val glassEnabled = boolean(ConfigSchema.Glass.ENABLED)
                val glassIcons = glassEnabled && boolean(ConfigSchema.Glass.ICON_GLASS)
                val glassWidgets = glassEnabled && boolean(ConfigSchema.Glass.WIDGET_GLASS)
                val glassLargeFolder = !glassEnabled || boolean(ConfigSchema.Glass.LARGE_FOLDER_GLASS)
                val glassLargeFolderNativeOutline = boolean(ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE)
                val widgetOverrides = selectedOs4MamlWidget(values[WidgetComponentStore.SELECTION_KEY])
                val nativeGlassOverrides = if (glassEnabled) {
                    Os4NativeGlassOptions.parse(string(ConfigSchema.Glass.OS4_NATIVE_OVERRIDES))
                        ?: return null
                } else emptyMap()
                fun scopedOverrides(
                    customKey: ConfigKey<Boolean>,
                    overridesKey: ConfigKey<String>,
                ): Map<String, Double>? = if (glassEnabled && boolean(customKey)) {
                    Os4NativeGlassOptions.parse(string(overridesKey))
                } else emptyMap()
                val dockGlassOverrides = scopedOverrides(
                    ConfigSchema.Glass.OS4_DOCK_GLASS_CUSTOM,
                    ConfigSchema.Glass.OS4_DOCK_GLASS_OVERRIDES,
                ) ?: return null
                val iconGlassOverrides = scopedOverrides(
                    ConfigSchema.Glass.OS4_ICON_GLASS_CUSTOM,
                    ConfigSchema.Glass.OS4_ICON_GLASS_OVERRIDES,
                ) ?: return null
                val widgetGlassOverrides = scopedOverrides(
                    ConfigSchema.Glass.OS4_WIDGET_GLASS_CUSTOM,
                    ConfigSchema.Glass.OS4_WIDGET_GLASS_OVERRIDES,
                ) ?: return null
                val largeFolderGlassOverrides = scopedOverrides(
                    ConfigSchema.Glass.OS4_LARGE_FOLDER_GLASS_CUSTOM,
                    ConfigSchema.Glass.OS4_LARGE_FOLDER_GLASS_OVERRIDES,
                ) ?: return null
                val recentsBlurPercent = integer(ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT)
                if (!grid && !icons && !bigFolder && !dockEnabled && !dividerEnabled && !glassEnabled &&
                    !widgetOverrides && recentsBlurPercent == 100) ""
                else {
                    fun dockOptions() = Os4DockOptions(
                        enabled = true,
                        width = dp(ConfigSchema.Dock.WIDTH_OFFSET),
                        height = dp(ConfigSchema.Dock.HEIGHT_OFFSET),
                        spacing = dp(ConfigSchema.Dock.SPACING),
                        bottom = dp(ConfigSchema.Dock.BOTTOM_OFFSET),
                        corner = dp(ConfigSchema.Dock.BLUR_CORNER_OFFSET),
                    )
                    fun offsets(start: Int) = Os4GridOffsets(
                        dp(offsetKeys[start]), dp(offsetKeys[start + 1]),
                        dp(offsetKeys[start + 2]), dp(offsetKeys[start + 3]), dp(offsetKeys[start + 4]),
                    )
                    Os4NativeConfPublisher.buildConf(
                        if (grid) integer(ConfigSchema.Grid.COLUMNS) else null,
                        if (grid) integer(ConfigSchema.Grid.ROWS) else null,
                        Os4MainScreenOptions(
                            iconSizeEnabled = icons,
                            bigFolderEnabled = bigFolder,
                            widget1x1To2x2Enabled = widget1x1To2x2,
                            iconSizePercent = if (icons) integer(ConfigSchema.Grid.ICON_SIZE_PERCENT) else 100,
                            landscape = if (grid) offsets(0) else Os4GridOffsets(),
                            portrait = if (grid) offsets(5) else Os4GridOffsets(),
                            dock = if (dockEnabled) dockOptions() else Os4DockOptions(),
                            divider = Os4DividerOptions(
                                enabled = dividerEnabled,
                                widthDp = dp10(ConfigSchema.Divider.WIDTH_DP),
                                heightScale = integer(ConfigSchema.Divider.HEIGHT_SCALE),
                                yOffsetDp = dp10(ConfigSchema.Divider.Y_OFFSET_DP),
                                colorR = integer(ConfigSchema.Divider.COLOR_RED),
                                colorG = integer(ConfigSchema.Divider.COLOR_GREEN),
                                colorB = integer(ConfigSchema.Divider.COLOR_BLUE),
                                alpha = integer(ConfigSchema.Divider.ALPHA),
                            ),
                            glassEnabled = glassEnabled,
                            glassNativeOverrides = nativeGlassOverrides,
                            glassDockOverrides = dockGlassOverrides,
                            glassIconOverrides = iconGlassOverrides,
                            glassWidgetOverrides = widgetGlassOverrides,
                            glassLargeFolderOverrides = largeFolderGlassOverrides,
                            glassIcons = glassIcons,
                            glassWidgets = glassWidgets,
                            glassLargeFolder = glassLargeFolder,
                            glassLargeFolderCorner = if (glassEnabled && glassLargeFolder) {
                                dp(ConfigSchema.Glass.LARGE_FOLDER_CORNER_RADIUS)
                            } else 0.0,
                            glassLargeFolderNativeOutline = glassLargeFolderNativeOutline,
                            widgetOverrides = widgetOverrides,
                            hookSafetyEnabled = boolean(ConfigSchema.Grid.NATIVE_HOOK_SAFETY),
                            glassIconSize = if (glassIcons) dp(ConfigSchema.Glass.ICON_SIZE_OFFSET) else 0.0,
                            glassIconCorner = if (glassIcons) dp(ConfigSchema.Glass.ICON_CORNER_RADIUS) else 0.0,
                            recentsBlurPercent = recentsBlurPercent,
                        ),
                    )
                }
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ClassCastException) {
            null
        } catch (_: NullPointerException) {
            null
        }

        private fun selectedOs4MamlWidget(value: Any?): Boolean =
            (value as? Set<*>)?.any { encoded ->
                if (encoded !is String) return@any false
                val parts = encoded.split('\t')
                parts.size >= 2 && parts[0] == WidgetComponentStore.MAML_V2 &&
                    parts[1].startsWith("os4:")
            } == true
    }
}
