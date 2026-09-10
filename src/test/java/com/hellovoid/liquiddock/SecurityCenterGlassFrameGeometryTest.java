package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/** Pure composition contract for Security Center Dock + All Apps glass. */
public class SecurityCenterGlassFrameGeometryTest {
    @Test
    public void dockOnlyFramePresentsExactlyDock() {
        SecurityCenterGlassGeometry dock = SecurityCenterGlassGeometry.resolve(
                3008, 1880,
                0f, 0f,
                34f, 261f, 240f, 1724f,
                40f);
        assertNotNull(dock);

        SecurityCenterGlassFrameGeometry frame =
                SecurityCenterGlassFrameGeometry.dockOnly(dock);

        assertEquals(1, frame.nodeCount());
        assertSame(dock, frame.nodeAt(0));
        assertSame(dock, frame.presentationGeometry());
    }

    @Test
    public void allAppsFrameRetainsDockAndPresentsUnion() {
        SecurityCenterGlassGeometry dock = SecurityCenterGlassGeometry.resolve(
                3008, 1880,
                0f, 0f,
                34f, 261f, 240f, 1724f,
                40f);
        SecurityCenterGlassGeometry apps = SecurityCenterGlassGeometry.resolve(
                3008, 1880,
                0f, 0f,
                263f, 261f, 2088f, 1724f,
                24f);
        assertNotNull(dock);
        assertNotNull(apps);

        SecurityCenterGlassFrameGeometry frame =
                SecurityCenterGlassFrameGeometry.dockAndApps(dock, apps);

        assertEquals(2, frame.nodeCount());
        assertSame(dock, frame.nodeAt(0));
        assertSame(apps, frame.nodeAt(1));
        SecurityCenterGlassGeometry presentation = frame.presentationGeometry();
        assertEquals(34f, presentation.left, 0.001f);
        assertEquals(261f, presentation.top, 0.001f);
        assertEquals(2054f, presentation.width, 0.001f);
        assertEquals(1463f, presentation.height, 0.001f);
    }
}
