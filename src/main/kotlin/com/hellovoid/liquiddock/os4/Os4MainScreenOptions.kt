package com.hellovoid.liquiddock.os4

import com.hellovoid.liquiddock.config.ConfigKey
import com.hellovoid.liquiddock.config.ConfigSchema

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

/** Pure preference extraction, independent from Android/root and launcher identity checks. */
data class Os4MainScreenOptions(
    val iconSizeEnabled: Boolean = false,
    val iconSizePercent: Int = 100,
    val widgetHorizontalStretch: Boolean = false,
    val landscape: Os4GridOffsets = Os4GridOffsets(),
    val portrait: Os4GridOffsets = Os4GridOffsets(),
    val dock: Os4DockOptions = Os4DockOptions(),
    val glassEnabled: Boolean = false,
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
                ConfigSchema.Grid.COLUMNS, ConfigSchema.Grid.ROWS,
                ConfigSchema.Grid.ICON_SIZE_ENABLED, ConfigSchema.Grid.ICON_SIZE_PERCENT,
                ConfigSchema.Grid.WIDGET_HORIZONTAL_STRETCH).map { it.name() })
            offsetKeys.forEach { add(it.name()); add("${it.name()}_tenths") }
            add(ConfigSchema.Glass.ENABLED.name())
            add(ConfigSchema.Dock.ENABLED.name())
            dockKeys.forEach { add(it.name()); add("${it.name()}_tenths") }
        }

        /** Empty means disabled; null means corrupt active settings and must clear stale state. */
        fun fromPreferences(values: Map<String, *>): String? = try {
            fun boolean(key: ConfigKey<Boolean>): Boolean =
                if (values.containsKey(key.name())) values[key.name()] as Boolean else key.uiDefault()
            fun integer(key: ConfigKey<Int>): Int =
                if (values.containsKey(key.name())) values[key.name()] as Int else key.uiDefault()
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
                val dockEnabled = boolean(ConfigSchema.Dock.ENABLED)
                val glassEnabled = boolean(ConfigSchema.Glass.ENABLED)
                if (!grid && !icons && !dockEnabled && !glassEnabled) ""
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
                            iconSizePercent = if (icons) integer(ConfigSchema.Grid.ICON_SIZE_PERCENT) else 100,
                            widgetHorizontalStretch = grid && boolean(ConfigSchema.Grid.WIDGET_HORIZONTAL_STRETCH),
                            landscape = if (grid) offsets(0) else Os4GridOffsets(),
                            portrait = if (grid) offsets(5) else Os4GridOffsets(),
                            dock = if (dockEnabled) dockOptions() else Os4DockOptions(),
                            glassEnabled = glassEnabled,
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
    }
}
