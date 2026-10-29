package com.hellovoid.liquiddock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Typed route/Cell contracts. Never scan production source to infer runtime behavior. */
class SettingsGuiOrganizationContractTest {
    private val declared get() = declaredSettingsPages { "resource:$it" }

    private val declaredRoutes = setOf(
        Page.LiquidMaterial, Page.LiquidRefraction, Page.LiquidColor,
        Page.LiquidLighting, Page.LiquidShadow, Page.LiquidSampling,
        Page.DockGeometry, Page.DockBehavior, Page.GridSplit,
        Page.GridLandscape, Page.GridPortrait,
        Page.WorkstationDock, Page.WorkstationAppsLandscape, Page.WorkstationAppsPortrait,
        Page.Recents, Page.AnimationWorkspace, Page.AnimationInteraction,
        Page.AnimationPopups, Page.AnimationSystem, Page.AnimationGui, Page.GlassMenus,
    ) + glassEdgePageScopes.keys

    private val customRoutes = setOf(
        Page.Home, Page.LayoutHub, Page.GlassHub, Page.MoreHub, Page.ThemePalette,
        Page.Grid, Page.GridBasics, Page.Dock, Page.DockRecentBlacklist,
        Page.Divider, Page.Workstation, Page.Liquid, Page.SidebarEdge,
        Page.GlassComponents, Page.GlassIcons, Page.GlassWidgets, Page.GlassFolders,
        Page.HandleMenuCustomization, Page.HandleMenuAll,
        Page.ShortcutPopupCustomization, Page.ShortcutPopupAll,
        Page.DialogCustomization, Page.DialogAll,
        Page.ThirdPartyApps, Page.Gboard, Page.GboardAll, Page.WidgetComponents,
        Page.LauncherHighlights, Page.LauncherHighlightsCompact,
        Page.LauncherHighlightsLarge, Page.Stroke, Page.Shadow,
        Page.SecurityCenterSidebar, Page.Animation, Page.Data, Page.About,
    )

    @Test fun everyPageHasExactlyOneDeclaredOrCustomGuiOwner() {
        assertEquals(declaredRoutes, declared.keys)
        assertTrue(declaredRoutes.intersect(customRoutes).isEmpty())
        assertEquals(Page.entries.toSet(), declaredRoutes + customRoutes)
        Page.entries.forEach { page ->
            val path = settingsPagePath(page)
            assertEquals(page, path.last())
            assertEquals(path.size, path.toSet().size)
        }
    }

    @Test fun allStaticCustomGlassRowsAreIndexedWithoutPhantomPreferences() {
        val customSearchPages = setOf(
            Page.GlassIcons, Page.GlassWidgets, Page.GlassFolders,
            Page.HandleMenuAll, Page.ShortcutPopupAll, Page.DialogAll, Page.GboardAll,
            Page.HandleMenuCustomization, Page.ShortcutPopupCustomization,
            Page.DialogCustomization, Page.Gboard, Page.GridBasics,
            Page.Divider, Page.Stroke, Page.Shadow,
        )
        val catalog = settingsSearchCatalog({ "resource:$it" }, declared)
        customSearchPages.forEach { page ->
            val rows = customGlassSearchRows(page)
            assertFalse("Missing custom catalog on $page", rows.isEmpty())
            assertEquals(rows.size, rows.map { it.key }.toSet().size)
            assertEquals(rows.map { it.key }.toSet(),
                catalog.filter { it.page == page && it.key != null }.map { it.key }.toSet())
            rows.forEach { row ->
                val ownerExists = when (page) {
                    Page.GlassFolders -> folderGlassFocusCell.containsKey(row.key)
                    Page.HandleMenuAll, Page.ShortcutPopupAll, Page.DialogAll, Page.GboardAll ->
                        row.key in customGlassSearchRows(page).map { it.key }
                    else -> customDenseFocusIndex(page, row.key) != null
                }
                assertTrue("Indexed setting without a navigable Cell: $page/${row.key}", ownerExists)
            }
        }
        // Dynamic widget catalog and app blacklist use page-level navigation rather
        // than inventing stable keys for a changing set of runtime entries.
        assertTrue(customGlassSearchRows(Page.WidgetComponents).isEmpty())
        assertTrue(customGlassSearchRows(Page.DockRecentBlacklist).isEmpty())
    }

    @Test fun semanticGlassCellsHaveBoundedSizeAndExactlyOneOwner() {
        val groups = liquidAllGroups
        assertTrue(groups.all { it.specs.isNotEmpty() && it.specs.size <= 3 })
        val allKeys = groups.flatMap { it.specs.map(IntSpec::key) }
        assertEquals(allKeys.size, allKeys.toSet().size)
        assertEquals(liquidSpecs.map(IntSpec::key).toSet(), allKeys.toSet())
        assertEquals(FolderGlassCell.entries.map { it.id }, folderGlassCellOrder)
        assertEquals(folderGlassCellOrder.size, folderGlassCellOrder.toSet().size)
    }
}
