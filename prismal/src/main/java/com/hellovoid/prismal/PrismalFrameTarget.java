package com.hellovoid.prismal;

/** Per-frame target selection; never changes ownership of the renderer's scene texture. */
final class PrismalFrameTarget {
    int framebuffer;
    int width;
    int height;

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
