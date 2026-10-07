package com.hellovoid.liquiddock;

/** Maps a root Surface buffer into the authoritative ViewRoot content UV rectangle. */
final class RootPassBlurContentRect {
    private static final RootPassBlurContentRect FULL =
            new RootPassBlurContentRect(0f, 0f, 1f, 1f);

    final float left;
    final float bottom;
    final float width;
    final float height;

    private RootPassBlurContentRect(float left, float bottom, float width, float height) {
        this.left = left;
        this.bottom = bottom;
        this.width = width;
        this.height = height;
    }

    static RootPassBlurContentRect full() {
        return FULL;
    }

    static RootPassBlurContentRect resolve(
            int surfaceWidth, int surfaceHeight,
            int insetLeft, int insetTop, int insetRight, int insetBottom) {
        return resolve(null, surfaceWidth, surfaceHeight,
                insetLeft, insetTop, insetRight, insetBottom);
    }

    static RootPassBlurContentRect resolve(
            RootPassBlurContentRect previous,
            int surfaceWidth, int surfaceHeight,
            int insetLeft, int insetTop, int insetRight, int insetBottom) {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return FULL;
        int left = clamp(insetLeft, 0, surfaceWidth);
        int right = clamp(insetRight, 0, surfaceWidth);
        int top = clamp(insetTop, 0, surfaceHeight);
        int bottom = clamp(insetBottom, 0, surfaceHeight);
        int contentWidth = surfaceWidth - left - right;
        int contentHeight = surfaceHeight - top - bottom;
        if (contentWidth <= 0 || contentHeight <= 0) return FULL;
        float resolvedLeft = left / (float) surfaceWidth;
        float resolvedBottom = bottom / (float) surfaceHeight;
        float resolvedWidth = contentWidth / (float) surfaceWidth;
        float resolvedHeight = contentHeight / (float) surfaceHeight;
        if (previous != null
                && close(previous.left, resolvedLeft)
                && close(previous.bottom, resolvedBottom)
                && close(previous.width, resolvedWidth)
                && close(previous.height, resolvedHeight)) {
            return previous;
        }
        if (close(resolvedLeft, 0f) && close(resolvedBottom, 0f)
                && close(resolvedWidth, 1f) && close(resolvedHeight, 1f)) {
            return FULL;
        }
        return new RootPassBlurContentRect(
                resolvedLeft, resolvedBottom, resolvedWidth, resolvedHeight);
    }

    boolean sameAs(RootPassBlurContentRect other) {
        return other != null
                && close(left, other.left)
                && close(bottom, other.bottom)
                && close(width, other.width)
                && close(height, other.height);
    }

    private static boolean close(float a, float b) {
        return Math.abs(a - b) < 0.00001f;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
