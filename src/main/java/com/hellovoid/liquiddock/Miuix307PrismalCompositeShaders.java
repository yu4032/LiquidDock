package com.hellovoid.liquiddock;

/** LiquidDock-only final crop from the portable Prismal framebuffer into the visible Dock view. */
final class Miuix307PrismalCompositeShaders {
    /*
     * TEMPORARY VISIBILITY PROBE for Gboard floating keyboard bring-up.
     * If this opaque output is not visible while eglSwapBuffers succeeds, the TextureView is
     * being occluded by Gboard's hierarchy/composition rather than Prismal producing transparency.
     * Remove immediately after the hardware A/B result.
     */
    static final String FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform vec4 uCropRect;
            varying vec2 vUv;
            void main() {
                gl_FragColor = vec4(1.0, 0.0, 1.0, 1.0);
            }
            """;

    private Miuix307PrismalCompositeShaders() {}
}
