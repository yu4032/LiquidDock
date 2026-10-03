package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeGridDropLegalityPolicyTest {

    @Test
    public void nonStandardTwoByTwoPositionsAreAllowedWithinBounds() {
        assertTrue(legal(8, 4, 1, 0, 2, 2));
        assertTrue(legal(8, 4, 2, 1, 2, 2));
        assertTrue(legal(4, 8, 1, 6, 2, 2));
        assertTrue(legal(10, 6, 7, 4, 2, 2));
    }

    @Test
    public void currentGridOverflowIsRejected() {
        assertFalse(legal(8, 4, 7, 2, 2, 2));
        assertFalse(legal(4, 8, 3, 6, 2, 2));
        assertFalse(legal(10, 6, 9, 5, 2, 2));
    }

    @Test
    public void spansMustAlsoFitAfterRotation() {
        assertTrue(legal(8, 4, 1, 1, 4, 2));
        assertFalse(legal(8, 3, 0, 0, 4, 2));
        assertFalse(legal(5, 4, 0, 0, 2, 5));
    }

    @Test
    public void invalidCoordinatesAndSpansAreRejected() {
        assertFalse(legal(8, 4, -1, 0, 2, 2));
        assertFalse(legal(8, 4, 0, -1, 1, 1));
        assertFalse(legal(8, 4, 0, 0, 0, 1));
        assertFalse(legal(8, 4, 0, 0, 1, 0));
    }

    private static boolean legal(int columns, int rows,
                                 int x, int y, int spanX, int spanY) {
        return HomeGridDropLegalityPolicy.isLegal(
                columns, rows, x, y, spanX, spanY);
    }
}
