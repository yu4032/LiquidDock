package com.hellovoid.liquiddock;

/** Pure topology classifier for HOME-grid rotation transforms. */
final class HomeGridRotationTopologyPolicy {
    enum Action {
        PRESERVE,
        TRANSPOSE,
        DEFER
    }

    private HomeGridRotationTopologyPolicy() {}

    static Action classify(int srcColumns, int srcRows, int dstColumns, int dstRows) {
        if (srcColumns <= 0 || srcRows <= 0 || dstColumns <= 0 || dstRows <= 0) {
            return Action.DEFER;
        }
        if (srcColumns == dstColumns && srcRows == dstRows) {
            return Action.PRESERVE;
        }
        if (srcColumns == dstRows && srcRows == dstColumns) {
            return Action.TRANSPOSE;
        }
        return Action.DEFER;
    }
}
