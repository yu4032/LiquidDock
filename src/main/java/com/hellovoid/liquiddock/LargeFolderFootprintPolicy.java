package com.hellovoid.liquiddock;

/** Explicit native large-folder types; only marked 2x2 models are customized. */
final class LargeFolderFootprintPolicy {
    static final int FOUR = 21;
    static final int NINE = 22;
    private LargeFolderFootprintPolicy() {}

    static boolean customizeDesktopFolder(boolean marked, boolean workstationAllApps) {
        return marked && !workstationAllApps;
    }

    static boolean normalizeAllAppsCopy(
            boolean workstationAllAppsView, long container, int type, int spanX, int spanY) {
        // Workstation All Apps ordinary folders must use 1x1 even after a previous
        // desktop 2x2 selection is disabled. Never depend on a large-folder marker.
        return (workstationAllAppsView || container == -104L)
                && type == 2 && (spanX != 1 || spanY != 1);
    }

    static boolean supportsType(int type) {
        return type == FOUR || type == NINE;
    }

    static boolean hasRealTwoByTwoFootprint(int itemType, int spanX, int spanY) {
        return supportsType(itemType) && spanX == 2 && spanY == 2;
    }

    /** The opt-in load repair must never grow an uncommitted folder footprint. */
    static boolean shouldRestoreLoadedTwoByTwo(
            boolean enabled, int itemType, boolean marked,
            int persistedX, int persistedY, int loadedX, int loadedY) {
        return enabled && marked && supportsType(itemType)
                && persistedX == 2 && persistedY == 2
                && (loadedX != 2 || loadedY != 2);
    }
}
