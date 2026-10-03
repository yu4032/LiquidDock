package com.hellovoid.liquiddock;

/** Pure scope policy for selecting generic MIUI occupancy planners on free HOME grids. */
final class HomeGridSqueezePlannerPolicy {
    private HomeGridSqueezePlannerPolicy() {}

    static boolean useGenericForSpan(int spanX, int spanY) {
        return spanX > 1 || spanY > 1;
    }

    static boolean useGenericForSqueeze(boolean isSpanMove, int spanX, int spanY) {
        return isSpanMove || useGenericForSpan(spanX, spanY);
    }

    static boolean matches(
            String gridName,
            int countX,
            int countY,
            int configuredColumns,
            int configuredRows) {
        if (!"land_grid".equals(gridName) && !"vertical_grid".equals(gridName)) {
            return false;
        }
        return (countX == configuredColumns && countY == configuredRows)
                || (countX == configuredRows && countY == configuredColumns);
    }
}
