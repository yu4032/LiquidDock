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
            val meta = pages.getValue(item.page)
            val row = meta.blocks[meta.indexOfKey(item.key!!)] as SettingsBlock.Row
            assertEquals(row.title, item.title)
        }
        assertFalse(catalog.any { it.title == "iOS 玻璃效果" })
        assertFalse(catalog.any { it.page == Page.GlassFolders && it.key != null })
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
