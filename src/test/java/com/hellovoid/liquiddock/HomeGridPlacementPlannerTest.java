package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class HomeGridPlacementPlannerTest {
    private static final HomeGridDimensions D8X4 = new HomeGridDimensions(8, 4);

    @Test
    public void completeRememberedTargetLayoutWinsOverRepacking() {
        HomeGridLayoutSnapshot remembered = HomeGridLayoutSnapshot.create(
                D8X4, HomeGridOrientation.PORTRAIT,
                Arrays.asList(pos(1,10,0,5,4,2), pos(2,10,3,0,1,1)));

        HomeGridPlacementPlanner.PlanResult result = HomeGridPlacementPlanner.plan(
                D8X4, HomeGridOrientation.PORTRAIT,
                Arrays.asList(pos(1,10,2,1,4,2), pos(2,10,7,3,1,1)),
                remembered);

        assertTrue(result.success());
        assertEquals(0, result.snapshot().get(1).cellX());
        assertEquals(5, result.snapshot().get(1).cellY());
        assertEquals(3, result.snapshot().get(2).cellX());
    }

    @Test
    public void largeItemsReserveBeforeIcons() {
        HomeGridPlacementPlanner.PlanResult result = HomeGridPlacementPlanner.plan(
                D8X4, HomeGridOrientation.PORTRAIT,
                Arrays.asList(pos(101,7,2,2,1,1), pos(100,7,2,1,4,2)), null);

        assertTrue(result.success());
        assertFalse(result.snapshot().get(100).overlaps(result.snapshot().get(101)));
    }

    @Test
    public void arbitraryTwoByTwoOriginIsNotMacroblockRestricted() {
        HomeGridLayoutSnapshot remembered = HomeGridLayoutSnapshot.create(
                D8X4, HomeGridOrientation.PORTRAIT,
                Collections.singletonList(pos(201,6,1,3,2,2)));

        HomeGridPlacementPlanner.PlanResult result = HomeGridPlacementPlanner.plan(
                D8X4, HomeGridOrientation.PORTRAIT,
                Collections.singletonList(pos(201,6,3,1,2,2)), remembered);

        assertTrue(result.success());
        assertEquals(1, result.snapshot().get(201).cellX());
        assertEquals(3, result.snapshot().get(201).cellY());
    }

    @Test
    public void everySupportedDimensionCanPlanFullPageWithoutCrossScreenSpill() {
        for (int columns = 2; columns <= 10; columns++) {
            for (int rows = 2; rows <= 6; rows++) {
                HomeGridDimensions dims = new HomeGridDimensions(columns, rows);
                List<HomeGridItemPosition> source = new ArrayList<>();
                long id = 1;
                for (int y = 0; y < rows; y++) {
                    for (int x = 0; x < columns; x++) {
                        source.add(pos(id++, 42, x, y, 1, 1));
                    }
                }
                HomeGridPlacementPlanner.PlanResult result = HomeGridPlacementPlanner.plan(
                        dims, HomeGridOrientation.PORTRAIT, source, null);
                assertTrue(columns + "x" + rows, result.success());
                assertEquals(columns * rows, result.snapshot().size());
                for (HomeGridItemPosition item : result.snapshot().positions()) {
                    assertEquals(42, item.screenId());
                }
            }
        }
    }

    @Test
    public void impossibleSpanFailsWithoutPartialSnapshot() {
        HomeGridPlacementPlanner.PlanResult result = HomeGridPlacementPlanner.plan(
                D8X4, HomeGridOrientation.PORTRAIT,
                Arrays.asList(pos(1,0,0,0,1,1), pos(2,0,0,0,5,1)), null);
        assertFalse(result.success());
        assertNull(result.snapshot());
    }

    private static HomeGridItemPosition pos(long id,long screen,int x,int y,int sx,int sy) {
        return new HomeGridItemPosition(id,screen,x,y,sx,sy);
    }
}
