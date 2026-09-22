package com.hellovoid.liquiddock;

/** Android-free serial authority for Recents -> HOME wallpaper settling. */
final class RecentsWallpaperSettleState {
    private long nextSerial;
    private long activeSerial;
    private long completionAuthoritySerial = -1L;
    private boolean pending;

    synchronized long onRecentsShown() {
        activeSerial = ++nextSerial;
        completionAuthoritySerial = -1L;
        pending = false;
        return activeSerial;
    }

    synchronized long onReturnStarted() {
        activeSerial = ++nextSerial;
        completionAuthoritySerial = -1L;
        pending = true;
        return activeSerial;
    }

    synchronized boolean armCompletionAuthority(long serial) {
        if (!pending || serial != activeSerial) return false;
        completionAuthoritySerial = serial;
        return true;
    }

    synchronized boolean revokeCompletionAuthority(long serial) {
        if (serial != activeSerial || completionAuthoritySerial != serial) return false;
        completionAuthoritySerial = -1L;
        return true;
    }

    synchronized boolean hasCompletionAuthority(long serial) {
        return pending
                && serial == activeSerial
                && completionAuthoritySerial == serial;
    }

    synchronized boolean cancelReturn(long serial) {
        if (!pending || serial != activeSerial) return false;
        pending = false;
        completionAuthoritySerial = -1L;
        return true;
    }

    synchronized boolean onWallpaperSettled(long serial) {
        if (!hasCompletionAuthority(serial)) return false;
        pending = false;
        completionAuthoritySerial = -1L;
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
