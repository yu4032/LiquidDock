package com.hellovoid.liquiddock;

/**
 * Source-driven consumer limiter. It never creates a timer or requests producer work; it only
 * decides whether an observed OES callback may schedule rendering. Forced freshness frames bypass
 * the limit without changing cadence state.
 */
final class PassBlurFrameRateLimiter {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final int maxFps;
    private long windowStartNanos = Long.MIN_VALUE;
    private long scheduledFrames;

    PassBlurFrameRateLimiter(int requestedFps) {
        maxFps = PassBlurQualityPolicy.renderFps(requestedFps);
    }

    synchronized boolean shouldSchedule(long nowNanos, boolean force) {
        if (force || maxFps == 0) return true;

        if (windowStartNanos == Long.MIN_VALUE || nowNanos < windowStartNanos) {
            windowStartNanos = nowNanos;
            scheduledFrames = 1L;
            return true;
        }

        long elapsedNanos = nowNanos - windowStartNanos;
        long wholeSeconds = elapsedNanos / NANOS_PER_SECOND;
        long remainderNanos = elapsedNanos % NANOS_PER_SECOND;
        long allowedFrames = 1L
                + wholeSeconds * maxFps
                + remainderNanos * maxFps / NANOS_PER_SECOND;
        if (scheduledFrames >= allowedFrames) return false;
        scheduledFrames++;
        return true;
    }
}
