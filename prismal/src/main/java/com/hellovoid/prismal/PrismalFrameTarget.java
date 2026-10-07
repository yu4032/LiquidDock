package com.hellovoid.prismal;

/** Per-frame target selection; never changes ownership of the renderer's scene texture. */
final class PrismalFrameTarget {
    int framebuffer;
    int width;
    int height;

    static int scaledDimension(int logical, int percent) {
        if (logical <= 0) throw new IllegalArgumentException("logical dimension <= 0");
        int safePercent = Math.max(50, Math.min(100, percent));
        return Math.max(1, (int) (((long) logical * safePercent + 99L) / 100L));
    }

    void selectTexture(int framebuffer, int width, int height) {
        this.framebuffer = framebuffer;
        this.width = width;
        this.height = height;
    }

    void selectSurface(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("surface dimensions <= 0");
        }
        selectTexture(0, width, height);
    }
}
