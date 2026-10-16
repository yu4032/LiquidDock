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

    /** FolderGridView's precomputed row pitch does not grow when a
     * ShortcutIcon#getIconSize value is overwritten during child measurement.
     * For this domain only, don't let an enlarged drawable exceed the OEM
     * cell contract and displace the last-row labels beyond its clip bounds.
     * Shrinking stays available; other domains retain the full 80..120% range.
     * This is a conservative compatibility guard until OEM folder-row sizing
     * can be independently reproduced and validated on real hardware.
     */
    static int fitNativeFolderCell(int px, boolean enabled, int percent) {
        return Math.min(px, scaledPx(px, enabled, percent));
    }
}