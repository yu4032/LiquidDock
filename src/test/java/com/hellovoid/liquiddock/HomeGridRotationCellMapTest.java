package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class HomeGridRotationCellMapTest {
    @Test
    public void standardEightByFourUsesMiuiMacroblockOrder() {
        assertCell(0, 0, HomeGridRotationCellMap.map(8, 4, 4, 8, 0, 0));
        assertCell(2, 0, HomeGridRotationCellMap.map(8, 4, 4, 8, 2, 0));
        assertCell(0, 2, HomeGridRotationCellMap.map(8, 4, 4, 8, 4, 0));
        assertCell(2, 2, HomeGridRotationCellMap.map(8, 4, 4, 8, 6, 0));
    }

    @Test
    public void nonStandardOffsetIsPreservedInsideMacroblock() {
        assertCell(1, 0, HomeGridRotationCellMap.map(8, 4, 4, 8, 1, 0));
        assertCell(3, 1, HomeGridRotationCellMap.map(8, 4, 4, 8, 3, 1));
        assertCell(1, 3, HomeGridRotationCellMap.map(8, 4, 4, 8, 5, 1));
    }

    @Test
    public void mappingRoundTripsForAllSupportedGridSizes() {
        for (int columns = 2; columns <= 10; columns++) {
            for (int rows = 2; rows <= 6; rows++) {
                for (int y = 0; y < rows; y++) {
                    for (int x = 0; x < columns; x++) {
                        HomeGridRotationCellMap.Cell mapped =
                                HomeGridRotationCellMap.map(
                                        columns, rows, rows, columns, x, y);
                        assertNotNull(mapped);
                        HomeGridRotationCellMap.Cell restored =
                                HomeGridRotationCellMap.map(
                                        rows, columns, columns, rows,
                                        mapped.x, mapped.y);
                        assertNotNull(restored);
                        assertEquals(x, restored.x);
                        assertEquals(y, restored.y);
                    }
                }
            }
        }
    }

    @Test
    public void oddGridMappingIsInBoundsAndBijective() {
        int srcColumns = 9;
        int srcRows = 5;
        int dstColumns = 5;
        int dstRows = 9;
        Set<String> seen = new HashSet<>();
        for (int y = 0; y < srcRows; y++) {
            for (int x = 0; x < srcColumns; x++) {
                HomeGridRotationCellMap.Cell mapped =
                        HomeGridRotationCellMap.map(
                                srcColumns, srcRows, dstColumns, dstRows, x, y);
                assertNotNull(mapped);
                assertTrue(mapped.x >= 0 && mapped.x < dstColumns);
                assertTrue(mapped.y >= 0 && mapped.y < dstRows);
                assertTrue(seen.add(mapped.x + ":" + mapped.y));
            }
        }
        assertEquals(srcColumns * srcRows, seen.size());
    }

    private static void assertCell(int x, int y, HomeGridRotationCellMap.Cell actual) {
        assertNotNull(actual);
        assertEquals(x, actual.x);
        assertEquals(y, actual.y);
    }
}
