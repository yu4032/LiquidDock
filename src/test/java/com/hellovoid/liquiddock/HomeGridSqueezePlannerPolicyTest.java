package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeGridSqueezePlannerPolicyTest {
    @Test
    public void stockSixByFourKeepsNativeOneByOnePlanner() {
        assertTrue(HomeGridSqueezePlannerPolicy.isVendorPadGrid(6, 4));
        assertTrue(HomeGridSqueezePlannerPolicy.isVendorPadGrid(4, 6));
        assertFalse(HomeGridSqueezePlannerPolicy.useGenericForSpan(true, 1, 1));
        assertFalse(HomeGridSqueezePlannerPolicy.useGenericForSqueeze(
                true, false, 1, 1));
    }

    @Test
    public void everyNonVendorGridUsesGenericPlannerEvenForOneByOneIcons() {
        assertFalse(HomeGridSqueezePlannerPolicy.isVendorPadGrid(8, 4));
        assertFalse(HomeGridSqueezePlannerPolicy.isVendorPadGrid(4, 8));
        assertFalse(HomeGridSqueezePlannerPolicy.isVendorPadGrid(10, 6));
        assertFalse(HomeGridSqueezePlannerPolicy.isVendorPadGrid(6, 10));
        assertTrue(HomeGridSqueezePlannerPolicy.useGenericForSpan(false, 1, 1));
        assertTrue(HomeGridSqueezePlannerPolicy.useGenericForSqueeze(
                false, false, 1, 1));
    }

    @Test
    public void multiCellItemsUseGenericPlannerOnVendorGridToo() {
        assertTrue(HomeGridSqueezePlannerPolicy.useGenericForSpan(true, 2, 1));
        assertTrue(HomeGridSqueezePlannerPolicy.useGenericForSpan(true, 1, 2));
        assertTrue(HomeGridSqueezePlannerPolicy.useGenericForSpan(true, 2, 2));
        assertTrue(HomeGridSqueezePlannerPolicy.useGenericForSqueeze(
                true, true, 1, 1));
        assertTrue(HomeGridSqueezePlannerPolicy.useGenericForSqueeze(
                true, false, 2, 1));
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
