package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Prevents ancestor clipping from becoming wallpaper sampling geometry again. */
public class LauncherGlassUnclippedGeometryContractTest {
    private static final Path STATIC_NODE = Path.of(
            "src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java");

    @Test
    public void staticGeometryMapsCompleteLocalCornersIntoStableRoot() throws Exception {
        assertTrue("missing lightweight static node", Files.exists(STATIC_NODE));
        String source = Files.readString(STATIC_NODE);

        assertFalse(source.contains("getGlobalVisibleRect"));
        assertTrue(source.contains("transformMatrixToGlobal"));
        assertTrue(source.contains("rootGlobal.invert"));
        assertTrue(source.contains("0f, 0f"));
        assertTrue(source.contains("width, 0f"));
        assertTrue(source.contains("0f, height"));
        assertTrue(source.contains("width, height"));
        assertTrue(source.contains("LauncherGlassGeometry.resolve"));
    }
}
