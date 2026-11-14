package com.hellovoid.liquiddock.os4

import com.hellovoid.liquiddock.config.ConfigSchema
import com.hellovoid.liquiddock.WidgetComponentStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Os4GlassConfigTest {
    @Test fun glassOnlyUsesExistingGlobalSwitch() {
        val result=Os4MainScreenOptions.fromPreferences(mapOf(
            ConfigSchema.Glass.ENABLED.name() to true,
            ConfigSchema.Dock.ENABLED.name() to false,
            ConfigSchema.Glass.ICON_GLASS.name() to false,
            ConfigSchema.Glass.WIDGET_GLASS.name() to false,
        ))
        assertEquals(
            "hook_safety_enabled=0\nicon_size_enabled=0\nglass_enabled=1\nicon_size_percent=100\nend=1\n",
            result,
        )
    }
    @Test fun glassWithDefaultComponentFlagsPublishesIconAndWidgetSettings() {
        val result = Os4MainScreenOptions.fromPreferences(mapOf(
            ConfigSchema.Glass.ENABLED.name() to true,
            ConfigSchema.Dock.ENABLED.name() to false,
        ))
        assertEquals(
            "hook_safety_enabled=0\nicon_size_enabled=0\nglass_enabled=1\nglass_icons=1\n" +
                "glass_icon_size=0.0\nglass_icon_corner=0.0\nglass_widgets=1\n" +
                "icon_size_percent=100\nend=1\n",
            result,
        )
    }

    @Test fun widgetGlassCanTurnOffWithoutDiscardingIndependentHideSelections() {
        val selection = setOf("M2\tos4:demo:widget_2x2\thide")
        val enabled = mapOf<String, Any>(
            ConfigSchema.Dock.ENABLED.name() to false,
            ConfigSchema.Glass.ENABLED.name() to true,
            ConfigSchema.Glass.WIDGET_GLASS.name() to true,
            WidgetComponentStore.SELECTION_KEY to selection,
        )
        val on = Os4MainScreenOptions.fromPreferences(enabled)
        assertTrue(on != null && on.contains("glass_widgets=1\n"))
        assertTrue(on != null && on.contains("widget_overrides=1\n"))

        val off = Os4MainScreenOptions.fromPreferences(
            enabled + (ConfigSchema.Glass.WIDGET_GLASS.name() to false)
        )
        assertTrue(off != null && !off.contains("glass_widgets=1\n"))
        assertTrue(off != null && off.contains("widget_overrides=1\n"))

        val globalOff = Os4MainScreenOptions.fromPreferences(
            enabled + (ConfigSchema.Glass.ENABLED.name() to false)
        )
        assertTrue(globalOff != null && !globalOff.contains("glass_enabled=1\n"))
        assertTrue(globalOff != null && globalOff.contains("widget_overrides=1\n"))
    }

    @Test fun backgroundHidePausesWithGlassButIndependentTextHideRemains() {
        fun catalog(name: String, type: String, order: Int = 0, depth: Int = 0, area: Float = 0.95f) =
            WidgetComponentStore.parseCatalog(
                "M2\tos4:clock:widget_2x2\t$name\t${name}ScreenElement\tos4xml/0\t\t$type\t$order\t$depth\t$area\t0"
            )!!
        val background = catalog("Rectangle", "background")
        val backgroundImage = catalog("Image", "image")
        val text = catalog("Text", "text")
        assertFalse(Os4MamlHidePolicy.shouldApply(background, false))
        assertFalse(Os4MamlHidePolicy.shouldApply(backgroundImage, false))
        assertTrue(Os4MamlHidePolicy.shouldApply(text, false))
        assertTrue(Os4MamlHidePolicy.shouldApply(background, true))
    }

    @Test fun defaultDoesNotForceVendorMaterial() {
        val defaultConf=Os4MainScreenOptions.fromPreferences(emptyMap<String,Any>())
        assertTrue(defaultConf != null && !defaultConf.contains("glass_enabled=1"))
        assertEquals("",Os4MainScreenOptions.fromPreferences(mapOf(
            ConfigSchema.Glass.ENABLED.name() to false,
            ConfigSchema.Dock.ENABLED.name() to false,
        )))
        assertEquals("",Os4MainScreenOptions.fromPreferences(mapOf(
            ConfigSchema.Core.ENABLED.name() to false,
            ConfigSchema.Glass.ENABLED.name() to true,
        )))
    }
    @Test fun malformedGlassTypeClearsNativeSnapshot() {
        assertEquals(null,Os4MainScreenOptions.fromPreferences(mapOf(
            ConfigSchema.Glass.ENABLED.name() to "yes",
        )))
        assertTrue(Os4MainScreenOptions.watchedKeys.contains(ConfigSchema.Glass.ENABLED.name()))
    }
}
