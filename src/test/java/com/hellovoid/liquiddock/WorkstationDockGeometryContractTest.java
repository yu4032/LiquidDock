package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Geometry behavior is typed-state tested; source checks below are static ownership boundaries. */
public class WorkstationDockGeometryContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    @Test public void workstationWidthOffsetDoesNotAccumulateAndRebasesOnNativeWidth() {
        WorkstationDockWidthState state = new WorkstationDockWidthState();

        assertEquals(1100, state.targetWidth(1000, 100));
        assertEquals(1100, state.targetWidth(1100, 100));
        assertEquals(1300, state.targetWidth(1200, 100));
        assertEquals(1, state.targetWidth(1200, -5000));
    }

    @Test public void workstationWidthTargetsVisibleDockContainerInsteadOfHiddenNormalBackground()
            throws Exception {
        String main = source("MainHook.java");
        int start = main.indexOf("private static void installWorkstationDockHooks");
        int end = main.indexOf("private static void installWorkstationModeGuard", start);
        assertTrue(start >= 0 && end > start);
        String workstationHooks = main.substring(start, end);
        assertFalse("workstation width must not mutate the hidden normal HotSeats background",
                workstationHooks.contains("widthOffset != 0) args[0]"));
        assertTrue("MainHook must install the visible workstation Dock geometry hook",
                main.contains("WorkstationDockGeometryHook.install(classLoader, config.workstation);"));

        String hook = source("WorkstationDockGeometryHook.java");
        assertTrue(hook.contains("HotSeatsListContentAdapter$LineViewHolder"));
        assertTrue(hook.contains("DockContainer"));
    }

    @Test public void workstationHasNoSecondWholeDockShadowOwner() throws Exception {
        String main = source("MainHook.java");
        assertFalse(main.contains("syncShadowGeometry();"));
        assertFalse(main.contains("shadowViewRef"));
        assertFalse(main.contains("nativeShadowTargetRef"));
    }
}
