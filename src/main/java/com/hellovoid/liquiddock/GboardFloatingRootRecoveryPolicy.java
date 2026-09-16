package com.hellovoid.liquiddock;

/** Pure policy for root-size changes that invalidate the prepared full-root backdrop. */
final class GboardFloatingRootRecoveryPolicy {
    enum Decision {
        NONE,
        REQUEST_FRESH,
        FAIL_CLOSED
    }

    private GboardFloatingRootRecoveryPolicy() {}

    static Decision decide(
            int oldWidth,
            int oldHeight,
            int newWidth,
            int newHeight,
            boolean liveRefreshAllowed) {
        if (oldWidth <= 0 || oldHeight <= 0 || newWidth <= 0 || newHeight <= 0) {
            return Decision.FAIL_CLOSED;
        }
        if (oldWidth == newWidth && oldHeight == newHeight) return Decision.NONE;
        return liveRefreshAllowed ? Decision.REQUEST_FRESH : Decision.FAIL_CLOSED;
    }
}
