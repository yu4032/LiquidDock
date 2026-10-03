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
    public void enableAdaptationForGeometryTests() {
        WidgetGridSizing.setWidgetAdaptationEnabled(true);
    }

    @After
    public void restoreDefaultAdaptationState() {
        WidgetGridSizing.setWidgetAdaptationEnabled(false);
    }

    @Test
    public void supportedWidgetSpecsAllowAnyPositiveSpan() {
        assertTrue(WidgetGridSizing.isSupportedSpec(1, 1));
        assertTrue(WidgetGridSizing.isSupportedSpec(2, 1));
        assertTrue(WidgetGridSizing.isSupportedSpec(4, 3));
        assertTrue(WidgetGridSizing.isSupportedSpec(10, 6));
        assertFalse(WidgetGridSizing.isSupportedSpec(0, 1));
        assertFalse(WidgetGridSizing.isSupportedSpec(1, 0));
    }

    @Test
    public void widgetAdaptationRequiresGridAndExplicitSwitch() {
        assertFalse(WidgetGridSizing.shouldAdaptWidgets(false, false));
        assertFalse(WidgetGridSizing.shouldAdaptWidgets(false, true));
        assertFalse(WidgetGridSizing.shouldAdaptWidgets(true, false));
        assertTrue(WidgetGridSizing.shouldAdaptWidgets(true, true));
    }

    @Test
    public void disabledAdaptationLeavesWidgetGeometryUntouched() {
        WidgetGridSizing.setWidgetAdaptationEnabled(false);
        assertArrayEquals(new int[]{0, 0, 0, 0},
                WidgetGridSizing.gridRect(
                        0, 0, 2, 2,
                        new int[]{0, 112, 224}, new int[]{0, 108, 216},
                        100, 100, 12, 8));
    }

    @Test
    public void oneByOneUsesCellBoundsNotTrailingGap() {
        int[] xs = {0, 112, 224};
        int[] ys = {0, 108, 216};

        assertArrayEquals(new int[]{0, 0, 100, 100},
                WidgetGridSizing.gridRect(
                        0, 0, 1, 1, xs, ys, 100, 100, 12, 8));
        assertArrayEquals(new int[]{112, 108, 100, 100},
                WidgetGridSizing.gridRect(
                        1, 1, 1, 1, xs, ys, 100, 100, 12, 8));
    }

    @Test
    public void multiSpanEndsAtLastCoveredCellEdge() {
        int[] xs = {10, 120, 230, 340};
        int[] ys = {20, 150, 280};

        assertArrayEquals(new int[]{10, 20, 210, 230},
                WidgetGridSizing.gridRect(
                        0, 0, 2, 2, xs, ys, 100, 100, 10, 30));
        assertArrayEquals(new int[]{120, 150, 320, 230},
                WidgetGridSizing.gridRect(
                        1, 1, 3, 2, xs, ys, 100, 100, 10, 30));
    }

    @Test
    public void finalRowAndColumnNeverAddAphantomTrailingGap() {
        int[] xs = {10, 120, 230};
        int[] ys = {20, 150, 280};

        int[] rect = WidgetGridSizing.gridRect(
                2, 2, 1, 1, xs, ys, 100, 100, 10, 30);
        assertArrayEquals(new int[]{230, 280, 100, 100}, rect);
        assertEquals(330, rect[0] + rect[2]);
        assertEquals(380, rect[1] + rect[3]);
    }

    @Test
    public void singleCellAxisUsesCellSizeOnly() {
        assertArrayEquals(new int[]{15, 25, 100, 100},
                WidgetGridSizing.gridRect(
                        0, 0, 1, 1,
                        new int[]{15}, new int[]{25},
                        100, 100, 12, 8));
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
