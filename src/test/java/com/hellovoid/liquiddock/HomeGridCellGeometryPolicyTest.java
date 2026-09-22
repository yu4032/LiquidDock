package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure geometry coverage for the CellLayout adapter's Android-free policy. */
public class HomeGridCellGeometryPolicyTest {
    private static HomeGridInstallConfig config() {
        return new HomeGridInstallConfig(
                true,
                10, 14, 18, 22,
                6, 8, 12, 16,
                3, 5,
                7, 9,
                2f);
    }

    private static HomeGridWorkstationGeometryConfig workstation() {
        return new HomeGridWorkstationGeometryConfig(
                20,
                24, 30, 34,
                18, 26, 28);
    }

    @Test
    public void landscapeNormalWorkspaceUsesConfiguredOffsets() {
        HomeGridCellGeometryPolicy.Result result = HomeGridCellGeometryPolicy.calculate(
                new HomeGridCellGeometryPolicy.Input(
                        config(), workstation(), false, false, false,
                        1200, 800, 8, 4, 100,
                        160, 80, 0, 1, 100));

        assertNotNull(result);
        assertEquals(210, result.left);
        assertEquals(214, result.right);
        assertEquals(165, result.top);
        assertEquals(269, result.bottom);
        assertEquals(87, result.cellSize);
        assertTrue(result.widthGap >= 0);
        assertEquals(5, result.heightGap);
    }

    @Test
    public void portraitNormalWorkspaceUsesPortraitOffsets() {
        HomeGridCellGeometryPolicy.Result result = HomeGridCellGeometryPolicy.calculate(
                new HomeGridCellGeometryPolicy.Input(
                        config(), workstation(), true, false, false,
                        800, 1200, 4, 8, 100,
                        80, 160, 0, 1, 100));

        assertNotNull(result);
        assertTrue(result.left >= 0);
        assertTrue(result.top >= 0);
        assertEquals(7, result.heightGap);
    }

    @Test
    public void workstationWorkspaceAppliesOnlyHorizontalTranslation() {
        HomeGridCellGeometryPolicy.Result normal = HomeGridCellGeometryPolicy.calculate(
                new HomeGridCellGeometryPolicy.Input(
                        config(), workstation(), false, false, false,
                        1200, 800, 8, 4, 100,
                        160, 80, 0, 1, 100));
        HomeGridCellGeometryPolicy.Result ws = HomeGridCellGeometryPolicy.calculate(
                new HomeGridCellGeometryPolicy.Input(
                        config(), workstation(), false, true, false,
                        1200, 800, 8, 4, 100,
                        160, 80, 0, 1, 100));

        assertNotNull(normal);
        assertNotNull(ws);
        assertEquals(normal.left - config().landscape.left + 20, ws.left);
        assertEquals(normal.right - config().landscape.right - 20, ws.right);
    }

    @Test
    public void workstationAllAppsLandscapeUsesAbsoluteEdges() {
        HomeGridCellGeometryPolicy.Result result = HomeGridCellGeometryPolicy.calculate(
                new HomeGridCellGeometryPolicy.Input(
                        config(), workstation(), false, true, true,
                        1400, 900, 8, 4, 120,
                        100, 60, 20, 30, 0));

        assertNotNull(result);
        assertEquals(24, result.left);
        assertEquals(24, result.right);
        assertEquals(30, result.top);
        assertEquals(34, result.bottom);
        assertEquals((900 - 30 - 34 - result.cellSize * 4) / 3, result.heightGap);
    }

    @Test
    public void workstationAllAppsPortraitUsesPortraitAbsoluteEdges() {
        HomeGridCellGeometryPolicy.Result result = HomeGridCellGeometryPolicy.calculate(
                new HomeGridCellGeometryPolicy.Input(
                        config(), workstation(), true, true, true,
                        900, 1400, 4, 8, 120,
                        80, 100, 18, 22, 0));

        assertNotNull(result);
        assertEquals(18, result.left);
        assertEquals(18, result.right);
        assertEquals(26, result.top);
        assertEquals(28, result.bottom);
    }

    @Test
    public void extremeMarginsClampCellComputationInsteadOfGoingNonPositive() {
        HomeGridInstallConfig extreme = new HomeGridInstallConfig(
                true,
                5000, 5000, 5000, 5000,
                5000, 5000, 5000, 5000,
                5000, 5000,
                0, 0,
                2f);
        HomeGridCellGeometryPolicy.Result result = HomeGridCellGeometryPolicy.calculate(
                new HomeGridCellGeometryPolicy.Input(
                        extreme, workstation(), false, false, false,
                        1000, 700, 8, 4, 100,
                        100, 50, 0, 1, 0));

        assertNotNull(result);
        assertTrue(result.cellSize >= 1);
        assertTrue(result.widthGap >= 0);
    }

    @Test
    public void invalidCountsAreRejected() {
        assertNull(HomeGridCellGeometryPolicy.calculate(
                new HomeGridCellGeometryPolicy.Input(
                        config(), workstation(), false, false, false,
                        1000, 700, 0, 4, 100,
                        100, 50, 0, 1, 0)));
    }

    @Test
    public void rotationSizeMismatchIsExplicitPolicy() {
        assertTrue(HomeGridCellGeometryPolicy.sizeMatchesOrientation(false, 1200, 800));
        assertFalse(HomeGridCellGeometryPolicy.sizeMatchesOrientation(false, 800, 1200));
        assertTrue(HomeGridCellGeometryPolicy.sizeMatchesOrientation(true, 800, 1200));
        assertFalse(HomeGridCellGeometryPolicy.sizeMatchesOrientation(true, 1200, 800));
    }
}
