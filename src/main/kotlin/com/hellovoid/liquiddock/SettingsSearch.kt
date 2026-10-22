package com.hellovoid.liquiddock

import android.content.Context
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
import androidx.compose.ui.focus.focusRequester
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

internal fun settingsSearchCatalog(context: Context): List<SettingsSearchItem> = buildList {
    val hubs = listOf(
        overviewEntries, layoutEntries, glassEntries, moreEntries,
        gridEntries, dockEntries, workstationEntries, liquidEntries,
        componentEntries, highlightEntries, animationEntries,
    ).flatten().associateBy(HubEntry::page)
    val scopeNames = mapOf(
        Page.DockEdge to "Dock", Page.IconEdge to "图标",
        Page.WidgetEdge to "小组件", Page.FolderEdge to "文件夹",
        Page.PopupEdge to "快捷菜单", Page.HandleMenuEdge to "顶部菜单",
        Page.DialogEdge to "对话弹窗", Page.GboardEdge to "Gboard",
        Page.SearchboxEdge to "系统搜索", Page.SecurityCenterEdge to "安全中心",
    )
    fun location(page: Page): String {
        val name = context.getString(page.titleRes)
        return scopeNames[page]?.let { it + " · " + name } ?: name
    }
    fun numbers(page: Page, specs: List<IntSpec>) {
        specs.forEach { spec ->
            add(SettingsSearchItem(page, spec.title, location(page) + " · " + spec.summary, spec.key))
        }
    }
    fun number(page: Page, spec: IntSpec) = numbers(page, listOf(spec))
    fun options(page: Page, vararg titles: String) {
        titles.forEach { add(SettingsSearchItem(page, it, location(page))) }
    }

    // All routes are searchable even when their controls are built dynamically.
    Page.entries.forEach { page ->
        add(SettingsSearchItem(page, location(page), hubs[page]?.summary ?: "配置页面"))
    }
    numbers(Page.GridBasics, gridDimensionSpecs)
    number(Page.GridBasics, launcher450IconSizeSpec)
    numbers(Page.GridLandscape, gridSpecs.filter { it.key.contains("landscape") })
    numbers(Page.GridPortrait, gridSpecs.filter { it.key.contains("portrait") })
    numbers(Page.GridSplit, splitGridSpecs)
    numbers(Page.DockGeometry, dockSpecs)
    numbers(Page.Divider, dividerSpecs)
    numbers(Page.WorkstationDock, workstationDockSpecs)
    numbers(Page.WorkstationDesktop, workstationDesktopSpecs)
    numbers(Page.WorkstationAppsLandscape, workstationAppsLandscapeSpecs)
    numbers(Page.WorkstationAppsPortrait, workstationAppsPortraitSpecs)
    number(Page.Recents, recentsBlurSpec)

    listOf(
        Page.LiquidMaterial to liquidMaterialSpecs,
        Page.LiquidRefraction to liquidRefractionSpecs,
        Page.LiquidColor to liquidColorSpecs,
        Page.LiquidLighting to (liquidLightingSpecs + liquidOs4Specs),
        Page.LiquidShadow to liquidShadowSpecs,
        Page.LiquidSampling to liquidSamplingSpecs,
    ).forEach { (page, specs) -> numbers(page, specs) }
    number(Page.LiquidSampling, passBlurCaptureScaleSpec)
    number(Page.LiquidSampling, passBlurRenderFpsSpec)
    numbers(Page.Stroke, strokeSpecs)
    numbers(Page.Shadow, shadowSpecs)

    listOf(iconSizeOffsetSpec, iconCornerRadiusSpec).forEach { number(Page.GlassIcons, it) }
    listOf(widgetSizeOffsetSpec, widgetCornerRadiusSpec).forEach { number(Page.GlassWidgets, it) }
    listOf(
        smallFolderSizeOffsetSpec, smallFolderCornerRadiusSpec,
        largeFolderSizeOffsetSpec, largeFolderCornerRadiusSpec,
        largeFolderPreviewIconScaleSpec, openFolderBlurSpec,
        openFolderNativeBlurSpec, openFolderIorSpec,
        openFolderThicknessSpec, openFolderTintSpec,
    ).forEach { number(Page.GlassFolders, it) }
    listOf(
        Page.AnimationWorkspace to listOf(
            animationWorkspaceVisibilitySpec, animationUnlockGlassFadeSpec,
            animationFolderOpenGlassFadeSpec,
            animationDockResizeSpec,
        ),
        Page.AnimationInteraction to listOf(animationPressInSpec, animationPressOutSpec),
        Page.AnimationPopups to listOf(animationShortcutDismissSpec, animationSecurityCenterExitSpec),
        Page.AnimationGui to listOf(animationSettingsPageSpec),
    ).forEach { (page, specs) -> numbers(page, specs) }

    options(Page.Home, "启用 LiquidDock")
    options(Page.MoreHub, "iOS 玻璃效果")
    options(Page.GridBasics, "自由主界面网格", "自定义图标大小", "小组件随水平边距拉伸")
    options(Page.DockBehavior, "Dock 自定义", "隐藏手机互联图标", "Dock 尺寸动画", "LiquidDock 顺滑尺寸动画")
    options(Page.Divider, "自定义 Dock 分隔线")
    options(Page.Recents, "禁用壁纸缩放", "取消壁纸压暗")
    options(Page.Liquid, "启用液态玻璃", "Dock 帧同步")
    options(Page.LiquidSampling, "OS4 法线与方向光可视化")
    options(Page.GlassIcons, "图标玻璃", "仅 Dock 功能图标玻璃", "多任务操作按钮玻璃")
    options(Page.GlassWidgets, "小部件玻璃", "小组件深色内容适配", "切换部件白化")
    options(Page.GlassFolders, "小文件夹玻璃", "大文件夹玻璃", "展开文件夹玻璃背景", "显示原生大文件夹描边", "绵狗修改版桌面适配", "壁纸闪烁修复")
    options(Page.Stroke, "显示完整描边", "方圆形连续曲线", "Fill-Diff 描边")
    options(Page.Shadow, "Dock 原生阴影", "原生描边阴影")
    options(Page.Gboard, "Gboard 玻璃", "实时背景采样")
    options(Page.ShortcutPopupCustomization, "快捷菜单深色模式适配")
    options(Page.DialogCustomization, "启用桌面对话弹窗玻璃", "关闭对话时背景压暗", "对话框深色模式")
    options(Page.AnimationSystem, "桌面/多任务隐藏手势小白条")
    options(Page.SecurityCenterSidebar, "安全中心玻璃")
    options(Page.About, "诊断日志")

    val scopedPages = listOf(Page.GboardAll, Page.ShortcutPopupAll, Page.HandleMenuAll, Page.DialogAll)
    scopedPages.forEach { page ->
        scopedOpticalDescriptors.forEach { descriptor ->
            add(SettingsSearchItem(
                page, descriptor.title,
                location(page) + " · 独立光学参数",
                descriptor.config.name(),
            ))
        }
    }
    val edgeTitles = listOf(
        "启用折射光照描边", "描边不透明度", "细线宽度",
        "暗色区延伸范围", "明暗过渡柔和度", "启用整体外阴影",
        "阴影柔和半径", "基础垂直偏移", "方向光响应",
        "阴影不透明度", "阴影红", "阴影绿", "阴影蓝",
    )
    (listOf(Page.GlassEdge) + scopeNames.keys).forEach { page ->
        edgeTitles.forEach { title -> add(SettingsSearchItem(page, title, location(page))) }
    }
}.distinctBy { Triple(it.page, it.title, it.key) }

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

/** Search is indexed by option but presented once per target page. */
internal data class SettingsSearchPageResult(
    val page: Page,
    val title: String,
    val description: String,
    val matchedOptions: List<String>,
)

internal fun searchSettingsPages(
    catalog: List<SettingsSearchItem>,
    query: String,
): List<SettingsSearchPageResult> {
    val routes = catalog.groupBy(SettingsSearchItem::page).mapValues { it.value.first() }
    return filterSettingsSearch(catalog, query)
        .groupBy(SettingsSearchItem::page)
        .map { (page, matches) ->
            val route = requireNotNull(routes[page])
            val labels = matches.filter { it !== route }
                .map(SettingsSearchItem::title).distinct()
            val detail = if (labels.isEmpty()) route.description
                else "匹配：" + labels.take(3).joinToString("、") +
                    (if (labels.size > 3) " 等 ${labels.size} 项" else "")
            SettingsSearchPageResult(page, route.title, detail, labels)
        }
        .sortedWith(compareBy<SettingsSearchPageResult> { result ->
            val needle = query.trim().lowercase(Locale.ROOT)
            val title = result.title.lowercase(Locale.ROOT)
            when {
                title == needle -> 0
                title.startsWith(needle) -> 1
                title.contains(needle) -> 2
                result.matchedOptions.any { it.equals(needle, true) } -> 3
                else -> 4
            }
        }.thenBy { it.title }).take(40)
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
    val results = remember(query, catalog) { searchSettingsPages(catalog, query) }
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
                item(key = "search-count") { ModernSectionLabel("相关页面 · " + results.size) }
                items(results, key = { it.page.name }) { result ->
                    ModernFeatureCard(
                        title = result.title,
                        summary = result.description,
                        page = result.page,
                        modifier = Modifier.padding(horizontal = 14.dp),
                        onClick = {
                            keyboard?.hide()
                            onSelect(catalog.first { it.page == result.page })
                        },
                    )
                }
            }
        }
    }
}
