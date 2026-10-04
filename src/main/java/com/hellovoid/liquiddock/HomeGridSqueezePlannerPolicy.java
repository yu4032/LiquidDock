package com.hellovoid.liquiddock;

/** Pure scope policy for selecting generic MIUI occupancy planners on free HOME grids. */
final class HomeGridSqueezePlannerPolicy {
    private HomeGridSqueezePlannerPolicy() {}

    static boolean isVendorPadGrid(int countX, int countY) {
        return (countX == 6 && countY == 4) || (countX == 4 && countY == 6);
    }

    static boolean useGenericForSpan(
            boolean vendorPadGrid, int spanX, int spanY) {
        return !vendorPadGrid || spanX > 1 || spanY > 1;
    }

    static boolean useGenericForSqueeze(
            boolean vendorPadGrid, boolean isSpanMove, int spanX, int spanY) {
        return !vendorPadGrid || isSpanMove || spanX > 1 || spanY > 1;
    }

    static boolean matches(
            String gridName,
            int countX,
            int countY,
            int configuredColumns,
            int configuredRows) {
        if ("land_grid".equals(gridName)) {
            return countX == configuredColumns && countY == configuredRows;
        }
        if ("vertical_grid".equals(gridName) || "land_split_grid".equals(gridName)) {
            return countX == configuredRows && countY == configuredColumns;
        }
        return false;
    }
}
