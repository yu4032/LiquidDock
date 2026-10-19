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
    fun titleMatchPrecedesSummaryMatch() {
        val result = filterSettingsSearch(catalog, "色散")
        assertEquals("色散强度", result.first().title)
    }
}
