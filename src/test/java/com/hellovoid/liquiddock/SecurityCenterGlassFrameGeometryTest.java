package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Method;

import org.junit.Test;

/** Pure composition contract for Security Center Dock + optional Toolbox + optional All Apps. */
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

    @Test
    public void toolboxFrameComposesDockBoxAndAllAppsOnOneBackdrop() throws Exception {
        SecurityCenterGlassGeometry dock = SecurityCenterGlassGeometry.resolve(
                3008, 1880, 0f, 0f, 34f, 261f, 240f, 1724f, 40f);
        SecurityCenterGlassGeometry box = SecurityCenterGlassGeometry.resolve(
                3008, 1880, 0f, 0f, 263f, 320f, 980f, 1650f, 44f);
        SecurityCenterGlassGeometry apps = SecurityCenterGlassGeometry.resolve(
                3008, 1880, 0f, 0f, 1000f, 261f, 2088f, 1724f, 24f);
        Method method = SecurityCenterGlassFrameGeometry.class.getDeclaredMethod(
                "compose", SecurityCenterGlassGeometry.class,
                SecurityCenterGlassGeometry.class, SecurityCenterGlassGeometry.class);
        method.setAccessible(true);
        SecurityCenterGlassFrameGeometry frame =
                (SecurityCenterGlassFrameGeometry) method.invoke(null, dock, box, apps);

        assertEquals(3, frame.nodeCount());
        assertSame(dock, frame.nodeAt(0));
        assertSame(box, frame.nodeAt(1));
        assertSame(apps, frame.nodeAt(2));
        SecurityCenterGlassGeometry presentation = frame.presentationGeometry();
        assertEquals(34f, presentation.left, 0.001f);
        assertEquals(261f, presentation.top, 0.001f);
        assertEquals(2054f, presentation.width, 0.001f);
        assertEquals(1463f, presentation.height, 0.001f);
    }
}
