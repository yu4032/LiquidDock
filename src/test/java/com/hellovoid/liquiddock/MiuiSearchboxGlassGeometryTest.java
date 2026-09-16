package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MiuiSearchboxGlassGeometryTest {
    @Test
    public void mapsTargetBoundsToWindowGeometry() {
        MiuiSearchboxGlassGeometry geometry = MiuiSearchboxGlassGeometry.fromWindowBounds(
                1200, 2400,
                90f, 300f, 1020f, 1650f,
                28f);

        assertEquals(1200, geometry.rootWidth);
        assertEquals(2400, geometry.rootHeight);
        assertEquals(90f, geometry.left, 0.001f);
        assertEquals(300f, geometry.top, 0.001f);
        assertEquals(600f, geometry.centerX, 0.001f);
        assertEquals(1125f, geometry.centerY, 0.001f);
        assertEquals(1020f, geometry.width, 0.001f);
        assertEquals(1650f, geometry.height, 0.001f);
    }

    @Test
    public void clampsAnimatedBoundsAtWindowEdges() {
        MiuiSearchboxGlassGeometry geometry = MiuiSearchboxGlassGeometry.fromWindowBounds(
                1000, 2000,
                -25f, 150f, 1100f, 1900f,
                40f);

        assertEquals(0f, geometry.left, 0.001f);
        assertEquals(1000f, geometry.width, 0.001f);
        assertEquals(150f, geometry.top, 0.001f);
        assertEquals(1850f, geometry.height, 0.001f);
    }
}
