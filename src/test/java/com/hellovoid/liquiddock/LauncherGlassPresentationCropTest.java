package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Separate glass footprint from external optical pixels. */
public class LauncherGlassPresentationCropTest {
    @Test public void externalCropExpandsWithoutChangingGlassBody() {
        LauncherGlassGeometry.Snapshot shape = LauncherGlassGeometry.resolveStatic(
                1200, 2400, 400f, 600f, 600f, 760f, 24f);
        LauncherGlassGeometry.Snapshot extended =
                LauncherGlassGeometry.withPresentationCrop(
                        shape, 1200, 2400, 376f, 576f, 624f, 784f);

        assertEquals(shape.centerX, extended.centerX, 0f);
        assertEquals(shape.centerY, extended.centerY, 0f);
        assertEquals(shape.width, extended.width, 0f);
        assertEquals(shape.height, extended.height, 0f);
        assertEquals(shape.cornerRadius, extended.cornerRadius, 0f);
        assertEquals(376f / 1200f, extended.cropLeft, 0.0001f);
        assertEquals((2400f - 784f) / 2400f, extended.cropBottom, 0.0001f);
        assertEquals(248f / 1200f, extended.cropWidth, 0.0001f);
        assertEquals(208f / 2400f, extended.cropHeight, 0.0001f);
        assertFalse(shape.sameAs(extended));
        assertTrue(extended.sameAs(LauncherGlassGeometry.withPresentationCrop(
                shape, 1200, 2400, 376f, 576f, 624f, 784f)));
    }

    @Test public void opticalCropCanCrossDisplayEdgeWithoutShrinkingShape() {
        LauncherGlassGeometry.Snapshot shape = LauncherGlassGeometry.resolveStatic(
                1000, 2000, 4f, 12f, 180f, 176f, 22f);
        LauncherGlassGeometry.Snapshot result =
                LauncherGlassGeometry.withPresentationCrop(
                        shape, 1000, 2000, -16f, -8f, 200f, 196f);
        assertEquals(4f, result.left, 0f);
        assertEquals(12f, result.top, 0f);
        assertEquals(176f, result.width, 0f);
        assertEquals(22f, result.cornerRadius, 0f);
        assertEquals(-0.016f, result.cropLeft, 0.0001f);
    }
}
