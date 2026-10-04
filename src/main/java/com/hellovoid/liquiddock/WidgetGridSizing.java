package com.hellovoid.liquiddock;

/** Shared geometry for widget layouts on the custom CellLayout grid. */
final class WidgetGridSizing {
    private static volatile boolean customGridEnabled;

    private WidgetGridSizing() {}

    static void setCustomGridEnabled(boolean enabled) {
        customGridEnabled = enabled;
    }

    static boolean isSupportedSpec(int spanX, int spanY) {
        // The active CellLayout dimensions are the real upper bound. Do not encode a second
        // launcher-specific whitelist here: any positive span that fits the current grid is valid.
        return spanX > 0 && spanY > 0;
    }

    /**
     * Returns {left, top, width, height} for the complete grid allocation.
     * When the custom grid is disabled, the empty rectangle makes both the
     * setupLayoutParam and post-layout enforcement paths leave MIUI untouched.
     */
    static int[] gridRect(int cellX, int cellY, int spanX, int spanY,
                          int[] xs, int[] ys, int cellWidth, int cellHeight,
                          int widthGap, int heightGap) {
        if (!customGridEnabled) return new int[]{0, 0, 0, 0};
        if (spanX <= 0 || spanY <= 0 || cellWidth <= 0 || cellHeight <= 0
                || xs == null || ys == null || xs.length == 0 || ys.length == 0
                || cellX < 0 || cellY < 0
                || cellX + spanX > xs.length || cellY + spanY > ys.length) {
            return new int[]{0, 0, 0, 0};
        }

        int left = xs[cellX];
        int top = ys[cellY];
        int right = xs[cellX + spanX - 1] + cellWidth;
        int bottom = ys[cellY + spanY - 1] + cellHeight;

        // Derive a multi-cell widget from the actual occupied cell origins, not from mWidthGap /
        // mHeightGap. Other Launcher geometry hooks can legitimately rebuild mXs/mYs with a pitch
        // that is not identical to the stored gap field. Using the last occupied cell's far edge
        // guarantees that a 2x1 widget has exactly the same outer span as two adjacent 1x1 cells,
        // while still excluding the trailing gap after the final occupied cell.
        return new int[]{
                left,
                top,
                Math.max(0, right - left),
                Math.max(0, bottom - top)
        };
    }

    static int[] centeredFrame(int[] allocation,
                               int leftMargin, int topMargin,
                               int rightMargin, int bottomMargin) {
        if (allocation == null || allocation.length < 4
                || allocation[2] <= 0 || allocation[3] <= 0) {
            return new int[]{0, 0, 0, 0};
        }

        int horizontalInset = Math.max(0, leftMargin) + Math.max(0, rightMargin);
        int verticalInset = Math.max(0, topMargin) + Math.max(0, bottomMargin);
        int width = Math.max(1, allocation[2] - horizontalInset);
        int height = Math.max(1, allocation[3] - verticalInset);

        // CellLayout.onLayout() does not apply MarginLayoutParams to x/y. Preserve the amount of
        // space MIUI reserved for the widget, but center that reduced frame inside the occupied
        // grid footprint instead of letting the removed margin area accumulate on the right/bottom.
        int left = allocation[0] + (allocation[2] - width) / 2;
        int top = allocation[1] + (allocation[3] - height) / 2;
        return new int[]{left, top, width, height};
    }

}
