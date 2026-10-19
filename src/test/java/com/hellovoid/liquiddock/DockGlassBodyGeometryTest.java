package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalEdgeDecoration;
import com.hellovoid.prismal.PrismalGeometry;
import org.junit.Test;
import static org.junit.Assert.*;

public class DockGlassBodyGeometryTest {
    @Test public void disabledOpticsLeavesDockPixelExact() {
        PrismalGeometry body = fullDock();
        assertSame(body, DockGlassBodyGeometry.fitOuterHairline(body,
                PrismalEdgeDecoration.NONE));
        PrismalEdgeDecoration shadowOnly = new PrismalEdgeDecoration(
                0f, 0f, .6f, 12f, 3f, 0f, 0f, 0f, .2f, .5f);
        assertSame(body, DockGlassBodyGeometry.fitOuterHairline(body, shadowOnly));
    }

    @Test public void outerHairlineFitsWithinNativeTextureBounds() {
        PrismalGeometry body = fullDock();
        PrismalEdgeDecoration ink = new PrismalEdgeDecoration(
                1f, .85f, .6f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        PrismalGeometry fitted = DockGlassBodyGeometry.fitOuterHairline(body, ink);
        float guard = ink.extraGuardPx(1f, body.glassHeight);
        assertEquals(3f, guard, .001f);
        assertEquals(guard, fitted.left(), .001f);
        assertEquals(body.framebufferWidth - guard, fitted.right(), .001f);
        assertEquals(guard, fitted.top(), .001f);
        assertEquals(body.framebufferHeight - guard, fitted.bottom(), .001f);
        assertEquals(body.topLeftRadius - guard, fitted.topLeftRadius, .001f);
        assertEquals(body.centerX, fitted.centerX, .001f);
        assertEquals(body.centerY, fitted.centerY, .001f);
    }

    @Test public void widerLineReservesCorrespondinglyMoreNativeOutput() {
        PrismalGeometry body = fullDock();
        PrismalEdgeDecoration wide = new PrismalEdgeDecoration(
                5f, .85f, .6f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        PrismalGeometry fitted = DockGlassBodyGeometry.fitOuterHairline(body, wide);
        assertEquals(7f, fitted.left(), .001f);
        assertEquals(7f, fitted.top(), .001f);
    }

    private static PrismalGeometry fullDock() {
        return new PrismalGeometry(1000, 200, 500, 100,
                1000, 200, 84f);
    }
}
