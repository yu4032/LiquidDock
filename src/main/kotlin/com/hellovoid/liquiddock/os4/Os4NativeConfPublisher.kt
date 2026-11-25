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
        if (options.folderGridCols != 0 && options.folderGridCols !in 2..8) return null
        if (options.folderGridRows != 0 && options.folderGridRows !in 2..8) return null
        if (options.iconSizePercent !in 80..120) return null
        if (Os4NativeGlassOptions.configLines(options.glassNativeOverrides) == null) return null
        if (Os4NativeGlassOptions.configLines(options.glassDockOverrides, "glass_native_dock_") == null) return null
        if (Os4NativeGlassOptions.configLines(options.glassIconOverrides, "glass_native_icon_") == null) return null
        if (Os4NativeGlassOptions.configLines(options.glassWidgetOverrides, "glass_native_widget_") == null) return null
        if (Os4NativeGlassOptions.configLines(options.glassLargeFolderOverrides, "glass_native_large_folder_") == null) return null
        if (options.glassEnabled && options.glassLargeFolder &&
            (!options.glassLargeFolderCorner.isFinite() || options.glassLargeFolderCorner !in 0.0..128.0) ||
            (!options.glassLargeFolder2x1Corner.isFinite() || options.glassLargeFolder2x1Corner !in 0.0..128.0)) return null
        if (grid && (!options.landscape.isValid() || !options.portrait.isValid())) return null
        if (options.dock.enabled && !options.dock.isValid()) return null
        if (options.divider.enabled && !options.divider.isValid()) return null
        if (options.glassEnabled) {
            for ((k, v) in options.prismalOverrides) {
                if (k == "prismal_icon_backlit_enabled" || k == "prismal_icon_outer_shadow_enabled") {
                    if (!v.isFinite() || v !in 0.0..1.0) return null
                    continue
                }
                val spec = Os4MainScreenOptions.prismalSpecsByKey[k] ?: return null
                if (!v.isFinite() || v !in spec.min..spec.max) return null
            }
        }
        if (options.glassEnabled && options.glassIcons &&
            (!options.glassIconSize.isFinite() || options.glassIconSize !in -40.0..40.0 ||
                !options.glassIconCorner.isFinite() || options.glassIconCorner !in 0.0..128.0)) return null
        if (options.recentsBlurPercent !in 0..100) return null
        if (options.allAppsBlurPercent !in 0..100) return null
        if (options.wsAllAppsBlurPercent !in 0..100) return null
        if (options.folderBlurPercent !in 0..100) return null
        if (options.shortcutBlurPercent !in 0..100) return null
        if (options.workstation.dockCustomization && !options.workstation.isValid()) return null
        return buildString {
            append("hook_safety_enabled=").append(if (options.hookSafetyEnabled) 1 else 0).append('\n')
            if (options.debugLogging) append("debug_logging=1\n")
            if (grid) {
                append("grid_cols=").append(gridCols).append('\n')
                append("grid_rows=").append(gridRows).append('\n')
            }
            append("icon_size_enabled=").append(if (options.iconSizeEnabled) 1 else 0).append('\n')
            if (options.bigFolderEnabled) {
                append("big_folder_enabled=1\n")
                if (options.bigFolderAlignIconEnabled) {
                    append("big_folder_align_icon=1\n")
                    if (options.bigFolderTitleEnabled) append("big_folder_title_enabled=1\n")
                }
            }
            if (options.folderPreviewInset != 0) {
                append("folder_preview_inset=").append(options.folderPreviewInset).append('\n')
            }
            if (options.folderGridCols > 0) append("folder_grid_cols=").append(options.folderGridCols).append('\n')
            if (options.folderGridRows > 0) append("folder_grid_rows=").append(options.folderGridRows).append('\n')
            if (options.widget1x1To2x2Enabled) append("widget_1x1_to_2x2=1\n")
            if (options.preventWidgetStretchEnabled) append("widget_prevent_stretch=1\n")
            // Explicit opt-in only. An absent key preserves the stock OS4 material.
            val anyGlass = options.glassEnabled || options.softGlassEnabled
            if (options.glassEnabled) append("glass_enabled=1\n")
            if (options.softGlassEnabled) append("soft_glass_enabled=1\n")
            if (options.phoneDockGlass) append("phone_dock_glass=1\n")
            if (options.glassEnabled && options.prismalOverrides.isNotEmpty()) {
                for (spec in Os4MainScreenOptions.prismalSpecs) {
                    val v = options.prismalOverrides[spec.confKey] ?: continue
                    if (spec.isBool) {
                        append(spec.confKey).append('=').append(if (v >= 0.5) 1 else 0).append('\n')
                    } else {
                        append(spec.confKey).append('=').append(Os4NativeGlassOptions.format(v)).append('\n')
                    }
                }
                listOf("prismal_icon_backlit_enabled", "prismal_icon_outer_shadow_enabled").forEach { k ->
                    options.prismalOverrides[k]?.let { v ->
                        append(k).append('=').append(if (v >= 0.5) 1 else 0).append('\n')
                    }
                }
            }
            if (options.softGlassEnabled && !options.glassEnabled) {
                Os4NativeGlassOptions.configLines(options.glassNativeOverrides)!!.forEach {
                    append(it).append('\n')
                }
                listOf(
                    "glass_native_dock_" to options.glassDockOverrides,
                    "glass_native_icon_" to options.glassIconOverrides,
                    "glass_native_widget_" to options.glassWidgetOverrides,
                    "glass_native_large_folder_" to options.glassLargeFolderOverrides,
                ).forEach { (prefix, scoped) ->
                    Os4NativeGlassOptions.configLines(scoped, prefix)!!.forEach {
                        append(it).append('\n')
                    }
                }
            }
            if (anyGlass && options.glassIcons) {
                append("glass_icons=1\n")
                append("glass_icon_size=").append(options.glassIconSize).append('\n')
                append("glass_icon_corner=").append(options.glassIconCorner).append('\n')
            }
            if (anyGlass && options.glassWidgets) append("glass_widgets=1\n")
            if (options.useDragMaterial) append("use_drag_material=1\n")
            if (anyGlass) {
                append("large_folder_glass=").append(if (options.glassLargeFolder) 1 else 0).append('\n')
                append("large_folder_corner=").append(options.glassLargeFolderCorner).append('\n')
                append("large_folder_2x1_corner=").append(options.glassLargeFolder2x1Corner).append('\n')
                append("large_folder_native_outline=")
                    .append(if (options.glassLargeFolderNativeOutline) 1 else 0).append('\n')
            }
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
            if (options.allAppsBlurPercent != 100) {
                append("allapps_blur_percent=").append(options.allAppsBlurPercent).append('\n')
            }
            if (options.wsAllAppsBlurPercent != 100) {
                append("ws_allapps_blur_percent=").append(options.wsAllAppsBlurPercent).append('\n')
            }
            if (options.folderBlurPercent != 100) {
                append("folder_blur_percent=").append(options.folderBlurPercent).append('\n')
            }
            if (options.shortcutBlurPercent != 100) {
                append("shortcut_blur_percent=").append(options.shortcutBlurPercent).append('\n')
            }
            if (options.workstation.dockCustomization) {
                val ws = options.workstation
                append("ws_dock_custom=1\n")
                append("ws_taskbar_mode=").append(if (ws.taskbarMode) 1 else 0).append('\n')
                append("ws_dock_icon_corner=").append(ws.dockIconGlassCornerRadius).append('\n')
                append("ws_dock_height=").append(ws.dockHeightOffset).append('\n')
                append("ws_dock_v_offset=").append(ws.dockVerticalOffset).append('\n')
                append("ws_dock_icon_land_v_offset=").append(ws.dockIconLandscapeVerticalOffset).append('\n')
                append("ws_dock_icon_port_v_offset=").append(ws.dockIconTopOffset).append('\n')
                append("ws_apps_land_h_offset=").append(ws.allAppsLandscapeHorizontalOffset).append('\n')
                append("ws_apps_land_top=").append(ws.allAppsLandscapeTopSpacing).append('\n')
                append("ws_apps_land_bottom=").append(ws.allAppsLandscapeBottomSpacing).append('\n')
                append("ws_apps_land_ind_y=").append(ws.allAppsLandscapeIndicatorY).append('\n')
                append("ws_apps_port_h_offset=").append(ws.allAppsPortraitHorizontalOffset).append('\n')
                append("ws_apps_port_top=").append(ws.allAppsPortraitTopSpacing).append('\n')
                append("ws_apps_port_bottom=").append(ws.allAppsPortraitBottomSpacing).append('\n')
                append("ws_apps_port_ind_y=").append(ws.allAppsPortraitIndicatorY).append('\n')
                append("ws_desktop_sync_normal=").append(if (ws.desktopSyncNormal) 1 else 0).append('\n')
                append("ws_desktop_land_h_offset=").append(ws.desktopLandscapeHorizontalOffset).append('\n')
                append("ws_desktop_land_top=").append(ws.desktopLandscapeTopSpacing).append('\n')
                append("ws_desktop_land_bottom=").append(ws.desktopLandscapeBottomSpacing).append('\n')
                append("ws_desktop_land_ind_y=").append(ws.desktopLandscapeIndicatorY).append('\n')
                append("ws_desktop_port_h_offset=").append(ws.desktopPortraitHorizontalOffset).append('\n')
                append("ws_desktop_port_top=").append(ws.desktopPortraitTopSpacing).append('\n')
                append("ws_desktop_port_bottom=").append(ws.desktopPortraitBottomSpacing).append('\n')
                append("ws_desktop_port_ind_y=").append(ws.desktopPortraitIndicatorY).append('\n')
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
