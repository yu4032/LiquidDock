package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeGridRestorePolicyTest {

    @Test
    public void topologyMustMatchConfiguredGridExactly() {
        int[] xs = {10, 110, 210, 310, 410};
        int[] ys = {20, 120, 220, 320, 420, 520, 620};

        assertTrue(HomeGridRestorePolicy.matrixReady(5, 7, 5, 7, xs, ys));
        assertFalse(HomeGridRestorePolicy.matrixReady(5, 7, 4, 7, xs, ys));
        assertFalse(HomeGridRestorePolicy.matrixReady(5, 7, 5, 6, xs, ys));
        assertFalse(HomeGridRestorePolicy.matrixReady(
                5, 7, 5, 7, new int[]{10, 110}, ys));
    }

    @Test
    public void physicalGridMustRemainInsideCellLayout() {
        assertTrue(HomeGridRestorePolicy.gridEnvelopeInside(
                600, 800, 90, 90,
                new int[]{20, 120, 220, 320, 420},
                new int[]{30, 130, 230, 330, 430, 530, 630}));

        assertFalse(HomeGridRestorePolicy.gridEnvelopeInside(
                500, 800, 90, 90,
                new int[]{20, 120, 220, 320, 420},
                new int[]{30, 130, 230, 330, 430, 530, 630}));

        assertFalse(HomeGridRestorePolicy.gridEnvelopeInside(
                600, 700, 90, 90,
                new int[]{20, 120, 220, 320, 420},
                new int[]{30, 130, 230, 330, 430, 530, 630}));
    }

    @Test
    public void duplicateOrReversedPhysicalOriginsAreNotReady() {
        assertFalse(HomeGridRestorePolicy.gridEnvelopeInside(
                500, 500, 80, 80,
                new int[]{0, 100, 100, 300},
                new int[]{0, 100, 200, 300}));
        assertFalse(HomeGridRestorePolicy.gridEnvelopeInside(
                500, 500, 80, 80,
                new int[]{0, 100, 200, 300},
                new int[]{0, 100, 90, 300}));
    }
}
