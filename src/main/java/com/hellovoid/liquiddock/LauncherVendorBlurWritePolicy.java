package com.hellovoid.liquiddock;

/** Pure write policy for vendor blur APIs after LiquidDock owns a Launcher material. */
final class LauncherVendorBlurWritePolicy {
    private LauncherVendorBlurWritePolicy() {}

    static boolean passWindowBlurEnabled(boolean ownedByLiquidDock, boolean requested) {
        return ownedByLiquidDock ? false : requested;
    }

    static int blurModeOrRadius(boolean ownedByLiquidDock, int requested) {
        return ownedByLiquidDock && requested > 0 ? 0 : requested;
    }
}
