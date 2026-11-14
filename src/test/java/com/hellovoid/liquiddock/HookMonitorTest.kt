package com.hellovoid.liquiddock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HookMonitorTest {

    private fun allEnabledConfig(masterEnabled: Boolean = true) = HookFeatureConfig(
        masterEnabled = masterEnabled,
        glassEnabled = true,
        iconGlassEnabled = true,
        widgetGlassEnabled = true,
        gridEnabled = true,
        iconSizeEnabled = true,
        bigFolderEnabled = true,
        widget1x1Enabled = true,
        dockEnabled = true,
        dividerEnabled = true,
        recentsBlurEnabled = true,
    )

    @Test
    fun parseStatusKeyValues_extractsPidAndFlags() {
        val parsed = parseStatusKeyValues(
            """
            pid=25282
            glass=1
            glass_icon_radius=0
            """.trimIndent(),
        )
        assertEquals("25282", parsed["pid"])
        assertEquals("1", parsed["glass"])
        assertEquals("0", parsed["glass_icon_radius"])
    }

    @Test
    fun evaluateHookMonitor_allActiveWhenStatusAndLogPresent() {
        val status = parseStatusKeyValues(
            """
            pid=25282
            glass=1
            glass_icons=1
            glass_icon_radius=1
            glass_widgets=1
            grid=1
            dock=1
            divider=1
            recents=1
            """.trimIndent(),
        )
        val bootLog = """
            100.123 home icons applied: scale=1.05
            100.200 OS4 big folder gate installed: 1
            100.210 OS4 big folder Pad bypass installed: patched=11
            100.300 OS4 widget 1x1<->2x2 menu installed=8/8
            100.305 widget resize: 1x1 enlarge enabled at 0x70cdc398ac
            100.310 MAML widget resource hook installed
        """.trimIndent()

        val snapshot = evaluateHookMonitor(allEnabledConfig(), status, bootLog)
        assertEquals(25282, snapshot.pid)
        assertTrue(snapshot.hasStatusFile)
        assertEquals(10, snapshot.enabledCount)
        assertEquals(10, snapshot.activeCount)
        assertEquals(0, snapshot.missingCount)
        assertEquals("Hook 已生效", snapshot.items.first { it.id == "big_folder" }.detail)
        assertEquals("Hook 已生效", snapshot.items.first { it.id == "widget_1x1" }.detail)
        assertEquals("Hook 已生效", snapshot.items.first { it.id == "glass_widgets" }.detail)
    }

    @Test
    fun evaluateHookMonitor_detectsMissingAndDisabledHooks() {
        val config = allEnabledConfig().copy(
            dividerEnabled = false,
            recentsBlurEnabled = false,
        )
        val status = parseStatusKeyValues(
            """
            pid=1234
            glass=1
            glass_icons=0
            glass_widgets=1
            grid=1
            dock=1
            divider=0
            recents=0
            """.trimIndent(),
        )
        val snapshot = evaluateHookMonitor(config, status, "")
        assertEquals(8, snapshot.enabledCount)
        assertEquals(HookProbeState.MISSING, snapshot.items.first { it.id == "glass_icons" }.state)
        assertEquals(HookProbeState.MISSING, snapshot.items.first { it.id == "big_folder" }.state)
        assertEquals(HookProbeState.DISABLED, snapshot.items.first { it.id == "divider" }.state)
        assertEquals(HookProbeState.DISABLED, snapshot.items.first { it.id == "recents" }.state)
    }

    @Test
    fun evaluateHookMonitor_marksAllPausedWhenMasterOff() {
        val snapshot = evaluateHookMonitor(allEnabledConfig(masterEnabled = false), emptyMap(), "")
        assertTrue(snapshot.items.all { it.state == HookProbeState.PAUSED })
        assertEquals(0, snapshot.activeCount)
    }
}
