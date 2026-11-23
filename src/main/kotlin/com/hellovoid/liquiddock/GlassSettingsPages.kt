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
internal fun SoftGlassPage(
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
                stringResource(R.string.page_soft_glass),
                "仅作用于 OS4 系统桌面的原生柔光玻璃。重启桌面生效，背景模糊设为 0 可观察清晰折射。",
            )
        }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.ENABLED,
                    "开启自定义",
                    "开启后自定义桌面原生柔光玻璃参数；重启桌面生效。",
                    masterEnabled,
                ) { liquidGlass = it }
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.DISABLE_STATIONARY_DOWNSAMPLE,
                    "取消桌面玻璃静止时的下采样",
                    "禁用桌面原生在玻璃静止时的采样降级与 0.25× 预缩放下采样，保持完整采样分辨率。",
                    masterEnabled && liquidGlass,
                )
            }
        }
        item { ModernSectionLabel("全局基准") }
        softGlassEntries.forEach { entry ->
            item {
                ModernFeatureCard(
                    title = stringResource(entry.titleRes),
                    summary = entry.summary,
                    onClick = { open(entry.page) },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
        if (!liquidGlass) {
            item {
                ModernSurface(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                        "开启自定义后应用这些柔光玻璃参数。",
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SoftGlassComponentsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
    searchFocus: SettingsFocusRequest? = null,
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
        stringResource(R.string.page_soft_glass_components),
        "OS4 系统桌面原生柔光玻璃组件：普通桌面图标、Dock 应用图标、小部件玻璃与 MAML 小组件隐藏规则。",
        focusItemIndex = searchFocus?.let { customDenseFocusIndex(Page.SoftGlassComponents, it.key) },
        focusSequence = searchFocus?.sequence,
    ) {
        item(key = "soft-icons-heading") { SmallTitle("图标与控件") }
        item(key = "soft-icons-toggle") {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.ICON_GLASS,
                    "图标玻璃",
                    "控制普通桌面图标与 Dock 应用图标；重启桌面生效。",
                    masterEnabled && liquidEnabled,
                ) { iconGlass = it }
            }
        }
        item(key = "soft-icons-geometry") {
            SettingsCard {
                IntSetting(prefs, iconSizeOffsetSpec, masterEnabled && liquidEnabled && iconGlass)
                ModernListDivider()
                IntSetting(prefs, iconCornerRadiusSpec, masterEnabled && liquidEnabled && iconGlass)
            }
        }
        item(key = "soft-widgets-heading") { SmallTitle("小组件") }
        item(key = "soft-widgets-toggle") {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.WIDGET_GLASS,
                    "小部件玻璃",
                    "替换小组件背景；关闭时恢复原生背景，保留其他独立隐藏规则；重启桌面生效",
                    masterEnabled && liquidEnabled,
                ) { widgetGlass = it }
            }
        }
        item(key = "soft-widgets-geometry") {
            SettingsCard {
                IntSetting(prefs, widgetSizeOffsetSpec, masterEnabled && liquidEnabled && widgetGlass)
                ModernListDivider()
                IntSetting(prefs, widgetCornerRadiusSpec, masterEnabled && liquidEnabled && widgetGlass)
            }
        }
        item(key = "soft-widgets-maml") {
            SettingsCard {
                ArrowPreference(
                    title = stringResource(R.string.widget_components_entry),
                    summary = "扫描并按节点隐藏小组件（MAML 与 RemoteViews）内部背景或元素；背景规则随小组件玻璃开关同步恢复。",
                    enabled = true,
                    onClick = { open(Page.WidgetComponents) },
                )
            }
        }
        item(key = "soft-folders-heading") { SmallTitle("文件夹") }
        item(key = "soft-folders-entry") {
            SettingsCard {
                ArrowPreference(
                    title = stringResource(R.string.page_glass_folders),
                    summary = "小文件夹与大文件夹玻璃开关、尺寸与圆角自定义。",
                    enabled = true,
                    onClick = { open(Page.GlassFolders) },
                )
            }
        }
        item(key = "soft-dock-heading") { SmallTitle("Dock 栏") }
        item(key = "soft-dock-entry") {
            SettingsCard {
                ArrowPreference(
                    title = stringResource(R.string.page_dock_geometry),
                    summary = "调整 Dock 尺寸、位置、圆角与图标间距。",
                    enabled = true,
                    onClick = { open(Page.DockGeometry) },
                )
            }
        }
    }
}

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
                    page = entry.page,
                )
            }
        }
        item {
            ModernFeatureCard(
                title = stringResource(R.string.page_glass_components),
                summary = "管理图标、小组件、文件夹与菜单液态玻璃",
                onClick = { open(Page.GlassComponents) },
                modifier = Modifier.padding(horizontal = 14.dp),
                page = Page.GlassComponents,
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
        summary = "选择需要调整的液态玻璃组件。",
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
    searchFocus: SettingsFocusRequest? = null,
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
    // Keep Prismal on every group, but avoid one massive offscreen blur
    // layer containing every switch, slider and stepper on this page.
    // The entire page still has only one LazyColumn as scroll owner.
    DenseSettingsList(
        padding,
        stringResource(R.string.page_glass_icons),
        "控制普通桌面图标与 Dock 应用图标玻璃，并调整尺寸与圆角。",
        focusItemIndex = searchFocus?.let { customDenseFocusIndex(Page.GlassIcons, it.key) },
        focusSequence = searchFocus?.sequence,
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
                if (os4Launcher) {
                    ModernListDivider()
                    BooleanSetting(
                        prefs,
                        ConfigSchema.Glass.OS4_ICON_GLASS_DOWNSAMPLE,
                        "图标玻璃下采样",
                        "开启后 1×1 图标复用共享下采样背景以降低 GPU 与显存开销；关闭后全分辨率采样更清晰但更耗性能。",
                        masterEnabled && liquidEnabled && iconGlass,
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
                    masterEnabled && liquidEnabled && iconGlass,
                )
                ModernListDivider()
                IntSetting(
                    prefs,
                    iconCornerRadiusSpec,
                    masterEnabled && liquidEnabled && iconGlass,
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
    searchFocus: SettingsFocusRequest? = null,
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
        if (os4Launcher) "关闭小部件玻璃将恢复隐藏的背景层；其他手动隐藏规则保持生效，切换后自动刷新桌面。"
        else "小组件玻璃、内容适配与独立外观分开调整。",
        focusItemIndex = searchFocus?.let { customDenseFocusIndex(Page.GlassWidgets, it.key) },
        focusSequence = searchFocus?.sequence,
    ) {
        item(key = "widget-function-heading") {
            SmallTitle("玻璃与内容适配")
        }
        item(key = "widget-enabled") {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.Glass.WIDGET_GLASS, "小部件玻璃",
                    "替换小组件背景；关闭后恢复隐藏的背景层并自动刷新桌面",
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
        item(key = "widget-related-heading") { SmallTitle("独立外观与组件管理") }
        item(key = "widget-components") {
            SettingsCard {
                ArrowPreference(
                    title = stringResource(R.string.widget_components_entry),
                    summary = stringResource(R.string.widget_components_entry_summary),
                    enabled = os4Launcher || (masterEnabled && liquidEnabled && widgetGlass),
                    onClick = { open(Page.WidgetComponents) },
                )
            }
        }
    }
}


/** Stable existing folder cell positions; multiple sliders in one cell share one target. */
internal enum class FolderGlassCell(val id: String) {
    SMALL_HEADING("folder-small-heading"),
    SMALL_ENABLED("folder-small-enabled"),
    SMALL_GEOMETRY("folder-small-geometry"),
    LARGE_HEADING("folder-large-heading"),
    LARGE_ENABLED("folder-large-enabled"),
    LARGE_GEOMETRY("folder-large-geometry"),
    SHADOW_HEADING("folder-shadow-heading"),
    SHADOW("folder-shadow");
}
internal val folderGlassCellOrder: List<String> = FolderGlassCell.entries.map { it.id }
internal val folderGlassFocusCell: Map<String, String> = mapOf(
    ConfigSchema.Glass.SMALL_FOLDER_GLASS.name() to FolderGlassCell.SMALL_ENABLED.id,
    smallFolderSizeOffsetSpec.key to FolderGlassCell.SMALL_GEOMETRY.id,
    smallFolderCornerRadiusSpec.key to FolderGlassCell.SMALL_GEOMETRY.id,
    ConfigSchema.Glass.LARGE_FOLDER_GLASS.name() to FolderGlassCell.LARGE_ENABLED.id,
    largeFolderSizeOffsetSpec.key to FolderGlassCell.LARGE_GEOMETRY.id,
    largeFolderCornerRadiusSpec.key to FolderGlassCell.LARGE_GEOMETRY.id,
    largeFolder2x1CornerRadiusSpec.key to FolderGlassCell.LARGE_GEOMETRY.id,
)

@Composable
internal fun GlassFoldersPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
    searchFocus: SettingsFocusRequest? = null,
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
    DenseSettingsList(
        padding,
        stringResource(R.string.page_glass_folders),
        "小文件夹与大文件夹分别调整，支持 2×1 与 2×2 及以上大文件夹独立圆角。",
        focusItemIndex = searchFocus?.key?.let(folderGlassFocusCell::get)
            ?.let(folderGlassCellOrder::indexOf)?.takeIf { it >= 0 }?.plus(1),
        focusSequence = searchFocus?.sequence,
    ) {
        item(key = FolderGlassCell.SMALL_HEADING.id) { SmallTitle("小文件夹") }
        item(key = FolderGlassCell.SMALL_ENABLED.id) {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.Glass.SMALL_FOLDER_GLASS,
                    "小文件夹玻璃", "保留 1x1 文件夹缩略预览",
                    masterEnabled && liquidEnabled,
                ) { smallFolderGlass = it }
            }
        }
        item(key = FolderGlassCell.SMALL_GEOMETRY.id) {
            SettingsCard {
                IntSetting(prefs, smallFolderSizeOffsetSpec, masterEnabled && liquidEnabled && smallFolderGlass)
                ModernListDivider()
                IntSetting(prefs, smallFolderCornerRadiusSpec, masterEnabled && liquidEnabled && smallFolderGlass)
            }
        }
        item(key = FolderGlassCell.LARGE_HEADING.id) { SmallTitle("大文件夹") }
        item(key = FolderGlassCell.LARGE_ENABLED.id) {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.Glass.LARGE_FOLDER_GLASS,
                    "大文件夹玻璃", "独立控制大文件夹材质",
                    masterEnabled && liquidEnabled,
                ) { largeFolderGlass = it }
                ModernListDivider()
                top.yukonga.miuix.kmp.basic.BasicComponent(
                    title = "大文件夹玻璃参数",
                    summary = "统一使用最外部柔光玻璃全局参数；点击前往调节",
                    enabled = masterEnabled && liquidEnabled && largeFolderGlass,
                    insideMargin = ModernPreferenceMargin,
                    onClick = { open(Page.SoftGlass) },
                )
            }
        }
        item(key = FolderGlassCell.LARGE_GEOMETRY.id) {
            SettingsCard {
                IntSetting(prefs, largeFolderSizeOffsetSpec, masterEnabled && liquidEnabled && largeFolderGlass)
                ModernListDivider()
                IntSetting(prefs, largeFolder2x1CornerRadiusSpec, masterEnabled && liquidEnabled && largeFolderGlass)
                ModernListDivider()
                IntSetting(prefs, largeFolderCornerRadiusSpec, masterEnabled && liquidEnabled && largeFolderGlass)
            }
        }
        item(key = FolderGlassCell.SHADOW_HEADING.id) { SmallTitle("玻璃阴影") }
        item(key = FolderGlassCell.SHADOW.id) {
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
