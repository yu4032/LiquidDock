package com.hellovoid.prismal;

/**
 * Native EGL surface draws only need source+blur FBOs. A standalone scene texture
 * is allocated on demand when a caller explicitly requests a texture-output frame.
 */
final class PrismalNativeOutputPolicy {
    private PrismalNativeOutputPolicy() {}

    static boolean canReuseBackdrop(
            int renderWidth, int renderHeight, int outputWidth, int outputHeight,
            int expectedRenderWidth, int expectedRenderHeight,
            int expectedOutputWidth, int expectedOutputHeight,
            int sourceFbo, int blurHorizontalFbo, int blurVerticalFbo) {
        return renderWidth == expectedRenderWidth
                && renderHeight == expectedRenderHeight
                && outputWidth == expectedOutputWidth
                && outputHeight == expectedOutputHeight
                && sourceFbo != 0 && blurHorizontalFbo != 0 && blurVerticalFbo != 0;
    }

    static boolean hasNativeOutput(int texture, int framebuffer) {
        return texture != 0 && framebuffer != 0;
    }
}
