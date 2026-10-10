package com.hellovoid.liquiddock;

/** Typed SystemUI-only scene state for native gesture-handle visibility. */
final class GestureHandleSystemUiSceneState {
    static final class Snapshot {
        final boolean home;
        final boolean recentsAnimation;
        final boolean overview;

        Snapshot(boolean home, boolean recentsAnimation, boolean overview) {
            this.home = home;
            this.recentsAnimation = recentsAnimation;
            this.overview = overview;
        }
    }

    private boolean home;
    private boolean recentsAnimation;
    private boolean overview;

    synchronized boolean onTaskMovedToFront(boolean homeTask) {
        home = homeTask;
        // A concrete task-front transition ends the previously settled overview state.
        // Entering overview will reassert it through onOverviewShown().
        overview = false;
        return shouldHideLocked();
    }

    synchronized boolean onRecentsAnimationChanged(boolean running) {
        recentsAnimation = running;
        if (running) overview = false;
        return shouldHideLocked();
    }

    synchronized boolean onOverviewShown() {
        overview = true;
        return true;
    }

    synchronized boolean onLauncherProxyDisconnected() {
        recentsAnimation = false;
        overview = false;
        return shouldHideLocked();
    }

    synchronized boolean shouldHide() {
        return shouldHideLocked();
    }

    /** Clear a finished module generation's HOME/RECENTS state before a fresh owner installs. */
    synchronized void resetForFutureReload() {
        home = false;
        recentsAnimation = false;
        overview = false;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(home, recentsAnimation, overview);
    }

    private boolean shouldHideLocked() {
        // During swipe-up / recents transition, SystemUI still owns the moving gesture handle.
        // Hide only after Overview is actually shown, or after a HOME transition has settled.
        return overview || (home && !recentsAnimation);
    }
}
