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
            uniform sampler2D uBackdrop;
            uniform sampler2D uBlurredBackdrop;
            uniform sampler2D uGlyphMask;
            uniform vec4 uGlyphRect;
            uniform mat3 uRootPxToMaskUv;
            uniform vec2 uRootSize;
            uniform vec2 uGlyphTexel;
            uniform vec2 uOutputTexel;
            uniform vec2 uGlyphLightDir;
            uniform vec4 uGlyphTint;
            uniform float uGlyphBrightness;
            uniform float uGlyphRefractionPx;
            uniform float uGlyphNormalStrength;
            uniform float uGlyphHighlightWidth;
            uniform float uGlyphHighlightStrength;
            uniform float uRimStrength;
            uniform float uPlainHighlight;
            uniform float uComponentLitRim;
            uniform float uComponentOppositeRim;
            uniform float uComponentFaceSheen;
            uniform float uComponentPlainHighlight;
            varying vec2 vUv;

            float maskAt(vec2 p) {
                return texture2D(uGlyphMask, clamp(p, 0.0, 1.0)).a;
            }

            void main() {
                vec2 screenUvTopLeft = vec2(vUv.x, 1.0 - vUv.y);
                vec2 rootPx = screenUvTopLeft * uRootSize;
                vec2 local = (uRootPxToMaskUv * vec3(rootPx, 1.0)).xy;
                float inside = step(0.0, local.x) * step(0.0, local.y)
                        * step(local.x, 1.0) * step(local.y, 1.0);
                vec2 clampedLocal = clamp(local, 0.0, 1.0);
                float alpha = maskAt(clampedLocal) * inside;
                if (alpha <= 0.001) {
                    gl_FragColor = vec4(0.0);
                    return;
                }

                float l = maskAt(clampedLocal - vec2(uGlyphTexel.x, 0.0));
                float r = maskAt(clampedLocal + vec2(uGlyphTexel.x, 0.0));
                float t = maskAt(clampedLocal - vec2(0.0, uGlyphTexel.y));
                float b = maskAt(clampedLocal + vec2(0.0, uGlyphTexel.y));
                vec2 grad = vec2(r - l, b - t);
                float gradLen = length(grad);
                vec2 outward = gradLen > 0.0001 ? normalize(grad) : vec2(0.0);
                vec3 N = normalize(vec3(
                        -outward.x * uGlyphNormalStrength,
                        -outward.y * uGlyphNormalStrength,
                        1.0));

                vec2 refractOffset = outward * uGlyphRefractionPx
                        * uGlyphNormalStrength * uOutputTexel;
                vec2 refractedUv = clamp(screenUvTopLeft + refractOffset, 0.0, 1.0);

                vec4 sharp = texture2D(uBackdrop, refractedUv);
                vec4 blurred = texture2D(uBlurredBackdrop, refractedUv);
                float edgeMix = clamp(gradLen * 1.5, 0.0, 1.0);
                vec3 color = mix(sharp.rgb, blurred.rgb, 0.28 + 0.42 * edgeMix);
                color = mix(color, uGlyphTint.rgb, clamp(uGlyphTint.a, 0.0, 1.0));
                color *= uGlyphBrightness;

                // Prismal soft-edge model adapted to glyph-mask normals. Keep the same directional
                // lit hairline, opposite glow, face sheen and plain highlight semantics instead
                // of a clock-specific single rim term.
                vec2 Lxy = normalize(uGlyphLightDir + vec2(1e-5));
                float edgeLight = dot(outward, Lxy);
                float shellRim = smoothstep(0.02, max(0.0001, uGlyphHighlightWidth), gradLen);
                float cosNV = clamp(N.z, 0.0, 1.0);
                float fresnel = pow(1.0 - cosNV, 2.9);

                vec3 hiSoft = vec3(0.98, 0.992, 1.008);
                vec3 hiVeil = vec3(0.958, 0.978, 1.012);
                vec3 oppTint = vec3(0.952, 0.968, 1.018);

                float litHairline = pow(max(edgeLight, 0.0), 3.6) * shellRim;
                float oppGlow = pow(max(-edgeLight, 0.0), 1.05) * shellRim
                        * (0.28 + 0.72 * fresnel);
                float rimLitSide = litHairline * uRimStrength * (0.58 + 0.42 * alpha);
                float rimOpposite = oppGlow * uRimStrength * 0.48
                        * (0.40 + 0.60 * alpha);
                color += hiSoft * rimLitSide * uComponentLitRim;
                color += mix(hiVeil, oppTint, 0.42)
                        * rimOpposite * uComponentOppositeRim;

                float faceSheenSoft = shellRim
                        * smoothstep(0.08, 0.82, edgeLight)
                        * fresnel * uRimStrength * 0.22;
                color += hiSoft * faceSheenSoft
                        * (0.48 + 0.52 * alpha) * uComponentFaceSheen;

                float plusHL = shellRim * uPlainHighlight * uRimStrength
                        * pow(max(edgeLight, 0.0), 2.2);
                color += plusHL * vec3(0.99, 0.995, 1.0)
                        * uComponentPlainHighlight;

                // Preserve the user's general specular strength as a broad secondary term.
                vec3 Lp = normalize(vec3(uGlyphLightDir, 1.45));
                vec3 V = vec3(0.0, 0.0, 1.0);
                vec3 H = normalize(Lp + V);
                float specular = pow(max(dot(N, H), 0.0), 18.0)
                        * uGlyphHighlightStrength;
                color += vec3(specular);

                gl_FragColor = vec4(color * alpha, alpha);
            }
            """;

    private Miuix307PrismalCompositeShaders() {}
}
