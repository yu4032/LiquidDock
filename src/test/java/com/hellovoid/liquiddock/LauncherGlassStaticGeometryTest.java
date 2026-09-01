package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

/** Regression coverage for Workspace StaticLayer geometry crossing viewport edges. */
public class LauncherGlassStaticGeometryTest {
    @Test public void staticShapeKeepsFullGeometryWhilePartiallyOffLeftEdge() {
        LauncherGlassGeometry.Snapshot geometry = LauncherGlassGeometry.resolveStatic(
                300, 200, -40f, 20f, 60f, 120f, 24f);

        assertNotNull(geometry);
        assertEquals(-40f, geometry.left, 0.001f);
        assertEquals(100f, geometry.width, 0.001f);
        assertEquals(10f, geometry.centerX, 0.001f);
        assertEquals(24f, geometry.cornerRadius, 0.001f);
    }

    @Test public void staticShapeKeepsFullGeometryWhilePartiallyOffRightEdge() {
        LauncherGlassGeometry.Snapshot geometry = LauncherGlassGeometry.resolveStatic(
                300, 200, 260f, 20f, 360f, 120f, 24f);

        assertNotNull(geometry);
        assertEquals(260f, geometry.left, 0.001f);
        assertEquals(100f, geometry.width, 0.001f);
        assertEquals(310f, geometry.centerX, 0.001f);
        assertEquals(24f, geometry.cornerRadius, 0.001f);
    }

    @Test public void staticShapeIsCulledOnlyAfterItFullyLeavesViewport() {
        assertNull(LauncherGlassGeometry.resolveStatic(
                300, 200, -120f, 20f, -20f, 120f, 24f));
        assertNull(LauncherGlassGeometry.resolveStatic(
                300, 200, 320f, 20f, 420f, 120f, 24f));
    }

    @Test public void dragSinkGeometryStillUsesViewportClipping() {
        LauncherGlassGeometry.Snapshot geometry = LauncherGlassGeometry.resolve(
                300, 200, -40f, 20f, 60f, 120f, 24f);

        assertNotNull(geometry);
        assertEquals(0f, geometry.left, 0.001f);
        assertEquals(60f, geometry.width, 0.001f);
        assertEquals(30f, geometry.centerX, 0.001f);
    }
}
