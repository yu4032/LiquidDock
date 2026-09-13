package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StageWorkspacePolicyTest {

    @Test
    public void firstLegacyDesktopColumnMapsAfterTwoStageColumns() {
        assertEquals(2, StageWorkspacePolicy.mapLogicalX(0, 1));
    }

    @Test
    public void lastOneWideLegacyDesktopColumnMapsToLastPhysicalColumn() {
        assertEquals(7, StageWorkspacePolicy.mapLogicalX(5, 1));
    }

    @Test
    public void twoWideItemCanEndExactlyAtPhysicalRightEdge() {
        assertEquals(6, StageWorkspacePolicy.mapLogicalX(4, 2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void logicalItemThatExceedsSixColumnDesktopIsRejected() {
        StageWorkspacePolicy.mapLogicalX(5, 2);
    }

    @Test
    public void ordinaryPhysicalPlacementCannotTouchStageColumns() {
        assertFalse(StageWorkspacePolicy.isOrdinaryPhysicalPlacementLegal(0, 1));
        assertFalse(StageWorkspacePolicy.isOrdinaryPhysicalPlacementLegal(1, 1));
        assertFalse(StageWorkspacePolicy.isOrdinaryPhysicalPlacementLegal(1, 2));
        assertTrue(StageWorkspacePolicy.isOrdinaryPhysicalPlacementLegal(2, 1));
        assertTrue(StageWorkspacePolicy.isOrdinaryPhysicalPlacementLegal(6, 2));
        assertFalse(StageWorkspacePolicy.isOrdinaryPhysicalPlacementLegal(7, 2));
    }

    @Test
    public void stageMappingOnlyActivatesForEightByFourLandscapeHome() {
        assertTrue(StageWorkspacePolicy.isSupported(
                HomeGridProfile.GRID_8X4, 8, 4, true));
        assertFalse(StageWorkspacePolicy.isSupported(
                HomeGridProfile.GRID_8X4, 4, 8, true));
        assertFalse(StageWorkspacePolicy.isSupported(
                HomeGridProfile.GRID_10X6, 10, 6, true));
        assertFalse(StageWorkspacePolicy.isSupported(
                HomeGridProfile.GRID_8X4, 8, 4, false));
    }

    @Test
    public void stageBoundsUseFirstAndThirdPhysicalGridCoordinates() {
        assertArrayEquals(new int[]{20, 220},
                StageWorkspacePolicy.stageBounds(
                        new int[]{20, 120, 220, 320, 420, 520, 620, 720}));
    }

    @Test(expected = IllegalArgumentException.class)
    public void stageBoundsRequireThirdGridCoordinate() {
        StageWorkspacePolicy.stageBounds(new int[]{20, 120});
    }
}
