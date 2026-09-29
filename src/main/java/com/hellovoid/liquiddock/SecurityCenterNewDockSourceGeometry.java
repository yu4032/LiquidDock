package com.hellovoid.liquiddock;

/** Exact Launcher source geometry passed into Security Center's New Dock entrypoint. */
final class SecurityCenterNewDockSourceGeometry {
    static final class Snapshot {
        final int x;
        final int y;
        final int width;
        final int height;
        final int radius;

        Snapshot(int x, int y, int width, int height, int radius) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.radius = radius;
        }
    }

    private static Snapshot current;

    private SecurityCenterNewDockSourceGeometry() {}

    static synchronized void publish(int x, int y, int width, int height, int radius) {
        if (width <= 0 || height <= 0 || radius < 0) return;
        current = new Snapshot(x, y, width, height, radius);
    }

    static synchronized Snapshot current() {
        return current;
    }

    static synchronized void clear() {
        current = null;
    }
}
