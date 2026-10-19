package com.hellovoid.liquiddock;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class DockGlassProxySpaceTest {
    @Test public void floatingDockWindowOffsetIsRemovedBeforeOutputMapping() {
        assertEquals(38f, DockGlassProxySpace.windowCoordinate(1638f, 1600, 0f), 0f);
        assertEquals(70f, DockGlassProxySpace.windowCoordinate(772f, 700, -2f), 0f);
    }
    @Test public void crossWindowConversionStaysWiredToBothGeometryBoundaries()
            throws Exception {
        Path java = Path.of("src/main/java/com/hellovoid/liquiddock");
        String hook = Files.readString(java.resolve("DockIconAnimationGlassHook.java"));
        String node = Files.readString(java.resolve("DockGlassItemNode.java"));
        assertTrue(hook.contains("animationRoot.getLocationOnScreen(screen)"));
        assertTrue(hook.contains("toScreen.postTranslate(screen[0] - origin[0], screen[1] - origin[1])"));
        assertTrue(node.contains("DockGlassProxySpace.windowCoordinate"));
        assertFalse(node.contains("rootToGlobal.mapPoints(points)"));
    }

    @Test public void aSharedFullscreenWindowPreservesItsCoordinates() {
        assertEquals(1638f, DockGlassProxySpace.windowCoordinate(1638f, 0, 0f), 0f);
        assertEquals(166f, DockGlassProxySpace.windowCoordinate(1804f, 1600, 0f)
                - DockGlassProxySpace.windowCoordinate(1638f, 1600, 0f), 0f);
    }
}
