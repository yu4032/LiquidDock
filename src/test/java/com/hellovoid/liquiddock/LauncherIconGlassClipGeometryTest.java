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
    @Test public void fullDrawableRowRemovesLateralSeamWithoutClippingLabel() {
        // A 100px icon inside a 160px ShortcutIcon may paint at x < 30 or x > 130.
        float[] row = LauncherIconGlassClipGeometry.cutoutRow(160f, 12f, 112f);
        assertArrayEquals(new float[]{-1f, 12f, 161f, 112f}, row, 0f);
        assertTrue(row[0] < 30f);
        assertTrue(row[2] > 130f);
        // Only the icon row is clipped; the text label begins below its bottom.
        assertEquals(112f, row[3], 0f);
    }

    @Test public void resizingDockInvalidatesFullRowWidth() {
        float[] before = LauncherIconGlassClipGeometry.cutoutRow(128f, 0f, 100f);
        float[] after = LauncherIconGlassClipGeometry.cutoutRow(160f, 0f, 100f);
        assertEquals(129f, before[2], 0f);
        assertEquals(161f, after[2], 0f);
    }
}
