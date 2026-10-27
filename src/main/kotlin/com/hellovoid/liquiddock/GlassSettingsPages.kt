package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
        item(key = "continuous-glass-corners") {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.CONTINUOUS_CORNERS,
                    "iPadOS 风格连续圆角",
                    "替换所有液态玻璃的圆角轮廓，并同步折射、高光与阴影；关闭恢复原轮廓，重启目标应用生效",
                    masterEnabled && liquidGlass,
                )
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
        item(key = "icons-glass-title") { SmallTitle("图标玻璃开关") }
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
        item(key = "icons-geometry-title") { SmallTitle("尺寸与圆角") }
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
    DenseSettingsList(
        padding,
        stringResource(R.string.page_glass_widgets),
        "小组件玻璃、内容适配与独立外观分开调整。",
    ) {
        item(key = "widget-function-heading") { SmallTitle("玻璃与内容适配") }
        item(key = "widget-enabled") {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.Glass.WIDGET_GLASS, "小部件玻璃",
                    "只替换小组件背景，保留 RemoteViews / MAML 的文字、图标与交互内容",
                    masterEnabled && liquidEnabled,
                ) { widgetGlass = it }
            }
        }
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
        item(key = "widget-related-heading") { SmallTitle("独立外观与组件管理") }
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
        item(key = "widget-components") {
            SettingsCard {
                ArrowPreference(
                    stringResource(R.string.widget_components_entry),
                    summary = stringResource(R.string.widget_components_entry_summary),
                    enabled = masterEnabled && liquidEnabled && widgetGlass,
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
