package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    searchFocus: SettingsFocusRequest? = null,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(searchFocus?.sequence) {
        val index = searchFocus?.let { customDenseFocusIndex(Page.Liquid, it.key) }
        if (index != null) listState.scrollToItem(index)
    }
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
        state = listState,
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
                val otherRevision = LocalSettingsPreferenceRevisions.current[
                    ConfigSchema.Glass.ADVANCED_MATERIAL_ENABLED.name()] ?: 0
                val otherEnabled = remember(otherRevision) {
                    prefs.getBoolean(ConfigSchema.Glass.ADVANCED_MATERIAL_ENABLED.name(), false)
                }
                SwitchPreference(
                    checked = liquidGlass && !otherEnabled,
                    onCheckedChange = { next ->
                        prefs.edit()
                            .putBoolean(ConfigSchema.Glass.ENABLED.name(), next)
                            .putBoolean(ConfigSchema.Glass.ADVANCED_MATERIAL_ENABLED.name(), false)
                            .apply()
                        liquidGlass = next
                    },
                    title = stringResource(R.string.liquid_enable),
                    summary = "与高级材质互斥；切换渲染管线后需重启相应应用",
                    enabled = masterEnabled,
                )
            }
        }
        item(key = "continuous-glass-corners") {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.CONTINUOUS_CORNERS,
                    "iOS 风格连续圆角",
                    "统一液态玻璃及系统原高级材质 Dock、小组件、文件夹的圆角；保留原生模糊与动画，重启桌面生效",
                    masterEnabled,
                )
            }
        }
        item(key = "native-material-link") {
            ModernFeatureCard(
                title = stringResource(R.string.page_native_advanced_material),
                summary = "使用 HyperOS 原高级材质替代自绘液态玻璃；图标与多任务按钮使用系统模糊",
                onClick = { open(Page.AdvancedMaterial) },
                modifier = Modifier.padding(horizontal = 14.dp),
            )
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
    searchFocus: SettingsFocusRequest? = null,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    val nativeEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ADVANCED_MATERIAL_ENABLED.name(), false,
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
                    "桌面与 Dock 图标的玻璃或原生材质；0 圆角为 Auto",
                    masterEnabled && (liquidEnabled || nativeEnabled),
                ) { iconGlass = it }
                ModernListDivider()
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS,
                    "仅 Dock 功能图标玻璃",
                    "仅搜索、小爱、全部应用、最近任务、Home、手机互联等系统功能入口；可在关闭“图标玻璃”后单独使用",
                    masterEnabled && (liquidEnabled || nativeEnabled),
                ) { functionalDockIconGlass = it }
                ModernListDivider()
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.RECENTS_CAPSULE_GLASS,
                    "多任务操作按钮玻璃",
                    "液态玻璃使用 Prismal；高级材质使用系统模糊。仅影响清除全部和设备互联胶囊背景",
                    masterEnabled && (liquidEnabled || nativeEnabled),
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
                    masterEnabled && (liquidEnabled || nativeEnabled) &&
                        (iconGlass || functionalDockIconGlass),
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
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    val nativeEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ADVANCED_MATERIAL_ENABLED.name(), false,
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
        focusItemIndex = searchFocus?.let { customDenseFocusIndex(Page.GlassWidgets, it.key) },
        focusSequence = searchFocus?.sequence,
    ) {
        item(key = "widget-function-heading") { SmallTitle("玻璃与内容适配") }
        item(key = "widget-enabled") {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.Glass.WIDGET_GLASS,
                    if (nativeEnabled) "小组件部件管理" else "小部件玻璃",
                    if (nativeEnabled) "保留 HyperOS 原生背景，允许按所选规则隐藏 RemoteViews / MAML 部件"
                    else "只替换小组件背景，保留 RemoteViews / MAML 的文字、图标与交互内容",
                    masterEnabled && (liquidEnabled || nativeEnabled),
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
                    enabled = masterEnabled && (liquidEnabled || nativeEnabled) && widgetGlass,
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
    LARGE_PREVIEW("folder-large-preview"),
    LARGE_NATIVE_OUTLINE("folder-large-native-outline"),
    LARGE_MINGOU_COMPAT("folder-large-mingou-compat"),
    OPEN_HEADING("folder-open-heading"),
    OPEN_ENABLED("folder-open-enabled"),
    OPEN_BLUR("folder-open-blur"),
    OPEN_OPTICS("folder-open-optics"),
    OPEN_FILL_HEADING("folder-open-fill-heading"),
    OPEN_FILL_RGB("folder-open-fill-rgb"),
    OPEN_FILL_ALPHA("folder-open-fill-alpha"),
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
    largeFolderPreviewIconScaleSpec.key to FolderGlassCell.LARGE_PREVIEW.id,
    ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.name() to FolderGlassCell.LARGE_NATIVE_OUTLINE.id,
    ConfigSchema.Glass.MINGOU_LARGE_FOLDER_COMPAT.name() to FolderGlassCell.LARGE_MINGOU_COMPAT.id,
    ConfigSchema.Glass.OPEN_FOLDER_GLASS.name() to FolderGlassCell.OPEN_ENABLED.id,
    openFolderBlurSpec.key to FolderGlassCell.OPEN_BLUR.id,
    openFolderNativeBlurSpec.key to FolderGlassCell.OPEN_BLUR.id,
    openFolderIorSpec.key to FolderGlassCell.OPEN_OPTICS.id,
    openFolderThicknessSpec.key to FolderGlassCell.OPEN_OPTICS.id,
    openFolderTintSpec.key to FolderGlassCell.OPEN_OPTICS.id,
    openFolderBaseColorRedSpec.key to FolderGlassCell.OPEN_FILL_RGB.id,
    openFolderBaseColorGreenSpec.key to FolderGlassCell.OPEN_FILL_RGB.id,
    openFolderBaseColorBlueSpec.key to FolderGlassCell.OPEN_FILL_RGB.id,
    openFolderBaseColorAlphaSpec.key to FolderGlassCell.OPEN_FILL_ALPHA.id
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
            }
        }
        item(key = FolderGlassCell.LARGE_GEOMETRY.id) {
            SettingsCard {
                IntSetting(prefs, largeFolderSizeOffsetSpec, masterEnabled && liquidEnabled && largeFolderGlass)
                ModernListDivider()
                IntSetting(prefs, largeFolderCornerRadiusSpec, masterEnabled && liquidEnabled && largeFolderGlass)
            }
        }
        item(key = FolderGlassCell.LARGE_PREVIEW.id) {
            SettingsCard {
                IntSetting(prefs, largeFolderPreviewIconScaleSpec, masterEnabled)
            }
        }
        item(key = FolderGlassCell.LARGE_NATIVE_OUTLINE.id) {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE,
                    "显示原生大文件夹描边",
                    "关闭后移除系统默认灰黑色圆角描边，保留玻璃与背景效果",
                    masterEnabled,
                )
            }
        }
        item(key = FolderGlassCell.LARGE_MINGOU_COMPAT.id) {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.Glass.MINGOU_LARGE_FOLDER_COMPAT,
                    "绵狗修改版桌面适配",
                    "默认关闭。开启后对含 LiquidDock 标记且数据库保存为 2×2 的大文件夹恢复网格尺寸；不检测桌面版本。修改后重启桌面生效",
                    enabled = true, // This compatibility repair has its own master switch.
                )
            }
        }
        item(key = FolderGlassCell.OPEN_HEADING.id) { SmallTitle("展开文件夹") }
        item(key = FolderGlassCell.OPEN_ENABLED.id) {
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
        item(key = FolderGlassCell.OPEN_BLUR.id) {
            SettingsCard {
                IntSetting(prefs, openFolderBlurSpec, masterEnabled && liquidEnabled && openFolderGlass)
                ModernListDivider()
                IntSetting(prefs, openFolderNativeBlurSpec, masterEnabled && liquidEnabled && openFolderGlass)
            }
        }
        item(key = FolderGlassCell.OPEN_OPTICS.id) {
            SettingsCard {
                IntSetting(prefs, openFolderIorSpec, masterEnabled && liquidEnabled && openFolderGlass)
                ModernListDivider()
                IntSetting(prefs, openFolderThicknessSpec, masterEnabled && liquidEnabled && openFolderGlass)
                ModernListDivider()
                IntSetting(prefs, openFolderTintSpec, masterEnabled && liquidEnabled && openFolderGlass)
            }
        }
        item(key = FolderGlassCell.OPEN_FILL_HEADING.id) { SmallTitle("展开文件夹 · 底色（纯色覆盖）") }
        item(key = FolderGlassCell.OPEN_FILL_RGB.id) {
            SettingsCard {
                IntSetting(prefs, openFolderBaseColorRedSpec, masterEnabled && liquidEnabled && openFolderGlass)
                ModernListDivider()
                IntSetting(prefs, openFolderBaseColorGreenSpec, masterEnabled && liquidEnabled && openFolderGlass)
                ModernListDivider()
                IntSetting(prefs, openFolderBaseColorBlueSpec, masterEnabled && liquidEnabled && openFolderGlass)
            }
        }
        item(key = FolderGlassCell.OPEN_FILL_ALPHA.id) {
            SettingsCard {
                IntSetting(prefs, openFolderBaseColorAlphaSpec, masterEnabled && liquidEnabled && openFolderGlass)
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
