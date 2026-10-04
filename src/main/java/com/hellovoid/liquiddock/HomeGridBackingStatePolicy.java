package com.hellovoid.liquiddock;

/** Pure readiness rule for applying custom CellLayout geometry during grid transitions. */
final class HomeGridBackingStatePolicy {
    private HomeGridBackingStatePolicy() {}

    static boolean canApplyGeometry(
            int configX, int configY, int backingX, int backingY) {
        if (configX <= 0 || configY <= 0) return false;
        if (backingX <= 0 || backingY <= 0) return true;
        return configX == backingX && configY == backingY;
    }
}
