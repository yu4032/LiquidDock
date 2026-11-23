package com.hellovoid.liquiddock.os4

import com.hellovoid.liquiddock.GlassEdgeEffects
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

data class Os4WorkstationOptions(
    val dockCustomization: Boolean = false,
    val taskbarMode: Boolean = false,
    val dockIconGlassCornerRadius: Double = 0.0,
    val dockHeightOffset: Double = 0.0,
    val dockVerticalOffset: Double = 0.0,
    val dockIconLandscapeVerticalOffset: Double = 0.0,
    val dockIconTopOffset: Double = 0.0,
    val allAppsLandscapeHorizontalOffset: Double = 0.0,
    val allAppsLandscapeTopSpacing: Double = 0.0,
    val allAppsLandscapeBottomSpacing: Double = 0.0,
    val allAppsLandscapeIndicatorY: Double = 0.0,
    val allAppsPortraitHorizontalOffset: Double = 0.0,
    val allAppsPortraitTopSpacing: Double = 0.0,
    val allAppsPortraitBottomSpacing: Double = 0.0,
    val allAppsPortraitIndicatorY: Double = 0.0,
    val desktopSyncNormal: Boolean = true,
    val desktopLandscapeHorizontalOffset: Double = 0.0,
    val desktopLandscapeTopSpacing: Double = 0.0,
    val desktopLandscapeBottomSpacing: Double = 0.0,
    val desktopLandscapeIndicatorY: Double = 0.0,
    val desktopPortraitHorizontalOffset: Double = 0.0,
    val desktopPortraitTopSpacing: Double = 0.0,
    val desktopPortraitBottomSpacing: Double = 0.0,
    val desktopPortraitIndicatorY: Double = 0.0,
) {
    internal fun isValid(): Boolean =
        dockIconGlassCornerRadius.isFinite() && dockIconGlassCornerRadius in 0.0..100.0 &&
            dockHeightOffset.isFinite() && dockHeightOffset in -80.0..80.0 &&
            dockVerticalOffset.isFinite() && dockVerticalOffset in -120.0..120.0 &&
            dockIconLandscapeVerticalOffset.isFinite() && dockIconLandscapeVerticalOffset in -48.0..48.0 &&
            dockIconTopOffset.isFinite() && dockIconTopOffset in -48.0..48.0 &&
            listOf(
                allAppsLandscapeHorizontalOffset,
                allAppsLandscapeTopSpacing,
                allAppsLandscapeBottomSpacing,
                allAppsPortraitHorizontalOffset,
                allAppsPortraitTopSpacing,
                allAppsPortraitBottomSpacing,
                desktopLandscapeHorizontalOffset,
                desktopLandscapeTopSpacing,
                desktopLandscapeBottomSpacing,
                desktopPortraitHorizontalOffset,
                desktopPortraitTopSpacing,
                desktopPortraitBottomSpacing,
            ).all { it.isFinite() && it in -240.0..240.0 } &&
            listOf(
                allAppsLandscapeIndicatorY,
                allAppsPortraitIndicatorY,
                desktopLandscapeIndicatorY,
                desktopPortraitIndicatorY,
            ).all { it.isFinite() && it in -160.0..160.0 }
}

/** Pure preference extraction, independent from Android/root and launcher identity checks. */
data class Os4MainScreenOptions(
    val iconSizeEnabled: Boolean = false,
    val bigFolderEnabled: Boolean = false,
    val bigFolderAlignIconEnabled: Boolean = false,
    val bigFolderTitleEnabled: Boolean = false,
    val folderPreviewInset: Int = 0,
    val folderGridCols: Int = 0,
    val folderGridRows: Int = 0,
    val widget1x1To2x2Enabled: Boolean = false,
    val preventWidgetStretchEnabled: Boolean = false,
    val iconSizePercent: Int = 100,
    val landscape: Os4GridOffsets = Os4GridOffsets(),
    val portrait: Os4GridOffsets = Os4GridOffsets(),
    val dock: Os4DockOptions = Os4DockOptions(),
    val divider: Os4DividerOptions = Os4DividerOptions(),
    val glassEnabled: Boolean = false,
    val softGlassEnabled: Boolean = false,
    val prismalOverrides: Map<String, Double> = emptyMap(),
    val glassNativeOverrides: Map<String, Double> = emptyMap(),
    val glassDockOverrides: Map<String, Double> = emptyMap(),
    val glassIconOverrides: Map<String, Double> = emptyMap(),
    val glassWidgetOverrides: Map<String, Double> = emptyMap(),
    val glassLargeFolderOverrides: Map<String, Double> = emptyMap(),
    val glassIcons: Boolean = false,
    val glassWidgets: Boolean = false,
    val useDragMaterial: Boolean = false,
    val glassLargeFolder: Boolean = true,
    val glassLargeFolderCorner: Double = 0.0,
    val glassLargeFolder2x1Corner: Double = 0.0,
    val glassLargeFolderNativeOutline: Boolean = true,
    val widgetOverrides: Boolean = false,
    val hookSafetyEnabled: Boolean = false,
    val debugLogging: Boolean = false,
    val glassIconSize: Double = 0.0,
    val glassIconCorner: Double = 0.0,
    val recentsBlurPercent: Int = 100,
    val allAppsBlurPercent: Int = 100,
    val workstation: Os4WorkstationOptions = Os4WorkstationOptions(),
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
        internal data class PrismalFieldSpec(
            val confKey: String,
            val configKey: ConfigKey<*>,
            val isBool: Boolean = false,
            val isDp: Boolean = false,
            val divisor: Double = 1.0,
            val min: Double = 0.0,
            val max: Double = 1.0,
        )

        internal val prismalSpecs: List<PrismalFieldSpec> = listOf(
            PrismalFieldSpec("prismal_continuous_corners", ConfigSchema.Glass.CONTINUOUS_CORNERS, isBool = true),
            PrismalFieldSpec("prismal_show_normals", ConfigSchema.Glass.PRISMAL_SHOW_NORMALS, isBool = true),
            PrismalFieldSpec("prismal_backlit_enabled", ConfigSchema.Glass.BACKLIT_ENABLED, isBool = true),
            PrismalFieldSpec("prismal_blur", ConfigSchema.Glass.BLUR, isDp = true, min = 0.0, max = 60.0),
            PrismalFieldSpec("prismal_thickness", ConfigSchema.Glass.THICKNESS, isDp = true, min = 1.0, max = 60.0),
            PrismalFieldSpec("prismal_lens_refraction", ConfigSchema.Glass.LENS_REFRACTION, isDp = true, min = 0.0, max = 8.0),
            PrismalFieldSpec("prismal_refraction_inset", ConfigSchema.Glass.PRISMAL_REFRACTION_INSET, isDp = true, min = 0.0, max = 80.0),
            PrismalFieldSpec("prismal_htw", ConfigSchema.Glass.PRISMAL_HEIGHT_TRANSITION_WIDTH, isDp = true, min = 1.0, max = 120.0),
            PrismalFieldSpec("prismal_smin_smoothing", ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING, isDp = true, min = 0.0, max = 24.0),
            PrismalFieldSpec("prismal_backlit_width", ConfigSchema.Glass.BACKLIT_WIDTH, isDp = true, min = 0.0, max = 5.0),
            PrismalFieldSpec("prismal_ior", ConfigSchema.Glass.IOR, divisor = 100.0, min = 1.0, max = 2.0),
            PrismalFieldSpec("prismal_normal_strength", ConfigSchema.Glass.NORMAL_STRENGTH, divisor = 100.0, min = 0.0, max = 3.0),
            PrismalFieldSpec("prismal_dome", ConfigSchema.Glass.DOME, divisor = 100.0, min = 0.0, max = 2.0),
            PrismalFieldSpec("prismal_depth_effect", ConfigSchema.Glass.DEPTH_EFFECT, divisor = 100.0, min = 0.0, max = 1.0),
            PrismalFieldSpec("prismal_chromatic", ConfigSchema.Glass.CHROMATIC, min = 0.0, max = 40.0),
            PrismalFieldSpec("prismal_brightness", ConfigSchema.Glass.BRIGHTNESS, divisor = 100.0, min = 0.5, max = 2.0),
            PrismalFieldSpec("prismal_caustics", ConfigSchema.Glass.CAUSTICS, divisor = 100.0, min = 0.0, max = 1.0),
            PrismalFieldSpec("prismal_highlight_width", ConfigSchema.Glass.HIGHLIGHT_WIDTH, divisor = 100.0, min = 0.5, max = 3.0),
            PrismalFieldSpec("prismal_tint_r", ConfigSchema.Glass.TINT_RED, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_tint_g", ConfigSchema.Glass.TINT_GREEN, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_tint_b", ConfigSchema.Glass.TINT_BLUE, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_tint_a", ConfigSchema.Glass.TINT_ALPHA, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_base_r", ConfigSchema.Glass.BASE_COLOR_RED, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_base_g", ConfigSchema.Glass.BASE_COLOR_GREEN, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_base_b", ConfigSchema.Glass.BASE_COLOR_BLUE, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_base_a", ConfigSchema.Glass.BASE_COLOR_ALPHA, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_displacement_scale", ConfigSchema.Glass.PRISMAL_DISPLACEMENT_SCALE, divisor = 100.0, min = 0.0, max = 4.0),
            PrismalFieldSpec("prismal_edge_falloff", ConfigSchema.Glass.PRISMAL_EDGE_REFRACTION_FALLOFF, divisor = 100.0, min = 0.0, max = 20.0),
            PrismalFieldSpec("prismal_fresnel_reflect", ConfigSchema.Glass.PRISMAL_FRESNEL_REFLECT, divisor = 100.0, min = 0.0, max = 5.0),
            PrismalFieldSpec("prismal_dispersion_r", ConfigSchema.Glass.PRISMAL_DISPERSION_R, divisor = 100.0, min = 0.0, max = 4.0),
            PrismalFieldSpec("prismal_dispersion_b", ConfigSchema.Glass.PRISMAL_DISPERSION_B, divisor = 100.0, min = 0.0, max = 4.0),
            PrismalFieldSpec("prismal_vibrancy", ConfigSchema.Glass.PRISMAL_VIBRANCY, divisor = 100.0, min = 0.0, max = 3.0),
            PrismalFieldSpec("prismal_os4_edge_width_px", ConfigSchema.Glass.OS4_EDGE_WIDTH_PX, min = 3.0, max = 64.0),
            PrismalFieldSpec("prismal_os4_reflect_offset_px", ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX, min = 0.0, max = 40.0),
            PrismalFieldSpec("prismal_os4_reflection_strength", ConfigSchema.Glass.OS4_REFLECTION_STRENGTH, divisor = 100.0, min = 0.0, max = 2.0),
            PrismalFieldSpec("prismal_os4_reflection_lighten", ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN, divisor = 100.0, min = 0.0, max = 1.0),
            PrismalFieldSpec("prismal_os4_directional_angle_range", ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE, divisor = 100.0, min = 0.05, max = 1.50),
            PrismalFieldSpec("prismal_os4_directional_intensity", ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY, divisor = 100.0, min = 0.0, max = 2.0),
            PrismalFieldSpec("prismal_os4_directional_opposite_intensity", ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY, divisor = 100.0, min = 0.0, max = 2.0),
            PrismalFieldSpec("prismal_light_dir_x", ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X, divisor = 100.0, min = -2.0, max = 2.0),
            PrismalFieldSpec("prismal_light_dir_y", ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y, divisor = 100.0, min = -2.0, max = 2.0),
            PrismalFieldSpec("prismal_shadow_r", ConfigSchema.Glass.PRISMAL_SHADOW_RED, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_shadow_g", ConfigSchema.Glass.PRISMAL_SHADOW_GREEN, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_shadow_b", ConfigSchema.Glass.PRISMAL_SHADOW_BLUE, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_shadow_a", ConfigSchema.Glass.PRISMAL_SHADOW_ALPHA, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_shadow_softness", ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS, divisor = 100.0, min = 0.0, max = 20.0),
            PrismalFieldSpec("prismal_transmittance", ConfigSchema.Glass.PRISMAL_TRANSMITTANCE, divisor = 100.0, min = 0.0, max = 1.0),
            PrismalFieldSpec("prismal_backdrop_scale_x", ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X, divisor = 100.0, min = 0.25, max = 4.0),
            PrismalFieldSpec("prismal_backdrop_scale_y", ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y, divisor = 100.0, min = 0.25, max = 4.0),
            PrismalFieldSpec("prismal_parallax_scale", ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE, divisor = 100.0, min = 0.0, max = 4.0),
            PrismalFieldSpec("prismal_backlit_strength", ConfigSchema.Glass.BACKLIT_STRENGTH, divisor = 100.0, min = 0.0, max = 1.0),
            PrismalFieldSpec("prismal_backlit_extent", ConfigSchema.Glass.BACKLIT_EXTENT, divisor = 100.0, min = 0.0, max = 1.0),
            PrismalFieldSpec("prismal_backlit_softness", ConfigSchema.Glass.BACKLIT_SOFTNESS, divisor = 100.0, min = 0.05, max = 2.0),
            PrismalFieldSpec("prismal_disable_stationary_downsample", ConfigSchema.Glass.DISABLE_STATIONARY_DOWNSAMPLE, isBool = true),
            PrismalFieldSpec("prismal_icon_downsample", ConfigSchema.Glass.OS4_ICON_GLASS_DOWNSAMPLE, isBool = true),
            PrismalFieldSpec("prismal_icon_fixed_wallpaper", ConfigSchema.Glass.OS4_ICON_GLASS_FIXED_WALLPAPER, isBool = true),
            PrismalFieldSpec("prismal_widget_corner", ConfigSchema.Glass.WIDGET_CORNER_RADIUS, isDp = true, min = 0.0, max = 128.0),
            PrismalFieldSpec("prismal_small_folder_corner", ConfigSchema.Glass.SMALL_FOLDER_CORNER_RADIUS, isDp = true, min = 0.0, max = 128.0),
            PrismalFieldSpec("prismal_outer_shadow_enabled", ConfigSchema.Glass.OUTER_SHADOW_ENABLED, isBool = true),
            PrismalFieldSpec("prismal_outer_shadow_radius", ConfigSchema.Glass.OUTER_SHADOW_RADIUS, isDp = true, min = 0.0, max = 24.0),
            PrismalFieldSpec("prismal_outer_shadow_offset_y", ConfigSchema.Glass.OUTER_SHADOW_OFFSET_Y, isDp = true, min = -16.0, max = 16.0),
            PrismalFieldSpec("prismal_outer_shadow_r", ConfigSchema.Glass.OUTER_SHADOW_RED, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_outer_shadow_g", ConfigSchema.Glass.OUTER_SHADOW_GREEN, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_outer_shadow_b", ConfigSchema.Glass.OUTER_SHADOW_BLUE, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_outer_shadow_a", ConfigSchema.Glass.OUTER_SHADOW_ALPHA, min = 0.0, max = 255.0),
            PrismalFieldSpec("prismal_outer_shadow_light_follow", ConfigSchema.Glass.OUTER_SHADOW_LIGHT_FOLLOW, divisor = 100.0, min = 0.0, max = 1.0),
        )

        internal val prismalSpecsByKey: Map<String, PrismalFieldSpec> = prismalSpecs.associateBy { it.confKey }

        private val dockKeys = listOf(
            ConfigSchema.Dock.WIDTH_OFFSET,
            ConfigSchema.Dock.HEIGHT_OFFSET,
            ConfigSchema.Dock.SPACING,
            ConfigSchema.Dock.BOTTOM_OFFSET,
            ConfigSchema.Dock.BLUR_CORNER_OFFSET,
        )
        val watchedKeys: Set<String> = buildSet {
            addAll(listOf(ConfigSchema.Core.ENABLED, ConfigSchema.Grid.ENABLED,
                ConfigSchema.Grid.NATIVE_HOOK_SAFETY, ConfigSchema.Debug.LOGGING,
                ConfigSchema.Grid.COLUMNS, ConfigSchema.Grid.ROWS,
                ConfigSchema.Grid.ICON_SIZE_ENABLED, ConfigSchema.Grid.ICON_SIZE_PERCENT,
                ConfigSchema.Grid.BIG_FOLDER_ENABLED,
                ConfigSchema.Grid.BIG_FOLDER_ALIGN_ICON_ENABLED,
                ConfigSchema.Grid.BIG_FOLDER_TITLE_ENABLED,
                ConfigSchema.Grid.FOLDER_PREVIEW_INSET,
                ConfigSchema.Grid.FOLDER_GRID_ENABLED,
                ConfigSchema.Grid.FOLDER_GRID_COLUMNS,
                ConfigSchema.Grid.FOLDER_GRID_ROWS,
                ConfigSchema.Grid.WIDGET_1X1_TO_2X2_ENABLED).map { it.name() })
            add(ConfigSchema.Grid.PREVENT_WIDGET_STRETCH.name())
            offsetKeys.forEach { add(it.name()); add("${it.name()}_tenths") }
            add(ConfigSchema.Glass.ENABLED.name())
            add(ConfigSchema.Glass.SOFT_GLASS_ENABLED.name())
            prismalSpecs.forEach { spec ->
                add(spec.configKey.name())
                if (spec.isDp) add("${spec.configKey.name()}_tenths")
            }
            add(GlassEdgeEffects.key(GlassEdgeEffects.ICON, ConfigSchema.Glass.BACKLIT_ENABLED))
            add(GlassEdgeEffects.key(GlassEdgeEffects.ICON, ConfigSchema.Glass.OUTER_SHADOW_ENABLED))
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
            add(ConfigSchema.Glass.USE_DRAG_MATERIAL.name())
            add(ConfigSchema.Glass.LARGE_FOLDER_GLASS.name())
            add(ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.name())
            listOf(
                ConfigSchema.Glass.ICON_SIZE_OFFSET,
                ConfigSchema.Glass.ICON_CORNER_RADIUS,
                ConfigSchema.Glass.LARGE_FOLDER_CORNER_RADIUS,
                ConfigSchema.Glass.LARGE_FOLDER_2X1_CORNER_RADIUS,
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
            add(ConfigSchema.Workstation.ALL_APPS_BLUR_PERCENT.name())
            add(ConfigSchema.Workstation.DOCK_CUSTOMIZATION.name())
            add(ConfigSchema.Workstation.TASKBAR_MODE.name())
            add(ConfigSchema.Workstation.DESKTOP_SYNC_NORMAL.name())
            listOf(
                ConfigSchema.Workstation.DOCK_ICON_GLASS_CORNER_RADIUS,
                ConfigSchema.Workstation.DOCK_HEIGHT_OFFSET,
                ConfigSchema.Workstation.DOCK_VERTICAL_OFFSET,
                ConfigSchema.Workstation.DOCK_ICON_LANDSCAPE_VERTICAL_OFFSET,
                ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET,
                ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET,
                ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING,
                ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING,
                ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_INDICATOR_Y,
                ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET,
                ConfigSchema.Workstation.ALL_APPS_PORTRAIT_TOP_SPACING,
                ConfigSchema.Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING,
                ConfigSchema.Workstation.ALL_APPS_PORTRAIT_INDICATOR_Y,
                ConfigSchema.Workstation.DESKTOP_LANDSCAPE_HORIZONTAL_OFFSET,
                ConfigSchema.Workstation.DESKTOP_LANDSCAPE_TOP_SPACING,
                ConfigSchema.Workstation.DESKTOP_LANDSCAPE_BOTTOM_SPACING,
                ConfigSchema.Workstation.DESKTOP_LANDSCAPE_INDICATOR_Y,
                ConfigSchema.Workstation.DESKTOP_PORTRAIT_HORIZONTAL_OFFSET,
                ConfigSchema.Workstation.DESKTOP_PORTRAIT_TOP_SPACING,
                ConfigSchema.Workstation.DESKTOP_PORTRAIT_BOTTOM_SPACING,
                ConfigSchema.Workstation.DESKTOP_PORTRAIT_INDICATOR_Y,
            ).forEach { add(it.name()); add("${it.name()}_tenths") }
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
                val folderGrid = boolean(ConfigSchema.Grid.FOLDER_GRID_ENABLED)
                val folderGridCols = if (folderGrid) integer(ConfigSchema.Grid.FOLDER_GRID_COLUMNS) else 0
                val folderGridRows = if (folderGrid) integer(ConfigSchema.Grid.FOLDER_GRID_ROWS) else 0
                val widget1x1To2x2 = boolean(ConfigSchema.Grid.WIDGET_1X1_TO_2X2_ENABLED)
                val preventWidgetStretch = boolean(ConfigSchema.Grid.PREVENT_WIDGET_STRETCH)
                val dockEnabled = boolean(ConfigSchema.Dock.ENABLED)
                val dividerEnabled = boolean(ConfigSchema.Divider.ENABLED)
                val glassEnabled = boolean(ConfigSchema.Glass.ENABLED)
                val softGlassEnabled = boolean(ConfigSchema.Glass.SOFT_GLASS_ENABLED)
                val prismalOverrides = if (glassEnabled) {
                    buildMap {
                        for (spec in prismalSpecs) {
                            val name = spec.configKey.name()
                            val tenthsName = "${name}_tenths"
                            if (spec.isBool) {
                                if (values.containsKey(name)) {
                                    @Suppress("UNCHECKED_CAST")
                                    val bKey = spec.configKey as ConfigKey<Boolean>
                                    put(spec.confKey, if (boolean(bKey)) 1.0 else 0.0)
                                }
                            } else if (spec.isDp) {
                                if (values.containsKey(name) || values.containsKey(tenthsName)) {
                                    @Suppress("UNCHECKED_CAST")
                                    val iKey = spec.configKey as ConfigKey<Int>
                                    val v = dp(iKey)
                                    require(v.isFinite() && v in spec.min..spec.max)
                                    put(spec.confKey, v)
                                }
                            } else {
                                if (values.containsKey(name)) {
                                    @Suppress("UNCHECKED_CAST")
                                    val iKey = spec.configKey as ConfigKey<Int>
                                    val v = integer(iKey).toDouble() / spec.divisor
                                    require(v.isFinite() && v in spec.min..spec.max)
                                    put(spec.confKey, v)
                                }
                            }
                        }
                        val iconBacklitKey = GlassEdgeEffects.key(GlassEdgeEffects.ICON, ConfigSchema.Glass.BACKLIT_ENABLED)
                        (values[iconBacklitKey] as? Boolean)?.let {
                            put("prismal_icon_backlit_enabled", if (it) 1.0 else 0.0)
                        }
                        val iconShadowKey = GlassEdgeEffects.key(GlassEdgeEffects.ICON, ConfigSchema.Glass.OUTER_SHADOW_ENABLED)
                        (values[iconShadowKey] as? Boolean)?.let {
                            put("prismal_icon_outer_shadow_enabled", if (it) 1.0 else 0.0)
                        }
                    }
                } else emptyMap()
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
                val allAppsBlurPercent = integer(ConfigSchema.Workstation.ALL_APPS_BLUR_PERCENT)
                val workstationEnabled = boolean(ConfigSchema.Workstation.DOCK_CUSTOMIZATION)
                if (!grid && !icons && !bigFolder && !folderGrid && !dockEnabled && !dividerEnabled && !glassEnabled &&
                    !widgetOverrides && !preventWidgetStretch && recentsBlurPercent == 100 &&
                    allAppsBlurPercent == 100 && !workstationEnabled) ""
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
                            bigFolderAlignIconEnabled = boolean(ConfigSchema.Grid.BIG_FOLDER_ALIGN_ICON_ENABLED),
                            bigFolderTitleEnabled = boolean(ConfigSchema.Grid.BIG_FOLDER_TITLE_ENABLED),
                            folderPreviewInset = integer(ConfigSchema.Grid.FOLDER_PREVIEW_INSET),
                            folderGridCols = folderGridCols,
                            folderGridRows = folderGridRows,
                            widget1x1To2x2Enabled = widget1x1To2x2,
                            preventWidgetStretchEnabled = preventWidgetStretch,
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
                            softGlassEnabled = softGlassEnabled,
                            prismalOverrides = prismalOverrides,
                            glassNativeOverrides = nativeGlassOverrides,
                            glassDockOverrides = dockGlassOverrides,
                            glassIconOverrides = iconGlassOverrides,
                            glassWidgetOverrides = widgetGlassOverrides,
                            glassLargeFolderOverrides = largeFolderGlassOverrides,
                            glassIcons = glassIcons,
                            glassWidgets = glassWidgets,
                            useDragMaterial = boolean(ConfigSchema.Glass.USE_DRAG_MATERIAL),
                            glassLargeFolder = glassLargeFolder,
                            glassLargeFolderCorner = if (glassEnabled && glassLargeFolder) {
                                dp(ConfigSchema.Glass.LARGE_FOLDER_CORNER_RADIUS)
                            } else 0.0,
                            glassLargeFolder2x1Corner = if (glassEnabled && glassLargeFolder) {
                                dp(ConfigSchema.Glass.LARGE_FOLDER_2X1_CORNER_RADIUS)
                            } else 0.0,
                            glassLargeFolderNativeOutline = glassLargeFolderNativeOutline,
                            widgetOverrides = widgetOverrides,
                            hookSafetyEnabled = boolean(ConfigSchema.Grid.NATIVE_HOOK_SAFETY),
                            debugLogging = boolean(ConfigSchema.Debug.LOGGING),
                            glassIconSize = if (glassIcons) dp(ConfigSchema.Glass.ICON_SIZE_OFFSET) else 0.0,
                            glassIconCorner = if (glassIcons) dp(ConfigSchema.Glass.ICON_CORNER_RADIUS) else 0.0,
                            recentsBlurPercent = recentsBlurPercent,
                            allAppsBlurPercent = allAppsBlurPercent,
                            workstation = if (workstationEnabled) {
                                Os4WorkstationOptions(
                                    dockCustomization = true,
                                    taskbarMode = boolean(ConfigSchema.Workstation.TASKBAR_MODE),
                                    dockIconGlassCornerRadius = dp(ConfigSchema.Workstation.DOCK_ICON_GLASS_CORNER_RADIUS),
                                    dockHeightOffset = dp(ConfigSchema.Workstation.DOCK_HEIGHT_OFFSET),
                                    dockVerticalOffset = dp(ConfigSchema.Workstation.DOCK_VERTICAL_OFFSET),
                                    dockIconLandscapeVerticalOffset = dp(ConfigSchema.Workstation.DOCK_ICON_LANDSCAPE_VERTICAL_OFFSET),
                                    dockIconTopOffset = dp(ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET),
                                    allAppsLandscapeHorizontalOffset = dp(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET),
                                    allAppsLandscapeTopSpacing = dp(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING),
                                    allAppsLandscapeBottomSpacing = dp(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING),
                                    allAppsLandscapeIndicatorY = dp(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_INDICATOR_Y),
                                    allAppsPortraitHorizontalOffset = dp(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET),
                                    allAppsPortraitTopSpacing = dp(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_TOP_SPACING),
                                    allAppsPortraitBottomSpacing = dp(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING),
                                    allAppsPortraitIndicatorY = dp(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_INDICATOR_Y),
                                    desktopSyncNormal = boolean(ConfigSchema.Workstation.DESKTOP_SYNC_NORMAL),
                                    desktopLandscapeHorizontalOffset = dp(ConfigSchema.Workstation.DESKTOP_LANDSCAPE_HORIZONTAL_OFFSET),
                                    desktopLandscapeTopSpacing = dp(ConfigSchema.Workstation.DESKTOP_LANDSCAPE_TOP_SPACING),
                                    desktopLandscapeBottomSpacing = dp(ConfigSchema.Workstation.DESKTOP_LANDSCAPE_BOTTOM_SPACING),
                                    desktopLandscapeIndicatorY = dp(ConfigSchema.Workstation.DESKTOP_LANDSCAPE_INDICATOR_Y),
                                    desktopPortraitHorizontalOffset = dp(ConfigSchema.Workstation.DESKTOP_PORTRAIT_HORIZONTAL_OFFSET),
                                    desktopPortraitTopSpacing = dp(ConfigSchema.Workstation.DESKTOP_PORTRAIT_TOP_SPACING),
                                    desktopPortraitBottomSpacing = dp(ConfigSchema.Workstation.DESKTOP_PORTRAIT_BOTTOM_SPACING),
                                    desktopPortraitIndicatorY = dp(ConfigSchema.Workstation.DESKTOP_PORTRAIT_INDICATOR_Y),
                                )
                            } else Os4WorkstationOptions(),
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
