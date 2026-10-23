package com.hellovoid.liquiddock.os4

import com.hellovoid.liquiddock.config.ConfigSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Os4GlassConfigTest {
    @Test fun glassOnlyUsesExistingGlobalSwitch() {
        val result=Os4MainScreenOptions.fromPreferences(mapOf(
            ConfigSchema.Glass.ENABLED.name() to true,
            ConfigSchema.Dock.ENABLED.name() to false,
        ))
        assertEquals(
            "icon_size_enabled=0\nglass_enabled=1\nicon_size_percent=100\nend=1\n",
            result,
        )
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
