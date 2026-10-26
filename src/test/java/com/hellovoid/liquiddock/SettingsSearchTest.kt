package com.hellovoid.liquiddock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private val catalog = listOf(
        SettingsSearchItem(Page.LiquidColor, "色散强度", "玻璃颜色与色散", "liquid_chromatic"),
        SettingsSearchItem(Page.LiquidShadow, "内阴影透明度", "玻璃内部阴影", "liquid_shadow_alpha"),
        SettingsSearchItem(Page.DockGeometry, "Dock 图标间距", "Dock 尺寸及位置", "dock_spacing"),
    )

    @Test
    fun sidebarAndSecurityCenterBothResolveToUnifiedSettingsPage() {
        val items = listOf(
            SettingsSearchItem(Page.SidebarEdge, "侧边栏 / 安全中心", "独立玻璃背景与光照"),
            SettingsSearchItem(Page.SidebarEdge, "模糊强度", "侧边栏背景"),
        )
        assertEquals(Page.SidebarEdge, filterSettingsSearch(items, "侧边栏").first().page)
        assertEquals(Page.SidebarEdge, filterSettingsSearch(items, "安全中心").first().page)
    }

    @Test
    fun blankQueryDoesNotFloodResults() {
        assertTrue(filterSettingsSearch(catalog, "  ").isEmpty())
    }

    @Test
    fun matchesChineseTitleAndAsciiPreferenceKey() {
        assertEquals(Page.LiquidColor, filterSettingsSearch(catalog, "色散")[0].page)
        assertEquals(Page.DockGeometry, filterSettingsSearch(catalog, "dock_spacing")[0].page)
    }

    @Test
    fun combinesKeywordsAndPreservesDestination() {
        val result = filterSettingsSearch(catalog, "玻璃 阴影")
        assertEquals(1, result.size)
        assertEquals(Page.LiquidShadow, result.single().page)
    }

    @Test
    fun multipleMatchingOptionsAppearUnderOnePage() {
        val items = listOf(
            SettingsSearchItem(Page.LiquidColor, "玻璃颜色", "色散与底色"),
            SettingsSearchItem(Page.LiquidColor, "色散强度", "玻璃材质"),
            SettingsSearchItem(Page.LiquidColor, "色散蓝", "玻璃材质"),
            SettingsSearchItem(Page.LiquidRefraction, "玻璃折射", "几何设置"),
        )
        val results = searchSettingsPages(items, "色散")
        assertEquals(1, results.size)
        assertEquals("玻璃颜色", results.single().title)
        assertEquals(Page.LiquidColor, results.single().page)
        assertEquals(setOf("色散强度", "色散蓝"), results.single().matchedOptions.toSet())
    }

    @Test
    fun resultNamesArePagesNotRawOptions() {
        val items = listOf(
            SettingsSearchItem(Page.DockGeometry, "Dock 尺寸与位置", "功能页面"),
            SettingsSearchItem(Page.DockGeometry, "Dock 图标间距", "图标间距"),
        )
        val results = searchSettingsPages(items, "间距")
        assertEquals(1, results.size)
        assertEquals("Dock 尺寸与位置", results.single().title)
        assertTrue(results.single().description.contains("Dock 图标间距"))
    }

    @Test
    fun pullDownIsAvailableOnlyOnRootTabs() {
        assertEquals(
            listOf(Page.Home, Page.LayoutHub, Page.GlassHub, Page.MoreHub),
            Page.entries.filter(::isRootPage),
        )
        assertTrue(!isRootPage(Page.Grid))
        assertTrue(!isRootPage(Page.LiquidColor))
        assertTrue(!isRootPage(Page.DockGeometry))
    }

    @Test
    fun titleMatchPrecedesSummaryMatch() {
        val result = filterSettingsSearch(catalog, "色散")
        assertEquals("色散强度", result.first().title)
    }
}
