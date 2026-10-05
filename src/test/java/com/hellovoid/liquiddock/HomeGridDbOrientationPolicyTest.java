package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class HomeGridDbOrientationPolicyTest {
    @Test
    public void eightByFiveUsesPortraitSentinelOnlyForPortraitProbe() {
        assertEquals(8, HomeGridDbOrientationPolicy.probeCellCountX(false, 8));
        assertEquals(
                HomeGridDbOrientationPolicy.VENDOR_PORTRAIT_SENTINEL_X,
                HomeGridDbOrientationPolicy.probeCellCountX(true, 5));
    }

    @Test
    public void landscapeFourColumnGridIsNeverMisclassifiedAsPortrait() {
        int probe = HomeGridDbOrientationPolicy.probeCellCountX(false, 4);
        assertNotEquals(HomeGridDbOrientationPolicy.VENDOR_PORTRAIT_SENTINEL_X, probe);
    }

    @Test
    public void fullScreenSyncNeverClaimsSplitOrMismatchedGrids() {
        assertEquals(true, HomeGridDbOrientationPolicy.isConfiguredFullScreenGrid(
                "land_grid", 8, 5, 8, 5));
        assertEquals(true, HomeGridDbOrientationPolicy.isConfiguredFullScreenGrid(
                "vertical_grid", 5, 8, 8, 5));
        assertEquals(false, HomeGridDbOrientationPolicy.isConfiguredFullScreenGrid(
                "land_split_grid", 5, 8, 8, 5));
        assertEquals(false, HomeGridDbOrientationPolicy.isConfiguredFullScreenGrid(
                "land_grid", 5, 8, 8, 5));
    }

    @Test
    public void everySupportedFreeGridProducesCorrectLegacyOrientationPredicate() {
        for (int columns = 2; columns <= 10; columns++) {
            for (int rows = 2; rows <= 6; rows++) {
                int landscapeProbe =
                        HomeGridDbOrientationPolicy.probeCellCountX(false, columns);
                int portraitProbe =
                        HomeGridDbOrientationPolicy.probeCellCountX(true, rows);
                assertNotEquals(
                        HomeGridDbOrientationPolicy.VENDOR_PORTRAIT_SENTINEL_X,
                        landscapeProbe);
                assertEquals(
                        HomeGridDbOrientationPolicy.VENDOR_PORTRAIT_SENTINEL_X,
                        portraitProbe);
            }
        }
    }
}
