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
    public void staticGeometryMapsUnclippedLocalBoundsIntoStableRoot() throws Exception {
        assertTrue("missing lightweight static node", Files.exists(STATIC_NODE));
        String source = Files.readString(STATIC_NODE);

        assertFalse(source.contains("getGlobalVisibleRect"));
        assertTrue(source.contains("transformMatrixToGlobal"));
        assertTrue(source.contains("rootGlobal.invert"));
        assertTrue(source.contains("float localLeft = 0f;"));
        assertTrue(source.contains("float localTop = 0f;"));
        assertTrue(source.contains("float localRight = hostWidth;"));
        assertTrue(source.contains("float localBottom = hostHeight;"));
        assertTrue(source.contains("localLeft, localTop"));
        assertTrue(source.contains("localRight, localTop"));
        assertTrue(source.contains("localLeft, localBottom"));
        assertTrue(source.contains("localRight, localBottom"));
        assertTrue(source.contains("kind == LauncherGlassDragState.Kind.ICON"));
        assertTrue(source.contains("LauncherGlassIconGeometry.resolve(material)"));
        assertTrue(source.contains("LauncherGlassGeometry.resolve"));
    }
}
