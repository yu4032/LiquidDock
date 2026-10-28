package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class LauncherIconGlassClipGeometryTest {
    @Test public void usesTheGlassOffsetAndCapsConfiguredRadius() {
        LauncherIconGlassClipGeometry.Result shape = LauncherIconGlassClipGeometry.resolve(
                10, 20, 110, 120, -10, 80, 22);
        assertEquals(20f, shape.left, 0f);
        assertEquals(30f, shape.top, 0f);
        assertEquals(100f, shape.right, 0f);
        assertEquals(110f, shape.bottom, 0f);
        assertEquals(40f, shape.radius, 0f);
    }
    @Test public void autoRadiusRemainsTheNativeIconRadius() {
        LauncherIconGlassClipGeometry.Result shape = LauncherIconGlassClipGeometry.resolve(
                0, 0, 100, 100, 10, 0, 22);
        assertEquals(-10f, shape.left, 0f);
        assertEquals(110f, shape.right, 0f);
        assertEquals(22f, shape.radius, 0f);
    }
    @Test public void fullDrawableRowCoversAllFourStraightEdgeSeams() {
        // Original drawable bounds alone can expose 1-2px shadow/edge lines
        // on every side. The subtraction region must extend beyond them.
        float[] row = LauncherIconGlassClipGeometry.cutoutRow(160f, 12f, 112f);
        assertArrayEquals(new float[]{-2f, 10f, 162f, 114f}, row, 0f);
        assertTrue(row[0] < 0f);
        assertTrue(row[1] < 12f);
        assertTrue(row[2] > 160f);
        assertTrue(row[3] > 112f);
    }

    @Test public void topGuardBandDoesNotRequestNegativeViewCoordinates() {
        float[] row = LauncherIconGlassClipGeometry.cutoutRow(160f, 0f, 100f);
        assertEquals(0f, row[1], 0f);
        assertEquals(102f, row[3], 0f);
    }

    @Test public void resizingDockInvalidatesFullRowWidth() {
        float[] before = LauncherIconGlassClipGeometry.cutoutRow(128f, 0f, 100f);
        float[] after = LauncherIconGlassClipGeometry.cutoutRow(160f, 0f, 100f);
        assertEquals(130f, before[2], 0f);
        assertEquals(162f, after[2], 0f);
    }
}
