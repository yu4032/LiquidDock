package com.hellovoid.liquiddock;

/** Pure model guard for native four-shortcut folders; never mutates occupancy. */
final class LargeFolderFootprintPolicy {
    static final int LARGE_FOUR_FOLDER_TYPE = 21;
    static final int SMALL_FOLDER_TYPE = 2;
    static final int PREVIEW_SIZE = 4;
    static final long WORKSPACE_CONTAINER = -100L;

    private LargeFolderFootprintPolicy() {}

    static boolean needsNativeConversion(int itemType, int spanX, int spanY,
                                         long id, long screenId, long container) {
        return itemType == LARGE_FOUR_FOLDER_TYPE
                && spanX == 1 && spanY == 1
                && id >= 0 && screenId >= 0
                && container == WORKSPACE_CONTAINER;
    }

    static boolean needsNativePromotion(int itemType, int spanX, int spanY,
                                        int childCount, long id, long screenId, long container) {
        return itemType == SMALL_FOLDER_TYPE
                && spanX == 1 && spanY == 1 && childCount == PREVIEW_SIZE
                && id >= 0 && screenId >= 0 && container == WORKSPACE_CONTAINER;
    }

    static boolean hasRealTwoByTwoFootprint(int itemType, int spanX, int spanY) {
        return itemType == LARGE_FOUR_FOLDER_TYPE && spanX == 2 && spanY == 2;
    }
}
