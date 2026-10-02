package com.hellovoid.liquiddock;

/** Pure policy for rebuilding MIUI's full-screen GridConfig with a custom row/column count. */
final class HomeGridVendorConfigPolicy {
    static final class Result {
        final int countX;
        final int countY;
        final int cellSize;
        final int left;

        Result(int countX, int countY, int cellSize, int left) {
            this.countX = countX;
            this.countY = countY;
            this.cellSize = cellSize;
            this.left = left;
        }
    }

    private HomeGridVendorConfigPolicy() {}

    static Result calculate(
            int width,
            int height,
            int top,
            int bottom,
            int dockBarHeight,
            int indicatorBarHeight,
            int countX,
            int countY) {
        if (width <= 0 || height <= 0 || countX <= 0 || countY <= 0) return null;
        int verticalSpace = Math.max(
                countY,
                height - Math.max(0, top) - Math.max(0, bottom)
                        - Math.max(0, dockBarHeight) - Math.max(0, indicatorBarHeight));
        int cellSize = Math.max(1, Math.min(width / countX, verticalSpace / countY));
        int left = Math.max(0, (width - cellSize * countX) / 2);
        return new Result(countX, countY, cellSize, left);
    }
}
