package com.hellovoid.liquiddock

import android.content.Context
import com.hellovoid.liquiddock.config.ConfigSchema
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * A read-only navigation catalog. Editing remains on the existing page, using
 * its original dependency checks, validation and preference callbacks.
 */
internal data class SettingsSearchItem(
    val page: Page,
    val title: String,
    val description: String,
    val key: String? = null,
)

internal fun settingsSearchCatalog(context: Context): List<SettingsSearchItem> =
    settingsSearchCatalog(context::getString, declaredSettingsPages(context::getString))

internal fun settingsSearchCatalog(
    text: (Int) -> String,
    pages: Map<Page, SettingsMeta>,
): List<SettingsSearchItem> = buildList {
    fun title(page: Page) = pages[page]?.title ?: text(page.titleRes)
    Page.entries.forEach { page ->
        val path = settingsPagePath(page).joinToString(" › ") { title(it) }
        add(SettingsSearchItem(page, title(page), path))
        // Custom pages reuse the exact ConfigKey/IntSpec catalog that renders their
        // sliders. Only searchable rows with a real preference key are indexed.
        customGlassSearchRows(page).forEach { row ->
            add(SettingsSearchItem(page, row.title,
                path + if (row.summary.isBlank()) "" else " · " + row.summary,
                row.key))
        }
        pages[page]?.blocks?.filterIsInstance<SettingsBlock.Row>()?.forEach { row ->
            add(SettingsSearchItem(page, row.title,
                path + if (row.summary.isBlank()) "" else " · " + row.summary, row.key))
        }
    }
}

/** Search entries for custom settings pages, derived from their existing rendered descriptors. */
internal data class CustomGlassSearchRow(val key: String, val title: String, val summary: String)

internal val expandedFolderSearchSpecs: List<IntSpec> = listOf(
    smallFolderSizeOffsetSpec, smallFolderCornerRadiusSpec,
    largeFolderSizeOffsetSpec, largeFolderCornerRadiusSpec,
    largeFolderPreviewIconScaleSpec,
    openFolderBlurSpec, openFolderNativeBlurSpec, openFolderIorSpec,
    openFolderThicknessSpec, openFolderTintSpec,
    openFolderBaseColorRedSpec, openFolderBaseColorGreenSpec,
    openFolderBaseColorBlueSpec, openFolderBaseColorAlphaSpec,
)

/** Custom page search never creates a focus key for an item not rendered by that page. */
internal fun customGlassSearchRows(page: Page): List<CustomGlassSearchRow> {
    val scope = when (page) {
        Page.HandleMenuAll -> ScopedGlassOptics.HANDLE_MENU
        Page.ShortcutPopupAll -> ScopedGlassOptics.POPUP
        Page.DialogAll -> ScopedGlassOptics.DIALOG
        Page.GboardAll -> ScopedGlassOptics.GBOARD
        else -> null
    }
    if (scope != null) {
        val basics = basicScopeSpecs(scope).map {
            CustomGlassSearchRow(it.key, it.title, "独立设置；未修改时继承全局玻璃")
        }
        val optics = scopedOpticalDescriptors.map { descriptor ->
            CustomGlassSearchRow(ScopedGlassOptics.key(scope, descriptor.config),
                descriptor.title, descriptor.summary)
        }
        return basics + optics
    }
    if (page == Page.GlassHub) {
        return listOf(CustomGlassSearchRow(
            ConfigSchema.Glass.CONTINUOUS_CORNERS.name(), "iPadOS 风格连续圆角",
            "Dock、小组件、文件夹、胶囊与菜单"))
    }
    if (page == Page.GlassFolders) {
        val toggles = listOf(
            CustomGlassSearchRow(ConfigSchema.Glass.SMALL_FOLDER_GLASS.name(), "小文件夹玻璃", ""),
            CustomGlassSearchRow(ConfigSchema.Glass.LARGE_FOLDER_GLASS.name(), "大文件夹玻璃", ""),
            CustomGlassSearchRow(ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.name(),
                "显示原生大文件夹描边", ""),
            CustomGlassSearchRow(ConfigSchema.Glass.MINGOU_LARGE_FOLDER_COMPAT.name(),
                "绵狗修改版桌面适配", ""),
            CustomGlassSearchRow(ConfigSchema.Glass.OPEN_FOLDER_GLASS.name(),
                "展开文件夹玻璃背景", ""),
        )
        return toggles + expandedFolderSearchSpecs.map { spec ->
            CustomGlassSearchRow(spec.key, spec.title, spec.summary)
        }
    }

    fun toggle(config: com.hellovoid.liquiddock.config.ConfigKey<Boolean>, title: String) =
        CustomGlassSearchRow(config.name(), title, "")
    fun number(spec: IntSpec) = CustomGlassSearchRow(spec.key, spec.title, spec.summary)
    fun row(key: String, title: String) = CustomGlassSearchRow(key, title, "")
    fun appearance(scope: String): List<CustomGlassSearchRow> = listOf(
        row(ScopedGlassOptics.key(scope, ConfigSchema.Glass.TINT_RED), "染色 · 红"),
        row(ScopedGlassOptics.key(scope, ConfigSchema.Glass.TINT_GREEN), "染色 · 绿"),
        row(ScopedGlassOptics.key(scope, ConfigSchema.Glass.TINT_BLUE), "染色 · 蓝"),
        row(ScopedGlassOptics.key(scope, ConfigSchema.Glass.TINT_ALPHA), "染色强度"),
        row(ScopedGlassOptics.key(scope, ConfigSchema.Glass.BLUR), "玻璃模糊"),
    )
    val rows = when (page) {
        Page.ShortcutPopupCustomization -> listOf(
            toggle(ConfigSchema.Glass.SHORTCUT_POPUP_GLASS, "启用快捷菜单玻璃"),
            toggle(ConfigSchema.Glass.SHORTCUT_POPUP_DARK_TEXT, "快捷菜单深色模式适配"),
            row(ConfigSchema.Glass.SHORTCUT_POPUP_CORNER_RADIUS.name(), "菜单圆角"),
        ) + appearance(ScopedGlassOptics.POPUP)
        Page.HandleMenuCustomization -> listOf(
            toggle(ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS, "启用应用顶部菜单玻璃"),
            row(ConfigSchema.Glass.HANDLE_MENU_CORNER_RADIUS.name(), "菜单圆角"),
        ) + appearance(ScopedGlassOptics.HANDLE_MENU)
        Page.DialogCustomization -> listOf(
            toggle(ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS, "启用桌面对话弹窗玻璃"),
            toggle(ConfigSchema.Glass.DIALOG_DISABLE_DIMMING, "关闭对话时背景压暗"),
            toggle(ConfigSchema.Glass.DIALOG_DARK_MODE, "对话框深色模式"),
            row(ConfigSchema.Glass.DIALOG_CORNER_RADIUS.name(), "对话弹窗圆角"),
            row(ConfigSchema.Glass.DIALOG_TINT_RED.name(), "染色 · 红"),
            row(ConfigSchema.Glass.DIALOG_TINT_GREEN.name(), "染色 · 绿"),
            row(ConfigSchema.Glass.DIALOG_TINT_BLUE.name(), "染色 · 蓝"),
            row(ConfigSchema.Glass.DIALOG_TINT_ALPHA.name(), "染色强度"),
            row(ConfigSchema.Glass.DIALOG_BLUR.name(), "对话背景模糊"),
        )
        Page.Gboard -> listOf(
            row(GboardGlassPreferences.ENABLED_KEY, "启用悬浮键盘液态玻璃"),
            row(GboardGlassPreferences.AUTO_RESIZE_AFTER_HANDLE_DRAG_KEY, "拖动后自动进入大小调整"),
            row(GboardGlassPreferences.REALTIME_BACKGROUND_SAMPLING_KEY, "实时背景采样"),
            row(GboardGlassPreferences.TINT_RED_KEY, "染色 · 红"),
            row(GboardGlassPreferences.TINT_GREEN_KEY, "染色 · 绿"),
            row(GboardGlassPreferences.TINT_BLUE_KEY, "染色 · 蓝"),
            row(GboardGlassPreferences.TINT_ALPHA_KEY, "染色强度"),
            row(GboardGlassPreferences.BLUR_KEY, "玻璃模糊"),
        )
        Page.GridBasics -> listOf(
            toggle(ConfigSchema.Grid.ICON_SIZE_ENABLED, "自定义图标大小"),
            number(launcher450IconSizeSpec),
            toggle(ConfigSchema.Grid.ENABLED, "自由主界面网格"),
        ) + gridDimensionSpecs.map(::number) + listOf(
            toggle(ConfigSchema.Grid.WIDGET_HORIZONTAL_STRETCH, "小组件随水平边距拉伸"))
        Page.GlassIcons -> listOf(
            toggle(ConfigSchema.Glass.ICON_GLASS, "图标玻璃"),
            toggle(ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS, "仅 Dock 功能图标玻璃"),
            toggle(ConfigSchema.Glass.RECENTS_CAPSULE_GLASS, "多任务操作按钮玻璃"),
            number(iconSizeOffsetSpec), number(iconCornerRadiusSpec),
        )
        Page.GlassWidgets -> listOf(
            toggle(ConfigSchema.Glass.WIDGET_GLASS, "小部件玻璃"),
            toggle(ConfigSchema.Glass.WIDGET_DARK_CONTENT, "小组件深色内容适配"),
            number(widgetSizeOffsetSpec), number(widgetCornerRadiusSpec),
        )
        Page.Divider -> listOf(toggle(ConfigSchema.Divider.ENABLED, "自定义 Dock 分隔线")) +
            dividerSpecs.map(::number)
        Page.Stroke -> listOf(
            toggle(ConfigSchema.Dock.STROKE_ENABLED, "显示完整描边"),
            toggle(ConfigSchema.Dock.SQUIRCLE, "方圆形连续曲线"),
            toggle(ConfigSchema.Dock.FILL_DIFF, "Fill-Diff 描边"),
        ) + strokeSpecs.map(::number)
        Page.Shadow -> listOf(
            toggle(ConfigSchema.Dock.SHADOW_ENABLED, "Dock 原生阴影"),
            toggle(ConfigSchema.Dock.STROKE_SHADOW, "原生描边阴影"),
        ) + shadowSpecs.map(::number)
        else -> emptyList()
    }
    return rows
}

/**
 * Position of a real lazy-list Cell for each indexed control on the fixed-size
 * component pages. The three-control Cell remains a single visual group.
 */
internal fun customDenseFocusIndex(page: Page, key: String): Int? {
    val index = when (page) {
        Page.GlassHub -> if (key == ConfigSchema.Glass.CONTINUOUS_CORNERS.name()) 1 else -1
        Page.ShortcutPopupCustomization, Page.HandleMenuCustomization -> {
            val popup = page == Page.ShortcutPopupCustomization
            val scope = if (popup) ScopedGlassOptics.POPUP else ScopedGlassOptics.HANDLE_MENU
            when (key) {
                (if (popup) ConfigSchema.Glass.SHORTCUT_POPUP_GLASS else
                    ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS).name() -> 2
                ConfigSchema.Glass.SHORTCUT_POPUP_DARK_TEXT.name() -> if (popup) 2 else -1
                (if (popup) ConfigSchema.Glass.SHORTCUT_POPUP_CORNER_RADIUS else
                    ConfigSchema.Glass.HANDLE_MENU_CORNER_RADIUS).name() -> 4
                ScopedGlassOptics.key(scope, ConfigSchema.Glass.TINT_RED),
                ScopedGlassOptics.key(scope, ConfigSchema.Glass.TINT_GREEN),
                ScopedGlassOptics.key(scope, ConfigSchema.Glass.TINT_BLUE) -> 6
                ScopedGlassOptics.key(scope, ConfigSchema.Glass.TINT_ALPHA) -> 7
                ScopedGlassOptics.key(scope, ConfigSchema.Glass.BLUR) -> 9
                else -> -1
            }
        }
        Page.DialogCustomization -> when (key) {
            ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS.name(),
            ConfigSchema.Glass.DIALOG_DISABLE_DIMMING.name(),
            ConfigSchema.Glass.DIALOG_DARK_MODE.name() -> 2
            ConfigSchema.Glass.DIALOG_CORNER_RADIUS.name() -> 4
            ConfigSchema.Glass.DIALOG_TINT_RED.name(),
            ConfigSchema.Glass.DIALOG_TINT_GREEN.name(),
            ConfigSchema.Glass.DIALOG_TINT_BLUE.name() -> 6
            ConfigSchema.Glass.DIALOG_TINT_ALPHA.name() -> 7
            ConfigSchema.Glass.DIALOG_BLUR.name() -> 9
            else -> -1
        }
        Page.Gboard -> when (key) {
            GboardGlassPreferences.ENABLED_KEY,
            GboardGlassPreferences.AUTO_RESIZE_AFTER_HANDLE_DRAG_KEY -> 2
            GboardGlassPreferences.REALTIME_BACKGROUND_SAMPLING_KEY -> 4
            GboardGlassPreferences.TINT_RED_KEY,
            GboardGlassPreferences.TINT_GREEN_KEY,
            GboardGlassPreferences.TINT_BLUE_KEY -> 6
            GboardGlassPreferences.TINT_ALPHA_KEY -> 7
            GboardGlassPreferences.BLUR_KEY -> 9
            else -> -1
        }
        Page.GridBasics -> when (key) {
            ConfigSchema.Grid.ICON_SIZE_ENABLED.name() -> 2
            launcher450IconSizeSpec.key -> 3
            ConfigSchema.Grid.ENABLED.name() -> 5
            ConfigSchema.Grid.WIDGET_HORIZONTAL_STRETCH.name() -> 7 + gridDimensionSpecs.size
            else -> gridDimensionSpecs.indexOfFirst { it.key == key }
                .takeIf { it >= 0 }?.plus(6) ?: -1
        }
        Page.GlassIcons -> when (key) {
            ConfigSchema.Glass.ICON_GLASS.name(),
            ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.name(),
            ConfigSchema.Glass.RECENTS_CAPSULE_GLASS.name() -> 2
            iconSizeOffsetSpec.key, iconCornerRadiusSpec.key -> 4
            else -> -1
        }
        Page.GlassWidgets -> when (key) {
            ConfigSchema.Glass.WIDGET_GLASS.name() -> 2
            ConfigSchema.Glass.WIDGET_DARK_CONTENT.name() -> 3
            widgetSizeOffsetSpec.key, widgetCornerRadiusSpec.key -> 5
            else -> -1
        }
        Page.Divider -> if (key == ConfigSchema.Divider.ENABLED.name()) 0
            else dividerSpecs.indexOfFirst { it.key == key }.takeIf { it >= 0 }?.plus(1) ?: -1
        Page.Stroke -> when (key) {
            ConfigSchema.Dock.STROKE_ENABLED.name() -> 0
            ConfigSchema.Dock.SQUIRCLE.name() -> 1
            ConfigSchema.Dock.FILL_DIFF.name() -> 2
            else -> {
                val background = strokeSpecs.filter { it.section == IntSection.StrokeBackground }
                val geometry = strokeSpecs.filter { it.section == IntSection.StrokeGeometry }
                val b = background.indexOfFirst { it.key == key }
                if (b >= 0) 4 + b else geometry.indexOfFirst { it.key == key }
                    .takeIf { it >= 0 }?.plus(5 + background.size) ?: -1
            }
        }
        Page.Shadow -> when (key) {
            ConfigSchema.Dock.SHADOW_ENABLED.name() -> 1
            ConfigSchema.Dock.STROKE_SHADOW.name() -> 3
            else -> shadowSpecs.indexOfFirst { it.key == key }
                .takeIf { it >= 0 }?.plus(4) ?: -1
        }
        else -> -1
    }
    return index.takeIf { it >= 0 }
}

internal fun filterSettingsSearch(
    catalog: List<SettingsSearchItem>,
    raw: String,
): List<SettingsSearchItem> {
    val query = raw.trim().lowercase(Locale.ROOT)
    if (query.isEmpty()) return emptyList()
    val words = query.split(Regex("\\s+")).filter(String::isNotBlank)
    return catalog.asSequence().filter { item ->
        val haystack = (item.title + " " + item.description + " " + item.key.orEmpty())
            .lowercase(Locale.ROOT)
        words.all { it in haystack }
    }.sortedWith(compareBy<SettingsSearchItem> { item ->
        val title = item.title.lowercase(Locale.ROOT)
        when {
            title == query -> 0
            title.startsWith(query) -> 1
            title.contains(query) -> 2
            else -> 3
        }
    }.thenBy { it.title.length }).toList()
}

@Composable
internal fun SettingsSearchScreen(
    padding: PaddingValues,
    query: String,
    onQueryChange: (String) -> Unit,
    catalog: List<SettingsSearchItem>,
    onSelect: (SettingsSearchItem) -> Unit,
    focusOnOpen: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusOnOpen) {
        if (focusOnOpen) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    val results = remember(query, catalog) { filterSettingsSearch(catalog, query) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "settings-search-input") {
            BakedPrismalSearchField(
                query = query, onQueryChange = onQueryChange, focus = focus,
            )
        }
        when {
            query.isBlank() -> item(key = "search-hint") {
                PageHeader("", "输入设置名称、关键词或参数名称。")
            }
            results.isEmpty() -> item(key = "search-empty") {
                PageHeader("", "没有匹配的设置，请换一个关键词。")
            }
            else -> {
                item(key = "search-count") { ModernSectionLabel("搜索结果 · " + results.size) }
                items(results, key = { "${it.page.name}:${it.key ?: "page"}" }) { result ->
                    ModernFeatureCard(
                        title = result.title,
                        summary = result.description,
                        modifier = Modifier.padding(horizontal = 14.dp),
                        onClick = {
                            keyboard?.hide()
                            onSelect(result)
                        },
                    )
                }
            }
        }
    }
}
