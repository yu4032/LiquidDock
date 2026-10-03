package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HomeGridRotationPlacementPolicyTest {

    @Test
    public void oddSizedGridAllowsOddWidgetOrigin() {
        boolean[][] occupied = new boolean[5][7];
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 7; y++) occupied[x][y] = true;
        }
        for (int x = 1; x < 3; x++) {
            for (int y = 1; y < 3; y++) occupied[x][y] = false;
        }

        int[] cell = HomeGridRotationPlacementPolicy.findNearestFreeCell(
                occupied, 5, 7, 2, 2, 7, 5, 2, 1);

        assertNotNull(cell);
        assertEquals(1, cell[0]);
        assertEquals(1, cell[1]);
    }

    @Test
    public void everySupportedGridKeepsFullPageIconsOnPage() {
        for (int columns = 2; columns <= 10; columns++) {
            for (int rows = 2; rows <= 6; rows++) {
                verifyFullPageRotation(columns, rows);
            }
        }
    }

    @Test
    public void everySpanThatFitsBothOrientationsHasAPlacement() {
        for (int columns = 2; columns <= 10; columns++) {
            for (int rows = 2; rows <= 6; rows++) {
                int targetColumns = rows;
                int targetRows = columns;
                int maxSpanX = Math.min(columns, targetColumns);
                int maxSpanY = Math.min(rows, targetRows);
                for (int spanX = 1; spanX <= maxSpanX; spanX++) {
                    for (int spanY = 1; spanY <= maxSpanY; spanY++) {
                        boolean[][] occupied = new boolean[targetColumns][targetRows];
                        int[] cell = HomeGridRotationPlacementPolicy.findNearestFreeCell(
                                occupied, targetColumns, targetRows,
                                spanX, spanY, columns, rows, 0, 0);
                        assertNotNull(columns + "x" + rows + " span "
                                + spanX + "x" + spanY, cell);
                    }
                }
            }
        }
    }

    private static void verifyFullPageRotation(int sourceColumns, int sourceRows) {
        int targetColumns = sourceRows;
        int targetRows = sourceColumns;
        boolean[][] occupied = new boolean[targetColumns][targetRows];

        int widgetX = sourceColumns > 2 ? 1 : 0;
        int widgetY = sourceRows > 2 ? 1 : 0;
        int[] widget = HomeGridRotationPlacementPolicy.findNearestFreeCell(
                occupied, targetColumns, targetRows,
                2, 2, sourceColumns, sourceRows, widgetX, widgetY);
        assertNotNull(sourceColumns + "x" + sourceRows, widget);
        assertTrue(HomeGridRotationPlacementPolicy.reserve(
                occupied, targetColumns, targetRows,
                widget[0], widget[1], 2, 2));

        for (int y = 0; y < sourceRows; y++) {
            for (int x = 0; x < sourceColumns; x++) {
                boolean underWidget = x >= widgetX && x < widgetX + 2
                        && y >= widgetY && y < widgetY + 2;
                if (underWidget) continue;
                int[] icon = HomeGridRotationPlacementPolicy.findNearestFreeCell(
                        occupied, targetColumns, targetRows,
                        1, 1, sourceColumns, sourceRows, x, y);
                assertNotNull("icon spill at " + sourceColumns + "x" + sourceRows, icon);
                assertTrue(HomeGridRotationPlacementPolicy.reserve(
                        occupied, targetColumns, targetRows,
                        icon[0], icon[1], 1, 1));
            }
        }

        int filled = 0;
        for (int x = 0; x < targetColumns; x++) {
            for (int y = 0; y < targetRows; y++) {
                if (occupied[x][y]) filled++;
            }
        }
        assertEquals(sourceColumns * sourceRows, filled);
    }
}
