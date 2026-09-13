package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StageWorkspaceDropLegalityTest {

    @Test
    public void stageModeRejectsOrdinaryItemsFromFirstTwoPhysicalColumns() {
        assertFalse(legal(true, 0, 0, 1, 1));
        assertFalse(legal(true, 1, 0, 1, 1));
        assertFalse(legal(true, 1, 0, 2, 1));
    }

    @Test
    public void stageModeKeepsOrdinaryDesktopPlacementInsideColumnsTwoThroughSeven() {
        assertTrue(legal(true, 2, 0, 1, 1));
        assertTrue(legal(true, 7, 3, 1, 1));
        assertTrue(legal(true, 6, 2, 2, 2));
        assertFalse(legal(true, 7, 2, 2, 1));
    }

    @Test
    public void stageModeStillHonorsTwoByTwoMacroblockRule() {
        assertTrue(legal(true, 2, 0, 2, 2));
        assertFalse(legal(true, 3, 0, 2, 2));
    }

    @Test
    public void disabledStagePreservesExistingEightByFourLegality() {
        assertTrue(legal(false, 0, 0, 1, 1));
        assertTrue(legal(false, 0, 0, 2, 2));
    }

    private static boolean legal(boolean stageEnabled,
                                 int x, int y, int spanX, int spanY) {
        return HomeGridDropLegalityPolicy.isLegal(
                HomeGridProfile.GRID_8X4,
                8, 4,
                x, y, spanX, spanY,
                stageEnabled);
    }
}
