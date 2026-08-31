package com.hellovoid.liquiddock;

/** Pure cross-process timing compensation for SystemUI -> Launcher home transition events. */
final class SystemUiHomeTransitionTimingPolicy {
    static long elapsedMs(long sourceUptimeMs, long receiveUptimeMs, long maxDurationMs) {
        long max = Math.max(0L, maxDurationMs);
        if (sourceUptimeMs <= 0L || receiveUptimeMs <= sourceUptimeMs) return 0L;
        return Math.min(max, receiveUptimeMs - sourceUptimeMs);
    }

    static long remainingMs(long totalDurationMs, long elapsedMs) {
        long total = Math.max(0L, totalDurationMs);
        long elapsed = Math.max(0L, Math.min(total, elapsedMs));
        return total - elapsed;
    }

    private SystemUiHomeTransitionTimingPolicy() {}
}
