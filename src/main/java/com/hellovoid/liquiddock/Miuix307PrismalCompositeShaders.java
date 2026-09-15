package com.hellovoid.liquiddock;

/** LiquidDock-only final crop from the portable Prismal framebuffer into the visible Dock view. */
final class Miuix307PrismalCompositeShaders {
    /*
     * TEMPORARY VISIBILITY PROBE for Gboard floating keyboard bring-up.
     * Preserve the normal crop mapping contract, but force an opaque final color so hardware can
     * distinguish view-layer occlusion from transparent/incorrect Prismal content.
     * Remove immediately after the hardware A/B result.
     */
    static final String FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform vec4 uCropRect;
            varying vec2 vUv;
            void main() {
                vec2 uv = uCropRect.xy + vUv * uCropRect.zw;
                vec4 ignoredScene = texture2D(uTexture, uv);
                gl_FragColor = vec4(1.0, 0.0, 1.0, 1.0) + ignoredScene * 0.0;
            }
            """;

    private Miuix307PrismalCompositeShaders() {}
}
