package com.hellovoid.liquiddock;

/** Pure preflight rules for applying a remembered layout back into a live CellLayout. */
final class HomeGridRestorePolicy {
    private HomeGridRestorePolicy() {}

    static boolean matrixReady(
            int expectedColumns,
            int expectedRows,
            int liveColumns,
            int liveRows,
            int[] xs,
            int[] ys) {
        return expectedColumns > 0
                && expectedRows > 0
                && liveColumns == expectedColumns
                && liveRows == expectedRows
                && xs != null
                && ys != null
                && xs.length == expectedColumns
                && ys.length == expectedRows;
    }

    static boolean gridEnvelopeInside(
            int width,
            int height,
            int cellWidth,
            int cellHeight,
            int[] xs,
            int[] ys) {
        if (width <= 0 || height <= 0 || cellWidth <= 0 || cellHeight <= 0
                || xs == null || ys == null || xs.length == 0 || ys.length == 0) {
            return false;
        }
        if (xs[0] < 0 || ys[0] < 0) return false;
        for (int i = 1; i < xs.length; i++) {
            if (xs[i] <= xs[i - 1]) return false;
        }
        for (int i = 1; i < ys.length; i++) {
            if (ys[i] <= ys[i - 1]) return false;
        }
        long right = (long) xs[xs.length - 1] + cellWidth;
        long bottom = (long) ys[ys.length - 1] + cellHeight;
        return right <= width && bottom <= height;
    }
}
