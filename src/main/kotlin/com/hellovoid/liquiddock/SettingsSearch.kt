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


/** Search entries for custom glass pages, derived from their existing rendered descriptors. */
internal data class CustomGlassSearchRow(val key: String, val title: String, val summary: String)

internal val expandedFolderSearchSpecs: List<IntSpec> = listOf(
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
    if (page == Page.GlassFolders) {
        return expandedFolderSearchSpecs.map { spec ->
            CustomGlassSearchRow(spec.key, spec.title, spec.summary)
        }
    }
    return emptyList()
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
