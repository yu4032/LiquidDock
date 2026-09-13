package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StageWorkspaceOverlayGeometryTest {

    @Test
    public void overlayUsesFirstAndThirdPhysicalGridCoordinates() {
        StageWorkspaceOverlayGeometry.Geometry geometry = StageWorkspaceOverlayGeometry.resolve(
                HomeGridProfile.GRID_8X4,
                8,
                4,
                true,
                new int[]{24, 124, 224, 324, 424, 524, 624, 724},
                40,
                760);

        assertEquals(24, geometry.left());
        assertEquals(224, geometry.right());
        assertEquals(40, geometry.top());
        assertEquals(760, geometry.bottom());
        assertEquals(200, geometry.width());
        assertEquals(720, geometry.height());
    }

    @Test
    public void overlayFailsClosedOutsideEightByFourLandscapeHome() {
        int[] xs = new int[]{24, 124, 224, 324, 424, 524, 624, 724};
        assertNull(StageWorkspaceOverlayGeometry.resolve(
                HomeGridProfile.GRID_8X4, 8, 4, false, xs, 40, 760));
        assertNull(StageWorkspaceOverlayGeometry.resolve(
                HomeGridProfile.GRID_10X6, 10, 6, true, xs, 40, 760));
        assertNull(StageWorkspaceOverlayGeometry.resolve(
                HomeGridProfile.GRID_8X4, 4, 8, true, xs, 40, 760));
    }

    @Test
    public void overlayRejectsMissingOrNonIncreasingGridAuthority() {
        assertNull(StageWorkspaceOverlayGeometry.resolve(
                HomeGridProfile.GRID_8X4, 8, 4, true,
                new int[]{24, 124}, 40, 760));
        assertNull(StageWorkspaceOverlayGeometry.resolve(
                HomeGridProfile.GRID_8X4, 8, 4, true,
                new int[]{24, 124, 24, 324, 424, 524, 624, 724}, 40, 760));
        assertNull(StageWorkspaceOverlayGeometry.resolve(
                HomeGridProfile.GRID_8X4, 8, 4, true,
                new int[]{24, 124, 224, 324, 424, 524, 624, 724}, 760, 40));
    }

    @Test
    public void overlayRejectsGeometryThatCannotRepresentAllPhysicalColumns() {
        assertNull(StageWorkspaceOverlayGeometry.resolve(
                HomeGridProfile.GRID_8X4, 8, 4, true,
                new int[]{24, 124, 224}, 40, 760));
    }
}
