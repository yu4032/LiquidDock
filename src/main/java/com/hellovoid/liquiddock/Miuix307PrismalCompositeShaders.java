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
            uniform vec2 uGlyphTexel;
            uniform vec2 uOutputTexel;
            uniform vec2 uGlyphLightDir;
            uniform float uGlyphRefractionPx;
            uniform float uGlyphNormalStrength;
            uniform float uGlyphHighlightWidth;
            uniform float uGlyphHighlightStrength;
            varying vec2 vUv;

            float maskAt(vec2 p) {
                return texture2D(uGlyphMask, clamp(p, 0.0, 1.0)).a;
            }

            void main() {
                vec2 screenUvTopLeft = vec2(vUv.x, 1.0 - vUv.y);
                vec2 local = (screenUvTopLeft - uGlyphRect.xy) / uGlyphRect.zw;
                float inside = step(0.0, local.x) * step(0.0, local.y)
                        * step(local.x, 1.0) * step(local.y, 1.0);
                vec2 clampedLocal = clamp(local, 0.0, 1.0);
                float alpha = maskAt(clampedLocal) * inside;

                float l = maskAt(clampedLocal - vec2(uGlyphTexel.x, 0.0));
                float r = maskAt(clampedLocal + vec2(uGlyphTexel.x, 0.0));
                float t = maskAt(clampedLocal - vec2(0.0, uGlyphTexel.y));
                float b = maskAt(clampedLocal + vec2(0.0, uGlyphTexel.y));
                vec2 grad = vec2(r - l, b - t);
                float gradLen = length(grad);
                vec2 normal = gradLen > 0.0001 ? normalize(grad) : vec2(0.0);

                vec2 uv = uCropRect.xy + vUv * uCropRect.zw;
                vec2 refractOffset = normal * uGlyphRefractionPx
                        * uGlyphNormalStrength * uOutputTexel;
                vec4 glass = texture2D(uTexture, clamp(uv + refractOffset, 0.0, 1.0));

                float edge = smoothstep(0.0, max(0.0001, uGlyphHighlightWidth), gradLen);
                vec2 lightDir = normalize(uGlyphLightDir);
                float facing = max(0.0, dot(-normal, lightDir));
                float highlight = edge * facing * uGlyphHighlightStrength;

                vec3 rgb = glass.rgb + vec3(highlight);
                gl_FragColor = vec4(rgb * alpha, glass.a * alpha);
            }
            """;

    private Miuix307PrismalCompositeShaders() {}
}
