package com.hellovoid.liquiddock;

/** Pure decision policy for vendor blur writes after LiquidDock owns a Launcher material. */
final class LauncherVendorBlurWritePolicy {
    private LauncherVendorBlurWritePolicy() {}

    /**
     * The pass-window gate is also a compositor eligibility signal. Once LiquidDock owns the
     * material, vendor enable and disable writes are both suppressed so the zero-radius GPU
     * composition hold cannot be dropped between Launcher transitions.
     */
    static boolean shouldSuppressPassWindowWrite(
            boolean ownedByLiquidDock, boolean requestedEnabled) {
        return ownedByLiquidDock;
    }

    /** Blur modes stay enabled for compositor eligibility while the visible radius remains zero. */
    static boolean shouldSuppressBlurModeWrite(
            boolean ownedByLiquidDock, int requestedValue) {
        return ownedByLiquidDock;
    }

    /** Positive radius would make the vendor blur visible; zero is safe and may pass through. */
    static boolean shouldSuppressBlurRadiusWrite(
            boolean ownedByLiquidDock, int requestedValue) {
        return ownedByLiquidDock && requestedValue > 0;
    }
}
