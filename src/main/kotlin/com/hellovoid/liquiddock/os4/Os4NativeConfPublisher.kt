package com.hellovoid.liquiddock.os4

import android.content.Context
import android.util.Log

/** Builds and publishes the versioned fail-closed OS4 native snapshot. */
object Os4NativeConfPublisher {
    private const val TAG = "LiquidDock-OS4Conf"

    @JvmStatic
    fun buildConf(gridCols: Int, gridRows: Int): String? {
        if (gridCols !in 2..10 || gridRows !in 2..10) return null
        return buildString {
            append("grid_cols=").append(gridCols).append('\n')
            append("grid_rows=").append(gridRows).append('\n')
            append("end=1").append('\n')
        }
    }

    /** Both counts are absent for icon-only snapshots; placement remains stock. */
    @JvmStatic
    fun buildConf(gridCols: Int?, gridRows: Int?, options: Os4MainScreenOptions): String? {
        if ((gridCols == null) != (gridRows == null)) return null
        val grid = gridCols != null
        if (grid && (gridCols !in 2..10 || gridRows !in 2..10)) return null
        if (options.iconSizePercent !in 80..120) return null
        if (Os4NativeGlassOptions.configLines(options.glassNativeOverrides) == null) return null
        if (grid && (!options.landscape.isValid() || !options.portrait.isValid())) return null
        if (options.dock.enabled && !options.dock.isValid()) return null
        if (options.glassEnabled && options.glassIcons &&
            (!options.glassIconSize.isFinite() || options.glassIconSize !in -40.0..40.0 ||
                !options.glassIconCorner.isFinite() || options.glassIconCorner !in 0.0..128.0)) return null
        return buildString {
            if (grid) {
                append("grid_cols=").append(gridCols).append('\n')
                append("grid_rows=").append(gridRows).append('\n')
            }
            append("icon_size_enabled=").append(if (options.iconSizeEnabled) 1 else 0).append('\n')
            // Explicit opt-in only. An absent key preserves the stock OS4 material.
            if (options.glassEnabled) append("glass_enabled=1\n")
            if (options.glassEnabled) {
                Os4NativeGlassOptions.configLines(options.glassNativeOverrides)!!.forEach {
                    append(it).append('\n')
                }
            }
            if (options.glassEnabled && options.glassIcons) {
                append("glass_icons=1\n")
                append("glass_icon_size=").append(options.glassIconSize).append('\n')
                append("glass_icon_corner=").append(options.glassIconCorner).append('\n')
            }
            if (options.glassEnabled && options.glassWidgets) append("glass_widgets=1\n")
            if (options.widgetOverrides) append("widget_overrides=1\n")
            append("icon_size_percent=").append(options.iconSizePercent).append('\n')
            if (grid) {
                append("widget_horizontal_stretch=")
                    .append(if (options.widgetHorizontalStretch) 1 else 0).append('\n')
                fun offsets(direction: String, offsets: Os4GridOffsets) {
                    append("grid_${direction}_horizontal_distance=").append(offsets.horizontal).append('\n')
                    append("grid_${direction}_top_distance=").append(offsets.top).append('\n')
                    append("grid_${direction}_bottom_distance=").append(offsets.bottom).append('\n')
                    append("grid_${direction}_row_gap=").append(offsets.rowGap).append('\n')
                    append("indicator_${direction}_y=").append(offsets.indicatorY).append('\n')
                }
                offsets("landscape", options.landscape)
                offsets("portrait", options.portrait)
            }
            if (options.dock.enabled) {
                append("dock_geometry_enabled=1\n")
                append("dock_geometry_width=").append(options.dock.width).append('\n')
                append("dock_geometry_height=").append(options.dock.height).append('\n')
                append("dock_geometry_spacing=").append(options.dock.spacing).append('\n')
                append("dock_geometry_bottom=").append(options.dock.bottom).append('\n')
                append("dock_geometry_corner=").append(options.dock.corner).append('\n')
            }
            append("end=1\n")
        }
    }

    @JvmStatic
    fun publish(context: Context, gridCols: Int, gridRows: Int): Boolean {
        val conf = buildConf(gridCols, gridRows)
        if (conf == null) {
            Log.e(TAG, "reject invalid grid ${gridCols}x$gridRows")
            return false
        }
        return Os4RootConfWriter.write(context, conf)
    }
}
