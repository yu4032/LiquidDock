package com.hellovoid.liquiddock;

/** Timing gate between target rotation geometry and a safe one-shot Workspace backdrop sample. */
final class LauncherGlassRotationSettlePolicy {
    private static final long BASE_TRANSITION_MS = 500L;

    private LauncherGlassRotationSettlePolicy() {}

    static long settleDelayMs(float durationRatio) {
        if (!Float.isFinite(durationRatio) || durationRatio <= 0f) return 0L;
        float ratio = Math.min(1f, durationRatio);
        return Math.round(BASE_TRANSITION_MS * ratio);
    }
}
