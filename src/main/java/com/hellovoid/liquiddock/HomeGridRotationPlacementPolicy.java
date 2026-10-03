package com.hellovoid.liquiddock;

/**
 * Pure placement policy for custom-grid rotation.
 *
 * <p>No profile or span is special-cased. Every item searches the actual destination matrix for
 * the nearest free in-bounds cell using normalized source/target centers. Larger items are reserved
 * first by the caller, so 1x1 icons consume the remaining same-page vacancies.</p>
 */
final class HomeGridRotationPlacementPolicy {
    private static final double EPSILON = 1.0e-12;

    private HomeGridRotationPlacementPolicy() {}

    static int[] findNearestFreeCell(
            boolean[][] occupied,
            int targetColumns,
            int targetRows,
            int spanX,
            int spanY,
            int sourceColumns,
            int sourceRows,
            int sourceX,
            int sourceY) {
        if (occupied == null
                || targetColumns <= 0 || targetRows <= 0
                || sourceColumns <= 0 || sourceRows <= 0
                || spanX <= 0 || spanY <= 0
                || spanX > targetColumns || spanY > targetRows) {
            return null;
        }

        double sourceCenterX = (sourceX + spanX / 2.0) / sourceColumns;
        double sourceCenterY = (sourceY + spanY / 2.0) / sourceRows;
        double bestDistance = Double.POSITIVE_INFINITY;
        int bestX = -1;
        int bestY = -1;

        for (int y = 0; y <= targetRows - spanY; y++) {
            for (int x = 0; x <= targetColumns - spanX; x++) {
                if (!isFree(occupied, targetColumns, targetRows, x, y, spanX, spanY)) {
                    continue;
                }
                double targetCenterX = (x + spanX / 2.0) / targetColumns;
                double targetCenterY = (y + spanY / 2.0) / targetRows;
                double dx = targetCenterX - sourceCenterX;
                double dy = targetCenterY - sourceCenterY;
                double distance = dx * dx + dy * dy;
                if (distance + EPSILON < bestDistance) {
                    bestDistance = distance;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        return bestX < 0 ? null : new int[]{bestX, bestY};
    }

    static boolean reserve(
            boolean[][] occupied,
            int columns,
            int rows,
            int cellX,
            int cellY,
            int spanX,
            int spanY) {
        if (!isFree(occupied, columns, rows, cellX, cellY, spanX, spanY)) return false;
        for (int x = cellX; x < cellX + spanX; x++) {
            for (int y = cellY; y < cellY + spanY; y++) {
                occupied[x][y] = true;
            }
        }
        return true;
    }

    private static boolean isFree(
            boolean[][] occupied,
            int columns,
            int rows,
            int cellX,
            int cellY,
            int spanX,
            int spanY) {
        if (occupied == null || occupied.length < columns
                || columns <= 0 || rows <= 0
                || cellX < 0 || cellY < 0
                || spanX <= 0 || spanY <= 0
                || cellX > columns - spanX
                || cellY > rows - spanY) {
            return false;
        }
        for (int x = cellX; x < cellX + spanX; x++) {
            if (occupied[x] == null || occupied[x].length < rows) return false;
            for (int y = cellY; y < cellY + spanY; y++) {
                if (occupied[x][y]) return false;
            }
        }
        return true;
    }
}
