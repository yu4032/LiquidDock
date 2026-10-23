package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellovoid.liquiddock.config.ConfigSchema
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text

// Pure relocation of the existing Liquid, component-glass, and highlight pages.
// Prismal rendering, settings writes, toggled dependencies and routing are unchanged.
@Composable
internal fun LiquidPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    val os4Launcher = LocalSettingsAvailability.current.isOs4Launcher()
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
                if (os4Launcher) "背景由桌面原生采样；可独立调整原生玻璃参数。重启桌面生效，背景模糊设为 0 可观察清晰折射。"
                else stringResource(R.string.liquid_header_summary),
            )
        }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.ENABLED,
                    stringResource(R.string.liquid_enable),
                    if (os4Launcher) "启用或关闭原生玻璃后重启桌面生效。"
                    else stringResource(R.string.liquid_enable_summary),
                    masterEnabled,
                ) { liquidGlass = it }
            }
        }
        if (os4Launcher) {
            os4NativeGlassSettings(prefs, masterEnabled && liquidGlass)
        } else {
            liquidEntries.forEach { entry ->
                item {
                    ModernFeatureCard(
                        title = stringResource(entry.titleRes),
                        summary = entry.summary,
                        onClick = { open(entry.page) },
                        modifier = Modifier.padding(horizontal = 14.dp),
                        page = entry.page,
                    )
                }
            }
            item {
                ModernFeatureCard(
                    title = stringResource(R.string.page_glass_components),
                    summary = "管理图标、小组件、文件夹与菜单玻璃",
                    onClick = { open(Page.GlassComponents) },
                    page = Page.GlassComponents,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
        if (os4Launcher) {
            item(key = "os4-glass-components-entry") {
                SettingsCard {
                    ArrowPreference(
                        title = "图标与小组件",
                        summary = "配置桌面图标、小部件玻璃与已载入的 MAML 小组件隐藏规则",
                        enabled = true,
                        onClick = { open(Page.GlassComponents) },
                    )
                }
            }
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
internal fun LiquidSpecPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    summary: String,
    groups: List<LiquidSpecGroup>,
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
        groupedLiquidSettings(groups, prefs, masterEnabled && liquidEnabled)
    }
}


@Composable
internal fun LiquidLightingPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    val enabled = masterEnabled && liquidEnabled
    DenseSettingsList(
        padding = padding,
        title = stringResource(R.string.page_liquid_lighting),
        summary = "调节焦散、菲涅尔反射与光源方向，并集中调整 OS4 边缘反射和方向光。",
    ) {
        groupedLiquidSettings(liquidLightingGroups + liquidOs4Groups, prefs, enabled)
    }
}

@Composable
internal fun LiquidSamplingPage(
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
        groupedLiquidSettings(liquidSamplingGroups, prefs, masterEnabled && liquidEnabled)
        item(key = "sampling-performance-title") { SmallTitle("实时背景采样与帧同步") }
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
        item(key = "sampling-debug-title") { SmallTitle("调试显示") }
        item(key = ConfigSchema.Glass.PRISMAL_SHOW_NORMALS.name()) {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.PRISMAL_SHOW_NORMALS,
                    "OS4 法线与方向光可视化",
                    "内部显示玻璃表面法线，边缘显示 OS4 反射使用的 SDF 法线；亮度反映 OS4 方向光，不包含背景反射纹理",
                    masterEnabled && liquidEnabled,
                )
            }
        }
    }
}

@Composable
internal fun GlassComponentsPage(
    padding: PaddingValues,
    open: (Page) -> Unit,
) {
    val os4Launcher = LocalSettingsAvailability.current.isOs4Launcher()
    val entries = if (os4Launcher) {
        componentEntries.map { entry ->
            when (entry.page) {
                Page.GlassIcons -> entry.copy(summary = "普通桌面图标与 Dock 应用图标")
                Page.GlassWidgets -> entry.copy(summary = "小部件玻璃与已载入的 MAML 小组件隐藏规则")
                else -> entry
            }
        }
    } else componentEntries
    HubPage(
        padding = padding,
        summary = if (os4Launcher) {
            "OS4 可配置普通桌面图标、Dock 应用图标、小部件玻璃和已载入的 MAML 小组件隐藏规则。"
        } else "选择需要调整的玻璃组件。",
        entries = entries,
        open = open,
    )
}

@Composable
internal fun GlassIconsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    val os4Launcher = LocalSettingsAvailability.current.isOs4Launcher()
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
        if (os4Launcher) "控制普通桌面图标与 Dock 应用图标玻璃，并调整尺寸与圆角。"
        else "调整桌面图标、Dock 功能图标与多任务胶囊玻璃。",
    ) {
        item(key = "icons-glass-title") { SmallTitle("图标玻璃开关") }
        item(key = "icons-glass-toggles") {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.ICON_GLASS,
                    "图标玻璃",
                    if (os4Launcher) "控制普通桌面图标与 Dock 应用图标；重启桌面生效。"
                    else "同时控制桌面与 Dock 全部图标；0 圆角为 Auto",
                    masterEnabled && liquidEnabled,
                ) { iconGlass = it }
                if (!os4Launcher) {
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
                        "使用液态玻璃替换清除全部和设备互联胶囊背景；采样未就绪时保持透明，不回退原生模糊",
                        masterEnabled && liquidEnabled,
                    )
                }
            }
        }
        item(key = "icons-geometry-title") { SmallTitle("尺寸与圆角") }
        item(key = "icons-glass-geometry") {
            SettingsCard {
                IntSetting(
                    prefs,
                    iconSizeOffsetSpec,
                    masterEnabled && liquidEnabled &&
                        (iconGlass || (!os4Launcher && functionalDockIconGlass)),
                )
                ModernListDivider()
                IntSetting(
                    prefs,
                    iconCornerRadiusSpec,
                    masterEnabled && liquidEnabled &&
                        (iconGlass || (!os4Launcher && functionalDockIconGlass)),
                )
            }
        }
        if (!os4Launcher) {
            item(key = "icons-appearance-title") { SmallTitle("独立外观") }
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
            item(key = "icons-glass-edge") {
                SettingsCard {
                    ArrowPreference(
                        title = "阴影",
                        summary = "独立设置 Prismal 外阴影与边缘描边",
                        enabled = masterEnabled && liquidEnabled,
                        onClick = { open(Page.IconEdge) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun GlassWidgetsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    val os4Launcher = LocalSettingsAvailability.current.isOs4Launcher()
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
    DenseSettingsList(
        padding,
        stringResource(R.string.page_glass_widgets),
        if (os4Launcher) "控制小部件玻璃；组件隐藏规则独立于玻璃开关。重启桌面生效。"
        else "小组件玻璃、内容适配与独立外观分开调整。",
    ) {
        item(key = "widget-function-heading") {
            SmallTitle(if (os4Launcher) "玻璃" else "玻璃与内容适配")
        }
        item(key = "widget-enabled") {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.Glass.WIDGET_GLASS, "小部件玻璃",
                    "只替换小组件背景，保留 RemoteViews / MAML 的文字、图标与交互内容",
                    masterEnabled && liquidEnabled,
                ) { widgetGlass = it }
            }
        }
        if (!os4Launcher) {
            item(key = "widget-dark-content") {
                SettingsCard {
                    BooleanSetting(
                        prefs, ConfigSchema.Glass.WIDGET_DARK_CONTENT, "小组件深色内容适配",
                        "将深色中性文字适配为浅色；MAML 优先使用原生深色变量，不处理图片与彩色内容",
                        masterEnabled && liquidEnabled && widgetGlass,
                    )
                }
            }
            item(key = "widget-geometry-heading") { SmallTitle("尺寸与圆角") }
            item(key = "widget-geometry") {
                SettingsCard {
                    IntSetting(prefs, widgetSizeOffsetSpec, masterEnabled && liquidEnabled && widgetGlass)
                    ModernListDivider()
                    IntSetting(prefs, widgetCornerRadiusSpec, masterEnabled && liquidEnabled && widgetGlass)
                }
            }
            item(key = "widget-edge") {
                SettingsCard {
                    ArrowPreference(
                        title = "阴影",
                        summary = "Prismal 外阴影与描边可独立设置，未调整时继承全局",
                        enabled = masterEnabled && liquidEnabled && widgetGlass,
                        onClick = { open(Page.WidgetEdge) },
                    )
                }
            }
        }
        item(key = "widget-related-heading") { SmallTitle(if (os4Launcher) "已载入的 MAML 小组件" else "独立外观与组件管理") }
        item(key = "widget-components") {
            SettingsCard {
                ArrowPreference(
                    title = if (os4Launcher) "已载入的 MAML 小组件组件管理" else stringResource(R.string.widget_components_entry),
                    summary = if (os4Launcher) "手动载入当前已挂载的 MAML 小组件，按精确节点设置隐藏规则；不依赖小部件玻璃开关。"
                    else stringResource(R.string.widget_components_entry_summary),
                    enabled = os4Launcher || (masterEnabled && liquidEnabled && widgetGlass),
                    onClick = { open(Page.WidgetComponents) },
                )
            }
        }
    }
}

@Composable
internal fun GlassFoldersPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
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
    var openFolderGlass by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Glass.OPEN_FOLDER_GLASS.name(),
                ConfigSchema.Glass.OPEN_FOLDER_GLASS.uiDefault(),
            ),
        )
    }
    DenseSettingsList(
        padding,
        stringResource(R.string.page_glass_folders),
        "小文件夹、大文件夹和展开文件夹分别调整，不混用背景与几何参数。",
    ) {
        item(key = "folder-small-heading") { SmallTitle("小文件夹") }
        item(key = "folder-small-enabled") {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.Glass.SMALL_FOLDER_GLASS,
                    "小文件夹玻璃", "保留 1x1 文件夹缩略预览",
                    masterEnabled && liquidEnabled,
                ) { smallFolderGlass = it }
            }
        }
        item(key = "folder-small-geometry") {
            SettingsCard {
                IntSetting(prefs, smallFolderSizeOffsetSpec, masterEnabled && liquidEnabled && smallFolderGlass)
                ModernListDivider()
                IntSetting(prefs, smallFolderCornerRadiusSpec, masterEnabled && liquidEnabled && smallFolderGlass)
            }
        }
        item(key = "folder-large-heading") { SmallTitle("大文件夹") }
        item(key = "folder-large-enabled") {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.Glass.LARGE_FOLDER_GLASS,
                    "大文件夹玻璃", "独立控制大文件夹材质",
                    masterEnabled && liquidEnabled,
                ) { largeFolderGlass = it }
            }
        }
        item(key = "folder-large-geometry") {
            SettingsCard {
                IntSetting(prefs, largeFolderSizeOffsetSpec, masterEnabled && liquidEnabled && largeFolderGlass)
                ModernListDivider()
                IntSetting(prefs, largeFolderCornerRadiusSpec, masterEnabled && liquidEnabled && largeFolderGlass)
            }
        }
        item(key = "folder-large-preview") {
            SettingsCard {
                IntSetting(prefs, largeFolderPreviewIconScaleSpec, masterEnabled)
            }
        }
        item(key = "folder-large-native-outline") {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE,
                    "显示原生大文件夹描边",
                    "关闭后移除系统默认灰黑色圆角描边，保留玻璃与背景效果",
                    masterEnabled,
                )
            }
        }
        item(key = "folder-large-mingou-compat") {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.Glass.MINGOU_LARGE_FOLDER_COMPAT,
                    "绵狗修改版桌面适配",
                    "默认关闭。开启后对含 LiquidDock 标记且数据库保存为 2×2 的大文件夹恢复网格尺寸；不检测桌面版本。修改后重启桌面生效",
                    enabled = true, // This compatibility repair has its own master switch.
                )
            }
        }
        item(key = "folder-open-heading") { SmallTitle("展开文件夹") }
        item(key = "folder-open-enabled") {
            SettingsCard {
                // FolderCling background is independent of the desktop preview;
                // its first Hook installation still requires a Launcher restart.
                BooleanSetting(
                    prefs, ConfigSchema.Glass.OPEN_FOLDER_GLASS,
                    "展开文件夹玻璃背景",
                    "替换文件夹展开后的原生背景；关闭后恢复系统样式，修改后需重启桌面",
                    masterEnabled && liquidEnabled,
                ) { openFolderGlass = it }
            }
        }
        item(key = "folder-open-blur") {
            SettingsCard {
                IntSetting(prefs, openFolderBlurSpec, masterEnabled && liquidEnabled && openFolderGlass)
                ModernListDivider()
                IntSetting(prefs, openFolderNativeBlurSpec, masterEnabled && liquidEnabled && openFolderGlass)
            }
        }
        item(key = "folder-open-optics") {
            SettingsCard {
                IntSetting(prefs, openFolderIorSpec, masterEnabled && liquidEnabled && openFolderGlass)
                ModernListDivider()
                IntSetting(prefs, openFolderThicknessSpec, masterEnabled && liquidEnabled && openFolderGlass)
                ModernListDivider()
                IntSetting(prefs, openFolderTintSpec, masterEnabled && liquidEnabled && openFolderGlass)
            }
        }
        item(key = "folder-shadow-heading") { SmallTitle("玻璃阴影") }
        item(key = "folder-shadow") {
            SettingsCard {
                ArrowPreference(
                    title = "阴影",
                    summary = "桌面与展开文件夹的 Prismal 阴影独立设置",
                    enabled = masterEnabled && liquidEnabled,
                    onClick = { open(Page.FolderEdge) },
                )
            }
        }
    }
}

@Composable
internal fun GlassMenusPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    DenseSettingsList(
        padding,
        stringResource(R.string.page_glass_menus),
        "菜单与弹窗按组件设置，系统级壁纸修复独立放置。",
    ) {
        item(key = "menus-heading") { SmallTitle("菜单与弹窗") }
        item(key = "menus-handle") {
            SettingsCard {
                ArrowPreference(
                    title = stringResource(R.string.page_handle_menu),
                    summary = "应用顶部控制器的菜单玻璃、颜色、模糊与独立光学参数",
                    enabled = masterEnabled && liquidEnabled,
                    targetPage = Page.HandleMenuCustomization,
                    onClick = { open(Page.HandleMenuCustomization) },
                )
            }
        }
        item(key = "menus-shortcut") {
            SettingsCard {
                ArrowPreference(
                    title = stringResource(R.string.page_shortcut_popup),
                    summary = "长按快捷菜单的玻璃、深色模式、模糊、颜色及独立光学参数",
                    enabled = masterEnabled && liquidEnabled,
                    targetPage = Page.ShortcutPopupCustomization,
                    onClick = { open(Page.ShortcutPopupCustomization) },
                )
            }
        }
        item(key = "menus-dialog") {
            SettingsCard {
                ArrowPreference(
                    title = stringResource(R.string.page_dialog_customization),
                    summary = "卸载、移除与二次确认弹窗的玻璃、背景压暗、模糊和颜色",
                    enabled = masterEnabled && liquidEnabled,
                    targetPage = Page.DialogCustomization,
                    onClick = { open(Page.DialogCustomization) },
                )
            }
        }
        item(key = "menus-flicker-heading") { SmallTitle("系统兼容性") }
        item(key = "menus-flicker") {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.Glass.WALLPAPER_FLICKER_FIX,
                    "壁纸闪烁修复",
                    "仅在壁纸闪烁时开启；需要为 LiquidDock 启用 System Framework（system）作用域并重启设备",
                    masterEnabled && liquidEnabled,
                )
            }
        }
    }
}

@Composable
internal fun LauncherHighlightsPage(
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
internal fun LauncherHighlightTogglePage(
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

