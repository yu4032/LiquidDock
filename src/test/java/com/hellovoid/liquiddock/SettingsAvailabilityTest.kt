package com.hellovoid.liquiddock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAvailabilityTest {
    private val os4 = SettingsAvailability(true)
    private val legacy = SettingsAvailability(false)

    @Test fun os4PortPagesAreNavigableForTestingWithoutBeingMarkedReady() {
        val testing = listOf(
            Page.Grid, Page.GridBasics, Page.GridLandscape, Page.GridPortrait,
            Page.Dock, Page.DockGeometry,
            Page.Divider,
            Page.SoftGlass, Page.SoftGlassMaterial,
            Page.SoftGlassColor, Page.SoftGlassLighting,
            Page.SoftGlassComponents,
            Page.SoftGlassDock, Page.SoftGlassIcons, Page.SoftGlassWidgets, Page.SoftGlassLargeFolder,
            Page.WidgetComponents,
            Page.GlassIcons, Page.GlassWidgets, Page.GlassFolders,
            Page.IconEdge,
            Page.Recents,
            Page.WorkstationDock, Page.WorkstationDesktop, Page.WorkstationAppsLandscape, Page.WorkstationAppsPortrait,
        )
        testing.forEach {
            assertEquals("OS4 test route ${it.name}", PagePortState.TESTING, os4.status(it))
            assertTrue("Open for testing ${it.name}", os4.allows(it))
        }
    }

    @Test fun unrelatedLauncherFeaturesStillGrayOut() {
        val grayedOut = setOf(
            Page.DockBehavior, Page.DockRecentBlacklist,
            Page.DockEdge, Page.WidgetEdge, Page.FolderEdge,
            Page.LauncherHighlights, Page.LauncherHighlightsCompact, Page.LauncherHighlightsLarge,
            Page.ShortcutPopupCustomization, Page.ShortcutPopupAll, Page.PopupEdge,
            Page.DialogCustomization, Page.DialogAll, Page.DialogEdge,
        )
        grayedOut.forEach {
            assertEquals("Grayed out in OS4 ${it.name}", PagePortState.UNAVAILABLE, os4.status(it))
            assertFalse("Disabled in OS4 ${it.name}", os4.allows(it))
        }
        Page.entries.filterNot { it in grayedOut }.forEach {
            assertTrue("Open in OS4 ${it.name}", os4.allows(it))
        }
    }

    @Test fun independentScopesAndSettingsChromeRemainReady() {
        val ready = listOf(
            Page.Home, Page.LayoutHub, Page.GlassHub, Page.MoreHub,
            Page.ThemePalette, Page.Data, Page.About,
            Page.Liquid, Page.LiquidMaterial, Page.LiquidRefraction, Page.LiquidColor,
            Page.LiquidLighting, Page.LiquidShadow, Page.LiquidSampling, Page.GlassEdge,
            Page.GlassComponents, Page.GlassMenus, Page.SidebarEdge,
            Page.HandleMenuCustomization, Page.HandleMenuAll, Page.HandleMenuEdge,
            Page.ThirdPartyApps, Page.Gboard, Page.GboardAll, Page.GboardEdge,
            Page.SearchboxEdge, Page.SecurityCenterEdge,
            Page.AnimationSystem,
        )
        ready.forEach {
            assertEquals("OS4 available ${it.name}", PagePortState.READY, os4.status(it))
            assertTrue(os4.allows(it))
        }
    }

    @Test fun olderLauncherRetainsAllOriginalPages() {
        Page.entries.forEach {
            assertEquals("Launcher 4.50 ${it.name}", PagePortState.READY, legacy.status(it))
        }
    }

    @Test fun futureRoutesFailClosedUnlessExplicitlyEnrolled() {
        assertEquals(PagePortState.TESTING, os4.status(Page.WidgetComponents))
        assertTrue(os4.allows(Page.WidgetComponents))
        assertEquals(PagePortState.TESTING, os4.status(Page.GlassFolders))
        assertTrue(os4.allows(Page.GlassFolders))
    }

    @Test fun availablePagesAreTheUnionOfReadyAndTesting() {
        Page.entries.forEach { page ->
            assertEquals(os4.status(page) != PagePortState.UNAVAILABLE, os4.allows(page))
        }
    }
}
