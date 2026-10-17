package com.hellovoid.liquiddock;

/** One folder presentation owner. Wallpaper-only capture may finish after native open starts. */
final class OpenFolderGlassState {
    private enum Phase { CLOSED, PREPARING, OPENING, OPEN, CLOSING }
    private Phase phase = Phase.CLOSED;
    private long serial;
    private boolean backdropReady;
    private boolean presented;
    private boolean recentsCovered;
    private boolean recentsReturnPending;

    /** Recents can hide FolderCling without closing it. The native
     * close callbacks, not transient View.visibility, own its lifecycle.
     */
    void onRecentsShown(long value) {
        if (!owns(value)) return;
        recentsCovered = true;
        recentsReturnPending = false;
    }

    void onRecentsHidden(long value) {
        if (!owns(value) || !recentsCovered) return;
        recentsCovered = false;
        recentsReturnPending = true;
    }

    boolean isRecentsCovered(long value) {
        return owns(value) && recentsCovered;
    }

    boolean isRecentsReturnPending(long value) {
        return owns(value) && recentsReturnPending;
    }

    boolean keepHiddenFolder(long value, boolean nativeStillOpened) {
        return owns(value) && (recentsCovered || (recentsReturnPending && nativeStillOpened));
    }

    /** Returning the native folder may still be INVISIBLE for one or more
     * OEM traversals; availability of the cached lens is not tied to that
     * transient View visibility.
     */
    boolean shouldReplayAfterRecents(long value, boolean nativeStillOpened) {
        return owns(value) && presented && !recentsCovered
                && recentsReturnPending && nativeStillOpened;
    }

    boolean completeRecentsReturn(long value) {
        if (!owns(value) || !recentsReturnPending || recentsCovered) return false;
        recentsReturnPending = false;
        return true;
    }

    long prepare() {
        serial++;
        phase = Phase.PREPARING;
        backdropReady = false;
        presented = false;
        recentsCovered = false;
        recentsReturnPending = false;
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

    /** Native source/background draw suppression begins with OEM OPENING,
     * before an async wallpaper capture can finish and reveal Prismal.
     * PREPARING (touch-down without opening) still retains native fallback.
     */
    boolean maySuppressNative() {
        return phase == Phase.OPENING || phase == Phase.OPEN || phase == Phase.CLOSING;
    }
    boolean isClosing() { return phase == Phase.CLOSING; }

    Runnable guardCompletion(long value, Runnable completion) {
        return () -> { if (owns(value)) completion.run(); };
    }

    void release() {
        phase = Phase.CLOSED;
        backdropReady = false;
        presented = false;
        recentsCovered = false;
        recentsReturnPending = false;
    }
}
