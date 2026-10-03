package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeGridSqueezeBoundsPolicyTest {
    @Test
    public void allFortyFiveFreeGridSizesUseRuntimeBounds() {
        int count = 0;
        for (int columns = 2; columns <= 10; columns++) {
            for (int rows = 2; rows <= 6; rows++) {
                assertTrue(HomeGridSqueezeBoundsPolicy.fits(
                        columns, rows, 0, 0, columns, rows));
                assertTrue(HomeGridSqueezeBoundsPolicy.fits(
                        columns, rows, columns - 1, rows - 1, 1, 1));

                assertFalse(HomeGridSqueezeBoundsPolicy.fits(
                        columns, rows, columns, 0, 1, 1));
                assertFalse(HomeGridSqueezeBoundsPolicy.fits(
                        columns, rows, 0, rows, 1, 1));
                assertFalse(HomeGridSqueezeBoundsPolicy.fits(
                        columns, rows, columns - 1, 0, 2, 1));
                assertFalse(HomeGridSqueezeBoundsPolicy.fits(
                        columns, rows, 0, rows - 1, 1, 2));

                // The same policy must work when the runtime matrix is the rotated transpose.
                assertTrue(HomeGridSqueezeBoundsPolicy.fits(
                        rows, columns, rows - 1, columns - 1, 1, 1));
                assertFalse(HomeGridSqueezeBoundsPolicy.fits(
                        rows, columns, rows, 0, 1, 1));
                assertFalse(HomeGridSqueezeBoundsPolicy.fits(
                        rows, columns, 0, columns, 1, 1));
                count++;
            }
        }
        assertTrue(count == 45);
    }

    @Test
    public void invalidSpansAndCoordinatesFailClosed() {
        assertFalse(HomeGridSqueezeBoundsPolicy.fits(8, 4, -1, 0, 1, 1));
        assertFalse(HomeGridSqueezeBoundsPolicy.fits(8, 4, 0, -1, 1, 1));
        assertFalse(HomeGridSqueezeBoundsPolicy.fits(8, 4, 0, 0, 0, 1));
        assertFalse(HomeGridSqueezeBoundsPolicy.fits(8, 4, 0, 0, 1, 0));
        assertFalse(HomeGridSqueezeBoundsPolicy.fits(0, 4, 0, 0, 1, 1));
        assertFalse(HomeGridSqueezeBoundsPolicy.fits(8, 0, 0, 0, 1, 1));
    }
}
