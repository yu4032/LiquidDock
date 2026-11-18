package com.hellovoid.liquiddock.os4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.hellovoid.liquiddock.config.ConfigSchema

class Os4NativeConfTest {
    @Test
    fun `7x4 snapshot is LF terminated and fail closed`() {
        assertEquals(
            "grid_cols=7\ngrid_rows=4\nend=1\n",
            Os4NativeConfPublisher.buildConf(7, 4),
        )
    }

    @Test
    fun `invalid ranges publish nothing`() {
        assertNull(Os4NativeConfPublisher.buildConf(1, 4))
        assertNull(Os4NativeConfPublisher.buildConf(11, 4))
        assertNull(Os4NativeConfPublisher.buildConf(7, 1))
        assertNull(Os4NativeConfPublisher.buildConf(7, 11))
    }

    @Test
    fun `10x10 snapshot is supported`() {
        assertEquals(
            "grid_cols=10\ngrid_rows=10\nend=1\n",
            Os4NativeConfPublisher.buildConf(10, 10),
        )
    }

    @Test
    fun `prevent stretch preference is watched and published to native config`() {
        val key = ConfigSchema.Grid.PREVENT_WIDGET_STRETCH.name()
        assertTrue(Os4MainScreenOptions.watchedKeys.contains(key))

        val conf = Os4MainScreenOptions.fromPreferences(
            mapOf(ConfigSchema.Core.ENABLED.name() to true, key to true),
        )

        assertTrue(conf?.contains("widget_prevent_stretch=1\n") == true)
        val disabled = Os4MainScreenOptions.fromPreferences(
            mapOf(ConfigSchema.Core.ENABLED.name() to true, key to false),
        )
        assertFalse(disabled?.contains("widget_prevent_stretch=") == true)
    }
}
