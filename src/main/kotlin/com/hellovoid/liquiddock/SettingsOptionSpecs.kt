package com.hellovoid.liquiddock

import android.content.Context
import android.content.SharedPreferences
import com.hellovoid.liquiddock.config.ConfigKey
import com.hellovoid.liquiddock.config.ConfigSchema
import com.hellovoid.liquiddock.config.PresetManager

// Declarative settings option and optical catalogs. Rendering and navigation remain
// in ComposeSettingsActivity; changing these specs must preserve persisted keys/defaults.
internal enum class IntSection { General, StrokeBackground, StrokeGeometry }

internal data class IntSpec(
    val config: ConfigKey<Int>,
    val title: String,
    val unit: String = "dp",
    val dependency: String? = null,
    val section: IntSection = IntSection.General,
    val summary: String = optionSummary(config.name()),
    val dynamicMax: ((Context) -> Int)? = null,
) {
    val key: String get() = config.name()
    val default: Int get() = config.uiDefault()
    val min: Int get() = requireNotNull(config.minInt())
    fun max(context: Context): Int =
        (dynamicMax?.invoke(context) ?: requireNotNull(config.maxInt())).coerceAtLeast(min)
    val isDecimal: Boolean get() = config.storageMode() == ConfigKey.StorageMode.DP_TENTHS
    fun resetValue(): Float {
        if (!key.startsWith("liquid_")) return default.toFloat()
        val preset = PresetManager.defaultValues()
        val raw = if (isDecimal) preset["${key}_tenths"] else preset[key]
        return if (raw is Number) raw.toFloat() / if (isDecimal) 10f else 1f else if (isDecimal) config.uiDefaultDp() else default.toFloat()
    }
}

internal data class HighlightToggleSpec(
    val compactConfig: ConfigKey<Boolean>,
    val largeConfig: ConfigKey<Boolean>,
    val titleRes: Int,
    val summaryRes: Int,
)

internal fun optionSummary(key: String): String = when (key) {
    "grid_landscape_horizontal_distance" -> "同时调整横屏布局左右两侧的水平距离"
    "grid_landscape_top_distance" -> "相对扣除 Dock 后的可用区域调整横屏顶部距离"
    "grid_landscape_bottom_distance" -> "相对扣除 Dock 后的可用区域调整横屏底部距离"
    "grid_portrait_horizontal_distance" -> "同时调整竖屏布局左右两侧的水平距离"
    "grid_portrait_top_distance" -> "相对扣除 Dock 后的可用区域调整竖屏顶部距离"
    "grid_portrait_bottom_distance" -> "相对扣除 Dock 后的可用区域调整竖屏底部距离"
    "grid_landscape_row_gap" -> "增减横屏图标行之间的垂直距离"
    "grid_portrait_row_gap" -> "增减竖屏图标行之间的垂直距离"
    "indicator_landscape_y" -> "调整横屏页面指示器的垂直位置"
    "indicator_portrait_y" -> "调整竖屏页面指示器的垂直位置"
    "blur_radius" -> "仅用于原生模糊模式；液态玻璃使用独立模糊参数"
    "height_offset" -> "相对默认高度增减 Dock 背景高度"
    "width_offset" -> "相对默认宽度增减 Dock 背景长度"
    "corner_offset" -> "相对默认值调整外部描边圆角"
    "blur_corner_offset" -> "单独调整内部模糊背景圆角"
    "dock_spacing" -> "增减相邻 Dock 图标之间的距离"
    "dock_bottom_offset" -> "调整 Dock 与屏幕底部的距离"
    "dock_divider_width_dp" -> "调整图标分隔竖线的宽度"
    "dock_divider_height_scale" -> "调整分隔竖线占图标高度的百分比"
    "dock_divider_y_offset" -> "上下偏移分隔竖线，正值下移负值上移"
    "dock_divider_color_r" -> "分隔竖线颜色 · 红"
    "dock_divider_color_g" -> "分隔竖线颜色 · 绿"
    "dock_divider_color_b" -> "分隔竖线颜色 · 蓝"
    "dock_divider_alpha" -> "分隔竖线不透明度"
    "workstation_grid_horizontal_offset" -> "整体平移工作台桌面图标区域，适用于当前实际网格列数；不继承普通桌面水平偏移"
    "workstation_all_apps_landscape_horizontal_offset" -> "直接设置工作台所有应用横屏图标区左右间距；不叠加系统默认位置"
    "workstation_all_apps_landscape_top_spacing" -> "直接设置工作台所有应用横屏图标区上间距；不叠加系统默认位置"
    "workstation_all_apps_landscape_bottom_spacing" -> "直接设置工作台所有应用横屏图标区下间距；不叠加系统默认位置"
    "workstation_all_apps_portrait_horizontal_offset" -> "直接设置工作台所有应用竖屏图标区左右间距；不叠加系统默认位置"
    "workstation_all_apps_portrait_top_spacing" -> "直接设置工作台所有应用竖屏图标区上间距；不叠加系统默认位置"
    "workstation_all_apps_portrait_bottom_spacing" -> "直接设置工作台所有应用竖屏图标区下间距；不叠加系统默认位置"
    "workstation_dock_vertical_offset" -> "同时移动工作台 Dock 背景和全部图标，正值向下、负值向上；横竖屏共用"
    "workstation_dock_icon_landscape_vertical_offset" -> "仅在横屏调整图标垂直偏移，正值向下、负值向上"
    "workstation_dock_icon_top_offset" -> "工作台任务栏模式直接平移整行图标（正值向下、负值向上）；普通工作台模式仍调整图标顶部间距"
    "liquid_blur" -> "控制玻璃背景的模糊程度"
    "liquid_thickness" -> "控制虚拟玻璃厚度对折射效果的影响"
    "liquid_ior" -> "折射率 IOR（155 表示 1.55）；控制边缘透镜弯曲与反射，1.00 关闭透镜折射、1.55 保持默认视觉"
    "liquid_normal_strength" -> "控制表面起伏对折射与光照的影响"
    "liquid_dome" -> "控制玻璃表面的凸起程度"
    "liquid_lens_refraction" -> "边缘透镜折射位移增益；实际强度同时受 IOR 影响，0 关闭透镜位移，其他光学效果仍保留"
    "liquid_chromatic" -> "控制红、绿、蓝通道分离形成的色散强度"
    "liquid_tint_alpha" -> "玻璃颜色乘色强度"
    "liquid_tint_r" -> "玻璃颜色 · 红"
    "liquid_tint_g" -> "玻璃颜色 · 绿"
    "liquid_tint_b" -> "玻璃颜色 · 蓝"
    "liquid_highlight_width" -> "控制边缘反射与高光带宽度"
    "liquid_depth_effect" -> "控制圆角处折射方向向中心偏转；直边不变，0 为关闭"
    "liquid_brightness" -> "整体输出亮度"
    "liquid_caustics" -> "焦散强度"
    "liquid_prismal_refraction_inset" -> "调整玻璃可见遮罩的内缩；20 为原轮廓，保留抗锯齿"
    "liquid_prismal_displacement_scale" -> "折射与视差位移总倍率"
    "liquid_prismal_height_transition_width" -> "控制玻璃表面从边缘到中心的高度过渡范围"
    "liquid_prismal_smin_smoothing" -> "控制圆角边界的平滑程度"
    "liquid_prismal_edge_refraction_falloff" -> "控制边缘折射向内部的衰减；越高越集中在边缘"
    "liquid_prismal_fresnel_reflect" -> "控制随观察角度增强的边缘反射强度"
    "liquid_prismal_dispersion_r" -> "红色通道相对色散倍率"
    "liquid_prismal_dispersion_b" -> "蓝色通道相对色散倍率"
    "liquid_prismal_vibrancy" -> "玻璃内背景的饱和度；100% 为原色，低于 100% 去饱和"
    "liquid_os4_edge_width_px" -> "OS4 边缘带宽度，使用完整逻辑输出像素，不随下采样比例变化"
    "liquid_os4_reflect_offset_px" -> "OS4 沿 SDF 边缘法线采样背景的偏移；0 为自动"
    "liquid_os4_reflection_strength" -> "OS4 背景反射与当前玻璃颜色的混合强度"
    "liquid_os4_reflection_lighten" -> "OS4 方向光在暗色背景上的补光强度"
    "liquid_os4_directional_angle_range" -> "OS4 方向光角度软衰减范围，相对 π 的百分比"
    "liquid_os4_directional_intensity" -> "OS4 主方向光强度"
    "liquid_os4_directional_opposite_intensity" -> "OS4 反向补光强度"
    "liquid_prismal_light_dir_x" -> "光源水平方向"
    "liquid_prismal_light_dir_y" -> "光源垂直方向"
    "liquid_prismal_shadow_r" -> "内阴影 · 红"
    "liquid_prismal_shadow_g" -> "内阴影 · 绿"
    "liquid_prismal_shadow_b" -> "内阴影 · 蓝"
    "liquid_prismal_shadow_alpha" -> "内阴影透明度"
    "liquid_prismal_shadow_softness" -> "控制内阴影边缘的柔和程度"
    "liquid_prismal_transmittance" -> "控制玻璃的透射与透明程度"
    "liquid_prismal_backdrop_scale_x" -> "背景取样水平缩放"
    "liquid_prismal_backdrop_scale_y" -> "背景取样垂直缩放"
    "liquid_prismal_parallax_scale" -> "表面视差倍率"
    "stroke_base_r" -> "描边基础颜色的红色通道"
    "stroke_base_g" -> "描边基础颜色的绿色通道"
    "stroke_base_b" -> "描边基础颜色的蓝色通道"
    "stroke_base_alpha" -> "描边基础颜色的不透明度"
    "sq_stroke_w" -> "方圆形模式下的描边宽度"
    "sq_stroke_off" -> "方圆形描边相对 Dock 边界的内缩量"
    "sq_outer_cp" -> "控制方圆曲线从直边过渡到圆角的形状"
    "stroke_w" -> "Fill-Diff 外层与挖空层之间的宽度"
    "std_stroke_w" -> "普通路径描边模式使用的线宽"
    "dock_shadow_radius" -> "整个 Dock 阴影边缘的柔和程度"
    "dock_shadow_size" -> "整个 Dock 阴影向外扩散的最大距离"
    "dock_shadow_alpha" -> "整个 Dock 下方阴影的浓度"
    "dock_shadow_y" -> "整个 Dock 阴影的垂直偏移，可为负数"
    "shadow_radius" -> "仅描边阴影的柔化半径"
    "shadow_alpha" -> "仅描边阴影的不透明度"
    else -> "调整此功能的数值"
}

internal val launcher450IconSizeSpec = IntSpec(
    ConfigSchema.Grid.ICON_SIZE_PERCENT,
    "图标大小",
    "%",
    summary = "调整工作区、Dock 和小文件夹中的图标大小；100% 为系统默认",
)

internal val gridDimensionSpecs = listOf(
    IntSpec(ConfigSchema.Grid.COLUMNS, "横屏列数", "列", summary = "2–10 列；竖屏自动交换为行数"),
    IntSpec(ConfigSchema.Grid.ROWS, "横屏行数", "行", summary = "2–10 行；竖屏自动交换为列数"),
)

internal val gridSpecs = listOf(
    IntSpec(ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE, "横屏水平距离偏移"),
    IntSpec(ConfigSchema.Grid.LANDSCAPE_TOP_DISTANCE, "横屏顶部距离偏移"),
    IntSpec(ConfigSchema.Grid.LANDSCAPE_BOTTOM_DISTANCE, "横屏底部距离偏移"),
    IntSpec(ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE, "竖屏水平距离偏移"),
    IntSpec(ConfigSchema.Grid.PORTRAIT_TOP_DISTANCE, "竖屏顶部距离偏移"),
    IntSpec(ConfigSchema.Grid.PORTRAIT_BOTTOM_DISTANCE, "竖屏底部距离偏移"),
    IntSpec(ConfigSchema.Grid.LANDSCAPE_ROW_GAP, "横屏图标纵向间距偏移"),
    IntSpec(ConfigSchema.Grid.PORTRAIT_ROW_GAP, "竖屏图标纵向间距偏移"),
    IntSpec(ConfigSchema.Grid.LANDSCAPE_INDICATOR_Y, "横屏指示器 Y"),
    IntSpec(ConfigSchema.Grid.PORTRAIT_INDICATOR_Y, "竖屏指示器 Y"),
)
internal val splitGridSpecs = listOf(
    IntSpec(
        ConfigSchema.Grid.SPLIT_HORIZONTAL_OFFSET,
        "分屏水平偏移",
        summary = "0 保留系统分屏对齐；正值向右、负值向左；重启桌面后生效",
    ),
)
internal val dockSpecs = listOf(
    IntSpec(ConfigSchema.Dock.BLUR_RADIUS, "模糊强度", ""),
    IntSpec(ConfigSchema.Dock.HEIGHT_OFFSET, "高度偏移"),
    IntSpec(ConfigSchema.Dock.WIDTH_OFFSET, "宽度偏移"),
    IntSpec(ConfigSchema.Dock.BLUR_CORNER_OFFSET, "内部模糊圆角偏移"),
    IntSpec(ConfigSchema.Dock.SPACING, "Dock 图标间距"),
    IntSpec(ConfigSchema.Dock.BOTTOM_OFFSET, "Dock 底部偏移"),
)
internal fun workstationSpecsFor(vararg configs: ConfigKey<Int>): List<IntSpec> {
    val keys = configs.mapTo(hashSetOf()) { it.name() }
    return workstationSpecs.filter { it.key in keys }
}

internal val workstationDockSpecs by lazy {
    workstationSpecsFor(
        ConfigSchema.Workstation.DOCK_ICON_GLASS_CORNER_RADIUS,
        ConfigSchema.Workstation.DOCK_VERTICAL_OFFSET,
        ConfigSchema.Workstation.DOCK_ICON_LANDSCAPE_VERTICAL_OFFSET,
        ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET,
    )
}
internal val workstationDesktopSpecs by lazy {
    workstationSpecsFor(
        ConfigSchema.Workstation.GRID_HORIZONTAL_OFFSET,
    )
}
internal val workstationAppsLandscapeSpecs by lazy {
    workstationSpecsFor(
        ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET,
        ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING,
        ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING,
    )
}
internal val workstationAppsPortraitSpecs by lazy {
    workstationSpecsFor(
        ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET,
        ConfigSchema.Workstation.ALL_APPS_PORTRAIT_TOP_SPACING,
        ConfigSchema.Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING,
    )
}

internal val dividerSpecs = listOf(
    IntSpec(ConfigSchema.Divider.WIDTH_DP, "分隔线宽度", "dp×10"),
    IntSpec(ConfigSchema.Divider.HEIGHT_SCALE, "分隔线高度比例", "%"),
    IntSpec(ConfigSchema.Divider.Y_OFFSET_DP, "分隔线垂直偏移", "dp×10"),
    IntSpec(ConfigSchema.Divider.COLOR_RED, "分隔线颜色 · 红", ""),
    IntSpec(ConfigSchema.Divider.COLOR_GREEN, "分隔线颜色 · 绿", ""),
    IntSpec(ConfigSchema.Divider.COLOR_BLUE, "分隔线颜色 · 蓝", ""),
    IntSpec(ConfigSchema.Divider.ALPHA, "分隔线透明度", ""),
)
internal val dividerKeys = dividerSpecs.map { it.key }
internal fun hasLegacyDividerConfig(prefs: SharedPreferences): Boolean = dividerKeys.any(prefs::contains)
internal fun ensureDividerDefaults(prefs: SharedPreferences) {
    val e = prefs.edit()
    dividerSpecs.forEach { if (!prefs.contains(it.key)) e.putInt(it.key, it.default) }
    e.apply()
}
internal val workstationSpecs = listOf(
    IntSpec(ConfigSchema.Workstation.DOCK_ICON_GLASS_CORNER_RADIUS, "工作台 Dock 图标玻璃圆角", "dp"),
    IntSpec(ConfigSchema.Workstation.GRID_HORIZONTAL_OFFSET, "工作台桌面水平偏移"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET, "所有应用 · 横屏水平间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING, "所有应用 · 横屏上间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING, "所有应用 · 横屏下间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET, "所有应用 · 竖屏水平间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_TOP_SPACING, "所有应用 · 竖屏上间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING, "所有应用 · 竖屏下间距"),
    IntSpec(ConfigSchema.Workstation.DOCK_VERTICAL_OFFSET, "整个 Dock 垂直平移"),
    IntSpec(ConfigSchema.Workstation.DOCK_ICON_LANDSCAPE_VERTICAL_OFFSET, "横屏 · Dock 图标垂直偏移"),
    IntSpec(ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET, "竖屏 · Dock 图标垂直偏移"),
)
internal val recentsBlurSpec = IntSpec(
    ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT,
    "背景模糊程度",
    "%",
    summary = "控制进入多任务界面后壁纸背景的系统模糊；保留系统过渡动画",
)
internal val iconSizeOffsetSpec = IntSpec(ConfigSchema.Glass.ICON_SIZE_OFFSET, "图标尺寸偏移", "dp/边")
internal val iconCornerRadiusSpec = IntSpec(ConfigSchema.Glass.ICON_CORNER_RADIUS, "图标圆角", "dp")
internal val widgetSizeOffsetSpec = IntSpec(ConfigSchema.Glass.WIDGET_SIZE_OFFSET, "小部件尺寸偏移", "dp/边")
internal val widgetCornerRadiusSpec = IntSpec(ConfigSchema.Glass.WIDGET_CORNER_RADIUS, "小部件圆角", "dp")
internal val smallFolderSizeOffsetSpec = IntSpec(ConfigSchema.Glass.SMALL_FOLDER_SIZE_OFFSET, "小文件夹尺寸偏移", "dp/边")
internal val smallFolderCornerRadiusSpec = IntSpec(ConfigSchema.Glass.SMALL_FOLDER_CORNER_RADIUS, "小文件夹圆角", "dp")
internal val largeFolderSizeOffsetSpec = IntSpec(ConfigSchema.Glass.LARGE_FOLDER_SIZE_OFFSET, "大文件夹尺寸偏移", "dp/边")
internal val largeFolderCornerRadiusSpec = IntSpec(ConfigSchema.Glass.LARGE_FOLDER_CORNER_RADIUS, "大文件夹圆角", "dp")
internal val openFolderBlurSpec = IntSpec(ConfigSchema.Glass.OPEN_FOLDER_BLUR, "展开文件夹 · 模糊", "px", summary = "只影响打开文件夹时冻结画面的模糊")
internal val openFolderNativeBlurSpec = IntSpec(ConfigSchema.Glass.OPEN_FOLDER_NATIVE_BLUR_PERCENT, "展开文件夹 · 外部原生模糊", "%", summary = "调整文件夹外壁纸的系统模糊，独立于玻璃内模糊；下次打开文件夹或多任务返回时生效")
internal val openFolderIorSpec = IntSpec(ConfigSchema.Glass.OPEN_FOLDER_IOR, "展开文件夹 · 折射率", "×100", summary = "100 = 1.00（不折射），155 = 1.55")
internal val openFolderThicknessSpec = IntSpec(ConfigSchema.Glass.OPEN_FOLDER_THICKNESS, "展开文件夹 · 玻璃厚度", "dp")
internal val openFolderTintSpec = IntSpec(ConfigSchema.Glass.OPEN_FOLDER_TINT_ALPHA, "展开文件夹 · 染色透明度", "", summary = "0 = 完全透明，160 = 较强染色；颜色继承全局玻璃颜色")
internal val largeFolderPreviewIconScaleSpec = IntSpec(
    ConfigSchema.Glass.LARGE_FOLDER_PREVIEW_ICON_PERCENT,
    "2×2 文件夹内图标大小",
    "%",
    summary = "调整真实 2×2 四宫格和九宫格的内部图标比例；100% 为原生效果，不改变文件夹占位和背景",
)
internal val passBlurCaptureScaleSpec = IntSpec(
    ConfigSchema.Glass.PASSBLUR_CAPTURE_SCALE,
    "工作区背景采样分辨率",
    "%",
    summary = "降低工作区 PassBlur 背景纹理分辨率以节省采样与模糊开销；边缘折射、轮廓和高光始终以原生分辨率绘制。Dock 不受影响；配置实时生效",
)
internal val passBlurRenderFpsSpec = IntSpec(
    ConfigSchema.Glass.PASSBLUR_RENDER_FPS,
    "玻璃实时渲染刷新率上限（0 = Auto）",
    "fps",
    summary = "限制除 Dock 外实时玻璃的最高刷新率；0 = 自动跟随可用源帧。已加载的工作区、Launcher 玻璃及独立应用玻璃实时生效；首次加载缺失的 Hook 时才需重启对应作用域",
    dynamicMax = DisplayRefreshRatePolicy::maxSupportedRefreshRateHz,
)
internal val liquidSpecs = listOf(
    IntSpec(ConfigSchema.Glass.BLUR, "玻璃模糊", "px"),
    IntSpec(ConfigSchema.Glass.THICKNESS, "玻璃厚度"),
    IntSpec(ConfigSchema.Glass.IOR, "折射率 IOR", "%"),
    IntSpec(ConfigSchema.Glass.NORMAL_STRENGTH, "法线强度", "%"),
    IntSpec(ConfigSchema.Glass.DOME, "穹顶凸起", "%"),
    IntSpec(ConfigSchema.Glass.LENS_REFRACTION, "透镜折射倍率", "×"),
    IntSpec(ConfigSchema.Glass.DEPTH_EFFECT, "透镜中心偏转", "%"),
    IntSpec(ConfigSchema.Glass.CHROMATIC, "色散强度", ""),
    IntSpec(ConfigSchema.Glass.TINT_ALPHA, "玻璃底色透明度", ""),
    IntSpec(ConfigSchema.Glass.TINT_RED, "底色 · 红", ""),
    IntSpec(ConfigSchema.Glass.TINT_GREEN, "底色 · 绿", ""),
    IntSpec(ConfigSchema.Glass.TINT_BLUE, "底色 · 蓝", ""),
    IntSpec(ConfigSchema.Glass.HIGHLIGHT_WIDTH, "玻璃边缘厚度", "%"),
    IntSpec(ConfigSchema.Glass.BRIGHTNESS, "亮度", "%"),
    IntSpec(ConfigSchema.Glass.CAUSTICS, "焦散强度", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_REFRACTION_INSET, "折射内缩", "px"),
    IntSpec(ConfigSchema.Glass.PRISMAL_DISPLACEMENT_SCALE, "位移倍率", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_HEIGHT_TRANSITION_WIDTH, "高度过渡"),
    IntSpec(ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING, "圆角平滑", "px"),
    IntSpec(ConfigSchema.Glass.PRISMAL_EDGE_REFRACTION_FALLOFF, "边缘折射衰减", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_FRESNEL_REFLECT, "菲涅尔反射", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_DISPERSION_R, "红色散倍率", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_DISPERSION_B, "蓝色散倍率", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_VIBRANCY, "色彩饱和度", "%"),
    IntSpec(ConfigSchema.Glass.OS4_EDGE_WIDTH_PX, "OS4 边缘带宽度", "px"),
    IntSpec(ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX, "OS4 反射偏移", "px"),
    IntSpec(ConfigSchema.Glass.OS4_REFLECTION_STRENGTH, "OS4 反射强度", "%"),
    IntSpec(ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN, "OS4 暗部补光", "%"),
    IntSpec(ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE, "OS4 方向光角度范围", "%π"),
    IntSpec(ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY, "OS4 主方向光强度", "%"),
    IntSpec(ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY, "OS4 反向补光强度", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X, "光源 X", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y, "光源 Y", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_RED, "内阴影红", ""),
    IntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_GREEN, "内阴影绿", ""),
    IntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_BLUE, "内阴影蓝", ""),
    IntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_ALPHA, "内阴影透明度", ""),
    IntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS, "内阴影柔和度", ""),
    IntSpec(ConfigSchema.Glass.PRISMAL_TRANSMITTANCE, "透射率", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X, "背景缩放 X", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y, "背景缩放 Y", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE, "视差倍率", "%"),
)
/** Shared optical descriptors only; each scope keeps its own preferences and page. */
internal data class ScopedOpticalDescriptor(
    val config: ConfigKey<Int>,
    val title: String,
    val unit: String,
    val summary: String,
)

internal val scopedOpticalDescriptors: List<ScopedOpticalDescriptor> by lazy {
    val supported = ScopedGlassOptics.opticalKeys().mapTo(hashSetOf()) { it.name() }
    liquidSpecs.filter { it.key in supported }.map {
        ScopedOpticalDescriptor(it.config, it.title, it.unit, it.summary)
    }
}

internal val launcherHighlightSpecs = listOf(
    HighlightToggleSpec(ConfigSchema.LauncherHighlight.SKY_HAZE, ConfigSchema.LauncherHighlight.LARGE_SKY_HAZE, R.string.highlight_sky_haze, R.string.highlight_sky_haze_summary),
    HighlightToggleSpec(ConfigSchema.LauncherHighlight.CAUSTICS, ConfigSchema.LauncherHighlight.LARGE_CAUSTICS, R.string.highlight_caustics, R.string.highlight_caustics_summary),
    HighlightToggleSpec(ConfigSchema.LauncherHighlight.PRESS_GLOW, ConfigSchema.LauncherHighlight.LARGE_PRESS_GLOW, R.string.highlight_press_glow, R.string.highlight_press_glow_summary),
)
internal fun liquidSpecsFor(vararg configs: ConfigKey<Int>): List<IntSpec> {
    val keys = configs.mapTo(hashSetOf()) { it.name() }
    return liquidSpecs.filter { it.key in keys }
}

internal val liquidMaterialSpecs = liquidSpecsFor(
    ConfigSchema.Glass.BLUR,
    ConfigSchema.Glass.THICKNESS,
    ConfigSchema.Glass.PRISMAL_TRANSMITTANCE,
    ConfigSchema.Glass.BRIGHTNESS,
)
internal val liquidRefractionSpecs = liquidSpecsFor(
    ConfigSchema.Glass.IOR,
    ConfigSchema.Glass.NORMAL_STRENGTH,
    ConfigSchema.Glass.DOME,
    ConfigSchema.Glass.LENS_REFRACTION,
    ConfigSchema.Glass.DEPTH_EFFECT,
    ConfigSchema.Glass.PRISMAL_REFRACTION_INSET,
    ConfigSchema.Glass.PRISMAL_DISPLACEMENT_SCALE,
    ConfigSchema.Glass.PRISMAL_HEIGHT_TRANSITION_WIDTH,
    ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING,
    ConfigSchema.Glass.PRISMAL_EDGE_REFRACTION_FALLOFF,
)
internal val liquidColorSpecs = liquidSpecsFor(
    ConfigSchema.Glass.CHROMATIC,
    ConfigSchema.Glass.TINT_ALPHA,
    ConfigSchema.Glass.TINT_RED,
    ConfigSchema.Glass.TINT_GREEN,
    ConfigSchema.Glass.TINT_BLUE,
    ConfigSchema.Glass.PRISMAL_DISPERSION_R,
    ConfigSchema.Glass.PRISMAL_DISPERSION_B,
    ConfigSchema.Glass.PRISMAL_VIBRANCY,
)
internal val liquidLightingSpecs = liquidSpecsFor(
    ConfigSchema.Glass.HIGHLIGHT_WIDTH,
    ConfigSchema.Glass.CAUSTICS,
    ConfigSchema.Glass.PRISMAL_FRESNEL_REFLECT,
    ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X,
    ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y,
)
internal val liquidShadowSpecs = liquidSpecsFor(
    ConfigSchema.Glass.PRISMAL_SHADOW_RED,
    ConfigSchema.Glass.PRISMAL_SHADOW_GREEN,
    ConfigSchema.Glass.PRISMAL_SHADOW_BLUE,
    ConfigSchema.Glass.PRISMAL_SHADOW_ALPHA,
    ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS,
)
internal val liquidSamplingSpecs = liquidSpecsFor(
    ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X,
    ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y,
    ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE,
)
internal val liquidOs4Specs = liquidSpecsFor(
    ConfigSchema.Glass.OS4_EDGE_WIDTH_PX,
    ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX,
    ConfigSchema.Glass.OS4_REFLECTION_STRENGTH,
    ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN,
    ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE,
    ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY,
    ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY,
)


/**
 * Explicit semantic groups replace arbitrary chunked(3) boundaries on Prismal
 * pages. Each group contains at most 3 controls, avoiding the old large
 * offscreen glass card while keeping related parameters contiguous.
 */
internal data class LiquidSpecGroup(val title: String, val specs: List<IntSpec>) {
    init { require(specs.isNotEmpty() && specs.size <= 3) }
}

private fun liquidGroup(title: String, vararg configs: ConfigKey<Int>): LiquidSpecGroup =
    LiquidSpecGroup(title, configs.map { config ->
        liquidSpecs.single { it.config == config }
    })

internal val liquidMaterialGroups = listOf(
    liquidGroup("玻璃主体", ConfigSchema.Glass.BLUR,
        ConfigSchema.Glass.THICKNESS, ConfigSchema.Glass.PRISMAL_TRANSMITTANCE),
    liquidGroup("亮度", ConfigSchema.Glass.BRIGHTNESS),
)
internal val liquidRefractionGroups = listOf(
    liquidGroup("透镜几何", ConfigSchema.Glass.IOR,
        ConfigSchema.Glass.DOME, ConfigSchema.Glass.LENS_REFRACTION),
    liquidGroup("法线与位移", ConfigSchema.Glass.NORMAL_STRENGTH,
        ConfigSchema.Glass.DEPTH_EFFECT, ConfigSchema.Glass.PRISMAL_DISPLACEMENT_SCALE),
    liquidGroup("边缘折射", ConfigSchema.Glass.PRISMAL_REFRACTION_INSET,
        ConfigSchema.Glass.PRISMAL_EDGE_REFRACTION_FALLOFF),
    liquidGroup("边缘过渡与圆角", ConfigSchema.Glass.PRISMAL_HEIGHT_TRANSITION_WIDTH,
        ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING),
)
internal val liquidColorGroups = listOf(
    liquidGroup("玻璃底色", ConfigSchema.Glass.TINT_RED,
        ConfigSchema.Glass.TINT_GREEN, ConfigSchema.Glass.TINT_BLUE),
    liquidGroup("透明度与色彩表现", ConfigSchema.Glass.TINT_ALPHA,
        ConfigSchema.Glass.PRISMAL_VIBRANCY),
    liquidGroup("色散", ConfigSchema.Glass.CHROMATIC,
        ConfigSchema.Glass.PRISMAL_DISPERSION_R, ConfigSchema.Glass.PRISMAL_DISPERSION_B),
)
internal val liquidLightingGroups = listOf(
    liquidGroup("高光、焦散与反射", ConfigSchema.Glass.HIGHLIGHT_WIDTH,
        ConfigSchema.Glass.CAUSTICS, ConfigSchema.Glass.PRISMAL_FRESNEL_REFLECT),
    liquidGroup("光源方向", ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X,
        ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y),
)
internal val liquidOs4Groups = listOf(
    liquidGroup("OS4 反射几何", ConfigSchema.Glass.OS4_EDGE_WIDTH_PX,
        ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX),
    liquidGroup("OS4 反射亮度", ConfigSchema.Glass.OS4_REFLECTION_STRENGTH,
        ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN),
    liquidGroup("OS4 方向光", ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE,
        ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY,
        ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY),
)
internal val liquidShadowGroups = listOf(
    liquidGroup("内阴影颜色", ConfigSchema.Glass.PRISMAL_SHADOW_RED,
        ConfigSchema.Glass.PRISMAL_SHADOW_GREEN, ConfigSchema.Glass.PRISMAL_SHADOW_BLUE),
    liquidGroup("内阴影透明度与柔和度", ConfigSchema.Glass.PRISMAL_SHADOW_ALPHA,
        ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS),
)
internal val liquidSamplingGroups = listOf(
    liquidGroup("背景缩放与视差", ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X,
        ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y,
        ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE),
)

/** Shared ordering for the global pages and all per-component "全部参数" pages. */
internal val liquidAllGroups: List<LiquidSpecGroup> by lazy {
    (liquidMaterialGroups + liquidRefractionGroups + liquidColorGroups +
        liquidLightingGroups + liquidOs4Groups + liquidShadowGroups +
        liquidSamplingGroups).also { groups ->
        val keys = groups.flatMap { group -> group.specs.map(IntSpec::key) }
        require(keys.size == keys.toSet().size) { "Duplicate optical settings group entries" }
        require(keys.toSet() == liquidSpecs.map(IntSpec::key).toSet()) {
            "Every global optical setting must belong to exactly one group"
        }
    }
}

/** Prismal controls that the soft-glass path actually consumes, with independent preference keys. */
private val softGlassBaseKeys = setOf(
    ConfigSchema.Glass.BLUR.name(),
    ConfigSchema.Glass.THICKNESS.name(),
    ConfigSchema.Glass.PRISMAL_TRANSMITTANCE.name(),
    ConfigSchema.Glass.BRIGHTNESS.name(),
    ConfigSchema.Glass.IOR.name(),
    ConfigSchema.Glass.PRISMAL_REFRACTION_INSET.name(),
    ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING.name(),
    ConfigSchema.Glass.TINT_ALPHA.name(),
    ConfigSchema.Glass.TINT_RED.name(),
    ConfigSchema.Glass.TINT_GREEN.name(),
    ConfigSchema.Glass.TINT_BLUE.name(),
    ConfigSchema.Glass.PRISMAL_VIBRANCY.name(),
    ConfigSchema.Glass.OS4_EDGE_WIDTH_PX.name(),
    ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX.name(),
    ConfigSchema.Glass.OS4_REFLECTION_STRENGTH.name(),
    ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN.name(),
    ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE.name(),
    ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY.name(),
    ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY.name(),
    ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X.name(),
    ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y.name(),
    ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X.name(),
    ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y.name(),
)

internal val softGlassSpecs: List<IntSpec> by lazy {
    val inherited = liquidSpecs.filter { it.config.name() in softGlassBaseKeys }.map { spec ->
        val config = requireNotNull(ConfigSchema.SoftGlass.forBaseKey(spec.config.name())) {
            "Missing independent soft-glass key for ${spec.config.name()}"
        }
        spec.copy(
            config = config,
            unit = if (spec.config.name() == ConfigSchema.Glass.THICKNESS.name()) "px" else spec.unit,
            title = when (spec.config.name()) {
                ConfigSchema.Glass.THICKNESS.name() -> "柔光折射厚度"
                ConfigSchema.Glass.BRIGHTNESS.name() -> "柔光亮度增量"
                ConfigSchema.Glass.PRISMAL_REFRACTION_INSET.name() -> "柔光形状边缘内缩"
                ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING.name() -> "柔光形状圆角平滑"
                else -> spec.title.replace("OS4 ", "柔光 ")
            },
            summary = when (spec.config.name()) {
                ConfigSchema.Glass.THICKNESS.name() ->
                    "HyperOS 4 原生折射厚度，直接使用像素，不进行 dp 密度换算"
                ConfigSchema.Glass.BRIGHTNESS.name() ->
                    "按 OS4 uBrightness 向颜色通道增加亮度；100 表示增加 1.0，不继承普通模式亮度。OS3 捕获图像为不透明源，默认从 0 开始以免整块裁白"
                ConfigSchema.Glass.PRISMAL_REFRACTION_INSET.name() ->
                    "控制 OS3 Prismal 表面轮廓内缩；玻璃尺寸与圆角保持 OS3 几何"
                ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING.name() ->
                    "控制 OS3 Prismal SDF 圆角平滑，并供窄边缘光法线采样使用"
                else -> "仅在柔光玻璃模式生效。${spec.summary}"
            },
        )
    }
    inherited + listOf(
        IntSpec(ConfigSchema.SoftGlass.OS4_DARKER, "OS4 暗部压低", "%",
            summary = "按亮度范围将背景颜色压向 OS4 使用的深紫色"),
        IntSpec(ConfigSchema.SoftGlass.OS4_EDGE_POWER, "OS4 边缘曲线幂次", "×100",
            summary = "驱动 mi_ltc 窄边缘光的 SDF；主玻璃折射与反射使用 OS4 固定曲线"),
        IntSpec(ConfigSchema.SoftGlass.OS4_LIGHT_Z, "OS4 光源 Z", "×100"),
        IntSpec(ConfigSchema.SoftGlass.OS4_BACKGROUND_BLUR_RADIUS, "OS4 背景模糊尺度", "×1000",
            summary = "独立生成 uBgColorTex 背景源；0–1000 映射到 OS3 PassBlur 的 0–60px 半径，500 对应 30px"),
        IntSpec(ConfigSchema.SoftGlass.OS4_BACKGROUND_BLUR_MIX, "OS4 背景层模糊混合", "‰",
            summary = "混合原始截屏和独立的 OS4 背景模糊源；折射采样仍使用单独的 PassBlur 半径"),
        IntSpec(ConfigSchema.SoftGlass.OS4_EDGE_AMBIENT, "OS4 边缘环境光", "%"),
        IntSpec(ConfigSchema.SoftGlass.OS4_EDGE_LIGHT_LENGTH, "OS4 边缘光角度幂次", "×100"),
        IntSpec(ConfigSchema.SoftGlass.OS4_DARKER_LOW, "OS4 暗部范围起点", "%"),
        IntSpec(ConfigSchema.SoftGlass.OS4_DARKER_HIGH, "OS4 暗部范围终点", "%"),
        IntSpec(ConfigSchema.SoftGlass.OS4_BACKGROUND_SATURATION, "OS4 背景饱和度", "%"),
        IntSpec(ConfigSchema.SoftGlass.OS4_BACKGROUND_BRIGHTNESS, "OS4 背景亮度偏移", "×100",
            summary = "按 OS4 uBgColorBrightness 乘背景源 alpha 后加到 RGB；OS3 捕获为不透明源，增大后容易裁白"),
        IntSpec(ConfigSchema.SoftGlass.OS4_INNER_BOTTOM, "OS4 内层底部提亮", "×100"),
        IntSpec(ConfigSchema.SoftGlass.OS4_INNER_WHITE, "OS4 内层提白", "%"),
        IntSpec(ConfigSchema.SoftGlass.OS4_INNER_MIX, "OS4 内层混合", "%"),
        IntSpec(ConfigSchema.SoftGlass.OS4_LUMINANCE_AMOUNT, "OS4 亮度曲线强度", "%"),
        IntSpec(ConfigSchema.SoftGlass.OS4_LUMINANCE_C0, "OS4 亮度曲线系数 C0", "×100"),
        IntSpec(ConfigSchema.SoftGlass.OS4_LUMINANCE_C1, "OS4 亮度曲线系数 C1", "×100"),
        IntSpec(ConfigSchema.SoftGlass.OS4_LUMINANCE_C2, "OS4 亮度曲线系数 C2", "×100"),
        IntSpec(ConfigSchema.SoftGlass.OS4_LUMINANCE_C3, "OS4 亮度曲线系数 C3", "×100"),
        IntSpec(ConfigSchema.SoftGlass.OS4_COLOR_POWER, "OS4 颜色幂次", "×100"),
        IntSpec(ConfigSchema.SoftGlass.OS4_UNSHADE, "OS4 原色保留", "%"),
        IntSpec(ConfigSchema.SoftGlass.OS4_BURN, "OS4 内层曲线幂次", "×100"),
    )
}

internal val softGlassGroups: List<LiquidSpecGroup> by lazy {
    fun option(key: ConfigKey<Int>) = requireNotNull(softGlassSpecs.singleOrNull {
        it.config.name() == key.name()
    })

    fun softOption(base: ConfigKey<Int>) = option(
        requireNotNull(ConfigSchema.SoftGlass.forBaseKey(base.name())))

    val groups = listOf(
        LiquidSpecGroup("柔光 · 主体与模糊", listOf(
            ConfigSchema.Glass.BLUR,
            ConfigSchema.Glass.THICKNESS,
            ConfigSchema.Glass.PRISMAL_TRANSMITTANCE,
        ).map(::softOption)),
        LiquidSpecGroup("柔光 · 折射与形状", listOf(
            ConfigSchema.Glass.IOR,
            ConfigSchema.Glass.PRISMAL_REFRACTION_INSET,
            ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING,
        ).map(::softOption)),
        LiquidSpecGroup("柔光 · 底色与亮度", listOf(
            ConfigSchema.Glass.TINT_ALPHA,
            ConfigSchema.Glass.PRISMAL_VIBRANCY,
            ConfigSchema.Glass.BRIGHTNESS,
        ).map(::softOption)),
        LiquidSpecGroup("柔光 · 底色通道", listOf(
            ConfigSchema.Glass.TINT_RED,
            ConfigSchema.Glass.TINT_GREEN,
            ConfigSchema.Glass.TINT_BLUE,
        ).map(::softOption)),
        LiquidSpecGroup("柔光 · OS4 折射与反射", listOf(
            ConfigSchema.Glass.OS4_EDGE_WIDTH_PX,
            ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX,
            ConfigSchema.Glass.OS4_REFLECTION_STRENGTH,
        ).map(::softOption)),
        LiquidSpecGroup("柔光 · OS4 反射补光", listOf(
            ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN,
        ).map(::softOption)),
        LiquidSpecGroup("柔光 · OS4 方向光", listOf(
            ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE,
            ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY,
            ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY,
        ).map(::softOption)),
        LiquidSpecGroup("柔光 · 方向光位置", listOf(
            ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X,
            ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y,
            ConfigSchema.SoftGlass.OS4_LIGHT_Z,
        ).map { key ->
            if (key.name().startsWith(ConfigSchema.SoftGlass.KEY_PREFIX)) option(key)
            else softOption(key)
        }),
        LiquidSpecGroup("柔光 · 背景取样", listOf(
            ConfigSchema.SoftGlass.OS4_BACKGROUND_BLUR_RADIUS,
            ConfigSchema.SoftGlass.OS4_BACKGROUND_BLUR_MIX,
            ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X,
            ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y,
        ).map { key ->
            if (key.name().startsWith(ConfigSchema.SoftGlass.KEY_PREFIX)) option(key)
            else softOption(key)
        }),
        LiquidSpecGroup("柔光 · OS4 窄边缘光", listOf(
            ConfigSchema.SoftGlass.OS4_EDGE_POWER,
            ConfigSchema.SoftGlass.OS4_EDGE_AMBIENT,
            ConfigSchema.SoftGlass.OS4_EDGE_LIGHT_LENGTH,
        ).map(::option)),
        LiquidSpecGroup("柔光 · OS4 暗部处理", listOf(
            ConfigSchema.SoftGlass.OS4_DARKER,
            ConfigSchema.SoftGlass.OS4_DARKER_LOW,
            ConfigSchema.SoftGlass.OS4_DARKER_HIGH,
        ).map(::option)),
        LiquidSpecGroup("柔光 · OS4 背景颜色", listOf(
            ConfigSchema.SoftGlass.OS4_BACKGROUND_SATURATION,
            ConfigSchema.SoftGlass.OS4_BACKGROUND_BRIGHTNESS,
        ).map(::option)),
        LiquidSpecGroup("柔光 · OS4 内层混合", listOf(
            ConfigSchema.SoftGlass.OS4_INNER_BOTTOM,
            ConfigSchema.SoftGlass.OS4_INNER_WHITE,
            ConfigSchema.SoftGlass.OS4_INNER_MIX,
        ).map(::option)),
        LiquidSpecGroup("柔光 · OS4 亮度曲线", listOf(
            ConfigSchema.SoftGlass.OS4_LUMINANCE_AMOUNT,
            ConfigSchema.SoftGlass.OS4_LUMINANCE_C0,
            ConfigSchema.SoftGlass.OS4_LUMINANCE_C1,
        ).map(::option)),
        LiquidSpecGroup("柔光 · OS4 亮度曲线系数", listOf(
            ConfigSchema.SoftGlass.OS4_LUMINANCE_C2,
            ConfigSchema.SoftGlass.OS4_LUMINANCE_C3,
            ConfigSchema.SoftGlass.OS4_BURN,
        ).map(::option)),
        LiquidSpecGroup("柔光 · OS4 最终颜色", listOf(
            ConfigSchema.SoftGlass.OS4_COLOR_POWER,
            ConfigSchema.SoftGlass.OS4_UNSHADE,
        ).map(::option)),
    )
    val groupedKeys = groups.flatMap { group -> group.specs.map(IntSpec::key) }
    require(groupedKeys.size == groupedKeys.toSet().size) {
        "Every soft-glass setting must appear in exactly one group"
    }
    require(groupedKeys.toSet() == softGlassSpecs.map(IntSpec::key).toSet()) {
        "Every visible soft-glass setting must belong to exactly one group"
    }
    groups
}

internal val strokeSpecs = listOf(
    IntSpec(ConfigSchema.Dock.CORNER_OFFSET, "描边圆角偏移", "dp", null, IntSection.StrokeGeometry),
    IntSpec(ConfigSchema.Dock.STROKE_RED, "描边底色 · 红", "", "dock_stroke", IntSection.StrokeBackground),
    IntSpec(ConfigSchema.Dock.STROKE_GREEN, "描边底色 · 绿", "", "dock_stroke", IntSection.StrokeBackground),
    IntSpec(ConfigSchema.Dock.STROKE_BLUE, "描边底色 · 蓝", "", "dock_stroke", IntSection.StrokeBackground),
    IntSpec(ConfigSchema.Dock.STROKE_ALPHA, "描边底色 · 透明度", "", "dock_stroke", IntSection.StrokeBackground),
    IntSpec(ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH, "方圆形描边宽度", "dp", "squircle", IntSection.StrokeGeometry),
    IntSpec(ConfigSchema.Dock.SQUIRCLE_STROKE_OFFSET, "方圆形描边内缩", "dp", "squircle", IntSection.StrokeGeometry),
    IntSpec(ConfigSchema.Dock.SQUIRCLE_CONTROL_POINT, "方圆曲线控制点", "", "squircle", IntSection.StrokeGeometry),
    IntSpec(ConfigSchema.Dock.FILL_DIFF_STROKE_WIDTH, "Fill-Diff 宽度", "dp", "fill_diff", IntSection.StrokeGeometry),
    IntSpec(ConfigSchema.Dock.STANDARD_STROKE_WIDTH, "标准描边宽度", "dp", null, IntSection.StrokeGeometry),
)
internal val shadowSpecs = listOf(
    IntSpec(ConfigSchema.Dock.SHADOW_RADIUS, "Dock 阴影柔化", "dp", "dock_shadow"),
    IntSpec(ConfigSchema.Dock.SHADOW_SIZE, "Dock 阴影扩散大小", "dp", "dock_shadow"),
    IntSpec(ConfigSchema.Dock.SHADOW_ALPHA, "Dock 阴影透明度", "", "dock_shadow"),
    IntSpec(ConfigSchema.Dock.SHADOW_Y, "Dock 阴影 Y 偏移", "dp", "dock_shadow"),
    IntSpec(ConfigSchema.Dock.STROKE_SHADOW_RADIUS, "描边阴影半径", "dp", "stroke_shadow"),
    IntSpec(ConfigSchema.Dock.STROKE_SHADOW_ALPHA, "描边阴影透明度", "", "stroke_shadow"),
)


// Animation-page option values, reset summaries and configuration identities.
internal val animationWorkspaceVisibilitySpec = IntSpec(
    ConfigSchema.Animation.WORKSPACE_VISIBILITY,
    "工作区玻璃显隐",
    "ms",
    summary = "工作区玻璃淡入淡出时长；已加载的桌面 Hook 即时生效",
)
internal val animationUnlockGlassFadeSpec = IntSpec(
    ConfigSchema.Animation.UNLOCK_GLASS_FADE,
    "解锁时工作区玻璃渐显",
    "ms",
    summary = "跟随 Launcher 原生解锁飞入动画末尾渐显；0 = 立即显示，修改后下次解锁生效",
)
internal val animationFolderOpenGlassFadeSpec = IntSpec(
    ConfigSchema.Animation.FOLDER_OPEN_GLASS_FADE,
    "文件夹打开 · 其他玻璃渐隐",
    "ms",
    summary = "文件夹打开时其他文件夹与小组件玻璃的退场时长；0 = 立即隐藏，下次打开文件夹生效",
)
internal val animationDockResizeSpec = IntSpec(
    ConfigSchema.Animation.DOCK_RESIZE,
    "Dock 尺寸变化",
    "ms",
    summary = "LiquidDock 顺滑尺寸动画的时长；需关闭系统 Dock 尺寸过渡并开启 LiquidDock 顺滑尺寸动画；重启桌面后生效",
)
internal val animationPressInSpec = IntSpec(
    ConfigSchema.Animation.PRESS_IN,
    "按压进入",
    "ms",
    summary = "LiquidDock 玻璃按下反馈速度；已加载的桌面 Hook 即时生效",
)
internal val animationPressOutSpec = IntSpec(
    ConfigSchema.Animation.PRESS_OUT,
    "按压释放",
    "ms",
    summary = "LiquidDock 玻璃松手恢复速度；已加载的桌面 Hook 即时生效",
)
internal val animationShortcutDismissSpec = IntSpec(
    ConfigSchema.Animation.SHORTCUT_POPUP_DISMISS_FADE,
    "快捷菜单退出渐隐",
    "ms",
    summary = "桌面快捷菜单关闭时 LiquidDock 玻璃的快速淡出；已加载的桌面 Hook 即时生效",
)
internal val animationSecurityCenterExitSpec = IntSpec(
    ConfigSchema.Animation.SECURITY_CENTER_EXIT_FADE,
    "安全中心退出渐隐",
    "ms",
    summary = "安全中心侧边栏与工具箱玻璃退出时的 LiquidDock 淡出；已加载的安全中心 Hook 即时生效",
)
internal val animationSettingsPageSpec = IntSpec(
    ConfigSchema.Animation.SETTINGS_PAGE,
    "GUI 页面切换",
    "ms",
    summary = "设置页面滑动与淡入淡出；下一次页面切换立即生效，无需重启",
)

