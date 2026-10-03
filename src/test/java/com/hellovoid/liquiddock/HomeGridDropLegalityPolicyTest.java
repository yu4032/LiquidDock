package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeGridDropLegalityPolicyTest {

    @Test
    public void arbitrarySpansAreFreeInsideLiveGridBounds() {
        assertTrue(legal(9, 5, 0, 0, 1, 1));
        assertTrue(legal(9, 5, 1, 1, 4, 2));
        assertTrue(legal(9, 5, 7, 3, 2, 2));
        assertTrue(legal(7, 4, 2, 1, 3, 2));
    }

    @Test
    public void rightAndBottomEdgeOverflowIsRejected() {
        assertFalse(legal(9, 5, 8, 3, 2, 2));
        assertFalse(legal(9, 5, 7, 4, 2, 2));
        assertFalse(legal(9, 5, 6, 2, 4, 2));
        assertFalse(legal(6, 10, 4, 9, 2, 2));
    }

    @Test
    public void exactEdgeFitRemainsLegal() {
        assertTrue(legal(10, 6, 8, 4, 2, 2));
        assertTrue(legal(6, 10, 2, 8, 4, 2));
        assertTrue(legal(8, 4, 7, 3, 1, 1));
    }

    @Test
    public void invalidCoordinatesAndSpansAreRejected() {
        assertFalse(legal(8, 4, -1, 0, 1, 1));
        assertFalse(legal(8, 4, 0, -1, 1, 1));
        assertFalse(legal(8, 4, 0, 0, 0, 1));
        assertFalse(legal(8, 4, 0, 0, 1, 0));
        assertFalse(legal(0, 4, 0, 0, 1, 1));
    }

    @Test
    public void arithmeticOverflowCannotBypassBounds() {
        assertFalse(legal(10, 6, Integer.MAX_VALUE, 0, 2, 1));
        assertFalse(legal(10, 6, 0, Integer.MAX_VALUE, 1, 2));
        assertFalse(legal(10, 6, 9, 5, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    private static boolean legal(int columns, int rows,
                                 int x, int y, int spanX, int spanY) {
        return HomeGridDropLegalityPolicy.isLegal(
                columns, rows, x, y, spanX, spanY);
    }
}
