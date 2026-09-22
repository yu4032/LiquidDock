package com.hellovoid.liquiddock;

/** Android-free serial authority for Recents -> HOME wallpaper settling. */
final class RecentsWallpaperSettleState {
    private long nextSerial;
    private long activeSerial;
    private boolean pending;

    synchronized long onRecentsShown() {
        activeSerial = ++nextSerial;
        pending = false;
        return activeSerial;
    }

    synchronized long onReturnStarted() {
        activeSerial = ++nextSerial;
        pending = true;
        return activeSerial;
    }

    synchronized boolean cancelReturn(long serial) {
        if (!pending || serial != activeSerial) return false;
        pending = false;
        return true;
    }

    synchronized boolean onWallpaperSettled(long serial) {
        if (!pending || serial != activeSerial) return false;
        pending = false;
        return true;
    }

    synchronized long pendingSerial() {
        return pending ? activeSerial : -1L;
    }

    synchronized boolean isPending() {
        return pending;
    }

    synchronized long activeSerial() {
        return activeSerial;
    }
}
