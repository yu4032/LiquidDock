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
            Page.Dock, Page.DockGeometry, Page.Divider,
            Page.SoftGlass, Page.SoftGlassMaterial,
            Page.SoftGlassColor, Page.SoftGlassLighting,
            Page.SoftGlassComponents, Page.WidgetComponents,
            Page.Recents,
        )
        testing.forEach {
            assertEquals("OS4 test route ${it.name}", PagePortState.TESTING, os4.status(it))
            assertTrue("Open for testing ${it.name}", os4.allows(it))
        }
    }

    @Test fun unrelatedLauncherFeaturesStillGrayOut() {
        val unavailable = listOf(
            Page.DockBehavior,
            Page.DockRecentBlacklist,
            Page.Workstation, Page.WorkstationDock,
            Page.WorkstationAppsLandscape, Page.WorkstationAppsPortrait,
            Page.GlassIcons, Page.GlassWidgets, Page.GlassFolders,
            Page.ShortcutPopupCustomization, Page.ShortcutPopupAll,
            Page.DialogCustomization, Page.DialogAll, Page.PopupEdge, Page.DialogEdge,
            Page.DockEdge, Page.IconEdge, Page.WidgetEdge, Page.FolderEdge,
            Page.LauncherHighlights, Page.LauncherHighlightsCompact,
            Page.LauncherHighlightsLarge,
        )
        unavailable.forEach {
            assertEquals("OS4 unavailable ${it.name}", PagePortState.UNAVAILABLE, os4.status(it))
            assertFalse("Blocked ${it.name}", os4.allows(it))
        }
    }

    @Test fun independentScopesAndSettingsChromeRemainReady() {
        val ready = listOf(
            Page.Home, Page.LayoutHub, Page.GlassHub, Page.MoreHub,
            Page.ThemePalette, Page.Data, Page.About,
            Page.Liquid, Page.LiquidMaterial, Page.LiquidColor,
            Page.LiquidLighting, Page.LiquidSampling, Page.GlassEdge,
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
        // WidgetComponents was explicitly enrolled in the OS4 testing routes.
        assertEquals(PagePortState.TESTING, os4.status(Page.WidgetComponents))
        assertTrue(os4.allows(Page.WidgetComponents))
        // Unported folder customization must still remain gated.
        assertEquals(PagePortState.UNAVAILABLE, os4.status(Page.GlassFolders))
        assertFalse(os4.allows(Page.GlassFolders))
    }

    @Test fun availablePagesAreTheUnionOfReadyAndTesting() {
        Page.entries.forEach { page ->
            assertEquals(os4.status(page) != PagePortState.UNAVAILABLE, os4.allows(page))
        }
    }
}
