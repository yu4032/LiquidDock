package com.hellovoid.liquiddock;

/** LiquidDock-only final crop from the portable Prismal framebuffer into the visible Dock view. */
final class Miuix307PrismalCompositeShaders {
    static final String FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform vec4 uCropRect;
            varying vec2 vUv;
            void main() {
                vec2 uv = uCropRect.xy + vUv * uCropRect.zw;
                vec4 sample = texture2D(uTexture, uv);
                // Prismal keeps straight alpha internally. Android window surfaces are composited
                // as premultiplied alpha, so premultiply only at this final Surface boundary.
                gl_FragColor = vec4(sample.rgb * sample.a, sample.a);
            }
            """;

    private Miuix307PrismalCompositeShaders() {}
}
