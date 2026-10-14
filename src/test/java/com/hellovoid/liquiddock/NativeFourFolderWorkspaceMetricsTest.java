package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class NativeFourFolderWorkspaceMetricsTest {
    @Test public void reusesExactGeometryConfigurationOnly() {
        Object grid = new Object();
        NativeFourFolderWorkspaceMetrics snapshot =
                new NativeFourFolderWorkspaceMetrics(grid, 359, 360, 287, 320,
                        2, 440, 0x21, 0, 287, 288, true);
        assertTrue(snapshot.matches(grid, 359, 360, 287, 320, 2, 440, 0x21, 0));
        assertFalse(snapshot.matches(new Object(), 359, 360, 287, 320, 2, 440, 0x21, 0));
        assertFalse(snapshot.matches(grid, 360, 360, 287, 320, 2, 440, 0x21, 0));
        assertFalse(snapshot.matches(grid, 359, 361, 287, 320, 2, 440, 0x21, 0));
        assertFalse(snapshot.matches(grid, 359, 360, 288, 320, 2, 440, 0x21, 0));
        assertFalse(snapshot.matches(grid, 359, 360, 287, 321, 2, 440, 0x21, 0));
        assertFalse(snapshot.matches(grid, 359, 360, 287, 320, 1, 440, 0x21, 0));
        assertFalse(snapshot.matches(grid, 359, 360, 287, 320, 2, 480, 0x21, 0));
        assertFalse(snapshot.matches(grid, 359, 360, 287, 320, 2, 440, 0x20, 0));
        assertFalse(snapshot.matches(grid, 359, 360, 287, 320, 2, 440, 0x21, 1));
        assertEquals(287, snapshot.iconW);
        assertEquals(288, snapshot.iconH);
        assertTrue(snapshot.sampledSibling);
    }

    @Test public void noSiblingSampleStillProvidesDeterministicFallback() {
        Object grid = new Object();
        NativeFourFolderWorkspaceMetrics fallback =
                new NativeFourFolderWorkspaceMetrics(grid, 300, 300, 240, 240,
                        1, 400, 0x20, 0, 240, 240, false);
        assertFalse(fallback.sampledSibling);
        assertEquals(240, fallback.iconW);
        assertTrue(fallback.matches(grid, 300, 300, 240, 240, 1, 400, 0x20, 0));
    }
}
