package com.hellovoid.prismal;

/**
 * GPU scissor for a full-frame Prismal quad. Only the glass silhouette (plus an
 * anti-aliasing guard) needs expensive procedural refraction fragment shading.
 * Inputs are logical top-left screen pixels; output uses GL bottom-left pixels.
 */
final class PrismalNodeScissor {
    static final float EDGE_GUARD_PX = 3f;

    static final class Rect {
        int x;
        int y;
        int width;
        int height;

        Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private PrismalNodeScissor() {}

    static Rect compute(PrismalGeometry geometry, int targetWidth, int targetHeight) {
        Rect output = new Rect(0, 0, 0, 0);
        return computeInto(geometry, targetWidth, targetHeight, output) ? output : null;
    }

    /** No allocation in the GL render loop. */
    static boolean computeInto(PrismalGeometry geometry, int targetWidth,
                               int targetHeight, Rect output) {
        if (output == null || geometry == null || targetWidth <= 0 || targetHeight <= 0
                || !Float.isFinite(geometry.left()) || !Float.isFinite(geometry.right())
                || !Float.isFinite(geometry.top()) || !Float.isFinite(geometry.bottom())) {
            return false;
        }
        float scaleX = targetWidth / (float) geometry.framebufferWidth;
        float scaleY = targetHeight / (float) geometry.framebufferHeight;
        int x0 = clampFloor((geometry.left() - EDGE_GUARD_PX) * scaleX, targetWidth);
        int x1 = clampCeil((geometry.right() + EDGE_GUARD_PX) * scaleX, targetWidth);
        int y0 = clampFloor((geometry.framebufferHeight - geometry.bottom()
                - EDGE_GUARD_PX) * scaleY, targetHeight);
        int y1 = clampCeil((geometry.framebufferHeight - geometry.top()
                + EDGE_GUARD_PX) * scaleY, targetHeight);
        if (x1 <= x0 || y1 <= y0) return false;
        output.x = x0;
        output.y = y0;
        output.width = x1 - x0;
        output.height = y1 - y0;
        return true;
    }

    private static int clampFloor(float value, int max) {
        return (int) Math.max(0, Math.min(max, Math.floor(value)));
    }

    private static int clampCeil(float value, int max) {
        return (int) Math.max(0, Math.min(max, Math.ceil(value)));
    }
}
