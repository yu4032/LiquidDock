package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class NativeNineFolderSizingTest {
    @Test public void ninePreviewUsesThreeRowsAndIndependentDimensions() {
        NativeNineFolderSizing.Geometry g = NativeNineFolderSizing.calculate(646, 649);
        assertTrue(g.iconWidth > 0);
        assertTrue(g.iconHeight > 0);
        assertTrue(g.edgeX * 2 + g.gapX * 2 + g.iconWidth * 3 <= 646);
        assertTrue(g.edgeY * 2 + g.gapY * 2 + g.iconHeight * 3 <= 649);
        NativeNineFolderSizing.Geometry rectangular = NativeNineFolderSizing.calculate(800, 500);
        assertTrue(rectangular.iconWidth > rectangular.iconHeight);
    }
}
