package com.hellovoid.liquiddock;

/** Pure geometry policy for compositor-local Gboard glass buffers. */
final class GboardSurfaceControlGeometryPolicy {
    static final class Frame {
        final int x;
        final int y;
        final int width;
        final int height;

        Frame(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
        }
    }

    static Frame from(float x, float y, int width, int height) {
        return new Frame(Math.round(x), Math.round(y), width, height);
    }

    private GboardSurfaceControlGeometryPolicy() {}
}
