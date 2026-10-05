package com.hellovoid.liquiddock;

/** Pure policy for MIUI's legacy DB-orientation probe used during Launcher model loading. */
final class HomeGridDbOrientationPolicy {
    static final int VENDOR_PORTRAIT_SENTINEL_X = 4;

    private HomeGridDbOrientationPolicy() {}

    /**
     * MIUI 4.50 identifies portrait database data with getCellCountX() == 4. Free grids can have
     * any X count, so only the orientation-probe call receives that legacy sentinel. Real grid
     * transforms keep their actual dimensions.
     */
    static int probeCellCountX(boolean portrait, int actualCellCountX) {
        if (portrait) return VENDOR_PORTRAIT_SENTINEL_X;
        return actualCellCountX == VENDOR_PORTRAIT_SENTINEL_X
                ? VENDOR_PORTRAIT_SENTINEL_X + 1
                : actualCellCountX;
    }

    static boolean isConfiguredFullScreenGrid(
            String gridName,
            int countX,
            int countY,
            int landscapeColumns,
            int landscapeRows) {
        if ("land_grid".equals(gridName)) {
            return countX == landscapeColumns && countY == landscapeRows;
        }
        if ("vertical_grid".equals(gridName)) {
            return countX == landscapeRows && countY == landscapeColumns;
        }
        return false;
    }
}
