package com.hellovoid.prismal;

/** Per-frame target selection; never changes ownership of the renderer's scene texture. */
final class PrismalFrameTarget {
    int framebuffer;
    int width;
    int height;
    int viewportX;
    int viewportY;
    private int clipRight, clipTop;

    static int scaledDimension(int logical, int percent) {
        if (logical <= 0) throw new IllegalArgumentException("logical dimension <= 0");
        int safePercent = Math.max(50, Math.min(100, percent));
        return Math.max(1, (int) (((long) logical * safePercent + 99L) / 100L));
    }

    void selectTexture(int framebuffer, int width, int height) {
        this.framebuffer = framebuffer;
        this.width = width;
        this.height = height;
        viewportX = viewportY = 0;
        clipRight = width;
        clipTop = height;
    }

    void selectSurface(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("surface dimensions <= 0");
        }
        selectTexture(0, width, height);
    }

    /** Keep even source origins so GPU derivative quads match the full logical framebuffer. */
    void selectTextureRegion(int framebuffer, int textureWidth, int textureHeight,
            int logicalWidth, int logicalHeight, int sourceLeft, int sourceBottom) {
        if (textureWidth <= 0 || textureHeight <= 0 || sourceLeft < 0 || sourceBottom < 0
                || (sourceLeft & 1) != 0 || (sourceBottom & 1) != 0
                || (long) sourceLeft + textureWidth > logicalWidth
                || (long) sourceBottom + textureHeight > logicalHeight) {
            throw new IllegalArgumentException("unaligned texture crop outside logical scene");
        }
        selectTexture(framebuffer, logicalWidth, logicalHeight);
        viewportX = -sourceLeft;
        viewportY = -sourceBottom;
        clipRight = textureWidth;
        clipTop = textureHeight;
    }

    boolean clipScissor(PrismalNodeScissor.Rect rect) {
        int right = Math.min(clipRight, rect.x + viewportX + rect.width);
        int top = Math.min(clipTop, rect.y + viewportY + rect.height);
        rect.x = Math.max(0, rect.x + viewportX);
        rect.y = Math.max(0, rect.y + viewportY);
        rect.width = Math.max(0, right - rect.x);
        rect.height = Math.max(0, top - rect.y);
        return rect.width > 0 && rect.height > 0;
    }
}
