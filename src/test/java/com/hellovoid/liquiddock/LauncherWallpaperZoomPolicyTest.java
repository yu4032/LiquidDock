package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class LauncherWallpaperZoomPolicyTest {
    @Test public void optOutPreservesEveryVendorTarget() {
        Float vendor = 0.6f;
        assertSame(vendor, LauncherWallpaperZoomPolicy.target(false, vendor));
    }

    @Test public void optInNeutralizesBothZoomOutAndZoomInTargets() {
        assertEquals(1.0f, (Float) LauncherWallpaperZoomPolicy.target(true, 0.6f), 0f);
        assertEquals(1.0f, (Float) LauncherWallpaperZoomPolicy.target(true, 1.2f), 0f);
        assertEquals(1.0f, (Float) LauncherWallpaperZoomPolicy.target(true, 1.0f), 0f);
    }

    @Test public void invalidValuesFailClosedToVendor() {
        Float nan = Float.NaN;
        Float infinity = Float.POSITIVE_INFINITY;
        Float zero = 0f;
        Float negative = -1f;
        assertSame(nan, LauncherWallpaperZoomPolicy.target(true, nan));
        assertSame(infinity, LauncherWallpaperZoomPolicy.target(true, infinity));
        assertSame(zero, LauncherWallpaperZoomPolicy.target(true, zero));
        assertSame(negative, LauncherWallpaperZoomPolicy.target(true, negative));
    }

    @Test public void unexpectedTypesAreNotReinterpreted() {
        Integer other = 1;
        assertSame(other, LauncherWallpaperZoomPolicy.target(true, other));
        assertEquals(null, LauncherWallpaperZoomPolicy.target(true, null));
    }
}
