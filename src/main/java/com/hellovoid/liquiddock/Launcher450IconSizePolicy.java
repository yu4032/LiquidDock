package com.hellovoid.liquiddock;

/** Shared percentage policy for Launcher 4.50 workspace, Dock, and 1x1 folders. */
final class Launcher450IconSizePolicy {
    static final int MIN_PERCENT = 80;
    static final int MAX_PERCENT = 120;
    static final int DEFAULT_PERCENT = 100;

    private Launcher450IconSizePolicy() {}

    static float scale(boolean enabled, int percent) {
        if (!enabled) return 1f;
        int clamped = Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));
        return clamped / 100f;
    }

    static int scaledPx(int px, boolean enabled, int percent) {
        if (px <= 0) return px;
        return Math.round(px * scale(enabled, percent));
    }
}