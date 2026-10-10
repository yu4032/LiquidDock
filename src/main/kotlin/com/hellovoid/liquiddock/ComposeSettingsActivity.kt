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
internal val LocalSettingsPreferenceRevisions = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }

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

internal enum class Page(val titleRes: Int) {
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
    GboardAll(R.string.page_gboard),
    DialogAll(R.string.page_dialog_customization),
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

// Settings option catalogs live in SettingsOptionSpecs.kt; navigation and rendering stay here.

internal data class HubEntry(
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

internal val gridEntries = listOf(
    HubEntry(Page.GridBasics, R.string.page_grid_basics, "图标大小、网格尺寸与小组件拉伸"),
    HubEntry(Page.GridLandscape, R.string.page_grid_landscape, "横屏距离、行距与页面指示器"),
    HubEntry(Page.GridPortrait, R.string.page_grid_portrait, "竖屏距离、行距与页面指示器"),
    HubEntry(Page.GridSplit, R.string.page_grid_split, "分屏布局距离与对齐"),
)

internal val dockEntries = listOf(
    HubEntry(Page.DockBehavior, R.string.page_dock_behavior, "功能开关、尺寸动画与系统入口"),
    HubEntry(Page.DockGeometry, R.string.page_dock_geometry, "高度、宽度、圆角、间距与底部位置"),
    HubEntry(Page.DockRecentBlacklist, R.string.page_dock_recent_blacklist, "过滤最近程序，同时由后续候选补位"),
    HubEntry(Page.Divider, R.string.page_divider, "分隔线尺寸、位置、颜色与透明度"),
)

internal val workstationEntries = listOf(
    HubEntry(Page.WorkstationDock, R.string.page_workstation_dock, "Dock 图标垂直偏移与玻璃圆角"),
    HubEntry(Page.WorkstationDesktop, R.string.page_workstation_desktop, "按当前网格列数整体调整工作台桌面水平位置"),
    HubEntry(Page.WorkstationAppsLandscape, R.string.page_workstation_apps_landscape, "所有应用横屏水平与上下间距"),
    HubEntry(Page.WorkstationAppsPortrait, R.string.page_workstation_apps_portrait, "所有应用竖屏水平与上下间距"),
)

internal val liquidEntries = listOf(
    HubEntry(Page.LiquidMaterial, R.string.page_liquid_material, "模糊、厚度、透射率与亮度"),
    HubEntry(Page.LiquidRefraction, R.string.page_liquid_refraction, "折射、法线、穹顶与位移"),
    HubEntry(Page.LiquidColor, R.string.page_liquid_color, "底色、色散与鲜艳度"),
    HubEntry(Page.LiquidLighting, R.string.page_liquid_lighting, "高光、边缘光、焦散与光源方向"),
    HubEntry(Page.LiquidShadow, R.string.page_liquid_shadow, "玻璃内部阴影颜色与柔和度"),
    HubEntry(Page.LiquidSampling, R.string.page_liquid_sampling, "背景缩放、帧率与帧同步"),
    HubEntry(Page.LiquidOs4, R.string.page_liquid_os4, "OS4 边缘反射与方向光"),
)

internal val componentEntries = listOf(
    HubEntry(Page.GlassIcons, R.string.page_glass_icons, "桌面图标、Dock 功能图标与多任务胶囊"),
    HubEntry(Page.GlassWidgets, R.string.page_glass_widgets, "小组件玻璃、内容适配与组件隐藏"),
    HubEntry(Page.GlassFolders, R.string.page_glass_folders, "大小文件夹玻璃与独立尺寸"),
    HubEntry(Page.GlassMenus, R.string.page_glass_menus, "快捷菜单、系统顶部菜单与对话弹窗"),
)

internal val highlightEntries = listOf(
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

// Animation option specs live in SettingsOptionSpecs.kt.

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
                Page.DialogCustomization -> DialogGlassSettingsPage(
                padding, prefs, masterEnabled, onOpenAll = { navigateTo(Page.DialogAll) },
            )
            Page.DialogAll -> ScopedGlassSettingsPage(
                padding, prefs, masterEnabled, ScopedGlassOptics.DIALOG, "桌面对话弹窗",
            )
                Page.ThirdPartyApps -> ThirdPartyAppsPage(
                    padding = padding,
                    prefs = prefs,
                    masterEnabled = masterEnabled,
                    openGboard = { navigateTo(Page.Gboard) },
                )
                Page.Gboard -> GboardSettingsPage(
                padding, prefs, masterEnabled, onOpenAll = { navigateTo(Page.GboardAll) },
            )
            Page.GboardAll -> ScopedGlassSettingsPage(
                padding, prefs, masterEnabled, ScopedGlassOptics.GBOARD, "Gboard",
            )
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
internal fun HubPage(
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

// Animation leaf pages live in AnimationSettingsPages.kt.

// Grid settings and 4×2 widget preflight remain together in GridSettingsPages.kt.

// Dock, Workstation, Divider and Recents pages live in DockWorkstationSettingsPages.kt.

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

// Liquid, component glass and highlight leaf pages live in GlassSettingsPages.kt.

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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { confirmDefaultReset = false },
                    modifier = Modifier.weight(1f),
                    minHeight = 42.dp,
                    insideMargin = PaddingValues(horizontal = 26.dp, vertical = 8.dp),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        confirmDefaultReset = false
                        applyDefaultPreset(activity)
                    },
                    modifier = Modifier.weight(1f),
                    minHeight = 42.dp,
                    insideMargin = PaddingValues(horizontal = 26.dp, vertical = 8.dp),
                    destructive = true,
                ) {
                    Text("恢复默认", color = androidx.compose.ui.graphics.Color.White)
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
internal fun LazyListScope.groupedIntSettings(
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
internal fun DenseSettingsList(
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
internal fun SettingsList(
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
internal fun IntSetting(
    prefs: SharedPreferences,
    spec: IntSpec,
    enabledOverride: Boolean? = null,
    steps: Int = 0,
    beforeSave: ((Float, () -> Unit) -> Unit)? = null,
    // Guarded settings may allow only preflight-free steps during an active drag.
    previewWriteAllowed: ((Float) -> Boolean)? = null,
) {
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
    // The thumb remains continuous, but the displayed quantized preview becomes
    // the persisted value immediately when allowed by the setting's safety policy.
    var sliderPreview by remember(spec.key) { mutableStateOf<Float?>(null) }
    val enabled = enabledOverride ?: spec.dependency?.let { prefs.getBoolean(it, false) } ?: true

    fun save(nextValue: Float) {
        val next = if (decimalDp) {
            (nextValue * 10f).roundToInt() / 10f
        } else {
            nextValue.roundToInt().toFloat()
        }
        val bounded = next.coerceIn(spec.min.toFloat(), maxValue.toFloat())
        if (bounded == value) return // already persisted this quantized value
        val persist = {
            value = bounded
            val editor = prefs.edit().putInt(spec.key, bounded.roundToInt())
            if (decimalDp) editor.putInt("${spec.key}_tenths", (bounded * 10f).roundToInt())
            editor.apply()
        }
        if (beforeSave != null) beforeSave(bounded, persist) else persist()
    }

    val displayValue = remember(value, decimalDp) {
        if (decimalDp) {
            String.format(java.util.Locale.ROOT, "%.1f", value)
        } else {
            value.roundToInt().toString()
        }
    }
    val previewLabel = sliderPreview?.let { draft ->
        if (decimalDp) String.format(java.util.Locale.ROOT, "%.1f", draft)
        else draft.roundToInt().toString()
    }
    val displayText = "${previewLabel ?: displayValue}${if (spec.unit.isBlank()) "" else " ${spec.unit}"}"
    val scaledValue = if (decimalDp) (value * 10f).roundToInt() else value.roundToInt()
    val scaledMin = if (decimalDp) spec.min * 10 else spec.min
    val scaledMax = if (decimalDp) maxValue * 10 else maxValue
    val sliderSteps = if (steps > 0) steps else
        DiscreteSliderSteps.forStoragePrecision(spec.min, maxValue, decimalDp)
    // The thumb remains continuous while dragging; its displayed/persisted
    // values use storage precision without allocating excessive native tick marks.
    val snapIncrement = if (decimalDp) 0.1f else 1f

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
            steps = sliderSteps,
            snapIncrement = snapIncrement,
            onValuePreview = { preview ->
                sliderPreview = preview
                if (preview != null && enabled &&
                    (beforeSave == null || previewWriteAllowed?.invoke(preview) == true)
                ) {
                    // Same quantized value as the right-hand label; save() suppresses
                    // repeated writes for frames in the same displayed step.
                    save(preview)
                }
            },
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
                prismalNumericAction = true,
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
