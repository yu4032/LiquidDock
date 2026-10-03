package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.List;

/**
 * Cell mapping that preserves MIUI's 2x2 macroblock rotation order without requiring an item to
 * start at a macroblock origin.
 */
final class HomeGridRotationCellMap {
    static final class Cell {
        final int x;
        final int y;

        Cell(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private HomeGridRotationCellMap() {}

    static Cell map(int srcColumns, int srcRows, int dstColumns, int dstRows, int x, int y) {
        if (srcColumns <= 0 || srcRows <= 0 || dstColumns <= 0 || dstRows <= 0
                || srcColumns * srcRows != dstColumns * dstRows
                || x < 0 || x >= srcColumns || y < 0 || y >= srcRows) {
            return null;
        }
        int rank = rank(srcColumns, srcRows, x, y);
        return cellAt(dstColumns, dstRows, rank);
    }

    static int rank(int columns, int rows, int targetX, int targetY) {
        int rank = 0;
        for (int blockY = 0; blockY < rows; blockY += 2) {
            for (int blockX = 0; blockX < columns; blockX += 2) {
                for (int offsetY = 0; offsetY < 2; offsetY++) {
                    for (int offsetX = 0; offsetX < 2; offsetX++) {
                        int x = blockX + offsetX;
                        int y = blockY + offsetY;
                        if (x >= columns || y >= rows) continue;
                        if (x == targetX && y == targetY) return rank;
                        rank++;
                    }
                }
            }
        }
        return -1;
    }

    static Cell cellAt(int columns, int rows, int rank) {
        if (columns <= 0 || rows <= 0 || rank < 0 || rank >= columns * rows) return null;
        int current = 0;
        for (int blockY = 0; blockY < rows; blockY += 2) {
            for (int blockX = 0; blockX < columns; blockX += 2) {
                for (int offsetY = 0; offsetY < 2; offsetY++) {
                    for (int offsetX = 0; offsetX < 2; offsetX++) {
                        int x = blockX + offsetX;
                        int y = blockY + offsetY;
                        if (x >= columns || y >= rows) continue;
                        if (current == rank) return new Cell(x, y);
                        current++;
                    }
                }
            }
        }
        return null;
    }

    static List<Cell> scanOrder(int columns, int rows) {
        ArrayList<Cell> cells = new ArrayList<>(Math.max(0, columns * rows));
        for (int rank = 0; rank < columns * rows; rank++) {
            Cell cell = cellAt(columns, rows, rank);
            if (cell != null) cells.add(cell);
        }
        return cells;
    }
}
