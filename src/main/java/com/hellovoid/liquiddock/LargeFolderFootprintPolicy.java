package com.hellovoid.liquiddock;

/** Model predicate only; ownership and user selection are checked at the View boundary. */
final class LargeFolderFootprintPolicy {
    private LargeFolderFootprintPolicy() {}

    static boolean hasRealTwoByTwoFootprint(int itemType, int spanX, int spanY) {
        return itemType == 21 && spanX == 2 && spanY == 2;
    }
}
