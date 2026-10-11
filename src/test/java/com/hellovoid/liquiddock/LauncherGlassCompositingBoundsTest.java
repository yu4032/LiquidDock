package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

/** Guards the conservative transparent TextureView compositor cropping model. */
public class LauncherGlassCompositingBoundsTest {
    @Test public void unionContainsEveryWidgetPlusAntiAliasingGuard() {
        LauncherGlassCompositingBounds b = new LauncherGlassCompositingBounds();
        assertTrue(b.include(250, 300, 200, 400));
        assertTrue(b.include(750, 600, 300, 250));
        assertTrue(b.finish(3008, 1880));
        assertEquals(130, b.left);
        assertEquals(80, b.top);
        assertEquals(920, b.right);
        assertEquals(745, b.bottom);
        assertTrue(b.area() < 3008L*1880L);
    }

    @Test public void fullscreenOrNearFullscreenScenesStayUncropped() {
        LauncherGlassCompositingBounds b = new LauncherGlassCompositingBounds();
        assertTrue(b.include(1504, 940, 3000, 1870));
        assertFalse(b.finish(3008, 1880));
    }

    @Test public void outOfBoundsGeometryCannotClipAwayVisiblePart() {
        LauncherGlassCompositingBounds b = new LauncherGlassCompositingBounds();
        assertTrue(b.include(-100, 500, 300, 400));
        assertTrue(b.finish(3008, 1880));
        assertEquals(0, b.left);
        assertEquals(280, b.top);
        assertEquals(70, b.right);
        assertEquals(720, b.bottom);
    }

    @Test public void invalidInputsMustFailClosedInsteadOfAClippedOutput() {
        LauncherGlassCompositingBounds b = new LauncherGlassCompositingBounds();
        assertFalse(b.include(Float.NaN, 500, 50, 50));
        assertFalse(b.include(10, 20, Float.POSITIVE_INFINITY, 100));
        assertFalse(b.include(10, 20, 0, 100));
        assertFalse(b.finish(3008, 1880));
        assertTrue(b.include(300, 500, 80, 120));
        assertFalse(b.include(1e30f, 500, 80, 120));
        // Caller must ignore the whole clip when ANY node is invalid.
        b.reset();
        assertFalse(b.finish(3008, 1880));
    }

    @Test public void resetDoesNotRetainOldBoundsAcrossSurfaceRebind() {
        LauncherGlassCompositingBounds b = new LauncherGlassCompositingBounds();
        b.include(200, 300, 160, 100);
        assertTrue(b.finish(3008, 1880));
        b.reset();
        assertFalse(b.valid);
        assertFalse(b.finish(3008, 1880));
        b.include(2500, 1300, 200, 150);
        assertTrue(b.finish(3008, 1880));
        assertTrue(b.left > 2000);
    }
}
