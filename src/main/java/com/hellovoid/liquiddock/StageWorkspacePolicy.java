package com.hellovoid.liquiddock;

/** Pure geometry and placement policy for the fixed Stage workspace prefix. */
final class StageWorkspacePolicy {
    static final int STAGE_COLUMNS = 2;
    static final int PHYSICAL_COLUMNS = 8;
    static final int DESKTOP_COLUMNS = PHYSICAL_COLUMNS - STAGE_COLUMNS;

    private StageWorkspacePolicy() {}

    static boolean isSupported(HomeGridProfile profile,
                               int columns,
                               int rows,
                               boolean normalHomeLandscape) {
        return normalHomeLandscape
                && profile == HomeGridProfile.GRID_8X4
                && columns == PHYSICAL_COLUMNS
                && rows == 4;
    }

    static int mapLogicalX(int logicalX, int spanX) {
        if (logicalX < 0 || spanX <= 0 || (long) logicalX + spanX > DESKTOP_COLUMNS) {
            throw new IllegalArgumentException("item does not fit six-column desktop");
        }
        return logicalX + STAGE_COLUMNS;
    }

    static boolean isOrdinaryPhysicalPlacementLegal(int physicalX, int spanX) {
        if (physicalX < STAGE_COLUMNS || spanX <= 0) return false;
        return (long) physicalX + spanX <= PHYSICAL_COLUMNS;
    }

    static boolean orientationMemoryOwnsTarget(HomeGridOrientation target,
                                               boolean stageActive) {
        if (target == null) return false;
        return !stageActive || target != HomeGridOrientation.LANDSCAPE;
    }

    static int[] stageBounds(int[] physicalXs) {
        if (physicalXs == null || physicalXs.length <= STAGE_COLUMNS) {
            throw new IllegalArgumentException("stable third grid coordinate required");
        }
        return new int[]{physicalXs[0], physicalXs[STAGE_COLUMNS]};
    }
}
