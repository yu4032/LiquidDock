package com.hellovoid.liquiddock;

import java.util.HashMap;

/** Applied native history with a capture barrier; never holds its monitor across WMS work. */
final class WallpaperNativeTransformStore {
    private final HashMap<Integer, LauncherWallpaperTransformState> displays = new HashMap<>();
    private int updates;

    synchronized void beginUpdate() {
        updates++;
    }

    synchronized void endUpdate() {
        updates--;
        notifyAll();
    }

    synchronized void record(int display, LauncherWallpaperTransformState.Sample sample) {
        displays.computeIfAbsent(display, ignored -> new LauncherWallpaperTransformState())
                .update(sample.timestampNs, sample.scale, sample.pivotX, sample.pivotY);
    }

    synchronized LauncherWallpaperTransformState.Sample latest(int display) {
        LauncherWallpaperTransformState state = displays.get(display);
        return state != null ? state.latest() : null;
    }

    synchronized LauncherWallpaperTransformState.Sample capture(
            int display, long timestampNs, long timeoutMs) {
        if (timestampNs <= 0) return null;
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (updates > 0) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return null;
            try {
                wait(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        LauncherWallpaperTransformState state = displays.get(display);
        return state != null ? state.at(timestampNs) : null;
    }
}
