package com.hellovoid.liquiddock;

/** Maps a root Surface buffer into the authoritative ViewRoot content UV rectangle. */
final class RootPassBlurContentRect {
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
        return new RootPassBlurContentRect(0f, 0f, 1f, 1f);
    }

    static RootPassBlurContentRect resolve(
            int surfaceWidth, int surfaceHeight,
            int insetLeft, int insetTop, int insetRight, int insetBottom) {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return full();
        int left = clamp(insetLeft, 0, surfaceWidth);
        int right = clamp(insetRight, 0, surfaceWidth);
        int top = clamp(insetTop, 0, surfaceHeight);
        int bottom = clamp(insetBottom, 0, surfaceHeight);
        int contentWidth = surfaceWidth - left - right;
        int contentHeight = surfaceHeight - top - bottom;
        if (contentWidth <= 0 || contentHeight <= 0) return full();
        return new RootPassBlurContentRect(
                left / (float) surfaceWidth,
                bottom / (float) surfaceHeight,
                contentWidth / (float) surfaceWidth,
                contentHeight / (float) surfaceHeight);
    }

    RootPassBlurContentRect subRect(
            int rootWidth, int rootHeight,
            int leftPx, int topPx, int widthPx, int heightPx) {
        if (rootWidth <= 0 || rootHeight <= 0 || widthPx <= 0 || heightPx <= 0) {
            return this;
        }

        int left = clamp(leftPx, 0, rootWidth);
        int top = clamp(topPx, 0, rootHeight);
        int right = clamp(leftPx + widthPx, 0, rootWidth);
        int bottom = clamp(topPx + heightPx, 0, rootHeight);
        if (right <= left || bottom <= top) return this;

        float x = left / (float) rootWidth;
        float y = (rootHeight - bottom) / (float) rootHeight;
        float w = (right - left) / (float) rootWidth;
        float h = (bottom - top) / (float) rootHeight;
        return new RootPassBlurContentRect(
                this.left + x * this.width,
                this.bottom + y * this.height,
                w * this.width,
                h * this.height);
    }

    boolean sameAs(RootPassBlurContentRect other) {
        return other != null
                && Math.abs(left - other.left) < 0.00001f
                && Math.abs(bottom - other.bottom) < 0.00001f
                && Math.abs(width - other.width) < 0.00001f
                && Math.abs(height - other.height) < 0.00001f;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
