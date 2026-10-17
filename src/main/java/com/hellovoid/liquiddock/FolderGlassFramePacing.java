package com.hellovoid.liquiddock;

/**
 * Budget the full-screen optical render, not the native folder animation.
 * At 165 Hz this keeps the last-known geometry current every pre-draw while
 * the expensive Prismal pass runs at no more than roughly half the refresh
 * cadence. First presentation and endpoints are never artificially delayed.
 */
final class FolderGlassFramePacing {
    static final long MIN_INTERVAL_NANOS = 11_000_000L;
    private long lastSubmittedNanos = Long.MIN_VALUE;
    private float lastSubmittedProgress = Float.NaN;

    boolean allow(long nowNanos, float progress) {
        if (!Float.isFinite(progress)) return false;
        boolean endpoint = (progress <= 0f || progress >= 1f)
                && Float.compare(progress, lastSubmittedProgress) != 0;
        if (lastSubmittedNanos != Long.MIN_VALUE
                && nowNanos >= lastSubmittedNanos
                && nowNanos - lastSubmittedNanos < MIN_INTERVAL_NANOS
                && !endpoint) return false;
        lastSubmittedNanos = nowNanos;
        lastSubmittedProgress = progress;
        return true;
    }
}
