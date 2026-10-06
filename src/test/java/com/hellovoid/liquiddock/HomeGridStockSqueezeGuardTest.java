package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeGridStockSqueezeGuardTest {
    @Test
    public void oneByOneItemsMayMove() {
        assertFalse(HomeGridStockSqueezeGuard.movedMultiCell(
                1, 1, 0, 0, 1, 0));
    }

    @Test
    public void multiCellItemsMayNotMove() {
        assertTrue(HomeGridStockSqueezeGuard.movedMultiCell(
                2, 1, 0, 0, 1, 0));
        assertTrue(HomeGridStockSqueezeGuard.movedMultiCell(
                1, 2, 0, 0, 0, 1));
        assertTrue(HomeGridStockSqueezeGuard.movedMultiCell(
                2, 2, 1, 1, 2, 1));
    }

    @Test
    public void stationaryMultiCellItemsAreAllowedAsBarriers() {
        assertFalse(HomeGridStockSqueezeGuard.movedMultiCell(
                4, 2, 1, 1, 1, 1));
    }
}
