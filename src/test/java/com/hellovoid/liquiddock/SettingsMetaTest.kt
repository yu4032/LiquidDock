package com.hellovoid.liquiddock

import org.junit.Assert.*
import org.junit.Test

class SettingsMetaTest {
    private val pages get() = declaredSettingsPages { "resource:$it" }

    @Test fun offscreenShadowUsesDeclaredPositionAndScopedKey() {
        val global = pages.getValue(Page.GlassEdge)
        val dock = pages.getValue(Page.DockEdge)
        val red = "liquid_outer_shadow_r"
        assertTrue(global.indexOfKey(red) > 10)
        assertEquals(-1, dock.indexOfKey(red))
        val scoped = GlassEdgeEffects.key(GlassEdgeEffects.DOCK, com.hellovoid.liquiddock.config.ConfigSchema.Glass.OUTER_SHADOW_RED)
        assertTrue(dock.indexOfKey(scoped) > 10)
        assertEquals(scoped, (dock.blocks[dock.indexOfKey(scoped)] as SettingsBlock.Row).key)
    }

    @Test fun optionCatalogContainsOnlyDeclaredRowsWithResolvablePositions() {
        val catalog = settingsSearchCatalog({ "resource:$it" }, pages)
        catalog.filter { it.key != null }.forEach { item ->
            val declared = pages[item.page]
            if (declared != null) {
                val row = declared.blocks[declared.indexOfKey(item.key!!)] as SettingsBlock.Row
                assertEquals(row.title, item.title)
            } else {
                val row = customGlassSearchRows(item.page).single { it.key == item.key }
                assertEquals(row.title, item.title)
            }
        }
        assertFalse(catalog.any { it.title == "iOS 玻璃效果" })
        assertTrue(catalog.any { it.page == Page.GlassFolders &&
            it.key == openFolderBaseColorAlphaSpec.key })
        assertTrue(catalog.any { it.page == Page.ShortcutPopupAll &&
            it.key == com.hellovoid.liquiddock.ScopedGlassOptics.key(
                com.hellovoid.liquiddock.ScopedGlassOptics.POPUP,
                com.hellovoid.liquiddock.config.ConfigSchema.Glass.BASE_COLOR_ALPHA) })
    }

    @Test fun allScopedGlassRowsAreIndexedUsingTheirRenderedPreferenceKeys() {
        val scopes = mapOf(
            Page.HandleMenuAll to ScopedGlassOptics.HANDLE_MENU,
            Page.ShortcutPopupAll to ScopedGlassOptics.POPUP,
            Page.DialogAll to ScopedGlassOptics.DIALOG,
            Page.GboardAll to ScopedGlassOptics.GBOARD,
        )
        val catalog = settingsSearchCatalog({ "resource:$it" }, pages)
        scopes.forEach { (page, scope) ->
            val entries = customGlassSearchRows(page)
            assertEquals(entries.size, entries.map { it.key }.toSet().size)
            assertEquals(basicScopeSpecs(scope).size + scopedOpticalDescriptors.size, entries.size)
            assertEquals(entries.map { it.key }.toSet(),
                catalog.filter { it.page == page && it.key != null }.map { it.key }.toSet())
            assertTrue("must include solid fill", entries.any {
                it.key == ScopedGlassOptics.key(scope,
                    com.hellovoid.liquiddock.config.ConfigSchema.Glass.BASE_COLOR_ALPHA)
            })
        }
        assertEquals(expandedFolderSearchSpecs.map(IntSpec::key).toSet(),
            customGlassSearchRows(Page.GlassFolders).map { it.key }.toSet())
    }

    @Test fun folderCellOrderAndSearchFocusUseTypedSharedIds() {
        assertEquals(FolderGlassCell.entries.map { it.id }, folderGlassCellOrder)
        assertEquals(folderGlassCellOrder.size, folderGlassCellOrder.toSet().size)
        assertEquals(expandedFolderSearchSpecs.map(IntSpec::key).toSet(),
            folderGlassFocusCell.keys)
        folderGlassFocusCell.values.forEach { assertTrue(it in folderGlassCellOrder) }
        assertEquals(FolderGlassCell.OPEN_FILL_ALPHA.id,
            folderGlassFocusCell.getValue(openFolderBaseColorAlphaSpec.key))
    }

    @Test fun fixedCustomComponentPagesIndexAllTheirRenderedStaticControls() {
        val catalog = settingsSearchCatalog({ "resource:$it" }, pages)
        for (page in listOf(Page.GlassIcons, Page.GlassWidgets, Page.Stroke,
                Page.Shadow, Page.Divider)) {
            val rows = customGlassSearchRows(page)
            assertTrue(rows.isNotEmpty())
            assertEquals(rows.size, rows.map { it.key }.toSet().size)
            assertEquals(rows.map { it.key }.toSet(),
                catalog.filter { it.page == page && it.key != null }.map { it.key }.toSet())
            rows.forEach {
                assertTrue("No focus Cell for ${page.name}/${it.key}",
                    customDenseFocusIndex(page, it.key) != null)
            }
        }
        assertEquals(strokeSpecs.size + 3, customGlassSearchRows(Page.Stroke).size)
        assertEquals(shadowSpecs.size + 2, customGlassSearchRows(Page.Shadow).size)
        assertEquals(dividerSpecs.size + 1, customGlassSearchRows(Page.Divider).size)
    }

    @Test fun gridBasicsHasSemanticCellsAndDoesNotLoseItsPreflightControls() {
        val rows = customGlassSearchRows(Page.GridBasics)
        val expected = setOf(
            com.hellovoid.liquiddock.config.ConfigSchema.Grid.ICON_SIZE_ENABLED.name(),
            launcher450IconSizeSpec.key,
            com.hellovoid.liquiddock.config.ConfigSchema.Grid.ENABLED.name(),
            com.hellovoid.liquiddock.config.ConfigSchema.Grid.WIDGET_HORIZONTAL_STRETCH.name(),
        ) + gridDimensionSpecs.map { it.key }
        assertEquals(expected, rows.map { it.key }.toSet())
        rows.forEach { assertTrue(customDenseFocusIndex(Page.GridBasics, it.key) != null) }
    }

    @Test fun customMenuAndKeyboardPagesSearchActualOriginalControls() {
        for (page in listOf(Page.ShortcutPopupCustomization, Page.HandleMenuCustomization,
                Page.DialogCustomization, Page.Gboard)) {
            val rows = customGlassSearchRows(page)
            assertTrue(rows.isNotEmpty())
            assertEquals(rows.size, rows.map { it.key }.toSet().size)
            rows.forEach { assertTrue("Missing GUI cell for ${page.name}/${it.key}",
                customDenseFocusIndex(page, it.key) != null) }
        }
    }

    @Test fun tintAndSolidColorSearchFindTheirOwningPages() {
        val catalog = settingsSearchCatalog({ "resource:$it" }, pages)
        val tint = filterSettingsSearch(catalog, "染色")
        assertTrue(tint.any { it.page == Page.LiquidColor && it.key ==
            com.hellovoid.liquiddock.config.ConfigSchema.Glass.TINT_ALPHA.name() })
        val base = filterSettingsSearch(catalog, "底色透明度")
        assertTrue(base.any { it.page == Page.LiquidColor && it.key ==
            com.hellovoid.liquiddock.config.ConfigSchema.Glass.BASE_COLOR_ALPHA.name() })
        val folder = filterSettingsSearch(catalog, "底色 文件夹")
        assertTrue(folder.any { it.page == Page.GlassFolders })
        for (target in listOf(Page.ShortcutPopupAll, Page.HandleMenuAll, Page.DialogAll,
                Page.GboardAll)) {
            val rows = catalog.filter { it.page == target && it.key != null }
            assertTrue(rows.any { "染色" in it.title })
            assertTrue(rows.any { "底色" in it.title })
        }
    }

    @Test fun canonicalParentsDoNotDependOnOverviewShortcutOrder() {
        assertEquals(Page.LayoutHub, canonicalSettingsParent(Page.Dock))
        assertEquals(Page.GlassHub, canonicalSettingsParent(Page.Liquid))
        assertEquals(Page.GlassHub, canonicalSettingsParent(Page.Animation))
        Page.entries.forEach { page ->
            val path = settingsPagePath(page)
            assertEquals(page, path.last())
            assertEquals(path.size, path.toSet().size)
        }
    }

    @Test fun scopeLabelsAreUniqueAndAllRowsHaveUniqueIdsAndKeys() {
        val edges = pages.values.filter { it.page in glassEdgePageScopes }
        assertEquals(edges.size, edges.map { it.title }.toSet().size)
        pages.values.forEach { meta ->
            assertEquals(meta.blocks.size, meta.blocks.map { it.id }.toSet().size)
            val keys = meta.blocks.filterIsInstance<SettingsBlock.Row>().map { it.key }
            assertEquals(keys.size, keys.toSet().size)
            assertEquals(-1, meta.indexOfKey("missing-key"))
        }
    }

    @Test fun centeringUsesViewportAndCorrectScrollDirection() {
        assertEquals(328f, settingsCenterScrollDelta(778, 100, 0, 1000))
        assertEquals(-200f, settingsCenterScrollDelta(100, 200, 0, 800))
        assertEquals(0f, settingsCenterScrollDelta(350, 100, -20, 820))
        // A row taller than the viewport keeps its beginning accessible.
        assertEquals(0f, settingsCenterScrollDelta(0, 1200, 0, 800))
    }
    @Test fun movedOptionsAndAnimationDependenciesComeFromTheirOwningPage() {
        val config = com.hellovoid.liquiddock.config.ConfigSchema.Animation.DOCK_RESIZE
        val resize = pages.getValue(Page.AnimationWorkspace).blocks
            .filterIsInstance<SettingsBlock.Row>().single { it.key == config.name() }
        assertTrue(resize.requires.contains(com.hellovoid.liquiddock.config.ConfigSchema.Dock.ENABLED to true))
        assertTrue(resize.requires.contains(com.hellovoid.liquiddock.config.ConfigSchema.Dock.RESIZE_ANIMATION to false))
        assertTrue(resize.requires.contains(com.hellovoid.liquiddock.config.ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION to true))
        assertEquals(-1, pages.getValue(Page.DockBehavior).indexOfKey(config.name()))
        val catalog = settingsSearchCatalog({ "resource:$it" }, pages)
        assertEquals(Page.GlassMenus, catalog.single { it.title == "壁纸闪烁修复" }.page)
        assertEquals(Page.LiquidSampling, catalog.single {
            it.key == com.hellovoid.liquiddock.config.ConfigSchema.Dock.FRAME_SYNC.name()
        }.page)
        assertTrue(pages.getValue(Page.Recents).indexOfKey(
            com.hellovoid.liquiddock.config.ConfigSchema.Animation.DISABLE_WALLPAPER_ZOOM.name()) >= 0)
        val owners = mapOf(
            Page.AnimationWorkspace to listOf(animationWorkspaceVisibilitySpec, animationUnlockGlassFadeSpec,
                animationFolderOpenGlassFadeSpec, animationDockResizeSpec),
            Page.AnimationInteraction to listOf(animationPressInSpec, animationPressOutSpec),
            Page.AnimationPopups to listOf(animationShortcutDismissSpec, animationSecurityCenterExitSpec),
            Page.AnimationGui to listOf(animationSettingsPageSpec),
        )
        owners.forEach { (page, specs) ->
            assertEquals(specs.map { it.key }, pages.getValue(page).blocks.filterIsInstance<SettingsBlock.Row>().map { it.key })
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateRowKeysAreRejectedAtDeclarationTime() {
        val spec = animationSettingsPageSpec
        SettingsMeta(Page.AnimationGui, "动画", listOf(
            SettingsBlock.Row("a", spec.key, spec.title, number = spec),
            SettingsBlock.Row("b", spec.key, spec.title, number = spec),
        ))
    }

}
