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
                gl_FragColor = texture2D(uTexture, uv);
            }
            """;

    static final String GLYPH_MASK_FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform sampler2D uGlyphMask;
            uniform vec4 uCropRect;
            uniform vec4 uGlyphRect;
            varying vec2 vUv;
            void main() {
                vec2 uv = uCropRect.xy + vUv * uCropRect.zw;
                vec4 glass = texture2D(uTexture, uv);
                vec2 screenUvTopLeft = vec2(vUv.x, 1.0 - vUv.y);
                vec2 local = (screenUvTopLeft - uGlyphRect.xy) / uGlyphRect.zw;
                float inside = step(0.0, local.x) * step(0.0, local.y)
                        * step(local.x, 1.0) * step(local.y, 1.0);
                float alpha = texture2D(uGlyphMask, clamp(local, 0.0, 1.0)).a * inside;
                gl_FragColor = vec4(glass.rgb * alpha, glass.a * alpha);
            }
            """;

    private Miuix307PrismalCompositeShaders() {}
}
