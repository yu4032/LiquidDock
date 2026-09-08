package com.hellovoid.liquiddock;

/** Pure per-Launcher-root ownership state for unsafe external drag-source capture. */
final class LauncherGlassDragSourceState {
    private int activeDrags;

    /** Returns true only for the transition that must pause the shared producer. */
    boolean begin() {
        activeDrags++;
        return activeDrags == 1;
    }

    /** Returns true only for the transition that may resume through fresh-frame authority. */
    boolean end() {
        if (activeDrags <= 0) return false;
        activeDrags--;
        return activeDrags == 0;
    }

    boolean isBlocked() {
        return activeDrags > 0;
    }

    void cancel() {
        activeDrags = 0;
    }
}
