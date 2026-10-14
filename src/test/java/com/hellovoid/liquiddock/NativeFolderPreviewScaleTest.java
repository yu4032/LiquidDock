package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class NativeFolderPreviewScaleTest {
    private static NativeFourFolderSizing.Size fourSize() {
        return NativeFourFolderSizing.calculate(718, 718, 360, 360, 288, 288);
    }

    @Test public void defaultPreservesExistingDeviceValidatedGeometry() {
        NativeFourFolderSizing.Size four = fourSize();
        NativeFolderPreviewScale.Geometry a = NativeFolderPreviewScale.four(four, 100);
        assertEquals(four.edgeX, a.edgeX);
        assertEquals(four.gapX, a.gapX);
        assertEquals(four.largeWidth, a.largeWidth);
        assertEquals(four.largeHeight, a.largeHeight);
        assertEquals(four.smallGap, a.smallGap);
        assertEquals(four.smallWidth, a.smallWidth);
        NativeNineFolderSizing.Geometry nine = NativeNineFolderSizing.calculate(646, 649, true);
        NativeFolderPreviewScale.Geometry b = NativeFolderPreviewScale.nine(nine, 100);
        assertEquals(nine.edgeX, b.edgeX);
        assertEquals(nine.gapX, b.gapX);
        assertEquals(nine.largeWidth, b.largeWidth);
        assertEquals(nine.smallWidth, b.smallWidth);
        assertEquals(nine.smallGap, b.smallGap);
    }

    private static void assertFits(NativeFolderPreviewScale.Geometry base,
            NativeFolderPreviewScale.Geometry scaled, int columns, int width, int height) {
        assertTrue(scaled.largeWidth > 0);
        assertTrue(scaled.largeHeight > 0);
        assertTrue(scaled.gapX >= 0);
        assertTrue(scaled.gapY >= 0);
        assertTrue(scaled.smallWidth > 0);
        assertTrue(scaled.smallHeight > 0);
        assertTrue(scaled.smallWidth * 2 + scaled.smallGap <= scaled.largeWidth);
        assertTrue(scaled.smallHeight * 2 + scaled.smallGap <= scaled.largeHeight);
        assertTrue(scaled.edgeX >= 0 && scaled.edgeY >= 0);
        assertTrue(2 * scaled.edgeX + columns * scaled.largeWidth
                + (columns - 1) * scaled.gapX <= width);
        assertTrue(2 * scaled.edgeY + columns * scaled.largeHeight
                + (columns - 1) * scaled.gapY <= height);
        // The slot centers must not drift by more than one pixel.
        float oldCenterX = base.edgeX + base.largeWidth / 2f;
        float newCenterX = scaled.edgeX + scaled.largeWidth / 2f;
        assertTrue(Math.abs(oldCenterX - newCenterX) <= 1f);
        float oldLastX = oldCenterX + (columns - 1) * (base.largeWidth + base.gapX);
        float newLastX = newCenterX + (columns - 1) * (scaled.largeWidth + scaled.gapX);
        assertTrue(Math.abs(oldLastX - newLastX) <= 1f);
    }

    @Test public void fourAndNinePreservePositionsAndSmallGrid() {
        NativeFourFolderSizing.Size four = fourSize();
        NativeFolderPreviewScale.Geometry baseFour = NativeFolderPreviewScale.four(four, 100);
        NativeNineFolderSizing.Geometry nine = NativeNineFolderSizing.calculate(646, 649, true);
        NativeFolderPreviewScale.Geometry baseNine = NativeFolderPreviewScale.nine(nine, 100);
        for (int pct : new int[]{60, 75, 90, 100, 110}) {
            assertFits(baseFour, NativeFolderPreviewScale.four(four, pct), 2,
                    four.backgroundWidth, four.backgroundHeight);
            assertFits(baseNine, NativeFolderPreviewScale.nine(nine, pct), 3, 646, 649);
        }
    }

    @Test public void valuesOutsideGuiRangeAreClamped() {
        NativeFourFolderSizing.Size base = fourSize();
        assertEquals(NativeFolderPreviewScale.four(base, 60).largeWidth,
                NativeFolderPreviewScale.four(base, -500).largeWidth);
        assertEquals(NativeFolderPreviewScale.four(base, 110).largeWidth,
                NativeFolderPreviewScale.four(base, 500).largeWidth);
    }
}
