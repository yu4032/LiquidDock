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

    @Test public void clipBandCoversFullIconRowIncludingPixelsOutsideDrawableBounds() {
        float[] row = LauncherIconGlassClipGeometry.iconRow(160f, 18f, 122f);
        assertArrayEquals(new float[]{-1f, 17f, 161f, 123f}, row, 0f);
        // The mask extends beyond both lateral icon edges. Labels below the
        // compound drawable are not part of the mask.
        assertTrue(row[0] < 20f);
        assertTrue(row[2] > 140f);
    }
    @Test public void clipBandRemainsValidWhenIconStartsAtTopOfView() {
        float[] row = LauncherIconGlassClipGeometry.iconRow(120f, 0f, 80f);
        assertArrayEquals(new float[]{-1f, 0f, 121f, 81f}, row, 0f);
    }
}
