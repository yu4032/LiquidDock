package com.hellovoid.liquiddock;

/** Admission policy for multi-cell items entering a full-screen custom HOME grid. */
final class HomeGridWidgetAdmissionPolicy {
    private HomeGridWidgetAdmissionPolicy() {}

    static boolean shouldReject(
            String gridName,
            int countX,
            int countY,
            int configuredColumns,
            int configuredRows,
            int spanX,
            int spanY) {
        if (!("land_grid".equals(gridName) || "vertical_grid".equals(gridName))) {
            return false;
        }
        if (!HomeGridSqueezePlannerPolicy.matches(
                gridName, countX, countY, configuredColumns, configuredRows)) {
            return false;
        }
        return !HomeGridDropLegalityPolicy.fitsBothOrientations(
                countX, countY, spanX, spanY);
    }
}
