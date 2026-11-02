package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class DockGlassIconOpacityTest {
    @Test public void staticIconFollowsNativeAlpha() {
        assertEquals(1f, DockGlassIconOpacity.resolve(1f, 1f, false), 0f);
        assertEquals(.65f, DockGlassIconOpacity.resolve(1f, .65f, false), 0f);
        assertEquals(.15f, DockGlassIconOpacity.resolve(.5f, .3f, false), .000001f);
        assertEquals(0f, DockGlassIconOpacity.resolve(1f, 0f, false), 0f);
    }

    @Test public void hiddenNativeSourceDoesNotHideFloatingProxy() {
        assertEquals(.5f, DockGlassIconOpacity.resolve(.5f, 0f, true), 0f);
        assertEquals(.75f, DockGlassIconOpacity.resolve(.75f, .2f, true), 0f);
        assertEquals(0f, DockGlassIconOpacity.resolve(0f, 1f, true), 0f);
    }

    @Test public void invalidOrOutOfRangeAlphaIsClamped() {
        assertEquals(0f, DockGlassIconOpacity.resolve(Float.NaN, 1f, false), 0f);
        assertEquals(0f, DockGlassIconOpacity.resolve(1f, Float.NaN, false), 0f);
        assertEquals(0f, DockGlassIconOpacity.resolve(1f, Float.POSITIVE_INFINITY, false), 0f);
        assertEquals(1f, DockGlassIconOpacity.resolve(2f, 2f, false), 0f);
        assertEquals(0f, DockGlassIconOpacity.resolve(1f, -1f, false), 0f);
    }
}
