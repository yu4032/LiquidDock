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
internal fun LiquidSpecPage(
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
        item(key = "lighting-basic-heading") {
            SmallTitle("基础光照与焦散")
        }
        groupedIntSettings(liquidLightingSpecs, prefs, enabled)
        item(key = "lighting-os4-heading") {
            SmallTitle("OS4 反射与方向光")
        }
        groupedIntSettings(liquidOs4Specs, prefs, enabled)
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
    HubPage(
        padding = padding,
        summary = "选择需要调整的玻璃组件。",
        entries = componentEntries,
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
                    "使用液态玻璃替换清除全部和设备互联胶囊背景；采样未就绪时保持透明，不回退原生模糊",
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
        item(key = "icons-glass-edge") {
            SettingsCard {
                ArrowPreference(
                    title = "外阴影与描边",
                    summary = "桌面图标使用独立边缘效果",
                    enabled = masterEnabled && liquidEnabled,
                    onClick = { open(Page.IconEdge) },
                )
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
            title = "外阴影与描边",
            summary = "小组件独立控制，未调整时继承全局",
            enabled = masterEnabled && liquidEnabled && widgetGlass,
            onClick = { open(Page.WidgetEdge) },
        )
        ArrowPreference(
            stringResource(R.string.widget_components_entry),
            summary = stringResource(R.string.widget_components_entry_summary),
            enabled = masterEnabled && liquidEnabled && widgetGlass,
            onClick = { open(Page.WidgetComponents) },
        )
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
        IntSetting(prefs, largeFolderPreviewIconScaleSpec, masterEnabled)
        // Opened FolderCling background is independent from the desktop
        // small/large folder icon glass. Native Hooks are installed once
        // when Launcher starts, so this switch requires a Launcher restart.
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.OPEN_FOLDER_GLASS,
            "展开文件夹玻璃背景",
            "替换文件夹展开后的原生背景；关闭后恢复系统样式，修改后需重启桌面",
            masterEnabled && liquidEnabled,
        ) { openFolderGlass = it }
        IntSetting(prefs, openFolderBlurSpec, masterEnabled && liquidEnabled && openFolderGlass)
        IntSetting(prefs, openFolderNativeBlurSpec, masterEnabled && liquidEnabled && openFolderGlass)
        IntSetting(prefs, openFolderIorSpec, masterEnabled && liquidEnabled && openFolderGlass)
        IntSetting(prefs, openFolderThicknessSpec, masterEnabled && liquidEnabled && openFolderGlass)
        IntSetting(prefs, openFolderTintSpec, masterEnabled && liquidEnabled && openFolderGlass)
        ArrowPreference(
            title = "外阴影与描边",
            summary = "桌面大小文件夹以及展开文件夹使用独立设置",
            enabled = masterEnabled && liquidEnabled,
            onClick = { open(Page.FolderEdge) },
        )
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE,
            "显示原生大文件夹描边",
            "关闭后移除系统默认灰黑色圆角描边，保留玻璃与背景效果",
            masterEnabled,
        )
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
        ArrowPreference(
            title = stringResource(R.string.page_handle_menu),
            summary = "应用顶部控制器的菜单玻璃、颜色、模糊与独立光学参数",
            enabled = masterEnabled && liquidEnabled,
            onClick = { open(Page.HandleMenuCustomization) },
        )
        ArrowPreference(
            title = stringResource(R.string.page_shortcut_popup),
            summary = "长按快捷菜单的玻璃、深色模式、模糊、颜色及独立光学参数",
            enabled = masterEnabled && liquidEnabled,
            onClick = { open(Page.ShortcutPopupCustomization) },
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

