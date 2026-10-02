package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class HomeGridVendorConfigPolicyTest {
    @Test
    public void tenBySixFitsInsideVendorWorkspace() {
        HomeGridVendorConfigPolicy.Result result = HomeGridVendorConfigPolicy.calculate(
                3008, 1880, 80, 80, 180, 40, 10, 6);
        assertNotNull(result);
        assertEquals(10, result.countX);
        assertEquals(6, result.countY);
        assertEquals((3008 - result.cellSize * 10) / 2, result.left);
    }

    @Test
    public void portraitUsesSwappedCounts() {
        HomeGridVendorConfigPolicy.Result result = HomeGridVendorConfigPolicy.calculate(
                1880, 3008, 80, 80, 180, 40, 6, 10);
        assertNotNull(result);
        assertEquals(6, result.countX);
        assertEquals(10, result.countY);
    }

    @Test
    public void twoByTwoRemainsCenteredInsteadOfExpandingPastWorkspace() {
        HomeGridVendorConfigPolicy.Result result = HomeGridVendorConfigPolicy.calculate(
                3008, 1880, 80, 80, 180, 40, 2, 2);
        assertNotNull(result);
        assertEquals((3008 - result.cellSize * 2) / 2, result.left);
    }
}
