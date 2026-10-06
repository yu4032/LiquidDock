package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeGridSqueezePlannerPolicyTest {
    @Test
    public void oneByOneIconsKeepStockPadPlannersOnCustomGrids() {
        assertFalse(HomeGridSqueezePlannerPolicy.useGenericForSpan(1, 1));
        assertFalse(HomeGridSqueezePlannerPolicy.useGenericForSqueeze(
                false, 1, 1));
    }

    @Test
    public void multiCellItemsAndSpanMovesUseGenericRectangularPlanners() {
        assertTrue(HomeGridSqueezePlannerPolicy.useGenericForSpan(2, 1));
        assertTrue(HomeGridSqueezePlannerPolicy.useGenericForSpan(1, 2));
        assertTrue(HomeGridSqueezePlannerPolicy.useGenericForSpan(2, 2));
        assertTrue(HomeGridSqueezePlannerPolicy.useGenericForSqueeze(
                true, 1, 1));
        assertTrue(HomeGridSqueezePlannerPolicy.useGenericForSqueeze(
                false, 2, 1));
    }

    @Test
    public void allConfiguredLayoutsMatchFullScreenAndSplitWorkspaceShapes() {
        for (int columns = 2; columns <= 10; columns++) {
            for (int rows = 2; rows <= 6; rows++) {
                assertTrue(HomeGridSqueezePlannerPolicy.matches(
                        "land_grid", columns, rows, columns, rows));
                assertTrue(HomeGridSqueezePlannerPolicy.matches(
                        "vertical_grid", rows, columns, columns, rows));
                assertTrue(HomeGridSqueezePlannerPolicy.matches(
                        "land_split_grid", rows, columns, columns, rows));
            }
        }
    }

    @Test
    public void nonHomeControllersAreNeverRebound() {
        assertFalse(HomeGridSqueezePlannerPolicy.matches(
                "land_grid_all_apps", 10, 5, 10, 5));
        assertFalse(HomeGridSqueezePlannerPolicy.matches(
                "vertical_grid_all_apps", 5, 10, 10, 5));
        assertFalse(HomeGridSqueezePlannerPolicy.matches(
                null, 10, 5, 10, 5));
    }

    @Test
    public void mismatchedHomeGridIsNotRebound() {
        assertFalse(HomeGridSqueezePlannerPolicy.matches(
                "land_grid", 8, 4, 10, 5));
        assertFalse(HomeGridSqueezePlannerPolicy.matches(
                "vertical_grid", 4, 8, 10, 5));
        assertFalse(HomeGridSqueezePlannerPolicy.matches(
                "land_split_grid", 4, 8, 10, 5));
    }
}
