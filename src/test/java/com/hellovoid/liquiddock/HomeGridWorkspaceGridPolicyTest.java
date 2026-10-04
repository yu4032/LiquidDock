package com.hellovoid.liquiddock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeGridWorkspaceGridPolicyTest {
    @Test
    public void allFortyFiveConfigurationsMapWithoutHardCodedSplitShape() {
        int combinations = 0;
        for (int columns = 2; columns <= 10; columns++) {
            for (int rows = 2; rows <= 6; rows++) {
                combinations++;
                assertArrayEquals(
                        new int[]{columns, rows},
                        HomeGridWorkspaceGridPolicy.fullScreenCounts(
                                "land_grid", columns, rows));
                assertArrayEquals(
                        new int[]{rows, columns},
                        HomeGridWorkspaceGridPolicy.fullScreenCounts(
                                "vertical_grid", columns, rows));
                assertArrayEquals(
                        new int[]{rows, columns},
                        HomeGridWorkspaceGridPolicy.splitCounts(columns, rows));
            }
        }
        org.junit.Assert.assertEquals(45, combinations);
    }

    @Test
    public void splitProfileIsNotPostProcessedAsFullScreenGeometry() {
        assertTrue(HomeGridWorkspaceGridPolicy.isSplitGridName("land_split_grid"));
        assertNull(HomeGridWorkspaceGridPolicy.fullScreenCounts(
                "land_split_grid", 7, 4));
        assertNull(HomeGridWorkspaceGridPolicy.fullScreenCounts(
                "land_grid_all_apps", 7, 4));
        assertNull(HomeGridWorkspaceGridPolicy.fullScreenCounts(
                "vertical_grid_all_apps", 7, 4));
    }
}
