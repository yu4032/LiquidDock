package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GridWidget4x2PreflightPolicyTest {
    @Test public void onlyLoweringUnderFourRequiresPreflight() {
        assertTrue(GridWidget4x2PreflightPolicy.needsCheck(4, 3));
        assertTrue(GridWidget4x2PreflightPolicy.needsCheck(10, 2));
        assertTrue(GridWidget4x2PreflightPolicy.needsCheck(3, 2));
        assertFalse(GridWidget4x2PreflightPolicy.needsCheck(4, 4));
        assertFalse(GridWidget4x2PreflightPolicy.needsCheck(3, 4));
        assertFalse(GridWidget4x2PreflightPolicy.needsCheck(5, 4));
        assertFalse(GridWidget4x2PreflightPolicy.needsCheck(3, 3));
    }

    @Test public void detectsFourByTwoAndItsRotatedOrientationOnDesktop() {
        assertTrue(GridWidget4x2PreflightPolicy.matches(-100, 4, 2));
        assertTrue(GridWidget4x2PreflightPolicy.matches(-100, 2, 4));
        assertFalse(GridWidget4x2PreflightPolicy.matches(-100, 3, 2));
        assertFalse(GridWidget4x2PreflightPolicy.matches(-100, 4, 1));
        assertFalse(GridWidget4x2PreflightPolicy.matches(-101, 4, 2));
        assertFalse(GridWidget4x2PreflightPolicy.matches(-1, 4, 2));
    }
}
