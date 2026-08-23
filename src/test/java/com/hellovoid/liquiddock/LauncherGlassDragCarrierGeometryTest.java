package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Regression coverage for mapping MIUI DragView geometry into the sibling glass carrier. */
public class LauncherGlassDragCarrierGeometryTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void expandedSourceUsesMappedBoundsWithoutApplyingScaleTwice() {
        LauncherGlassDragCarrierGeometry.Snapshot geometry =
                LauncherGlassDragCarrierGeometry.resolve(
                        rectCorners(90f, 190f, 210f, 310f),
                        rectCorners(102f, 202f, 198f, 298f),
                        0f, 0f);

        assertEquals(90f, geometry.carrierLeft, EPSILON);
        assertEquals(190f, geometry.carrierTop, EPSILON);
        assertEquals(120f, geometry.carrierWidth(), EPSILON);
        assertEquals(120f, geometry.carrierHeight(), EPSILON);
        assertEquals(12f, geometry.visualLeft, EPSILON);
        assertEquals(12f, geometry.visualTop, EPSILON);
        assertEquals(108f, geometry.visualRight, EPSILON);
        assertEquals(108f, geometry.visualBottom, EPSILON);
        assertEquals(150f, geometry.visualCenterX(), EPSILON);
        assertEquals(250f, geometry.visualCenterY(), EPSILON);
    }

    @Test
    public void shrunkenSourceUsesMappedBoundsWithoutApplyingScaleTwice() {
        LauncherGlassDragCarrierGeometry.Snapshot geometry =
                LauncherGlassDragCarrierGeometry.resolve(
                        rectCorners(110f, 210f, 190f, 290f),
                        rectCorners(118f, 218f, 182f, 282f),
                        0f, 0f);

        assertEquals(110f, geometry.carrierLeft, EPSILON);
        assertEquals(210f, geometry.carrierTop, EPSILON);
        assertEquals(80f, geometry.carrierWidth(), EPSILON);
        assertEquals(80f, geometry.carrierHeight(), EPSILON);
        assertEquals(8f, geometry.visualLeft, EPSILON);
        assertEquals(8f, geometry.visualTop, EPSILON);
        assertEquals(72f, geometry.visualRight, EPSILON);
        assertEquals(72f, geometry.visualBottom, EPSILON);
        assertEquals(150f, geometry.visualCenterX(), EPSILON);
        assertEquals(250f, geometry.visualCenterY(), EPSILON);
    }

    @Test
    public void hostScrollIsRestoredOnceForSiblingChildLayoutCoordinates() {
        LauncherGlassDragCarrierGeometry.Snapshot geometry =
                LauncherGlassDragCarrierGeometry.resolve(
                        rectCorners(90f, 170f, 190f, 270f),
                        rectCorners(100f, 180f, 180f, 260f),
                        20f, 30f);

        assertEquals(110f, geometry.carrierLeft, EPSILON);
        assertEquals(200f, geometry.carrierTop, EPSILON);
        assertEquals(10f, geometry.visualLeft, EPSILON);
        assertEquals(10f, geometry.visualTop, EPSILON);
        assertEquals(90f, geometry.visualRight, EPSILON);
        assertEquals(90f, geometry.visualBottom, EPSILON);
        assertEquals(160f, geometry.visualCenterX(), EPSILON);
        assertEquals(250f, geometry.visualCenterY(), EPSILON);
    }

    private static float[] rectCorners(float left, float top, float right, float bottom) {
        return new float[]{
                left, top,
                right, top,
                left, bottom,
                right, bottom
        };
    }
}
