package com.hellovoid.liquiddock;

/** One folder presentation owner. Wallpaper-only capture may finish after native open starts. */
final class OpenFolderGlassState {
    private enum Phase { CLOSED, PREPARING, OPENING, OPEN, CLOSING }
    private Phase phase = Phase.CLOSED;
    private long serial;
    private boolean backdropReady;
    private boolean presented;

    long prepare() {
        serial++;
        phase = Phase.PREPARING;
        backdropReady = false;
        presented = false;
        return serial;
    }

    boolean owns(long value) { return value == serial && phase != Phase.CLOSED; }

    boolean onBackdropReady(long value) {
        if (!owns(value)) return false;
        backdropReady = true;
        return true;
    }

    boolean beginOpen(long value) {
        if (!owns(value) || phase != Phase.PREPARING) return false;
        // A source that excludes Launcher can capture the wallpaper while
        // native FolderCling begins its animation; never block native input.
        phase = Phase.OPENING;
        return true;
    }

    boolean onPresented(long value) {
        if (!owns(value) || !backdropReady || phase == Phase.PREPARING
                || (phase == Phase.CLOSING && !presented)) return false;
        presented = true;
        if (phase == Phase.OPENING) phase = Phase.OPEN;
        return true;
    }

    boolean beginClose(long value) {
        if (!owns(value) || phase == Phase.PREPARING) return false;
        phase = Phase.CLOSING;
        return true;
    }

    boolean finishClose(long value) {
        if (!owns(value)) return false;
        release();
        return true;
    }

    boolean maySuppressNative() { return phase != Phase.CLOSED && presented; }
    boolean isClosing() { return phase == Phase.CLOSING; }

    Runnable guardCompletion(long value, Runnable completion) {
        return () -> { if (owns(value)) completion.run(); };
    }

    void release() {
        phase = Phase.CLOSED;
        backdropReady = false;
        presented = false;
    }
}
