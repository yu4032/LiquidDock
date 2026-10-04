package com.hellovoid.liquiddock;

import static org.junit.Assert.assertSame;

import org.junit.Test;

public class HomeGridTransformMatrixPolicyTest {
    @Test
    public void emptyTargetCellsRetainVendorSpaceSentinel() {
        Object space = new Object();
        Object[][] template = new Object[4][7];
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 7; y++) {
                template[x][y] = space;
            }
        }

        Object[][] copy = HomeGridTransformMatrixPolicy.copyTemplate(template, 4, 7);

        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 7; y++) {
                assertSame(space, copy[x][y]);
            }
        }
    }
}
