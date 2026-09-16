package com.hellovoid.liquiddock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MiuiSearchboxGlassGeometryTest {
    @Test
    public void mapsTargetBoundsToWindowGeometryAndBottomOriginUvCrop() {
        MiuiSearchboxGlassGeometry geometry = MiuiSearchboxGlassGeometry.fromWindowBounds(
                1200, 2400,
                90f, 300f, 1020f, 1650f,
                28f);

        assertEquals(1200, geometry.rootWidth);
        assertEquals(2400, geometry.rootHeight);
        assertEquals(600f, geometry.centerX, 0.001f);
        assertEquals(1125f, geometry.centerY, 0.001f);
        assertEquals(1020f, geometry.width, 0.001f);
        assertEquals(1650f, geometry.height, 0.001f);
        assertArrayEquals(new float[]{
                90f / 1200f,
                (2400f - 1950f) / 2400f,
                1020f / 1200f,
                1650f / 2400f,
        }, geometry.toCropUvRect(), 0.0001f);
    }

    @Test
    public void clampsBoundsAtWindowEdgesWithoutScalingWholeWindow() {
        MiuiSearchboxGlassGeometry geometry = MiuiSearchboxGlassGeometry.fromWindowBounds(
                1000, 2000,
                -25f, 150f, 1100f, 1900f,
                40f);

        assertEquals(0f, geometry.left, 0.001f);
        assertEquals(1000f, geometry.width, 0.001f);
        assertEquals(150f, geometry.top, 0.001f);
        assertEquals(1850f, geometry.height, 0.001f);
        assertArrayEquals(new float[]{0f, 0f, 1f, 0.925f}, geometry.toCropUvRect(), 0.0001f);
    }

    @Test
    public void removesAnimatedAncestorTranslationFromSettledCrop() {
        assertEquals(300f,
                MiuiSearchboxGlassGeometry.settledCoordinate(1850f, 1550f),
                0.001f);
        assertEquals(90f,
                MiuiSearchboxGlassGeometry.settledCoordinate(90f, 0f),
                0.001f);
    }
}
