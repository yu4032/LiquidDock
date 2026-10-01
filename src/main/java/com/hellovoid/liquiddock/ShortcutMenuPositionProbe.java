package com.hellovoid.liquiddock;

/** Stage-2 diagnostic authority captured directly from HyperOS ShortcutMenuPosition. */
final class ShortcutMenuPositionProbe {
    static final class Snapshot {
        final int x;
        final int y;
        final int width;
        final int height;
        final int gravity;

        Snapshot(int x, int y, int width, int height, int gravity) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.gravity = gravity;
        }
    }

    private static volatile Snapshot latest;

    private ShortcutMenuPositionProbe() {}

    static void record(int x, int y, int width, int height, int gravity) {
        if (width <= 0 || height <= 0) return;
        latest = new Snapshot(x, y, width, height, gravity);
    }

    static Snapshot latest() {
        return latest;
    }

    static void clear() {
        latest = null;
    }
}
