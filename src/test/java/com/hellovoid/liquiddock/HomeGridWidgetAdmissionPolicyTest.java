package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeGridWidgetAdmissionPolicyTest {
    @Test
    public void fourByTwoIsRejectedOnEightByThreeHomeGrid() {
        assertTrue(HomeGridWidgetAdmissionPolicy.shouldReject(
                "land_grid", 8, 3, 8, 3, 4, 2));
        assertTrue(HomeGridWidgetAdmissionPolicy.shouldReject(
                "vertical_grid", 3, 8, 8, 3, 4, 2));
    }

    @Test
    public void fourByTwoMayFitCurrentLandscapeButIsRejectedAtCommitForRotationSafety() {
        assertTrue(HomeGridDropLegalityPolicy.isLegal(
                8, 3, 0, 0, 4, 2));
        assertTrue(HomeGridWidgetAdmissionPolicy.shouldReject(
                "land_grid", 8, 3, 8, 3, 4, 2));
    }

    @Test
    public void twoByTwoRemainsAdmissibleOnEightByThree() {
        assertFalse(HomeGridWidgetAdmissionPolicy.shouldReject(
                "land_grid", 8, 3, 8, 3, 2, 2));
        assertFalse(HomeGridWidgetAdmissionPolicy.shouldReject(
                "vertical_grid", 3, 8, 8, 3, 2, 2));
    }

    @Test
    public void fourByTwoRemainsAdmissibleWhenBothOrientationsFit() {
        assertFalse(HomeGridWidgetAdmissionPolicy.shouldReject(
                "land_grid", 8, 4, 8, 4, 4, 2));
        assertFalse(HomeGridWidgetAdmissionPolicy.shouldReject(
                "vertical_grid", 4, 8, 8, 4, 4, 2));
    }

    @Test
    public void nativeAdmissionGuardDoesNotOwnSplitOrUnrelatedGrids() {
        assertFalse(HomeGridWidgetAdmissionPolicy.shouldReject(
                "land_split_grid", 3, 8, 8, 3, 4, 2));
        assertFalse(HomeGridWidgetAdmissionPolicy.shouldReject(
                "vendor_grid", 8, 3, 8, 3, 4, 2));
    }
}
