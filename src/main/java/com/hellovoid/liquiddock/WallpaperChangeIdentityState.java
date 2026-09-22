package com.hellovoid.liquiddock;

/** Android-free deduplication for home-wallpaper change authorities. */
final class WallpaperChangeIdentityState {
    private static final int UNKNOWN_WALLPAPER_ID = -1;

    private int lastWallpaperId = UNKNOWN_WALLPAPER_ID;

    synchronized void initialize(int wallpaperId) {
        if (wallpaperId >= 0) lastWallpaperId = wallpaperId;
    }

    /**
     * Returns true when a change authority should advance wallpaper content generation.
     *
     * <p>A non-negative system wallpaper ID is authoritative and duplicate vendor/system
     * notifications for the same ID are coalesced. Negative IDs are deliberately accepted because
     * some live/default wallpaper implementations do not expose a persistent image ID.</p>
     */
    synchronized boolean shouldAdvance(int wallpaperId) {
        if (wallpaperId < 0) return true;
        if (wallpaperId == lastWallpaperId) return false;
        lastWallpaperId = wallpaperId;
        return true;
    }

    synchronized int lastWallpaperId() {
        return lastWallpaperId;
    }
}
