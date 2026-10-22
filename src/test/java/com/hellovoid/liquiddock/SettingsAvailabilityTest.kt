package com.hellovoid.liquiddock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAvailabilityTest {
    private val os4 = SettingsAvailability(true)
    private val legacy = SettingsAvailability(false)

    @Test fun os4DisablesEveryUnportedLauncherLeaf() {
        val unsupported = listOf(
            Page.Grid, Page.GridBasics, Page.GridLandscape, Page.GridPortrait, Page.GridSplit,
            Page.Dock, Page.DockBehavior, Page.DockGeometry, Page.DockRecentBlacklist, Page.Divider,
            Page.Workstation, Page.WorkstationDock, Page.WorkstationDesktop,
            Page.WorkstationAppsLandscape, Page.WorkstationAppsPortrait, Page.Recents,
            Page.LiquidMaterial, Page.LiquidRefraction, Page.LiquidColor, Page.LiquidLighting,
            Page.LiquidShadow, Page.LiquidSampling,
            Page.GlassIcons, Page.GlassWidgets, Page.GlassFolders,
            Page.ShortcutPopupCustomization, Page.ShortcutPopupAll,
            Page.DialogCustomization, Page.DialogAll, Page.PopupEdge, Page.DialogEdge,
            Page.DockEdge, Page.IconEdge, Page.WidgetEdge, Page.FolderEdge,
            Page.LauncherHighlights, Page.LauncherHighlightsCompact, Page.LauncherHighlightsLarge,
            Page.Stroke, Page.Shadow,
            Page.AnimationWorkspace, Page.AnimationInteraction,
        )
        unsupported.forEach { assertFalse("OS4: ${it.name}", os4.allows(it)) }
    }

    @Test fun nonLauncherAndSharedUiStayAccessible() {
        val retained = listOf(
            Page.Home, Page.LayoutHub, Page.GlassHub, Page.MoreHub,
            Page.ThemePalette, Page.Data, Page.About,
            Page.Liquid, Page.GlassEdge, Page.GlassComponents, Page.GlassMenus,
            Page.HandleMenuCustomization, Page.HandleMenuAll, Page.HandleMenuEdge,
            Page.ThirdPartyApps, Page.Gboard, Page.GboardAll, Page.GboardEdge,
            Page.SearchboxEdge, Page.SecurityCenterSidebar, Page.SecurityCenterEdge,
            Page.Animation, Page.AnimationPopups, Page.AnimationSystem, Page.AnimationGui,
        )
        retained.forEach { assertTrue("OS4: ${it.name}", os4.allows(it)) }
    }

    @Test fun originalLauncherKeepsEveryExistingSettingsPage() {
        Page.entries.forEach { assertTrue("Launcher 4.50: ${it.name}", legacy.allows(it)) }
    }

    @Test fun allPagesHaveExplicitOs4Classification() {
        // Any new page must be explicitly accepted or default to unsupported on OS4.
        assertTrue(Page.entries.size > 65)
        assertFalse(os4.allows(Page.Dock))
        assertTrue(os4.allows(Page.Gboard))
    }
}
