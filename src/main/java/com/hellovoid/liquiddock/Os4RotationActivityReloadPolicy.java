package com.hellovoid.liquiddock;

/** Pure debounce policy for reloading the Launcher Activity after a rectangular rotation. */
final class Os4RotationActivityReloadPolicy {
    private static final long MIN_INTERVAL_MS = 1_500L;

    private Os4RotationActivityReloadPolicy() {}

    static boolean shouldReload(
            boolean rectangularGrid,
            int previousOrientation,
            int currentOrientation,
            long nowUptimeMs,
            long lastReloadUptimeMs) {
        return rectangularGrid
                && previousOrientation != 0
                && currentOrientation != 0
                && previousOrientation != currentOrientation
                && (lastReloadUptimeMs == 0L
                    || nowUptimeMs - lastReloadUptimeMs >= MIN_INTERVAL_MS);
    }
}
