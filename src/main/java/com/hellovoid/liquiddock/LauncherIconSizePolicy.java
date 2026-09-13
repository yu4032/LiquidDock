package com.hellovoid.liquiddock;

/** Shared icon-size policy for Workspace, Dock, and 1x1 folder preview rendering. */
final class LauncherIconSizePolicy {
    static final int MIN_PERCENT = 80;
    static final int MAX_PERCENT = 120;
    static final int DEFAULT_PERCENT = 100;

    private LauncherIconSizePolicy() {}

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
