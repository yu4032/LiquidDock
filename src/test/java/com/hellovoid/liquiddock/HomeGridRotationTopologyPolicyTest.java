package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HomeGridRotationTopologyPolicyTest {
    @Test
    public void everySupportedGridRecognizesIdentityAndTranspose() {
        for (int columns = 2; columns <= 10; columns++) {
            for (int rows = 2; rows <= 6; rows++) {
                assertEquals(
                        HomeGridRotationTopologyPolicy.Action.PRESERVE,
                        HomeGridRotationTopologyPolicy.classify(
                                columns, rows, columns, rows));

                if (columns == rows) {
                    assertEquals(
                            HomeGridRotationTopologyPolicy.Action.PRESERVE,
                            HomeGridRotationTopologyPolicy.classify(
                                    columns, rows, rows, columns));
                } else {
                    assertEquals(
                            HomeGridRotationTopologyPolicy.Action.TRANSPOSE,
                            HomeGridRotationTopologyPolicy.classify(
                                    columns, rows, rows, columns));
                }
            }
        }
    }

    @Test
    public void partialOrUnrelatedTopologyFailsClosed() {
        assertEquals(
                HomeGridRotationTopologyPolicy.Action.DEFER,
                HomeGridRotationTopologyPolicy.classify(7, 4, 4, 6));
        assertEquals(
                HomeGridRotationTopologyPolicy.Action.DEFER,
                HomeGridRotationTopologyPolicy.classify(0, 4, 4, 7));
    }
}
