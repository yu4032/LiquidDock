package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeFourFolderSizingTest {
    @Test public void customGridUsesActualCellWidthAndHeightInsteadOfTheStockGrid() {
        NativeFourFolderSizing.Size size = NativeFourFolderSizing.calculate(620, 500, 300, 240, 160, 150);
        assertEquals(70, size.insetX);
        assertEquals(45, size.insetY);
        assertEquals(480, size.backgroundWidth);
        assertEquals(410, size.backgroundHeight);
    }

    @Test public void fourIconBoundsFollowTheActualWorkspaceIconSize() {
        NativeFourFolderSizing.Size size = NativeFourFolderSizing.calculate(718, 721, 359, 165);
        assertEquals(97, size.insetX);
        assertEquals(524, size.backgroundWidth);
        assertEquals(527, size.backgroundHeight);
        assertEquals(196, size.largeWidth);
        assertEquals(198, size.largeHeight);
        assertEquals(91, size.smallWidth);
        NativeFourFolderSizing.Size largerIcons = NativeFourFolderSizing.calculate(718, 721, 359, 205);
        assertEquals(564, largerIcons.backgroundWidth);
        assertEquals(567, largerIcons.backgroundHeight);
    }

    @Test public void expandedPreviewUsesBothCellsButKeepsTheStockWidgetInset() {
        NativeFourFolderSizing.Size size = NativeFourFolderSizing.calculate(718, 721, 359, 287);
        assertEquals(36, size.insetX);
        assertEquals(646, size.backgroundWidth);
        assertEquals(649, size.backgroundHeight);
        assertEquals(243, size.largeWidth);
        assertEquals(243, size.largeHeight);
        assertEquals(113, size.smallWidth);
        assertEquals(113, size.smallHeight);
        // The right/bottom icon edges land inside the enlarged background.
        assertEquals(626, size.insetX + size.edgeX + size.largeWidth * 2 + size.gapX);
        assertEquals(627, size.insetY + size.edgeY + size.largeHeight * 2 + size.gapY);
    }

    @Test public void rectangularGridUsesIndependentHorizontalAndVerticalGeometry() {
        NativeFourFolderSizing.Size size = NativeFourFolderSizing.calculate(800, 600, 359, 287);
        assertEquals(728, size.backgroundWidth);
        assertEquals(528, size.backgroundHeight);
        assertEquals(273, size.largeWidth);
        assertEquals(198, size.largeHeight);
        assertTrue(size.largeWidth > size.largeHeight);
        assertTrue(Math.abs(size.largeWidth - (size.smallWidth * 2 + size.smallGap)) <= 1);
    }

    @Test public void compactFolderRetainsItsNativeGeometry() {
        NativeFourFolderSizing.Size size = NativeFourFolderSizing.calculate(359, 359, 359, 287);
        assertEquals(287, size.backgroundWidth);
        assertEquals(108, size.largeWidth);
        assertEquals(50, size.smallWidth);
        assertEquals(25, size.edgeX);
        assertEquals(21, size.gapX);
    }
}
