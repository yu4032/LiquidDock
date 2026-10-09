package com.hellovoid.prismal;

import org.junit.Test;
import static org.junit.Assert.*;

public class PrismalNodeScissorTest {
    @Test public void fullResolutionBoundsUseGlBottomLeftAndPreserveGuard() {
        PrismalGeometry g = new PrismalGeometry(1000, 800, 500f, 150f, 200f, 100f, 20f);
        PrismalNodeScissor.Rect r = PrismalNodeScissor.compute(g, 1000, 800);
        assertNotNull(r);
        assertEquals(397, r.x);
        assertEquals(597, r.y);
        assertEquals(206, r.width);
        assertEquals(106, r.height);
    }

    @Test public void lowResolutionTargetStillMapsLogicalGeometryCorrectly() {
        PrismalGeometry g = new PrismalGeometry(1000, 800, 500f, 150f, 200f, 100f, 20f);
        PrismalNodeScissor.Rect r = PrismalNodeScissor.compute(g, 500, 400);
        assertNotNull(r);
        assertEquals(198, r.x);
        assertEquals(298, r.y);
        assertEquals(104, r.width);
        assertEquals(54, r.height);
    }

    @Test public void clippedNodesNeverEscapeFramebuffer() {
        PrismalGeometry g = new PrismalGeometry(1000, 800, 5f, 5f, 100f, 100f, 20f);
        PrismalNodeScissor.Rect r = PrismalNodeScissor.compute(g, 1000, 800);
        assertNotNull(r);
        assertEquals(0, r.x);
        assertEquals(742, r.y);
        assertEquals(58, r.width);
        assertEquals(58, r.height);
    }

    @Test public void offscreenNodesAreSkipped() {
        PrismalGeometry g = new PrismalGeometry(1000, 800, -500f, -500f, 100f, 100f, 20f);
        assertNull(PrismalNodeScissor.compute(g, 1000, 800));
    }
}
