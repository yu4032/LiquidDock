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
                    "表面法线可视化",
                    "以颜色显示玻璃表面法线方向，用于检查折射、曲面与光照响应",
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
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS,
            "应用顶部菜单液态玻璃",
            "将应用顶部控制器展开后的分屏、小窗等胶囊背景替换为液态玻璃；已有 Hook 时实时生效，首次加载失败则需重启系统界面",
            masterEnabled && liquidEnabled,
        )
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.SHORTCUT_POPUP_GLASS,
            "桌面快捷菜单玻璃背景",
            "替换长按桌面图标弹出的快捷菜单背景；关闭后恢复系统材质；已有 Hook 时实时生效，首次加载缺失的 Hook 需重启桌面",
            masterEnabled && liquidEnabled,
        )
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.SHORTCUT_POPUP_DARK_TEXT,
            "快捷菜单深色模式适配",
            "将快捷菜单文字和图标统一改为白色；关闭后恢复系统原样；已有 Hook 时实时生效",
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

