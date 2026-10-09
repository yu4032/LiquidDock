package com.hellovoid.liquiddock

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.hellovoid.liquiddock.config.ConfigKey
import com.hellovoid.liquiddock.config.ConfigSchema
import com.hellovoid.liquiddock.config.PresetManager
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.window.WindowDialog

// A single observer updates only the changed preference key; long slider pages
// do not register listeners per control or re-read preferences on every frame.
private val LocalSettingsPreferenceRevisions = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }

class ComposeSettingsActivity : SettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val controller = remember { ThemeController(ColorSchemeMode.MonetSystem) }
            val preferences = remember { PreferenceManager.getDefaultSharedPreferences(this) }
            val revisions = remember(preferences) { mutableStateMapOf<String, Int>() }
            DisposableEffect(preferences) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
                    if (changed != null) {
                        val key = changed.removeSuffix("_tenths")
                        revisions[key] = (revisions[key] ?: 0) + 1
                    }
                }
                preferences.registerOnSharedPreferenceChangeListener(listener)
                onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            CompositionLocalProvider(LocalSettingsPreferenceRevisions provides revisions) {
                MiuixTheme(controller = controller) { LiquidDockSettings(this) }
            }
        }
    }
}

private enum class Page(val titleRes: Int) {
    Home(R.string.app_name),
    LayoutHub(R.string.page_layout_hub),
    GlassHub(R.string.page_glass_hub),
    MoreHub(R.string.page_more_hub),

    Grid(R.string.page_grid),
    GridBasics(R.string.page_grid_basics),
    GridLandscape(R.string.page_grid_landscape),
    GridPortrait(R.string.page_grid_portrait),
    GridSplit(R.string.page_grid_split),

    Dock(R.string.page_dock),
    DockBehavior(R.string.page_dock_behavior),
    DockGeometry(R.string.page_dock_geometry),
    DockRecentBlacklist(R.string.page_dock_recent_blacklist),
    Divider(R.string.page_divider),
    Workstation(R.string.page_workstation),
    WorkstationDock(R.string.page_workstation_dock),
    WorkstationDesktop(R.string.page_workstation_desktop),
    WorkstationAppsLandscape(R.string.page_workstation_apps_landscape),
    WorkstationAppsPortrait(R.string.page_workstation_apps_portrait),
    Recents(R.string.page_recents),

    Liquid(R.string.page_liquid),
    LiquidMaterial(R.string.page_liquid_material),
    LiquidRefraction(R.string.page_liquid_refraction),
    LiquidColor(R.string.page_liquid_color),
    LiquidLighting(R.string.page_liquid_lighting),
    LiquidShadow(R.string.page_liquid_shadow),
    LiquidSampling(R.string.page_liquid_sampling),
    LiquidOs4(R.string.page_liquid_os4),

    GlassComponents(R.string.page_glass_components),
    GlassIcons(R.string.page_glass_icons),
    GlassWidgets(R.string.page_glass_widgets),
    GlassFolders(R.string.page_glass_folders),
    GlassMenus(R.string.page_glass_menus),
    DialogCustomization(R.string.page_dialog_customization),
    ThirdPartyApps(R.string.page_third_party_apps),
    Gboard(R.string.page_gboard),
    WidgetComponents(R.string.page_widget_components),
    LauncherHighlights(R.string.page_launcher_highlights),
    LauncherHighlightsCompact(R.string.page_launcher_highlights_compact),
    LauncherHighlightsLarge(R.string.page_launcher_highlights_large),
    Stroke(R.string.page_stroke),
    Shadow(R.string.page_shadow),

    SecurityCenterSidebar(R.string.page_security_center_sidebar),
    Animation(R.string.page_animation),
    AnimationWorkspace(R.string.page_animation_workspace),
    AnimationInteraction(R.string.page_animation_interaction),
    AnimationPopups(R.string.page_animation_popups),
    AnimationSystem(R.string.page_animation_system),
    AnimationGui(R.string.page_animation_gui),
    Data(R.string.page_data),
    About(R.string.page_about),
}

private val ROOT_PAGES = listOf(Page.Home, Page.LayoutHub, Page.GlassHub, Page.MoreHub)

private fun isRootPage(page: Page): Boolean = page in ROOT_PAGES

// Ordinary UI writes are mirrored to API101 Remote Preferences by LiquidDockApp's
// SharedPreferences listener. No per-control JSON/file/root synchronization exists.

private enum class IntSection { General, StrokeBackground, StrokeGeometry }

private data class IntSpec(
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
        return if (raw is Number) raw.toFloat() / if (isDecimal) 10f else 1f else default.toFloat()
    }
}

private data class HighlightToggleSpec(
    val compactConfig: ConfigKey<Boolean>,
    val largeConfig: ConfigKey<Boolean>,
    val titleRes: Int,
    val summaryRes: Int,
)

private fun optionSummary(key: String): String = when (key) {
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
    "workstation_dock_icon_top_offset" -> "通过图标顶部装饰偏移调整工作台 Dock 图标垂直位置；正值通常向下移动"
    "liquid_folder_corner_radius" -> "0 表示自动跟随 MIUI 原生圆角；大于 0 时同时覆盖桌面与拖动文件夹玻璃"
    "liquid_blur" -> "控制玻璃背景的模糊程度"
    "liquid_thickness" -> "控制虚拟玻璃厚度对折射效果的影响"
    "liquid_ior" -> "折射率；越高，边缘弯曲越明显"
    "liquid_normal_strength" -> "控制表面起伏对折射与光照的影响"
    "liquid_dome" -> "控制玻璃表面的凸起程度"
    "liquid_lens_refraction" -> "控制边缘折射位移倍率；0 关闭透镜位移，其他玻璃光学效果仍保留"
    "liquid_chromatic" -> "控制红、绿、蓝通道分离形成的色散强度"
    "liquid_tint_alpha" -> "玻璃颜色乘色强度"
    "liquid_tint_r" -> "玻璃颜色 · 红"
    "liquid_tint_g" -> "玻璃颜色 · 绿"
    "liquid_tint_b" -> "玻璃颜色 · 蓝"
    "liquid_highlight_width" -> "控制边缘反射与高光带宽度"
    "liquid_depth_effect" -> "控制圆角处折射方向向中心偏转；直边不变，0 为关闭"
    "liquid_brightness" -> "整体输出亮度"
    "liquid_specular_sharp" -> "镜面高光锐度"
    "liquid_specular_strength" -> "双镜面高光强度"
    "liquid_rim_light" -> "边缘光强度"
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
    "liquid_prismal_plain_highlight" -> "基础边缘高光"
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

private val launcher450IconSizeSpec = IntSpec(
    ConfigSchema.Grid.ICON_SIZE_PERCENT,
    "图标大小",
    "%",
    summary = "调整工作区、Dock 和小文件夹中的图标大小；100% 为系统默认",
)

private val gridDimensionSpecs = listOf(
    IntSpec(ConfigSchema.Grid.COLUMNS, "横屏列数", "列", summary = "2–10 列；竖屏自动交换为行数"),
    IntSpec(ConfigSchema.Grid.ROWS, "横屏行数", "行", summary = "2–6 行；竖屏自动交换为列数"),
)

private val gridSpecs = listOf(
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
private val splitGridSpecs = listOf(
    IntSpec(
        ConfigSchema.Grid.SPLIT_HORIZONTAL_OFFSET,
        "分屏水平偏移",
        summary = "0 保留系统分屏对齐；正值向右、负值向左；重启桌面后生效",
    ),
)
private val dockSpecs = listOf(
    IntSpec(ConfigSchema.Dock.BLUR_RADIUS, "模糊强度", ""),
    IntSpec(ConfigSchema.Dock.HEIGHT_OFFSET, "高度偏移"),
    IntSpec(ConfigSchema.Dock.WIDTH_OFFSET, "宽度偏移"),
    IntSpec(ConfigSchema.Dock.BLUR_CORNER_OFFSET, "内部模糊圆角偏移"),
    IntSpec(ConfigSchema.Dock.SPACING, "Dock 图标间距"),
    IntSpec(ConfigSchema.Dock.BOTTOM_OFFSET, "Dock 底部偏移"),
)
private fun workstationSpecsFor(vararg configs: ConfigKey<Int>): List<IntSpec> {
    val keys = configs.mapTo(hashSetOf()) { it.name() }
    return workstationSpecs.filter { it.key in keys }
}

private val workstationDockSpecs by lazy {
    workstationSpecsFor(
        ConfigSchema.Workstation.DOCK_ICON_GLASS_CORNER_RADIUS,
        ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET,
    )
}
private val workstationDesktopSpecs by lazy {
    workstationSpecsFor(
        ConfigSchema.Workstation.GRID_HORIZONTAL_OFFSET,
    )
}
private val workstationAppsLandscapeSpecs by lazy {
    workstationSpecsFor(
        ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET,
        ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING,
        ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING,
    )
}
private val workstationAppsPortraitSpecs by lazy {
    workstationSpecsFor(
        ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET,
        ConfigSchema.Workstation.ALL_APPS_PORTRAIT_TOP_SPACING,
        ConfigSchema.Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING,
    )
}

private val dividerSpecs = listOf(
    IntSpec(ConfigSchema.Divider.WIDTH_DP, "分隔线宽度", "dp×10"),
    IntSpec(ConfigSchema.Divider.HEIGHT_SCALE, "分隔线高度比例", "%"),
    IntSpec(ConfigSchema.Divider.Y_OFFSET_DP, "分隔线垂直偏移", "dp×10"),
    IntSpec(ConfigSchema.Divider.COLOR_RED, "分隔线颜色 · 红", ""),
    IntSpec(ConfigSchema.Divider.COLOR_GREEN, "分隔线颜色 · 绿", ""),
    IntSpec(ConfigSchema.Divider.COLOR_BLUE, "分隔线颜色 · 蓝", ""),
    IntSpec(ConfigSchema.Divider.ALPHA, "分隔线透明度", ""),
)
private val dividerKeys = dividerSpecs.map { it.key }
private fun hasLegacyDividerConfig(prefs: SharedPreferences): Boolean = dividerKeys.any(prefs::contains)
private fun ensureDividerDefaults(prefs: SharedPreferences) {
    val e = prefs.edit()
    dividerSpecs.forEach { if (!prefs.contains(it.key)) e.putInt(it.key, it.default) }
    e.apply()
}
private val workstationSpecs = listOf(
    IntSpec(ConfigSchema.Workstation.DOCK_ICON_GLASS_CORNER_RADIUS, "工作台 Dock 图标玻璃圆角", "dp"),
    IntSpec(ConfigSchema.Workstation.GRID_HORIZONTAL_OFFSET, "工作台桌面水平偏移"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET, "所有应用 · 横屏水平间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING, "所有应用 · 横屏上间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING, "所有应用 · 横屏下间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET, "所有应用 · 竖屏水平间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_TOP_SPACING, "所有应用 · 竖屏上间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING, "所有应用 · 竖屏下间距"),
    IntSpec(ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET, "工作台 Dock 图标垂直偏移"),
)
private val recentsBlurSpec = IntSpec(
    ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT,
    "背景模糊程度",
    "%",
    summary = "控制进入多任务界面后壁纸背景的系统模糊；保留系统过渡动画",
)
private val iconSizeOffsetSpec = IntSpec(ConfigSchema.Glass.ICON_SIZE_OFFSET, "图标尺寸偏移", "dp/边")
private val iconCornerRadiusSpec = IntSpec(ConfigSchema.Glass.ICON_CORNER_RADIUS, "图标圆角", "dp")
private val widgetSizeOffsetSpec = IntSpec(ConfigSchema.Glass.WIDGET_SIZE_OFFSET, "小部件尺寸偏移", "dp/边")
private val widgetCornerRadiusSpec = IntSpec(ConfigSchema.Glass.WIDGET_CORNER_RADIUS, "小部件圆角", "dp")
private val smallFolderSizeOffsetSpec = IntSpec(ConfigSchema.Glass.SMALL_FOLDER_SIZE_OFFSET, "小文件夹尺寸偏移", "dp/边")
private val smallFolderCornerRadiusSpec = IntSpec(ConfigSchema.Glass.SMALL_FOLDER_CORNER_RADIUS, "小文件夹圆角", "dp")
private val largeFolderSizeOffsetSpec = IntSpec(ConfigSchema.Glass.LARGE_FOLDER_SIZE_OFFSET, "大文件夹尺寸偏移", "dp/边")
private val largeFolderCornerRadiusSpec = IntSpec(ConfigSchema.Glass.LARGE_FOLDER_CORNER_RADIUS, "大文件夹圆角", "dp")
private val passBlurCaptureScaleSpec = IntSpec(
    ConfigSchema.Glass.PASSBLUR_CAPTURE_SCALE,
    "工作区背景采样分辨率",
    "%",
    summary = "降低工作区 PassBlur 背景纹理分辨率以节省采样与模糊开销；边缘折射、轮廓和高光始终以原生分辨率绘制。Dock 不受影响；配置实时生效",
)
private val passBlurRenderFpsSpec = IntSpec(
    ConfigSchema.Glass.PASSBLUR_RENDER_FPS,
    "玻璃实时渲染刷新率上限（0 = Auto）",
    "fps",
    summary = "限制除 Dock 外实时玻璃的最高刷新率；0 = 自动跟随可用源帧。工作区与已连接的 Launcher 玻璃实时生效；独立应用需按其作用域重启",
    dynamicMax = DisplayRefreshRatePolicy::maxSupportedRefreshRateHz,
)
private val liquidSpecs = listOf(
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
    IntSpec(ConfigSchema.Glass.SPECULAR_SHARPNESS, "高光锐度", ""),
    IntSpec(ConfigSchema.Glass.SPECULAR_STRENGTH, "高光强度", "%"),
    IntSpec(ConfigSchema.Glass.RIM_LIGHT, "边缘光强度", "%"),
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
    IntSpec(ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT, "基础高光", "%"),
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
private val launcherHighlightSpecs = listOf(
    HighlightToggleSpec(ConfigSchema.LauncherHighlight.SKY_HAZE, ConfigSchema.LauncherHighlight.LARGE_SKY_HAZE, R.string.highlight_sky_haze, R.string.highlight_sky_haze_summary),
    HighlightToggleSpec(ConfigSchema.LauncherHighlight.SPECULAR, ConfigSchema.LauncherHighlight.LARGE_SPECULAR, R.string.highlight_specular, R.string.highlight_specular_summary),
    HighlightToggleSpec(ConfigSchema.LauncherHighlight.LIT_RIM, ConfigSchema.LauncherHighlight.LARGE_LIT_RIM, R.string.highlight_lit_rim, R.string.highlight_lit_rim_summary),
    HighlightToggleSpec(ConfigSchema.LauncherHighlight.OPPOSITE_RIM, ConfigSchema.LauncherHighlight.LARGE_OPPOSITE_RIM, R.string.highlight_opposite_rim, R.string.highlight_opposite_rim_summary),
    HighlightToggleSpec(ConfigSchema.LauncherHighlight.CORNER_RIM, ConfigSchema.LauncherHighlight.LARGE_CORNER_RIM, R.string.highlight_corner_rim, R.string.highlight_corner_rim_summary),
    HighlightToggleSpec(ConfigSchema.LauncherHighlight.FACE_SHEEN, ConfigSchema.LauncherHighlight.LARGE_FACE_SHEEN, R.string.highlight_face_sheen, R.string.highlight_face_sheen_summary),
    HighlightToggleSpec(ConfigSchema.LauncherHighlight.PLAIN_HIGHLIGHT, ConfigSchema.LauncherHighlight.LARGE_PLAIN_HIGHLIGHT, R.string.highlight_plain, R.string.highlight_plain_summary),
    HighlightToggleSpec(ConfigSchema.LauncherHighlight.CAUSTICS, ConfigSchema.LauncherHighlight.LARGE_CAUSTICS, R.string.highlight_caustics, R.string.highlight_caustics_summary),
    HighlightToggleSpec(ConfigSchema.LauncherHighlight.PRESS_GLOW, ConfigSchema.LauncherHighlight.LARGE_PRESS_GLOW, R.string.highlight_press_glow, R.string.highlight_press_glow_summary),
)
private fun liquidSpecsFor(vararg configs: ConfigKey<Int>): List<IntSpec> {
    val keys = configs.mapTo(hashSetOf()) { it.name() }
    return liquidSpecs.filter { it.key in keys }
}

private val liquidMaterialSpecs = liquidSpecsFor(
    ConfigSchema.Glass.BLUR,
    ConfigSchema.Glass.THICKNESS,
    ConfigSchema.Glass.PRISMAL_TRANSMITTANCE,
    ConfigSchema.Glass.BRIGHTNESS,
)
private val liquidRefractionSpecs = liquidSpecsFor(
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
private val liquidColorSpecs = liquidSpecsFor(
    ConfigSchema.Glass.CHROMATIC,
    ConfigSchema.Glass.TINT_ALPHA,
    ConfigSchema.Glass.TINT_RED,
    ConfigSchema.Glass.TINT_GREEN,
    ConfigSchema.Glass.TINT_BLUE,
    ConfigSchema.Glass.PRISMAL_DISPERSION_R,
    ConfigSchema.Glass.PRISMAL_DISPERSION_B,
    ConfigSchema.Glass.PRISMAL_VIBRANCY,
)
private val liquidLightingSpecs = liquidSpecsFor(
    ConfigSchema.Glass.HIGHLIGHT_WIDTH,
    ConfigSchema.Glass.SPECULAR_SHARPNESS,
    ConfigSchema.Glass.SPECULAR_STRENGTH,
    ConfigSchema.Glass.RIM_LIGHT,
    ConfigSchema.Glass.CAUSTICS,
    ConfigSchema.Glass.PRISMAL_FRESNEL_REFLECT,
    ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT,
    ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X,
    ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y,
)
private val liquidShadowSpecs = liquidSpecsFor(
    ConfigSchema.Glass.PRISMAL_SHADOW_RED,
    ConfigSchema.Glass.PRISMAL_SHADOW_GREEN,
    ConfigSchema.Glass.PRISMAL_SHADOW_BLUE,
    ConfigSchema.Glass.PRISMAL_SHADOW_ALPHA,
    ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS,
)
private val liquidSamplingSpecs = liquidSpecsFor(
    ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X,
    ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y,
    ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE,
)
private val liquidOs4Specs = liquidSpecsFor(
    ConfigSchema.Glass.OS4_EDGE_WIDTH_PX,
    ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX,
    ConfigSchema.Glass.OS4_REFLECTION_STRENGTH,
    ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN,
    ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE,
    ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY,
    ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY,
)

private val strokeSpecs = listOf(
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
private val shadowSpecs = listOf(
    IntSpec(ConfigSchema.Dock.SHADOW_RADIUS, "Dock 阴影柔化", "dp", "dock_shadow"),
    IntSpec(ConfigSchema.Dock.SHADOW_SIZE, "Dock 阴影扩散大小", "dp", "dock_shadow"),
    IntSpec(ConfigSchema.Dock.SHADOW_ALPHA, "Dock 阴影透明度", "", "dock_shadow"),
    IntSpec(ConfigSchema.Dock.SHADOW_Y, "Dock 阴影 Y 偏移", "dp", "dock_shadow"),
    IntSpec(ConfigSchema.Dock.STROKE_SHADOW_RADIUS, "描边阴影半径", "dp", "stroke_shadow"),
    IntSpec(ConfigSchema.Dock.STROKE_SHADOW_ALPHA, "描边阴影透明度", "", "stroke_shadow"),
)

private data class HubEntry(
    val page: Page,
    val titleRes: Int,
    val summary: String,
)

private val overviewEntries = listOf(
    HubEntry(Page.Dock, R.string.page_dock, "Dock 外观、位置、动画与最近程序"),
    HubEntry(Page.Liquid, R.string.page_liquid, "材质、折射、色彩、光照与性能"),
    HubEntry(Page.Grid, R.string.page_grid, "网格尺寸、横竖屏与分屏布局"),
    HubEntry(Page.Animation, R.string.page_animation, "工作区、Dock、玻璃交互与系统界面"),
)

private val layoutEntries = listOf(
    HubEntry(Page.Grid, R.string.page_grid, "网格尺寸、横竖屏间距与分屏布局"),
    HubEntry(Page.Dock, R.string.page_dock, "Dock 行为、尺寸、位置与最近程序"),
    HubEntry(Page.Workstation, R.string.page_workstation, "工作台桌面、Dock 与所有应用布局"),
    HubEntry(Page.Recents, R.string.page_recents, "多任务壁纸背景与模糊"),
    HubEntry(Page.SecurityCenterSidebar, R.string.page_security_center_sidebar, "侧滑呼出、工具箱与侧边栏设置"),
)

private val glassEntries = listOf(
    HubEntry(Page.Liquid, R.string.page_liquid, "液态玻璃材质与光学参数"),
    HubEntry(Page.GlassComponents, R.string.page_glass_components, "图标、小组件、文件夹与菜单外观"),
    HubEntry(Page.Stroke, R.string.page_stroke, "Dock 描边、方圆曲线与颜色"),
    HubEntry(Page.Shadow, R.string.page_shadow, "Dock 与描边阴影"),
    HubEntry(Page.Animation, R.string.page_animation, "工作区、Dock 与玻璃交互动画"),
    HubEntry(Page.ThirdPartyApps, R.string.page_third_party_apps, "Gboard 等第三方应用适配"),
)

private val moreEntries = listOf(
    HubEntry(Page.Data, R.string.page_data, "默认配置、导入与导出"),
    HubEntry(Page.About, R.string.page_about, "第三方开源项目与许可"),
)

private val gridEntries = listOf(
    HubEntry(Page.GridBasics, R.string.page_grid_basics, "图标大小、网格尺寸与小组件拉伸"),
    HubEntry(Page.GridLandscape, R.string.page_grid_landscape, "横屏距离、行距与页面指示器"),
    HubEntry(Page.GridPortrait, R.string.page_grid_portrait, "竖屏距离、行距与页面指示器"),
    HubEntry(Page.GridSplit, R.string.page_grid_split, "分屏布局距离与对齐"),
)

private val dockEntries = listOf(
    HubEntry(Page.DockBehavior, R.string.page_dock_behavior, "功能开关、尺寸动画与系统入口"),
    HubEntry(Page.DockGeometry, R.string.page_dock_geometry, "高度、宽度、圆角、间距与底部位置"),
    HubEntry(Page.DockRecentBlacklist, R.string.page_dock_recent_blacklist, "过滤最近程序，同时由后续候选补位"),
    HubEntry(Page.Divider, R.string.page_divider, "分隔线尺寸、位置、颜色与透明度"),
)

private val workstationEntries = listOf(
    HubEntry(Page.WorkstationDock, R.string.page_workstation_dock, "Dock 图标垂直偏移与玻璃圆角"),
    HubEntry(Page.WorkstationDesktop, R.string.page_workstation_desktop, "按当前网格列数整体调整工作台桌面水平位置"),
    HubEntry(Page.WorkstationAppsLandscape, R.string.page_workstation_apps_landscape, "所有应用横屏水平与上下间距"),
    HubEntry(Page.WorkstationAppsPortrait, R.string.page_workstation_apps_portrait, "所有应用竖屏水平与上下间距"),
)

private val liquidEntries = listOf(
    HubEntry(Page.LiquidMaterial, R.string.page_liquid_material, "模糊、厚度、透射率与亮度"),
    HubEntry(Page.LiquidRefraction, R.string.page_liquid_refraction, "折射、法线、穹顶与位移"),
    HubEntry(Page.LiquidColor, R.string.page_liquid_color, "底色、色散与鲜艳度"),
    HubEntry(Page.LiquidLighting, R.string.page_liquid_lighting, "高光、边缘光、焦散与光源方向"),
    HubEntry(Page.LiquidShadow, R.string.page_liquid_shadow, "玻璃内部阴影颜色与柔和度"),
    HubEntry(Page.LiquidSampling, R.string.page_liquid_sampling, "背景缩放、帧率与帧同步"),
    HubEntry(Page.LiquidOs4, R.string.page_liquid_os4, "OS4 边缘反射与方向光"),
)

private val componentEntries = listOf(
    HubEntry(Page.GlassIcons, R.string.page_glass_icons, "桌面图标、Dock 功能图标与多任务胶囊"),
    HubEntry(Page.GlassWidgets, R.string.page_glass_widgets, "小组件玻璃、内容适配与组件隐藏"),
    HubEntry(Page.GlassFolders, R.string.page_glass_folders, "大小文件夹玻璃与独立尺寸"),
    HubEntry(Page.GlassMenus, R.string.page_glass_menus, "快捷菜单、系统顶部菜单与对话弹窗"),
)

private val highlightEntries = listOf(
    HubEntry(Page.LauncherHighlightsCompact, R.string.page_launcher_highlights_compact, "天空雾光、镜面高光、边缘光、焦散与按压辉光"),
    HubEntry(Page.LauncherHighlightsLarge, R.string.page_launcher_highlights_large, "小组件与大文件夹使用独立高光开关"),
)

private val animationEntries = listOf(
    HubEntry(Page.AnimationWorkspace, R.string.page_animation_workspace, "工作区显隐、Dock 恢复与尺寸变化"),
    HubEntry(Page.AnimationInteraction, R.string.page_animation_interaction, "玻璃按压进入与释放"),
    HubEntry(Page.AnimationPopups, R.string.page_animation_popups, "快捷菜单与安全中心退出渐隐"),
    HubEntry(Page.AnimationSystem, R.string.page_animation_system, "系统手势手柄等界面联动"),
    HubEntry(Page.AnimationGui, R.string.page_animation_gui, "设置页面切换时长"),
)

private val animationWorkspaceVisibilitySpec = IntSpec(
    ConfigSchema.Animation.WORKSPACE_VISIBILITY,
    "工作区玻璃显隐",
    "ms",
    summary = "工作区玻璃淡入淡出时长；重启桌面后生效",
)
private val animationDockIconRevealSpec = IntSpec(
    ConfigSchema.Animation.DOCK_ICON_REVEAL,
    "Dock 图标玻璃恢复",
    "ms",
    summary = "应用退出动画末尾的 Dock 图标玻璃恢复；重启桌面后生效",
)
private val animationDockResizeSpec = IntSpec(
    ConfigSchema.Animation.DOCK_RESIZE,
    "Dock 尺寸变化",
    "ms",
    summary = "LiquidDock 顺滑尺寸动画的时长；需关闭系统 Dock 尺寸过渡并开启 LiquidDock 顺滑尺寸动画；重启桌面后生效",
)
private val animationPressInSpec = IntSpec(
    ConfigSchema.Animation.PRESS_IN,
    "按压进入",
    "ms",
    summary = "LiquidDock 玻璃按下反馈速度；重启桌面后生效",
)
private val animationPressOutSpec = IntSpec(
    ConfigSchema.Animation.PRESS_OUT,
    "按压释放",
    "ms",
    summary = "LiquidDock 玻璃松手恢复速度；重启桌面后生效",
)
private val animationShortcutDismissSpec = IntSpec(
    ConfigSchema.Animation.SHORTCUT_POPUP_DISMISS_FADE,
    "快捷菜单退出渐隐",
    "ms",
    summary = "桌面快捷菜单关闭时 LiquidDock 玻璃的快速淡出；重启桌面后生效",
)
private val animationSecurityCenterExitSpec = IntSpec(
    ConfigSchema.Animation.SECURITY_CENTER_EXIT_FADE,
    "安全中心退出渐隐",
    "ms",
    summary = "安全中心侧边栏与工具箱玻璃退出时的 LiquidDock 淡出；重启安全中心后生效",
)
private val animationSettingsPageSpec = IntSpec(
    ConfigSchema.Animation.SETTINGS_PAGE,
    "GUI 页面切换",
    "ms",
    summary = "设置页面滑动与淡入淡出；下一次页面切换立即生效，无需重启",
)

@Composable
private fun LiquidDockSettings(activity: ComposeSettingsActivity) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
    val uiPrefs = remember {
        activity.getSharedPreferences(SETTINGS_UI_PREFS, Context.MODE_PRIVATE)
    }
    var uiGlassEnabled by remember {
        mutableStateOf(uiPrefs.getBoolean(SETTINGS_UI_GLASS_ENABLED, true))
    }
    val masterRevision = LocalSettingsPreferenceRevisions.current[ConfigSchema.Core.ENABLED.name()] ?: 0
    var masterEnabled by remember(masterRevision) {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Core.ENABLED.name(),
                ConfigSchema.Core.ENABLED.uiDefault(),
            ),
        )
    }
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    var navigationStack by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val root = isRootPage(page)
    val selectedRootIndex = ROOT_PAGES.indexOf(page).coerceAtLeast(0)
    val rootIcons = listOf(
        MiuixIcons.Home,
        MiuixIcons.GridView,
        MiuixIcons.Image,
        MiuixIcons.Settings,
    )
    val restartScopeItems = listOf(
        RestartScopeItem("com.miui.home", "桌面", "com.miui.home"),
        RestartScopeItem("com.android.systemui", "系统界面", "com.android.systemui"),
        RestartScopeItem("com.miui.securitycenter", "安全中心", "com.miui.securitycenter"),
        RestartScopeItem("com.google.android.inputmethod.latin", "Gboard", "com.google.android.inputmethod.latin"),
        RestartScopeItem("com.android.quicksearchbox", "系统搜索", "com.android.quicksearchbox"),
    )
    var showRestartScopes by remember { mutableStateOf(false) }
    var selectedRestartScopes by remember {
        mutableStateOf(setOf("com.miui.home", "com.android.systemui"))
    }

    fun navigateTo(target: Page) {
        if (target == page) return
        navigationStack = ArrayList(navigationStack).apply { add(page.name) }
        page = target
    }

    fun navigateBack() {
        val previous = navigationStack.lastOrNull()
            ?.let { runCatching { Page.valueOf(it) }.getOrNull() }
        navigationStack = ArrayList(navigationStack.dropLast(1))
        page = previous ?: Page.Home
    }

    fun selectRoot(target: Page) {
        navigationStack = arrayListOf()
        page = target
    }

    BackHandler(enabled = !root) { navigateBack() }

    ModernSettingsScaffold(
        title = stringResource(page.titleRes),
        glassEnabled = uiGlassEnabled,
        showBack = !root,
        backLabel = stringResource(R.string.action_back),
        onBack = { navigateBack() },
        actions = {
            ModernTopActionButton(
                text = stringResource(R.string.action_restart_scopes),
                onClick = {
                    selectedRestartScopes = when (page) {
                        Page.Gboard -> setOf("com.google.android.inputmethod.latin")
                        Page.SecurityCenterSidebar,
                        Page.Animation,
                        Page.AnimationPopups -> setOf(
                            "com.miui.home",
                            "com.miui.securitycenter",
                        )
                        Page.AnimationSystem -> setOf("com.android.systemui")
                        Page.Home -> setOf("com.miui.home", "com.android.systemui")
                        else -> setOf("com.miui.home")
                    }
                    showRestartScopes = true
                },
            )
        },
        bottomBar = {
            if (root) {
                ModernBottomNavigation(
                    labels = listOf(
                        stringResource(R.string.tab_overview),
                        stringResource(R.string.tab_layout),
                        stringResource(R.string.tab_glass),
                        stringResource(R.string.tab_more),
                    ),
                    icons = rootIcons,
                    selectedIndex = selectedRootIndex,
                    onSelected = { index -> selectRoot(ROOT_PAGES[index]) },
                )
            }
        },
        overlay = {
            RestartScopesDialog(
                visible = showRestartScopes,
                items = restartScopeItems,
                selected = selectedRestartScopes,
                onToggle = { id, checked ->
                    selectedRestartScopes = if (checked) {
                        selectedRestartScopes + id
                    } else {
                        selectedRestartScopes - id
                    }
                    if (prefs.getBoolean(
                            ConfigSchema.Debug.LOGGING.name(),
                            ConfigSchema.Debug.LOGGING.runtimeFallback(),
                        )
                    ) {
                        (activity.application as LiquidDockApp).logScopedRestart(
                            "UI_TOGGLE|$id|checked=$checked|selected=$selectedRestartScopes",
                        )
                    }
                },
                onDismiss = { showRestartScopes = false },
                onRestart = { confirmedSelection ->
                    showRestartScopes = false
                    // Submit the exact selection visible in the dialog, not a
                    // separately captured parent-page restart snapshot.
                    activity.restartHookScopes(confirmedSelection.toSet())
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val duration = prefs.getInt(
                    ConfigSchema.Animation.SETTINGS_PAGE.name(),
                    ConfigSchema.Animation.SETTINGS_PAGE.uiDefault(),
                ).coerceIn(0, 700)
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally(tween(duration)) { it / 4 } + fadeIn(tween(duration))) togetherWith
                            (slideOutHorizontally(tween(duration)) { -it / 6 } + fadeOut(tween(duration)))
                } else {
                    (slideInHorizontally(tween(duration)) { -it / 4 } + fadeIn(tween(duration))) togetherWith
                            (slideOutHorizontally(tween(duration)) { it / 6 } + fadeOut(tween(duration)))
                }
            },
            label = "settings-page",
        ) { target ->
            when (target) {
                Page.Home -> HomePage(
                    padding,
                    prefs,
                    masterEnabled,
                    { masterEnabled = it },
                    ::navigateTo,
                )
                Page.LayoutHub -> HubPage(
                    padding,
                    "主屏幕、Dock、工作台与多任务布局",
                    layoutEntries,
                    ::navigateTo,
                )
                Page.GlassHub -> HubPage(
                    padding,
                    "玻璃材质、组件样式、动画与第三方应用外观",
                    glassEntries,
                    ::navigateTo,
                )
                Page.MoreHub -> MoreHubPage(
                    padding = padding,
                    glassEnabled = uiGlassEnabled,
                    onGlassEnabledChange = { enabled ->
                        uiGlassEnabled = enabled
                        uiPrefs.edit()
                            .putBoolean(SETTINGS_UI_GLASS_ENABLED, enabled)
                            .apply()
                    },
                    open = ::navigateTo,
                )

                Page.Grid -> GridPage(padding, prefs, masterEnabled, ::navigateTo)
                Page.GridBasics -> GridBasicsPage(padding, prefs, masterEnabled)
                Page.GridLandscape -> GridLandscapePage(padding, prefs, masterEnabled)
                Page.GridPortrait -> GridPortraitPage(padding, prefs, masterEnabled)
                Page.GridSplit -> GridSplitPage(padding, prefs, masterEnabled)

                Page.Dock -> DockPage(padding, prefs, masterEnabled, ::navigateTo)
                Page.DockBehavior -> DockBehaviorPage(padding, prefs, masterEnabled)
                Page.DockGeometry -> DockGeometryPage(padding, prefs, masterEnabled)
                Page.DockRecentBlacklist -> DockRecentBlacklistPage(padding, activity, prefs, masterEnabled)
                Page.Divider -> DividerPage(padding, prefs, masterEnabled)
                Page.Workstation -> WorkstationPage(padding, prefs, masterEnabled, ::navigateTo)
                Page.WorkstationDock -> WorkstationSpecPage(
                    padding, prefs, masterEnabled, workstationDockSpecs,
                    "工作台 Dock 图标位置与玻璃圆角参数",
                )
                Page.WorkstationDesktop -> WorkstationSpecPage(
                    padding, prefs, masterEnabled, workstationDesktopSpecs,
                    "工作台桌面水平偏移作用于当前实际网格，不限制固定列数",
                )
                Page.WorkstationAppsLandscape -> WorkstationSpecPage(
                    padding, prefs, masterEnabled, workstationAppsLandscapeSpecs,
                    "所有应用横屏布局参数",
                )
                Page.WorkstationAppsPortrait -> WorkstationSpecPage(
                    padding, prefs, masterEnabled, workstationAppsPortraitSpecs,
                    "所有应用竖屏布局参数",
                )
                Page.Recents -> RecentsPage(padding, prefs, masterEnabled)

                Page.Liquid -> LiquidPage(padding, prefs, masterEnabled, ::navigateTo)
                Page.LiquidMaterial -> LiquidSpecPage(
                    padding,
                    prefs,
                    masterEnabled,
                    "控制玻璃主体的基础材质感",
                    liquidMaterialSpecs,
                )
                Page.LiquidRefraction -> LiquidSpecPage(
                    padding,
                    prefs,
                    masterEnabled,
                    "折射形状、位移、边缘衰减与表面几何",
                    liquidRefractionSpecs,
                )
                Page.LiquidColor -> LiquidSpecPage(
                    padding,
                    prefs,
                    masterEnabled,
                    "底色、色散与背景鲜艳度",
                    liquidColorSpecs,
                )
                Page.LiquidLighting -> LiquidSpecPage(
                    padding,
                    prefs,
                    masterEnabled,
                    "镜面高光、边缘光、焦散与光源方向",
                    liquidLightingSpecs,
                )
                Page.LiquidShadow -> LiquidSpecPage(
                    padding,
                    prefs,
                    masterEnabled,
                    "玻璃内部阴影颜色与柔和程度",
                    liquidShadowSpecs,
                )
                Page.LiquidSampling -> LiquidSamplingPage(padding, prefs, masterEnabled)
                Page.LiquidOs4 -> LiquidSpecPage(
                    padding,
                    prefs,
                    masterEnabled,
                    "OS4 风格边缘反射与方向补光",
                    liquidOs4Specs,
                )

                Page.GlassComponents -> GlassComponentsPage(padding, ::navigateTo)
                Page.GlassIcons -> GlassIconsPage(padding, prefs, masterEnabled, ::navigateTo)
                Page.GlassWidgets -> GlassWidgetsPage(padding, prefs, masterEnabled, ::navigateTo)
                Page.GlassFolders -> GlassFoldersPage(padding, prefs, masterEnabled)
                Page.GlassMenus -> GlassMenusPage(padding, prefs, masterEnabled, ::navigateTo)
                Page.DialogCustomization -> DialogGlassSettingsPage(padding, prefs, masterEnabled)
                Page.ThirdPartyApps -> ThirdPartyAppsPage(
                    padding = padding,
                    prefs = prefs,
                    masterEnabled = masterEnabled,
                    openGboard = { navigateTo(Page.Gboard) },
                )
                Page.Gboard -> GboardSettingsPage(padding, prefs, masterEnabled)
                Page.WidgetComponents -> WidgetComponentsPage(padding, activity, prefs)
                Page.LauncherHighlights -> LauncherHighlightsPage(padding, ::navigateTo)
                Page.LauncherHighlightsCompact -> LauncherHighlightTogglePage(
                    padding, prefs, masterEnabled, compact = true,
                )
                Page.LauncherHighlightsLarge -> LauncherHighlightTogglePage(
                    padding, prefs, masterEnabled, compact = false,
                )
                Page.Stroke -> StrokePage(padding, prefs, masterEnabled)
                Page.Shadow -> ShadowPage(padding, prefs, masterEnabled)

                Page.SecurityCenterSidebar -> SecurityCenterSidebarPage(
                    padding = padding,
                    prefs = prefs,
                    masterEnabled = masterEnabled,
                )
                Page.Animation -> AnimationPage(padding, ::navigateTo)
                Page.AnimationWorkspace -> AnimationWorkspacePage(padding, prefs, masterEnabled)
                Page.AnimationInteraction -> AnimationInteractionPage(padding, prefs, masterEnabled)
                Page.AnimationPopups -> AnimationPopupsPage(padding, prefs, masterEnabled)
                Page.AnimationSystem -> AnimationSystemPage(padding, prefs, masterEnabled)
                Page.AnimationGui -> AnimationGuiPage(padding, prefs, masterEnabled)
                Page.Data -> DataPage(padding, activity)
                Page.About -> AboutPage(padding, activity, prefs)
            }
        }
    }
}

@Composable
private fun HomePage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    onMasterChanged: (Boolean) -> Unit,
    open: (Page) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ModernSectionLabel("状态")
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Core.ENABLED,
                    stringResource(R.string.enable_liquiddock),
                    stringResource(R.string.enable_liquiddock_summary),
                ) { onMasterChanged(it) }
            }
        }
        item { ModernSectionLabel("快捷入口") }
        overviewEntries.forEach { entry ->
            item {
                ModernFeatureCard(
                    title = stringResource(entry.titleRes),
                    summary = entry.summary,
                    onClick = { open(entry.page) },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
        item {
            ModernSurface(
                modifier = Modifier.padding(start = 14.dp, top = 4.dp, end = 14.dp),
            ) {
                Text(
                    text = if (masterEnabled) "LiquidDock 正在运行" else "LiquidDock 已暂停",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (masterEnabled) {
                        "可即时生效的外观设置会立即更新；需要重启的项目会在说明中明确标注。"
                    } else {
                        "LiquidDock 已暂停，已保存的设置不会丢失。"
                    },
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun HubPage(
    padding: PaddingValues,
    summary: String,
    entries: List<HubEntry>,
    open: (Page) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeader("", summary) }
        entries.forEach { entry ->
            item {
                ModernFeatureCard(
                    title = stringResource(entry.titleRes),
                    summary = entry.summary,
                    onClick = { open(entry.page) },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun MoreHubPage(
    padding: PaddingValues,
    glassEnabled: Boolean,
    onGlassEnabledChange: (Boolean) -> Unit,
    open: (Page) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeader("", "设置界面、预设与开源许可") }
        item {
            SettingsCard {
                SwitchPreference(
                    checked = glassEnabled,
                    onCheckedChange = onGlassEnabledChange,
                    title = stringResource(R.string.settings_glass_effect),
                    summary = stringResource(R.string.settings_glass_effect_summary),
                )
            }
        }
        moreEntries.forEach { entry ->
            item {
                ModernFeatureCard(
                    title = stringResource(entry.titleRes),
                    summary = entry.summary,
                    onClick = { open(entry.page) },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun AnimationPage(
    padding: PaddingValues,
    open: (Page) -> Unit,
) {
    HubPage(
        padding = padding,
        summary = "调整工作区、Dock、弹出界面与系统界面动画",
        entries = animationEntries,
        open = open,
    )
}

@Composable
private fun AnimationWorkspacePage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val dockCustomizationEnabled = prefs.getBoolean(
        ConfigSchema.Dock.ENABLED.name(),
        ConfigSchema.Dock.ENABLED.uiDefault(),
    )
    val systemResizeEnabled = prefs.getBoolean(
        ConfigSchema.Dock.RESIZE_ANIMATION.name(),
        ConfigSchema.Dock.RESIZE_ANIMATION.uiDefault(),
    )
    val smoothResizeEnabled = prefs.getBoolean(
        ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION.name(),
        ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION.uiDefault(),
    )
    SettingsList(
        padding,
        stringResource(R.string.page_animation_workspace),
        "调整工作区与 Dock 的玻璃过渡动画。",
    ) {
        IntSetting(prefs, animationWorkspaceVisibilitySpec, masterEnabled)
        IntSetting(prefs, animationDockIconRevealSpec, masterEnabled)
        IntSetting(
            prefs,
            animationDockResizeSpec,
            masterEnabled && dockCustomizationEnabled && !systemResizeEnabled && smoothResizeEnabled,
        )
    }
}

@Composable
private fun AnimationInteractionPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    SettingsList(
        padding,
        stringResource(R.string.page_animation_interaction),
        "玻璃按下与释放反馈独立调节；0 ms 表示立即完成。",
    ) {
        IntSetting(prefs, animationPressInSpec, masterEnabled)
        IntSetting(prefs, animationPressOutSpec, masterEnabled)
    }
}

@Composable
private fun AnimationPopupsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    SettingsList(
        padding,
        stringResource(R.string.page_animation_popups),
        "调整快捷菜单与安全中心玻璃的退出渐隐。",
    ) {
        IntSetting(prefs, animationShortcutDismissSpec, masterEnabled)
        IntSetting(prefs, animationSecurityCenterExitSpec, masterEnabled)
    }
}

@Composable
private fun AnimationSystemPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    SettingsList(
        padding,
        stringResource(R.string.page_animation_system),
        "系统界面联动单独放置，避免与普通玻璃动画混在同一页。",
    ) {
        BooleanSetting(
            prefs,
            ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS,
            "桌面/多任务隐藏手势小白条",
            "进入桌面或多任务后隐藏手势手柄，离开后交还系统原生显示时序；开关即时生效，首次安装此版本需重启系统界面",
            masterEnabled,
        )
    }
}

@Composable
private fun AnimationGuiPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    SettingsList(
        padding,
        stringResource(R.string.page_animation_gui),
        "调整设置页面的切换动画。",
    ) {
        IntSetting(prefs, animationSettingsPageSpec, masterEnabled)
    }
}

@Composable
private fun GridPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    val customGridRevision = LocalSettingsPreferenceRevisions.current[ConfigSchema.Grid.ENABLED.name()] ?: 0
    var customGrid by remember(customGridRevision) {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Grid.ENABLED.name(),
                ConfigSchema.Grid.ENABLED.uiDefault(),
            ),
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeader(
                stringResource(R.string.page_grid),
                "设置桌面网格与不同屏幕方向下的布局。",
            )
        }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Grid.ENABLED,
                    "自由主界面网格",
                    "允许 2×2 到 10×6 的工作区布局；全屏居中、分屏跟随系统 pane 对齐；重启桌面生效",
                    masterEnabled,
                ) { customGrid = it }
            }
        }
        gridEntries.forEach { entry ->
            item {
                ModernFeatureCard(
                    title = stringResource(entry.titleRes),
                    summary = entry.summary,
                    onClick = { open(entry.page) },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
        if (!customGrid) {
            item {
                ModernSurface(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                        "开启自由主界面网格后应用下方布局参数。",
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GridBasicsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val customGridRevision = LocalSettingsPreferenceRevisions.current[ConfigSchema.Grid.ENABLED.name()] ?: 0
    var customGrid by remember(customGridRevision) {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Grid.ENABLED.name(),
                ConfigSchema.Grid.ENABLED.uiDefault(),
            ),
        )
    }
    var iconSizeEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Grid.ICON_SIZE_ENABLED.name(),
                ConfigSchema.Grid.ICON_SIZE_ENABLED.uiDefault(),
            ),
        )
    }
    SettingsList(
        padding,
        stringResource(R.string.page_grid_basics),
        "调整网格行列数、图标大小与小组件宽度。",
    ) {
        BooleanSetting(
            prefs,
            ConfigSchema.Grid.ICON_SIZE_ENABLED,
            "自定义图标大小",
            "工作区、Dock、小文件夹、文件夹内图标与工作台 App 页；重启桌面后生效",
            masterEnabled,
        ) { iconSizeEnabled = it }
        IntSetting(
            prefs,
            launcher450IconSizeSpec,
            masterEnabled && iconSizeEnabled,
        )
        BooleanSetting(
            prefs,
            ConfigSchema.Grid.ENABLED,
            "自由主界面网格",
            "控制自定义行列数是否参与布局；重启桌面后生效",
            masterEnabled,
        ) { customGrid = it }
        gridDimensionSpecs.forEach { IntSetting(prefs, it, masterEnabled && customGrid) }
        BooleanSetting(
            prefs,
            ConfigSchema.Grid.WIDGET_HORIZONTAL_STRETCH,
            "小组件随水平边距拉伸",
            "多列小组件随水平距离偏移调整宽度；关闭时保持原尺寸并居中；1×1 始终不拉伸；重启桌面后生效",
            masterEnabled && customGrid,
        )
    }
}

@Composable
private fun GridLandscapePage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val customGrid = prefs.getBoolean(
        ConfigSchema.Grid.ENABLED.name(),
        ConfigSchema.Grid.ENABLED.uiDefault(),
    )
    val landscapeSpecs = gridSpecs.filter {
        it.key.startsWith("grid_landscape") || it.key == "indicator_landscape_y"
    }
    DenseSettingsList(
        padding,
        stringResource(R.string.page_grid_landscape),
        "调整横屏桌面的间距与页面指示器位置。",
    ) {
        groupedIntSettings(landscapeSpecs, prefs, masterEnabled && customGrid)
    }
}

@Composable
private fun GridPortraitPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val customGrid = prefs.getBoolean(
        ConfigSchema.Grid.ENABLED.name(),
        ConfigSchema.Grid.ENABLED.uiDefault(),
    )
    val portraitSpecs = gridSpecs.filter {
        it.key.startsWith("grid_portrait") || it.key == "indicator_portrait_y"
    }
    DenseSettingsList(
        padding,
        stringResource(R.string.page_grid_portrait),
        "调整竖屏桌面的间距与页面指示器位置。",
    ) {
        groupedIntSettings(portraitSpecs, prefs, masterEnabled && customGrid)
    }
}

@Composable
private fun GridSplitPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val customGrid = prefs.getBoolean(
        ConfigSchema.Grid.ENABLED.name(),
        ConfigSchema.Grid.ENABLED.uiDefault(),
    )
    SettingsList(
        padding,
        stringResource(R.string.page_grid_split),
        "分屏使用系统 pane 作为边界，只在这里调整额外距离。",
    ) {
        splitGridSpecs.forEach { IntSetting(prefs, it, masterEnabled && customGrid) }
    }
}

@Composable
private fun DockPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    var dockEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Dock.ENABLED.name(),
                ConfigSchema.Dock.ENABLED.uiDefault(),
            ),
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeader(
                stringResource(R.string.page_dock),
                "调整 Dock 行为、尺寸、位置与外观。",
            )
        }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Dock.ENABLED,
                    stringResource(R.string.dock_customization),
                    stringResource(R.string.dock_customization_summary),
                    masterEnabled,
                ) { dockEnabled = it }
            }
        }
        dockEntries.forEach { entry ->
            item {
                ModernFeatureCard(
                    title = stringResource(entry.titleRes),
                    summary = entry.summary,
                    onClick = { open(entry.page) },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
        item {
            ModernFeatureCard(
                title = stringResource(R.string.page_stroke),
                summary = "描边样式与颜色",
                onClick = { open(Page.Stroke) },
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
        item {
            ModernFeatureCard(
                title = stringResource(R.string.page_shadow),
                summary = "Dock 与描边阴影",
                onClick = { open(Page.Shadow) },
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
        if (!dockEnabled) {
            item {
                ModernSurface(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text("开启 Dock 自定义后应用下方设置。", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DockBehaviorPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val dockEnabled = prefs.getBoolean(
        ConfigSchema.Dock.ENABLED.name(),
        ConfigSchema.Dock.ENABLED.uiDefault(),
    )
    var resizeAnimation by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Dock.RESIZE_ANIMATION.name(),
                ConfigSchema.Dock.RESIZE_ANIMATION.uiDefault(),
            ),
        )
    }
    SettingsList(
        padding,
        stringResource(R.string.page_dock_behavior),
        "管理 Dock 功能入口与尺寸变化动画。",
    ) {
        BooleanSetting(
            prefs,
            ConfigSchema.Dock.HIDE_MIRROR_SHORTCUT,
            "隐藏手机互联图标",
            "仅隐藏 Dock 入口，不修改系统互联开关或设备连接状态",
            masterEnabled,
        )
        BooleanSetting(
            prefs,
            ConfigSchema.Dock.RESIZE_ANIMATION,
            stringResource(R.string.dock_resize_animation),
            stringResource(R.string.dock_resize_animation_summary),
            masterEnabled && dockEnabled,
        ) { resizeAnimation = it }
        BooleanSetting(
            prefs,
            ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION,
            stringResource(R.string.dock_smooth_resize_animation),
            stringResource(R.string.dock_smooth_resize_animation_summary),
            masterEnabled && dockEnabled && !resizeAnimation,
        )
    }
}

@Composable
private fun DockGeometryPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val dockEnabled = prefs.getBoolean(
        ConfigSchema.Dock.ENABLED.name(),
        ConfigSchema.Dock.ENABLED.uiDefault(),
    )
    DenseSettingsList(
        padding,
        stringResource(R.string.page_dock_geometry),
        "调整 Dock 的尺寸、位置、圆角与图标间距。",
    ) {
        groupedIntSettings(dockSpecs, prefs, masterEnabled && dockEnabled)
    }
}

@Composable
private fun DividerPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    val legacyDefault = remember { hasLegacyDividerConfig(prefs) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Divider.ENABLED.name(), legacyDefault)) }
    DenseSettingsList(padding, stringResource(R.string.page_divider)) {
        item(key = "divider-enabled") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Divider.ENABLED, "自定义 Dock 分隔线", "独立于 Dock 尺寸、模糊和单位开关；宽度与偏移固定使用 dp", masterEnabled, default = legacyDefault) {
                    enabled = it
                    if (it) ensureDividerDefaults(prefs)
                }
            }
        }
        items(dividerSpecs, key = { it.key }) { spec ->
            SettingsCard {
                IntSetting(prefs, spec, masterEnabled && enabled)
            }
        }
    }
}

@Composable
private fun WorkstationPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    var enabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Workstation.DOCK_CUSTOMIZATION.name(),
                ConfigSchema.Workstation.DOCK_CUSTOMIZATION.uiDefault(),
            ),
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeader(
                stringResource(R.string.page_workstation),
                "调整工作台桌面、Dock 与所有应用布局。",
            )
        }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Workstation.DOCK_CUSTOMIZATION,
                    stringResource(R.string.workstation_customization),
                    stringResource(R.string.workstation_customization_summary),
                    masterEnabled,
                ) { enabled = it }
            }
        }
        workstationEntries.forEach { entry ->
            item {
                ModernFeatureCard(
                    title = stringResource(entry.titleRes),
                    summary = entry.summary,
                    onClick = { open(entry.page) },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
        if (!enabled) {
            item {
                ModernSurface(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text("开启工作台自定义后应用下方设置。", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun WorkstationSpecPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    specs: List<IntSpec>,
    summary: String,
) {
    val enabled = prefs.getBoolean(
        ConfigSchema.Workstation.DOCK_CUSTOMIZATION.name(),
        ConfigSchema.Workstation.DOCK_CUSTOMIZATION.uiDefault(),
    )
    DenseSettingsList(
        padding = padding,
        title = "",
        summary = summary,
    ) {
        items(specs, key = { it.key }) { spec ->
            SettingsCard {
                IntSetting(prefs, spec, masterEnabled && enabled)
            }
        }
    }
}

@Composable
private fun RecentsPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    SettingsList(
        padding,
        stringResource(R.string.page_recents),
        stringResource(R.string.recents_header_summary),
    ) {
        IntSetting(prefs, recentsBlurSpec, masterEnabled)
        BooleanSetting(
            prefs,
            ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING,
            "取消壁纸压暗",
            "进入多任务时保留系统背景模糊与过渡动画，仅移除壁纸黑色压暗；重启桌面后生效",
            masterEnabled,
        )
    }
}

@Composable
private fun SecurityCenterSidebarPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val liquidGlassEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            PageHeader(
                stringResource(R.string.page_security_center_sidebar),
                stringResource(R.string.security_center_sidebar_header_summary),
            )
        }
        item { SmallTitle(stringResource(R.string.security_center_sidebar_category_gesture)) }
        item {
            SettingsCard {
                SideSlideHoldSetting(prefs, masterEnabled)
            }
        }
        item { SmallTitle(stringResource(R.string.security_center_sidebar_category_appearance)) }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.SECURITY_CENTER_GLASS,
                    stringResource(R.string.liquid_security_center_glass_enable),
                    stringResource(R.string.liquid_security_center_glass_enable_summary),
                    masterEnabled && liquidGlassEnabled,
                )
            }
        }
    }
}

@Composable
private fun LiquidPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    val liquidRevision = LocalSettingsPreferenceRevisions.current[ConfigSchema.Glass.ENABLED.name()] ?: 0
    var liquidGlass by remember(liquidRevision) {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Glass.ENABLED.name(),
                ConfigSchema.Glass.ENABLED.uiDefault(),
            ),
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeader(
                stringResource(R.string.page_liquid),
                stringResource(R.string.liquid_header_summary),
            )
        }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.ENABLED,
                    stringResource(R.string.liquid_enable),
                    stringResource(R.string.liquid_enable_summary),
                    masterEnabled,
                ) { liquidGlass = it }
            }
        }
        liquidEntries.forEach { entry ->
            item {
                ModernFeatureCard(
                    title = stringResource(entry.titleRes),
                    summary = entry.summary,
                    onClick = { open(entry.page) },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
        item {
            ModernFeatureCard(
                title = stringResource(R.string.page_glass_components),
                summary = "管理图标、小组件、文件夹与菜单玻璃",
                onClick = { open(Page.GlassComponents) },
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
        if (!liquidGlass) {
            item {
                ModernSurface(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                        "开启液态玻璃后应用这些外观参数。",
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidSpecPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    summary: String,
    specs: List<IntSpec>,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    DenseSettingsList(
        padding = padding,
        title = "",
        summary = summary,
    ) {
        groupedIntSettings(specs, prefs, masterEnabled && liquidEnabled)
    }
}

@Composable
private fun LiquidSamplingPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    DenseSettingsList(
        padding,
        stringResource(R.string.page_liquid_sampling),
        "调整背景缩放、PassBlur 渲染分辨率与实时刷新上限。采样保护区由渲染器自动计算。",
    ) {
        items(liquidSamplingSpecs, key = { it.key }) { spec ->
            SettingsCard {
                IntSetting(prefs, spec, masterEnabled && liquidEnabled)
            }
        }
        item(key = passBlurCaptureScaleSpec.key) {
            SettingsCard {
                IntSetting(prefs, passBlurCaptureScaleSpec, masterEnabled && liquidEnabled)
            }
        }
        item(key = passBlurRenderFpsSpec.key) {
            SettingsCard {
                IntSetting(prefs, passBlurRenderFpsSpec, masterEnabled && liquidEnabled)
            }
        }
        item(key = ConfigSchema.Dock.FRAME_SYNC.name()) {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Dock.FRAME_SYNC,
                    stringResource(R.string.dock_frame_sync),
                    stringResource(R.string.dock_frame_sync_summary),
                    masterEnabled && liquidEnabled,
                )
            }
        }
        item(key = ConfigSchema.Glass.PRISMAL_SHOW_NORMALS.name()) {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.PRISMAL_SHOW_NORMALS,
                    "表面法线可视化",
                    "以颜色显示玻璃表面法线方向，用于检查折射、曲面与光照响应",
                    masterEnabled && liquidEnabled,
                )
            }
        }
    }
}

@Composable
private fun GlassComponentsPage(
    padding: PaddingValues,
    open: (Page) -> Unit,
) {
    HubPage(
        padding = padding,
        summary = "选择需要调整的玻璃组件。",
        entries = componentEntries,
        open = open,
    )
}

@Composable
private fun GlassIconsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    var iconGlass by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Glass.ICON_GLASS.name(),
                ConfigSchema.Glass.ICON_GLASS.uiDefault(),
            ),
        )
    }
    var functionalDockIconGlass by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.name(),
                ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.uiDefault(),
            ),
        )
    }
    // Keep Prismal on every group, but avoid one massive offscreen blur
    // layer containing every switch, slider and stepper on this page.
    // The entire page still has only one LazyColumn as scroll owner.
    DenseSettingsList(
        padding,
        stringResource(R.string.page_glass_icons),
        "调整桌面图标、Dock 功能图标与多任务胶囊玻璃。",
    ) {
        item(key = "icons-glass-toggles") {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.ICON_GLASS,
                    "图标玻璃",
                    "同时控制桌面与 Dock 全部图标；0 圆角为 Auto",
                    masterEnabled && liquidEnabled,
                ) { iconGlass = it }
                ModernListDivider()
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS,
                    "仅 Dock 功能图标玻璃",
                    "仅搜索、小爱、全部应用、最近任务、Home、手机互联等系统功能入口；可在关闭“图标玻璃”后单独使用",
                    masterEnabled && liquidEnabled,
                ) { functionalDockIconGlass = it }
                ModernListDivider()
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.RECENTS_CAPSULE_GLASS,
                    "多任务操作按钮玻璃",
                    "将多任务界面的清除全部和设备互联胶囊背景替换为液态玻璃",
                    masterEnabled && liquidEnabled,
                )
            }
        }
        item(key = "icons-glass-geometry") {
            SettingsCard {
                IntSetting(
                    prefs,
                    iconSizeOffsetSpec,
                    masterEnabled && liquidEnabled && (iconGlass || functionalDockIconGlass),
                )
                ModernListDivider()
                IntSetting(
                    prefs,
                    iconCornerRadiusSpec,
                    masterEnabled && liquidEnabled && (iconGlass || functionalDockIconGlass),
                )
            }
        }
        item(key = "icons-glass-highlights") {
            SettingsCard {
                ArrowPreference(
                    stringResource(R.string.launcher_highlights_entry),
                    summary = stringResource(R.string.launcher_highlights_entry_summary),
                    enabled = masterEnabled && liquidEnabled,
                    onClick = { open(Page.LauncherHighlights) },
                )
            }
        }
    }
}

@Composable
private fun GlassWidgetsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    var widgetGlass by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Glass.WIDGET_GLASS.name(),
                ConfigSchema.Glass.WIDGET_GLASS.uiDefault(),
            ),
        )
    }
    SettingsList(
        padding,
        stringResource(R.string.page_glass_widgets),
        "小组件材质与内部组件隐藏分开管理。",
    ) {
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.WIDGET_GLASS,
            "小部件玻璃",
            "只替换小组件背景，保留 RemoteViews / MAML 的文字、图标与交互内容",
            masterEnabled && liquidEnabled,
        ) { widgetGlass = it }
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.WIDGET_DARK_CONTENT,
            "小组件深色内容适配",
            "将深色中性文字适配为浅色；MAML 优先使用原生深色变量，不处理图片与彩色内容",
            masterEnabled && liquidEnabled && widgetGlass,
        )
        IntSetting(prefs, widgetSizeOffsetSpec, masterEnabled && liquidEnabled && widgetGlass)
        IntSetting(prefs, widgetCornerRadiusSpec, masterEnabled && liquidEnabled && widgetGlass)
        ArrowPreference(
            stringResource(R.string.widget_components_entry),
            summary = stringResource(R.string.widget_components_entry_summary),
            enabled = masterEnabled && liquidEnabled && widgetGlass,
            onClick = { open(Page.WidgetComponents) },
        )
    }
}

@Composable
private fun GlassFoldersPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    var smallFolderGlass by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Glass.SMALL_FOLDER_GLASS.name(),
                ConfigSchema.Glass.SMALL_FOLDER_GLASS.uiDefault(),
            ),
        )
    }
    var largeFolderGlass by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Glass.LARGE_FOLDER_GLASS.name(),
                ConfigSchema.Glass.LARGE_FOLDER_GLASS.uiDefault(),
            ),
        )
    }
    SettingsList(
        padding,
        stringResource(R.string.page_glass_folders),
        "小文件夹与大文件夹保留独立开关和几何参数。",
    ) {
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.SMALL_FOLDER_GLASS,
            "小文件夹玻璃",
            "保留 1x1 文件夹缩略预览",
            masterEnabled && liquidEnabled,
        ) { smallFolderGlass = it }
        IntSetting(prefs, smallFolderSizeOffsetSpec, masterEnabled && liquidEnabled && smallFolderGlass)
        IntSetting(prefs, smallFolderCornerRadiusSpec, masterEnabled && liquidEnabled && smallFolderGlass)
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.LARGE_FOLDER_GLASS,
            "大文件夹玻璃",
            "独立控制大文件夹材质",
            masterEnabled && liquidEnabled,
        ) { largeFolderGlass = it }
        IntSetting(prefs, largeFolderSizeOffsetSpec, masterEnabled && liquidEnabled && largeFolderGlass)
        IntSetting(prefs, largeFolderCornerRadiusSpec, masterEnabled && liquidEnabled && largeFolderGlass)
    }
}

@Composable
private fun GlassMenusPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    SettingsList(
        padding,
        stringResource(R.string.page_glass_menus),
        "系统菜单、快捷菜单与弹窗材质集中在单独页面。",
    ) {
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.WALLPAPER_FLICKER_FIX,
            "壁纸闪烁修复",
            "仅在壁纸闪烁时开启；需要为 LiquidDock 启用 System Framework（system）作用域并重启设备",
            masterEnabled && liquidEnabled,
        )
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS,
            "应用顶部菜单液态玻璃",
            "将应用顶部控制器展开后的分屏、小窗等胶囊背景替换为液态玻璃；重启系统界面后生效",
            masterEnabled && liquidEnabled,
        )
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.SHORTCUT_POPUP_GLASS,
            "桌面快捷菜单玻璃背景",
            "替换长按桌面图标弹出的快捷菜单背景；关闭后保留系统原生材质，重启桌面后生效",
            masterEnabled && liquidEnabled,
        )
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.SHORTCUT_POPUP_DARK_TEXT,
            "快捷菜单深色模式适配",
            "将快捷菜单文字和图标统一改为白色；关闭后保留系统原样，重启桌面后生效",
            masterEnabled && liquidEnabled,
        )
        ArrowPreference(
            title = stringResource(R.string.page_dialog_customization),
            summary = "卸载、移除与二次确认弹窗的玻璃、背景压暗、模糊和颜色",
            enabled = masterEnabled && liquidEnabled,
            onClick = { open(Page.DialogCustomization) },
        )
    }
}

@Composable
private fun LauncherHighlightsPage(
    padding: PaddingValues,
    open: (Page) -> Unit,
) {
    HubPage(
        padding = padding,
        summary = stringResource(R.string.launcher_highlights_header_summary),
        entries = highlightEntries,
        open = open,
    )
}

@Composable
private fun LauncherHighlightTogglePage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    compact: Boolean,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    SettingsList(
        padding = padding,
        title = "",
        summary = if (compact) {
            "图标、小文件夹与 Dock 图标使用这一组高光层。"
        } else {
            "小组件与大文件夹使用这一组独立高光层。"
        },
    ) {
        launcherHighlightSpecs.forEach { spec ->
            BooleanSetting(
                prefs,
                if (compact) spec.compactConfig else spec.largeConfig,
                stringResource(spec.titleRes),
                stringResource(spec.summaryRes),
                masterEnabled && liquidEnabled,
            )
        }
    }
}

@Composable
private fun StrokePage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    var dockStroke by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.STROKE_ENABLED.name(), ConfigSchema.Dock.STROKE_ENABLED.uiDefault())) }
    var squircle by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.SQUIRCLE.name(), ConfigSchema.Dock.SQUIRCLE.uiDefault())) }
    var fillDiff by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.FILL_DIFF.name(), ConfigSchema.Dock.FILL_DIFF.uiDefault())) }
    DenseSettingsList(padding, "描边") {
        item(key = "stroke-enabled") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Dock.STROKE_ENABLED, "显示完整描边", "控制 Dock 边框与灯光", masterEnabled) { dockStroke = it }
            }
        }
        item(key = "stroke-squircle") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Dock.SQUIRCLE, "方圆形连续曲线", "iPad 风格连续圆角", masterEnabled) { squircle = it }
            }
        }
        item(key = "stroke-fill-diff") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Dock.FILL_DIFF, "Fill-Diff 描边", "通过填充与挖空获得清晰抗锯齿", masterEnabled) { fillDiff = it }
            }
        }
        item(key = "stroke-colors-title") { SmallTitle("描边背景色") }
        items(strokeSpecs.filter { it.section == IntSection.StrokeBackground }, key = { it.key }) { spec ->
            SettingsCard {
                IntSetting(prefs, spec, masterEnabled && dockStroke)
            }
        }
        item(key = "stroke-geometry-title") { SmallTitle("方圆形与线宽") }
        items(strokeSpecs.filter { it.section == IntSection.StrokeGeometry }, key = { it.key }) { spec ->
            val enabled = when (spec.dependency) {
                "dock_stroke" -> dockStroke
                "squircle" -> squircle
                "fill_diff" -> fillDiff
                else -> true
            }
            SettingsCard {
                IntSetting(prefs, spec, masterEnabled && enabled)
            }
        }
    }
}

@Composable
private fun ShadowPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    val dockEnabled = prefs.getBoolean(ConfigSchema.Dock.ENABLED.name(), ConfigSchema.Dock.ENABLED.uiDefault())
    var dockShadow by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.SHADOW_ENABLED.name(), ConfigSchema.Dock.SHADOW_ENABLED.uiDefault())) }
    var strokeShadow by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.STROKE_SHADOW.name(), ConfigSchema.Dock.STROKE_SHADOW.uiDefault())) }
    DenseSettingsList(padding, "阴影") {
        item(key = "dock-shadow-enabled") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Dock.SHADOW_ENABLED, "整个 Dock 下方阴影", "跟随 Dock 长宽、高度和圆角", masterEnabled && dockEnabled) { dockShadow = it }
            }
        }
        item(key = "stroke-shadow-enabled") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Dock.STROKE_SHADOW, "描边阴影", "描边下方的柔和阴影", masterEnabled && dockEnabled) { strokeShadow = it }
            }
        }
        items(shadowSpecs, key = { it.key }) { spec ->
            SettingsCard {
                IntSetting(prefs, spec, masterEnabled && dockEnabled && when (spec.dependency) {
                    "dock_shadow" -> dockShadow
                    "stroke_shadow" -> strokeShadow
                    else -> true
                })
            }
        }
    }
}

@Composable
private fun DataPage(padding: PaddingValues, activity: ComposeSettingsActivity) {
    var confirmDefaultReset by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item { PageHeader("预设", "默认配置、JSON 备份与恢复") }
        item { SmallTitle("预设") }
        item { SettingsCard { ArrowPreference("应用默认配置", summary = "恢复内置默认参数与开关（需确认）", onClick = { confirmDefaultReset = true }) } }
        item { SmallTitle("备份与应用") }
        item {
            SettingsCard {
                ArrowPreference("导出当前参数", summary = "保存为 LiquidDock JSON", onClick = activity::launchExport)
                ArrowPreference("导入参数", summary = "校验并恢复参数；完成后自动重启桌面", onClick = activity::launchImport)
            }
        }
    }
    WindowDialog(
        show = confirmDefaultReset,
        title = "确认恢复默认配置",
        onDismissRequest = { confirmDefaultReset = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("将覆盖当前内置配置与开关并重启桌面。该操作不能直接撤销，建议先导出 JSON 备份。")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { confirmDefaultReset = false }, minWidth = 72.dp) {
                    Text("取消")
                }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 5.dp))
                Button(onClick = {
                    confirmDefaultReset = false
                    applyDefaultPreset(activity)
                }, minWidth = 72.dp) {
                    Text("恢复默认")
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
private fun AboutPage(
    padding: PaddingValues,
    activity: ComposeSettingsActivity,
    prefs: SharedPreferences,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item { PageHeader("许可", "LiquidDock 使用的第三方开源项目与许可证") }
        item { SmallTitle("运行诊断") }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Debug.LOGGING,
                    "诊断日志",
                    "将 LiquidDock 运行诊断写入日志文件；修改后重启对应 Hook 作用域生效",
                )
            }
        }
        item { SmallTitle("界面与运行框架") }
        item {
            SettingsCard {
                ArrowPreference("Compose Miuix", summary = "MIUIX Compose 界面框架 · Apache-2.0", onClick = { openUrl(activity, "https://github.com/compose-miuix-ui/miuix") })
                ArrowPreference("PrismalAGSL", summary = "设置界面液态玻璃组件 · MIT", onClick = { openUrl(activity, "https://github.com/styropyr0/PrismalAGSL") })
                ArrowPreference("AndroidX / Jetpack", summary = "Activity、Preference、AppCompat · Apache-2.0", onClick = { openUrl(activity, "https://source.android.com/docs/setup/about/licenses") })
                ArrowPreference("LSPosed API", summary = "模块 Hook API · GPL-3.0", onClick = { openUrl(activity, "https://github.com/LSPosed/LSPosed") })
            }
        }
        item { SmallTitle("开源项目") }
        item {
            SettingsCard {
                ArrowPreference("HyperCeiler", summary = "开源模块项目 · GPL-3.0", onClick = { openUrl(activity, "https://github.com/ReChronoRain/HyperCeiler") })
                ArrowPreference("Prismal", summary = "液态玻璃光学模型 · MIT", onClick = { openUrl(activity, "https://github.com/styropyr0/Prismal") })
            }
        }
        item { SmallTitle("许可说明") }
        item {
            SettingsCard {
                ArrowPreference("第三方开源声明", summary = "依赖版本、用途与许可证文本链接", onClick = { openUrl(activity, "https://github.com/yu4032/LiquidDock/blob/main/THIRD_PARTY_NOTICES.md") })
            }
        }
    }
}

/**
 * Keep at most three numeric settings in one native Prismal glass surface.
 * This avoids a separate blur/refraction GraphicsLayer per dense-list row,
 * while each small group remains a LazyColumn item that scrolls with its
 * border. Sliders, steppers, and their live setting callbacks are untouched.
 */
private fun LazyListScope.groupedIntSettings(
    specs: List<IntSpec>,
    prefs: SharedPreferences,
    enabled: Boolean,
) {
    items(specs.chunked(3), key = { group -> "int-group:${group.first().key}" }) { group ->
        SettingsCard {
            group.forEachIndexed { index, spec ->
                if (index > 0) ModernListDivider()
                IntSetting(prefs, spec, enabled)
            }
        }
    }
}

/**
 * Long settings pages have ONE lazy, vertically scrolling root.
 * Each visible control owns its own moving glass card; the previous
 * viewport-fixed outer card made the border appear stationary while only
 * its children scrolled. Lazy item keys and off-screen disposal are retained.
 */
@Composable
private fun DenseSettingsList(
    padding: PaddingValues,
    title: String,
    summary: String? = null,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!summary.isNullOrBlank()) item(key = "dense-page-summary") {
            PageHeader(title, summary)
        }
        content()
    }
}

@Composable
private fun SettingsList(
    padding: PaddingValues,
    title: String,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeader(title, summary) }
        item { SettingsCard(content) }
    }
}

@Composable
internal fun PageHeader(@Suppress("UNUSED_PARAMETER") title: String, summary: String? = null) {
    if (summary.isNullOrBlank()) return
    Text(
        text = summary,
        modifier = Modifier.padding(
            start = 20.dp,
            end = 20.dp,
            top = 8.dp,
            bottom = 6.dp,
        ),
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.62f),
    )
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    ModernSurface(
        modifier = Modifier.padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        Column(content = content)
    }
}

@Composable
internal fun BooleanSetting(
    prefs: SharedPreferences, config: ConfigKey<Boolean>, title: String, summary: String? = null,
    enabled: Boolean = true, default: Boolean = config.uiDefault(), onChanged: (Boolean) -> Unit = {},
) {
    val key = config.name()
    val revision = LocalSettingsPreferenceRevisions.current[key] ?: 0
    var value by remember(key, revision) { mutableStateOf(prefs.getBoolean(key, default)) }
    SwitchPreference(
        checked = value,
        onCheckedChange = { value = it; prefs.edit().putBoolean(key, it).apply(); onChanged(it) },
        title = title,
        summary = summary,
        enabled = enabled,
    )
}

@Composable
private fun IntSetting(prefs: SharedPreferences, spec: IntSpec, enabledOverride: Boolean? = null) {
    val decimalDp = spec.isDecimal
    val context = LocalContext.current
    val maxValue = remember(spec.key, context) { spec.max(context) }
    val resetValue = remember(spec.key, maxValue) {
        spec.resetValue().coerceIn(spec.min.toFloat(), maxValue.toFloat())
    }
    // Reading the disk-backed preference on every drag/recomposition was wasteful:
    // remember() already owns the initial state until this setting leaves composition.
    val storedRevision = LocalSettingsPreferenceRevisions.current[spec.key] ?: 0
    var value by remember(spec.key, maxValue) {
        val initial = (if (decimalDp && prefs.contains("${spec.key}_tenths"))
            prefs.getInt("${spec.key}_tenths", (resetValue * 10f).roundToInt()) / 10f
        else prefs.getInt(spec.key, resetValue.roundToInt()).toFloat())
            .coerceIn(spec.min.toFloat(), maxValue.toFloat())
        mutableStateOf(initial)
    }
    LaunchedEffect(spec.key, maxValue, storedRevision) {
        // Reread only the externally changed key; normal drag recomposition is pure state.
        if (storedRevision > 0) {
            value = (if (decimalDp && prefs.contains("${spec.key}_tenths"))
                prefs.getInt("${spec.key}_tenths", (resetValue * 10f).roundToInt()) / 10f
            else prefs.getInt(spec.key, resetValue.roundToInt()).toFloat())
                .coerceIn(spec.min.toFloat(), maxValue.toFloat())
        }
    }
    var editingValue by remember(spec.key) { mutableStateOf(false) }
    val enabled = enabledOverride ?: spec.dependency?.let { prefs.getBoolean(it, false) } ?: true

    fun save(nextValue: Float) {
        val next = if (decimalDp) {
            (nextValue * 10f).roundToInt() / 10f
        } else {
            nextValue.roundToInt().toFloat()
        }
        val bounded = next.coerceIn(spec.min.toFloat(), maxValue.toFloat())
        if (bounded == value) return // already persisted this quantized value
        value = bounded
        val editor = prefs.edit().putInt(spec.key, value.roundToInt())
        if (decimalDp) editor.putInt("${spec.key}_tenths", (value * 10f).roundToInt())
        editor.apply()
    }

    val displayValue = remember(value, decimalDp) {
        if (decimalDp) {
            String.format(java.util.Locale.ROOT, "%.1f", value)
        } else {
            value.roundToInt().toString()
        }
    }
    val displayText = "$displayValue${if (spec.unit.isBlank()) "" else " ${spec.unit}"}"
    val scaledValue = if (decimalDp) (value * 10f).roundToInt() else value.roundToInt()
    val scaledMin = if (decimalDp) spec.min * 10 else spec.min
    val scaledMax = if (decimalDp) maxValue * 10 else maxValue

    Column(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = spec.title,
            summary = spec.summary,
            enabled = enabled,
            insideMargin = ModernPreferenceMargin,
            endActions = {
                Text(
                    text = displayText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled) { editingValue = true }
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            },
        )

        ModernGlassSlider(
            value = value,
            onValueChange = ::save,
            valueRange = spec.min.toFloat()..maxValue.toFloat(),
            visibilityThreshold = if (decimalDp) 0.1f else 1f,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { save(resetValue) },
                enabled = enabled && kotlin.math.abs(value - resetValue) > 0.0001f,
                minWidth = 56.dp,
                minHeight = 36.dp,
                insideMargin = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("重置")
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 4.dp))
            ModernGlassStepper(
                value = scaledValue,
                valueRange = scaledMin..scaledMax,
                enabled = enabled,
                onValueChange = { next ->
                    save(if (decimalDp) next / 10f else next.toFloat())
                },
            )
        }
    }
    NumericSettingInputDialog(
        visible = editingValue,
        title = spec.title,
        currentText = displayValue,
        valueRange = spec.min.toFloat()..maxValue.toFloat(),
        integerOnly = !decimalDp,
        onDismiss = { editingValue = false },
        onConfirm = { next ->
            save(next)
            editingValue = false
        },
    )
}

private fun applyDefaultPreset(activity: ComposeSettingsActivity) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    PresetManager.applyDefault(prefs.edit())
    Toast.makeText(activity, "默认配置已应用", Toast.LENGTH_LONG).show()
    activity.restartLauncher()
    // Destroy remembered page/slider state after applying a new full preset.
    activity.recreate()
}
