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
        if (options.divider.enabled && !options.divider.isValid()) return null
        if (options.glassEnabled && options.glassIcons &&
            (!options.glassIconSize.isFinite() || options.glassIconSize !in -40.0..40.0 ||
                !options.glassIconCorner.isFinite() || options.glassIconCorner !in 0.0..128.0)) return null
        if (options.recentsBlurPercent !in 0..100) return null
        return buildString {
            append("hook_safety_enabled=").append(if (options.hookSafetyEnabled) 1 else 0).append('\n')
            if (grid) {
                append("grid_cols=").append(gridCols).append('\n')
                append("grid_rows=").append(gridRows).append('\n')
            }
            append("icon_size_enabled=").append(if (options.iconSizeEnabled) 1 else 0).append('\n')
            if (options.bigFolderEnabled) append("big_folder_enabled=1\n")
            if (options.widget1x1To2x2Enabled) append("widget_1x1_to_2x2=1\n")
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
            // 启用过或改过数值才下发分隔符块（enabled=0 也要下发，native 才能热关），
            // 默认状态保持 conf 与旧版本完全一致。
            val divider = options.divider
            if (divider.enabled || divider != Os4DividerOptions()) {
            append("dock_divider_enabled=").append(if (divider.enabled) 1 else 0).append('\n')
            append("dock_divider_width=").append(divider.widthDp.coerceIn(0, 160)).append('\n')
            append("dock_divider_height_scale=").append(divider.heightScale.coerceIn(0, 200)).append('\n')
            append("dock_divider_y_offset=").append(divider.yOffsetDp.coerceIn(-80, 80)).append('\n')
            append("dock_divider_color_r=").append(divider.colorR.coerceIn(0, 255)).append('\n')
            append("dock_divider_color_g=").append(divider.colorG.coerceIn(0, 255)).append('\n')
            append("dock_divider_color_b=").append(divider.colorB.coerceIn(0, 255)).append('\n')
            append("dock_divider_alpha=").append(divider.alpha.coerceIn(0, 255)).append('\n')
            }
            if (options.recentsBlurPercent != 100) {
                append("recents_blur_percent=").append(options.recentsBlurPercent).append('\n')
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
