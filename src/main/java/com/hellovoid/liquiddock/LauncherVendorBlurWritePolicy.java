package com.hellovoid.liquiddock;

/** Pure decision policy for vendor blur writes after LiquidDock owns a Launcher material. */
final class LauncherVendorBlurWritePolicy {
    private LauncherVendorBlurWritePolicy() {}

    static boolean shouldSuppressPassWindowWrite(
            boolean ownedByLiquidDock, boolean requestedEnabled) {
        return ownedByLiquidDock && requestedEnabled;
    }

    static boolean shouldSuppressPositiveBlurWrite(
            boolean ownedByLiquidDock, int requestedValue) {
        return ownedByLiquidDock && requestedValue > 0;
    }
}
