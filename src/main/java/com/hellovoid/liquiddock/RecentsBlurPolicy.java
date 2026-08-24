package com.hellovoid.liquiddock;

/** Unit conversion for Launcher Recents blur, kept independent from hook mechanics. */
final class RecentsBlurPolicy {
    private RecentsBlurPolicy() {}

    static float ratioFromPercent(int percent) {
        return Math.max(0, Math.min(100, percent)) / 100f;
    }

    static float scaleGestureRatio(float systemRatio, int percent) {
        return Math.max(0f, Math.min(1f, systemRatio)) * ratioFromPercent(percent);
    }
}
