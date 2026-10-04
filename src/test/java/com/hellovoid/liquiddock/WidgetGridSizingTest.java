package com.hellovoid.liquiddock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WidgetGridSizingTest {
    @Before
    public void enableCustomGridForGeometryTests() {
        WidgetGridSizing.setCustomGridEnabled(true);
    }

    @After
    public void restoreDefaultGridState() {
        WidgetGridSizing.setCustomGridEnabled(false);
    }


    @Test
    public void supportedWidgetSpecsAllowAnyPositiveSpan() {
        assertTrue(WidgetGridSizing.isSupportedSpec(1, 1));
        assertTrue(WidgetGridSizing.isSupportedSpec(2, 1));
        assertTrue(WidgetGridSizing.isSupportedSpec(3, 1));
        assertTrue(WidgetGridSizing.isSupportedSpec(4, 3));
        assertTrue(WidgetGridSizing.isSupportedSpec(10, 6));

        assertFalse(WidgetGridSizing.isSupportedSpec(0, 1));
        assertFalse(WidgetGridSizing.isSupportedSpec(1, 0));
        assertFalse(WidgetGridSizing.isSupportedSpec(-1, 2));
    }

    @Test
    public void disabledCustomGridLeavesWidgetGeometryUntouched() {
        WidgetGridSizing.setCustomGridEnabled(false);
        assertArrayEquals(new int[]{0, 0, 0, 0},
                WidgetGridSizing.gridRect(
                        0, 0, 2, 2,
                        new int[]{0, 112, 224}, new int[]{0, 108, 216},
                        100, 100, 12, 8));
    }

    @Test
    public void oneByOneUsesOnlyTheOccupiedCell() {
        int[] xs = {0, 112, 224};
        int[] ys = {0, 108, 216};

        assertArrayEquals(new int[]{0, 0, 100, 100},
                WidgetGridSizing.gridRect(
                        0, 0, 1, 1, xs, ys, 100, 100, 12, 8));
        assertArrayEquals(new int[]{112, 0, 100, 100},
                WidgetGridSizing.gridRect(
                        1, 0, 1, 1, xs, ys, 100, 100, 12, 8));
    }

    @Test
    public void adjacentWidgetAllocationsShareTheSameBoundary() {
        int[] xs = {0, 112, 224, 336};
        int[] ys = {0, 108, 216};

        int[] oneByOne = WidgetGridSizing.gridRect(
                0, 0, 1, 1, xs, ys, 100, 100, 12, 8);
        int[] twoByOne = WidgetGridSizing.gridRect(
                1, 0, 2, 1, xs, ys, 100, 100, 12, 8);
        int[] top = WidgetGridSizing.gridRect(
                0, 0, 2, 1, xs, ys, 100, 100, 12, 8);
        int[] bottom = WidgetGridSizing.gridRect(
                0, 1, 2, 1, xs, ys, 100, 100, 12, 8);

        assertEquals(12, twoByOne[0] - (oneByOne[0] + oneByOne[2]));
        assertEquals(8, bottom[1] - (top[1] + top[3]));
    }

    @Test
    public void twoByTwoFollowsIndependentHorizontalAndVerticalPitch() {
        int[] xs = {10, 120, 230, 340};
        int[] ys = {20, 150, 280};

        assertArrayEquals(new int[]{10, 20, 210, 230},
                WidgetGridSizing.gridRect(
                        0, 0, 2, 2, xs, ys, 100, 100, 10, 30));
    }

    @Test
    public void stackedWidgetsPreserveTheConfiguredInterCellGap() {
        int[] xs = {0, 112, 224};
        int[] ys = {0, 108, 216};

        int[] whole = WidgetGridSizing.gridRect(
                0, 0, 2, 2, xs, ys, 100, 100, 12, 8);
        int[] top = WidgetGridSizing.gridRect(
                0, 0, 2, 1, xs, ys, 100, 100, 12, 8);
        int[] bottom = WidgetGridSizing.gridRect(
                0, 1, 2, 1, xs, ys, 100, 100, 12, 8);

        assertEquals(whole[0], top[0]);
        assertEquals(whole[0] + whole[2], top[0] + top[2]);
        assertEquals(whole[1], top[1]);
        assertEquals(8, bottom[1] - (top[1] + top[3]));
        assertEquals(whole[1] + whole[3], bottom[1] + bottom[3]);
    }

    @Test
    public void oneByOneWidgetsStayCenteredInsideTheirOwnGridCells() {
        int[] xs = {0, 112, 224};
        int[] ys = {0, 108, 216};

        int[] topLeft = WidgetGridSizing.gridRect(
                0, 0, 1, 1, xs, ys, 100, 100, 12, 8);
        int[] topRight = WidgetGridSizing.gridRect(
                1, 0, 1, 1, xs, ys, 100, 100, 12, 8);
        int[] bottomLeft = WidgetGridSizing.gridRect(
                0, 1, 1, 1, xs, ys, 100, 100, 12, 8);

        assertEquals(50, topLeft[0] + topLeft[2] / 2);
        assertEquals(162, topRight[0] + topRight[2] / 2);
        assertEquals(50, topLeft[1] + topLeft[3] / 2);
        assertEquals(158, bottomLeft[1] + bottomLeft[3] / 2);
    }

    @Test
    public void finalRowAndColumnUseTheirActualOrigins() {
        int[] xs = {10, 120, 230};
        int[] ys = {20, 150, 280};

        assertArrayEquals(new int[]{230, 280, 100, 100},
                WidgetGridSizing.gridRect(
                        2, 2, 1, 1, xs, ys, 100, 100, 10, 30));
    }

    @Test
    public void singleCellAxesUseTheirActualOrigins() {
        int[] xs = {15};
        int[] ys = {25};

        assertArrayEquals(new int[]{15, 25, 100, 100},
                WidgetGridSizing.gridRect(
                        0, 0, 1, 1, xs, ys, 100, 100, 12, 8));
    }

    @Test
    public void marginDrivenGapChangesDoNotShiftAWidgetsCellCenter() {
        int[] compactXs = {40, 160, 280, 400};
        int[] wideXs = {40, 180, 320, 460};
        int[] ys = {20, 128};

        int[] compact = WidgetGridSizing.gridRect(
                1, 0, 2, 1, compactXs, ys, 100, 100, 20, 8);
        int[] wide = WidgetGridSizing.gridRect(
                1, 0, 2, 1, wideXs, ys, 100, 100, 40, 8);

        assertEquals(160, compact[0]);
        assertEquals(220, compact[2]);
        assertEquals(180, wide[0]);
        assertEquals(240, wide[2]);

        // The frame center follows the occupied cell centers, not a synthetic trailing gap.
        assertEquals(270, compact[0] + compact[2] / 2);
        assertEquals(300, wide[0] + wide[2] / 2);
    }

    @Test
    public void widgetMarginsShrinkSymmetricallyAroundTheAllocationCenter() {
        int[] centered = WidgetGridSizing.centeredFrame(
                new int[]{160, 40, 220, 120},
                12, 8, 28, 12);

        assertArrayEquals(new int[]{180, 50, 180, 100}, centered);
        assertEquals(270, centered[0] + centered[2] / 2);
        assertEquals(100, centered[1] + centered[3] / 2);
    }

    @Test
    public void changingWorkspaceGapDoesNotBiasTheCenteredWidgetFrame() {
        int[] compact = WidgetGridSizing.centeredFrame(
                WidgetGridSizing.gridRect(
                        1, 0, 2, 1,
                        new int[]{40, 160, 280, 400}, new int[]{20, 128},
                        100, 100, 20, 8),
                12, 0, 28, 0);
        int[] wide = WidgetGridSizing.centeredFrame(
                WidgetGridSizing.gridRect(
                        1, 0, 2, 1,
                        new int[]{40, 180, 320, 460}, new int[]{20, 128},
                        100, 100, 40, 8),
                12, 0, 28, 0);

        assertEquals(270, compact[0] + compact[2] / 2);
        assertEquals(300, wide[0] + wide[2] / 2);
    }

    @Test
    public void twoByOneUsesActualAdjacentCellOriginsEvenWhenStoredGapIsStale() {
        int[] xs = {40, 180, 320};
        int[] ys = {20, 128};

        int[] first = WidgetGridSizing.gridRect(
                0, 0, 1, 1, xs, ys, 100, 100, 20, 8);
        int[] second = WidgetGridSizing.gridRect(
                1, 0, 1, 1, xs, ys, 100, 100, 20, 8);
        int[] twoByOne = WidgetGridSizing.gridRect(
                0, 0, 2, 1, xs, ys, 100, 100, 20, 8);

        assertEquals(first[0], twoByOne[0]);
        assertEquals(second[0] + second[2], twoByOne[0] + twoByOne[2]);
        assertEquals(240, twoByOne[2]);
    }

    @Test
    public void invalidGridGeometryReturnsEmptyRect() {
        int[] xs = {0, 112};
        int[] ys = {0, 108};
        assertArrayEquals(new int[]{0, 0, 0, 0},
                WidgetGridSizing.gridRect(
                        1, 0, 2, 1, xs, ys, 100, 100, 12, 8));
    }
}
