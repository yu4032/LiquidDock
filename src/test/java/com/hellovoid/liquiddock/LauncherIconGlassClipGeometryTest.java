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
    @Test public void bakedClipMatchesGlassInDrawableLocalCoordinates() {
        LauncherIconGlassClipGeometry.Result glass = LauncherIconGlassClipGeometry.resolve(
                30f, 12f, 130f, 112f, 0f, 28f, 22f);
        LauncherIconGlassClipGeometry.Result local =
                LauncherIconGlassClipGeometry.localize(glass, 30f, 12f);
        assertEquals(0f, local.left, 0f);
        assertEquals(0f, local.top, 0f);
        assertEquals(100f, local.right, 0f);
        assertEquals(100f, local.bottom, 0f);
        assertEquals(28f, local.radius, 0f);
    }

    @Test public void bakedClipRetainsNegativeOffsetAndRadius() {
        LauncherIconGlassClipGeometry.Result glass = LauncherIconGlassClipGeometry.resolve(
                30f, 12f, 130f, 112f, 8f, 32f, 22f);
        LauncherIconGlassClipGeometry.Result local =
                LauncherIconGlassClipGeometry.localize(glass, 30f, 12f);
        assertEquals(-8f, local.left, 0f);
        assertEquals(-8f, local.top, 0f);
        assertEquals(108f, local.right, 0f);
        assertEquals(108f, local.bottom, 0f);
        assertEquals(32f, local.radius, 0f);
    }

    @Test public void resizingDockCreatesDifferentBakedLocalGeometry() {
        LauncherIconGlassClipGeometry.Result before =
                LauncherIconGlassClipGeometry.localize(
                        LauncherIconGlassClipGeometry.resolve(20f, 0f, 120f, 100f,
                                0f, 22f, 22f), 20f, 0f);
        LauncherIconGlassClipGeometry.Result after =
                LauncherIconGlassClipGeometry.localize(
                        LauncherIconGlassClipGeometry.resolve(20f, 0f, 148f, 128f,
                                0f, 28f, 22f), 20f, 0f);
        assertEquals(100f, before.right, 0f);
        assertEquals(128f, after.right, 0f);
    }
}
