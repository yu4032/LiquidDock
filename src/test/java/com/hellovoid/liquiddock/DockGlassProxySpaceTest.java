package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Cross-window geometry is verified through the production coordinate policy. */
public class DockGlassProxySpaceTest {
    @Test public void floatingDockWindowOffsetIsRemovedBeforeOutputMapping() {
        assertEquals(38f, DockGlassProxySpace.windowCoordinate(1638f, 1600, 0f), 0f);
        assertEquals(70f, DockGlassProxySpace.windowCoordinate(772f, 700, -2f), 0f);
    }

    @Test public void aSharedFullscreenWindowPreservesItsCoordinates() {
        assertEquals(1638f, DockGlassProxySpace.windowCoordinate(1638f, 0, 0f), 0f);
        assertEquals(166f, DockGlassProxySpace.windowCoordinate(1804f, 1600, 0f)
                - DockGlassProxySpace.windowCoordinate(1638f, 1600, 0f), 0f);
    }

    @Test public void crossWindowRectangleMaintainsSizeAndMovesToDockLocalSpace() {
        float left = DockGlassProxySpace.windowCoordinate(1638f, 1600, 0f);
        float right = DockGlassProxySpace.windowCoordinate(1718f, 1600, 0f);
        float top = DockGlassProxySpace.windowCoordinate(772f, 700, -2f);
        float bottom = DockGlassProxySpace.windowCoordinate(852f, 700, -2f);
        assertEquals(38f, left, 0f);
        assertEquals(118f, right, 0f);
        assertEquals(70f, top, 0f);
        assertEquals(150f, bottom, 0f);
        assertEquals(80f, right - left, 0f);
        assertEquals(80f, bottom - top, 0f);
    }

    @Test public void negativeAndNonzeroGlobalOffsetsRemainConsistent() {
        assertEquals(150f, DockGlassProxySpace.windowCoordinate(130f, 0, 20f), 0f);
        assertEquals(-15f, DockGlassProxySpace.windowCoordinate(85f, 110, 10f), 0f);
    }
}
