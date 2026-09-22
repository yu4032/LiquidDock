package com.hellovoid.liquiddock;

/**
 * Coalesces the vendor wallpaper callback with the background WallpaperInfoUpdateTask lifecycle.
 *
 * <p>DesktopWallpaperManager reuses one Runnable and removes queued copies before scheduling the
 * newest update. A task that was already running cannot be cancelled, so completion is valid only
 * when no newer wallpaper callback arrived after that task started.</p>
 */
final class LauncherWallpaperTransactionState {
    private long latestChangeSerial;
    private long pendingTaskSerial;

    synchronized long onWallpaperChanged() {
        long serial = ++latestChangeSerial;
        pendingTaskSerial = serial;
        return serial;
    }

    synchronized long onTaskStarted() {
        long serial = pendingTaskSerial;
        pendingTaskSerial = 0L;
        return serial;
    }

    synchronized boolean shouldPublishTaskCompletion(long taskSerial) {
        return taskSerial > 0L && taskSerial == latestChangeSerial;
    }

    synchronized long latestChangeSerial() {
        return latestChangeSerial;
    }
}
