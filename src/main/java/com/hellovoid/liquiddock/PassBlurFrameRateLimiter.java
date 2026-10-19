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
        // Missed source slots are dropped, not banked. Otherwise an idle HOME or paused
        // producer can accumulate seconds of credit and render every incoming frame during
        // the next unlock/app-return transition, bypassing the configured consumer cap.
        // Keep the fractional cadence anchored so e.g. a 50 Hz source still averages 30 FPS.
        scheduledFrames = allowedFrames;
        return true;
    }
}
