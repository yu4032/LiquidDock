package com.hellovoid.liquiddock

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.hellovoid.liquiddock.config.ConfigSchema
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Settings

// Shared controls and their keyed preference revisions live in SettingsControls.kt.

class ComposeSettingsActivity : SettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themePrefs = remember { getSharedPreferences(SETTINGS_UI_PREFS, Context.MODE_PRIVATE) }
            val paletteState = rememberSettingsUiPalette(themePrefs)
            val palette = paletteState.value
            val controller = rememberSettingsUiThemeController(palette)
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
                SettingsUiColorTheme(controller = controller, palette = palette) {
                    LiquidDockSettings(
                        activity = this,
                        selectedPalette = palette,
                        onPaletteSelected = { chosen ->
                            paletteState.value = chosen
                            themePrefs.edit()
                                .putString(SETTINGS_UI_THEME_PALETTE, chosen.persistedValue)
                                .apply()
                        },
                    )
                }
            }
        }
    }
}

// Route model and menu catalogs live in SettingsNavigationModel.kt.

@Composable
private fun LiquidDockSettings(
    activity: ComposeSettingsActivity,
    selectedPalette: SettingsUiPalette,
    onPaletteSelected: (SettingsUiPalette) -> Unit,
) {
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
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchCatalog = remember(activity) { settingsSearchCatalog(activity) }

    fun closeSearch() {
        searchOpen = false
        searchQuery = ""
    }
    fun openSearch() {
        if (!root || searchOpen) return
        searchQuery = ""
        searchOpen = true
    }
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

    BackHandler(enabled = searchOpen || !root) {
        if (searchOpen) closeSearch() else navigateBack()
    }

    val searchTransition = updateTransition(searchOpen, label = "settings-search")

    ModernSettingsScaffold(
        title = if (searchOpen) "搜索设置" else stringResource(page.titleRes),
        glassEnabled = uiGlassEnabled,
        showBack = searchOpen || !root,
        backLabel = stringResource(R.string.action_back),
        onBack = { if (searchOpen) closeSearch() else navigateBack() },
        actions = {
            if (!searchOpen) ModernTopActionButton(
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
                AnimatedVisibility(
                    visible = !searchOpen,
                    enter = slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        initialOffsetY = { it / 3 },
                    ) + fadeIn(tween(180)),
                    exit = slideOutVertically(
                        animationSpec = tween(220),
                        targetOffsetY = { it / 2 },
                    ) + fadeOut(tween(160)),
                ) {
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
        searchTransition.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                // Pulling down brings search in from the top; closing reverses
                // the motion. Both screens stay composed until the spring settles.
                (slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialOffsetY = { if (targetState) -it / 5 else it / 10 },
                ) + fadeIn(tween(210))) togetherWith
                    (slideOutVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        targetOffsetY = { if (targetState) it / 10 else -it / 5 },
                    ) + fadeOut(tween(160)))
            },
        ) { showingSearch ->
            if (showingSearch) {
                SettingsSearchScreen(
                    padding = padding,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    catalog = searchCatalog,
                    // Avoid opening the IME halfway through the page transition.
                    focusOnOpen = searchTransition.currentState && !searchTransition.isRunning,
                    onSelect = { item ->
                        closeSearch()
                        navigateTo(item.page)
                    },
                )
            } else {
                AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize().then(
                // Only the four bottom-navigation root pages may reveal search.
                // Keep nested-scroll entirely off subpages, not merely disabled.
                if (root && !searchOpen && !showRestartScopes) {
                    Modifier.nestedScroll(rememberSettingsPullDownSearch(::openSearch))
                } else Modifier,
            ),
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
                    liquidMaterialGroups,
                )
                Page.LiquidRefraction -> LiquidSpecPage(
                    padding,
                    prefs,
                    masterEnabled,
                    "折射形状、位移、边缘衰减与表面几何",
                    liquidRefractionGroups,
                )
                Page.LiquidColor -> LiquidSpecPage(
                    padding,
                    prefs,
                    masterEnabled,
                    "底色、色散与背景鲜艳度",
                    liquidColorGroups,
                )
                Page.LiquidLighting -> LiquidLightingPage(padding, prefs, masterEnabled)
                Page.GlassEdge -> GlassEdgeSettingsPage(
                    padding, prefs, masterEnabled, GlassEdgeEffects.GLOBAL,
                    open = ::navigateTo,
                )
                Page.DockEdge -> GlassEdgeSettingsPage(
                    padding, prefs, masterEnabled, GlassEdgeEffects.DOCK,
                )
                Page.IconEdge -> GlassEdgeSettingsPage(
                    padding, prefs, masterEnabled, GlassEdgeEffects.ICON,
                )
                Page.WidgetEdge -> GlassEdgeSettingsPage(
                    padding, prefs, masterEnabled, GlassEdgeEffects.WIDGET,
                )
                Page.FolderEdge -> GlassEdgeSettingsPage(
                    padding, prefs, masterEnabled, GlassEdgeEffects.FOLDER,
                )
                Page.PopupEdge -> GlassEdgeSettingsPage(
                    padding, prefs, masterEnabled, GlassEdgeEffects.POPUP,
                )
                Page.HandleMenuEdge -> GlassEdgeSettingsPage(
                    padding, prefs, masterEnabled, GlassEdgeEffects.HANDLE_MENU,
                )
                Page.DialogEdge -> GlassEdgeSettingsPage(
                    padding, prefs, masterEnabled, GlassEdgeEffects.DIALOG,
                )
                Page.GboardEdge -> GlassEdgeSettingsPage(
                    padding, prefs, masterEnabled, GlassEdgeEffects.GBOARD,
                )
                Page.SearchboxEdge -> GlassEdgeSettingsPage(
                    padding, prefs, masterEnabled, GlassEdgeEffects.SEARCHBOX,
                )
                Page.SecurityCenterEdge -> GlassEdgeSettingsPage(
                    padding, prefs, masterEnabled, GlassEdgeEffects.SECURITY_CENTER,
                )
                Page.LiquidShadow -> LiquidSpecPage(
                    padding,
                    prefs,
                    masterEnabled,
                    "玻璃内部阴影颜色与柔和程度",
                    liquidShadowGroups,
                )
                Page.LiquidSampling -> LiquidSamplingPage(padding, prefs, masterEnabled)

                Page.GlassComponents -> GlassComponentsPage(padding, ::navigateTo)
                Page.GlassIcons -> GlassIconsPage(padding, prefs, masterEnabled, ::navigateTo)
                Page.GlassWidgets -> GlassWidgetsPage(padding, prefs, masterEnabled, ::navigateTo)
                Page.GlassFolders -> GlassFoldersPage(padding, prefs, masterEnabled, ::navigateTo)
                Page.GlassMenus -> GlassMenusPage(padding, prefs, masterEnabled, ::navigateTo)
                Page.HandleMenuCustomization -> HandleMenuGlassSettingsPage(
                    padding, prefs, masterEnabled,
                    onOpenAll = { navigateTo(Page.HandleMenuAll) },
                    onOpenEdge = { navigateTo(Page.HandleMenuEdge) },
                )
                Page.HandleMenuAll -> ScopedGlassSettingsPage(
                    padding, prefs, masterEnabled, ScopedGlassOptics.HANDLE_MENU, "应用顶部菜单",
                )
                Page.ShortcutPopupCustomization -> ShortcutPopupGlassSettingsPage(
                    padding, prefs, masterEnabled,
                    onOpenAll = { navigateTo(Page.ShortcutPopupAll) },
                    onOpenEdge = { navigateTo(Page.PopupEdge) },
                )
                Page.ShortcutPopupAll -> ScopedGlassSettingsPage(
                    padding, prefs, masterEnabled, ScopedGlassOptics.POPUP, "桌面快捷菜单",
                )
                Page.DialogCustomization -> DialogGlassSettingsPage(
                padding, prefs, masterEnabled,
                onOpenAll = { navigateTo(Page.DialogAll) },
                onOpenEdge = { navigateTo(Page.DialogEdge) },
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
                Page.Shadow -> ShadowPage(padding, prefs, masterEnabled, ::navigateTo)

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
                Page.ThemePalette -> SettingsUiPalettePage(
                    padding = padding,
                    selected = selectedPalette,
                    onSelect = onPaletteSelected,
                )
                Page.Data -> DataPage(padding, activity)
                Page.About -> AboutPage(padding, activity, prefs)
            }
                }
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

// Dock decoration controls live in DockDecorationSettingsPages.kt.
// Security Center sidebar, data management and notices live in SettingsUtilityPages.kt.
