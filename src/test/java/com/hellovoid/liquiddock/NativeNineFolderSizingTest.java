package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class NativeNineFolderSizingTest {
    private static void fitsFourSmallIconsInsideBottomRightCell(int width,
            int height, boolean turner) {
        NativeNineFolderSizing.Geometry g =
                NativeNineFolderSizing.calculate(width, height, turner);
        assertNotNull(g);
        int lastX = g.edgeX + 2 * (g.largeWidth + g.gapX);
        int lastY = g.edgeY + 2 * (g.largeHeight + g.gapY);
        assertEquals(g.largeWidth, g.largeHeight);
        assertEquals(g.smallWidth, g.smallHeight);
        assertTrue(g.smallWidth < g.largeWidth);
        assertTrue(g.smallWidth * 2 + g.smallGap <= g.largeWidth);
        assertTrue(g.smallHeight * 2 + g.smallGap <= g.largeHeight);
        assertTrue(lastX + g.largeWidth <= width);
        assertTrue(lastY + g.largeHeight <= height);
        assertTrue(lastX + g.smallWidth * 2 + g.smallGap <= width - g.edgeX);
        assertTrue(lastY + g.smallHeight * 2 + g.smallGap <= height - g.edgeY);
    }

    @Test public void oemTwelvePreviewLayoutFitsTurnerBackground() {
        fitsFourSmallIconsInsideBottomRightCell(646, 649, true);
        fitsFourSmallIconsInsideBottomRightCell(718, 718, true);
        NativeNineFolderSizing.Geometry g = NativeNineFolderSizing.calculate(646, 649, true);
        assertEquals((int) (646 * .09f), g.edgeX);
        assertEquals((int) (646 * .044f), g.gapX);
        assertEquals((int) (646 * .022f), g.smallGap);
    }

    @Test public void portraitLandscapeAndAlternateDeviceRatios() {
        for (boolean turner : new boolean[]{true, false}) {
            fitsFourSmallIconsInsideBottomRightCell(500, 800, turner);
            fitsFourSmallIconsInsideBottomRightCell(800, 500, turner);
            fitsFourSmallIconsInsideBottomRightCell(480, 480, turner);
        }
    }

    @Test public void rejectZeroAndTooSmallDimensions() {
        assertNull(NativeNineFolderSizing.calculate(0, 600, true));
        assertNull(NativeNineFolderSizing.calculate(600, 0, false));
        assertNull(NativeNineFolderSizing.calculate(1, 1, true));
    }
}
