package com.hellovoid.liquiddock;

/**
 * Bounded pacing of geometry-only Workspace output submissions.
 *
 * <p>The source frame callback is never paced: it always presents the latest backdrop with
 * the latest static geometry. The geometry-only fallback is delayed only if a static surface
 * buffer was just submitted inside the same refresh interval. Source rendering can consume
 * the pending geometry work during that brief delay; otherwise the fallback still presents it.
 * No dirty-cache/transform reuse, capture freeze or PassBlur rate change is involved.</p>
 */
final class WorkspaceGeometrySwapPacingPolicy {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private WorkspaceGeometrySwapPacingPolicy() {}

    static long framePeriodNs(float refreshHz) {
        if (!Float.isFinite(refreshHz) || refreshHz < 30f || refreshHz > 240f) {
            return 0L;
        }
        return Math.max(1L, (long) (NANOS_PER_SECOND / (double) refreshHz));
    }

    static long geometryDelayMs(long lastStaticSwapNs, long nowNs, long framePeriodNs) {
        if (lastStaticSwapNs <= 0L || nowNs <= lastStaticSwapNs || framePeriodNs <= 0L) {
            return 0L;
        }
        long elapsedNs = nowNs - lastStaticSwapNs;
        if (elapsedNs >= framePeriodNs) return 0L;
        long remainingNs = framePeriodNs - elapsedNs;
        // Handler only accepts whole milliseconds. Round up to avoid immediately requeueing
        // the same geometry twice inside one display period. A source frame is never delayed.
        return Math.max(1L, (remainingNs + NANOS_PER_MILLI - 1L) / NANOS_PER_MILLI);
    }
}
