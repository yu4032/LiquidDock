package com.hellovoid.prismal;

/** Lightweight shaders for renderer-owned local OS4 Bloom mask and positioned composite. */
final class PrismalBloomShaderSources {
    static final String MASK_VERTEX = """
            attribute vec2 aPosition;
            attribute vec2 aUv;
            varying vec2 vUv;

            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vUv = aUv;
            }
            """;

    static final String MASK_FRAGMENT = """
            precision highp float;

            uniform vec2 u_targetSize;
            uniform vec2 u_glassSize;
            uniform vec4 u_cornerRadii;
            uniform float u_bloomWidthPx;
            uniform float u_bloomIntensity;
            uniform vec2 u_lightDir;

            varying vec2 vUv;

            float radiusAtCentered(vec2 c, vec4 radii) {
                if (c.x >= 0.0) {
                    if (c.y <= 0.0) return radii.y;
                    return radii.z;
                }
                if (c.y <= 0.0) return radii.x;
                return radii.w;
            }

            float sdRoundedRectRealistic(vec2 coord, vec2 halfSize, float radius) {
                vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
                float outside = length(max(cornerCoord, 0.0)) - radius;
                float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
                return outside + inside;
            }

            float bloomSd(vec2 p, vec2 halfSize, vec4 radii) {
                vec2 cKy = vec2(p.x, -p.y);
                float maxRadius = min(halfSize.x, halfSize.y);
                float radius = min(radiusAtCentered(cKy, radii), maxRadius);
                return sdRoundedRectRealistic(cKy, halfSize, radius);
            }

            float os4BloomCurve(float t) {
                t = clamp(t, 0.0, 1.0);
                if (t >= 0.85) return 1.0;
                float shoulder = clamp(mix(0.5, 1.0, sqrt(t)), 0.0, 1.0);
                float smoother = shoulder * shoulder * shoulder
                        * (shoulder * (shoulder * 6.0 - 15.0) + 10.0);
                float shaped = clamp((smoother - 0.5) * 2.0, 0.0, 1.0);
                return 1.0 - pow(1.0 - shaped, 1.35);
            }

            void main() {
                vec2 pPx = (vUv - 0.5) * u_targetSize;
                vec2 halfSize = max(u_glassSize * 0.5, vec2(0.5));
                float bloomWidth = max(u_bloomWidthPx, 0.0);
                float halfWidth = bloomWidth * 0.5;
                float sd = bloomSd(pPx, halfSize, u_cornerRadii);
                float aa = 1.0;

                float outer = 1.0 - smoothstep(-aa, aa, sd - halfWidth);
                float inner = 1.0 - smoothstep(-aa, aa, sd + halfWidth);
                float ring = clamp(outer - inner, 0.0, 1.0);

                float sdfXp = bloomSd(pPx + vec2(1.0, 0.0), halfSize, u_cornerRadii);
                float sdfXn = bloomSd(pPx - vec2(1.0, 0.0), halfSize, u_cornerRadii);
                float sdfYp = bloomSd(pPx + vec2(0.0, 1.0), halfSize, u_cornerRadii);
                float sdfYn = bloomSd(pPx - vec2(0.0, 1.0), halfSize, u_cornerRadii);
                vec2 edgeNormal = normalize(vec2(sdfXp - sdfXn, sdfYp - sdfYn));
                if (dot(edgeNormal, edgeNormal) < 0.001) edgeNormal = vec2(0.0, 1.0);

                vec2 lightDir = normalize(u_lightDir + vec2(1e-5));
                float facing = dot(edgeNormal, lightDir);
                float mainLight = pow(max(facing, 0.0), 1.45) * 0.95;
                float oppositeLight = pow(max(-facing, 0.0), 1.10) * 0.34;
                float ambientLight = 0.16;
                float light = max(ambientLight, mainLight + oppositeLight);

                float edgeDistance = abs(sd);
                float crossT = clamp(1.0 - edgeDistance / max(halfWidth, 1.0), 0.0, 1.0);
                float crossSection = mix(0.52, 1.0, os4BloomCurve(crossT));
                float alpha = clamp(ring * crossSection * light * max(u_bloomIntensity, 0.0), 0.0, 1.0);
                vec3 cool = vec3(0.91, 0.955, 1.035);
                vec3 bloomRgb = mix(cool, vec3(1.0), clamp(mainLight, 0.0, 1.0));
                gl_FragColor = vec4(bloomRgb * alpha, alpha);
            }
            """;

    static final String COMPOSITE_VERTEX = """
            attribute vec2 aPosition;
            attribute vec2 aUv;
            uniform vec2 u_resolution;
            uniform vec2 u_centerPx;
            uniform vec2 u_targetSize;
            varying vec2 vUv;

            void main() {
                vec2 localPx = aPosition * 0.5 * u_targetSize;
                vec2 screenPx = u_centerPx + localPx;
                vec2 ndc = screenPx / u_resolution * 2.0 - 1.0;
                gl_Position = vec4(ndc, 0.0, 1.0);
                vUv = aUv;
            }
            """;

    static final String COMPOSITE_FRAGMENT = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vUv;

            void main() {
                vec4 bloom = texture2D(uTexture, vUv);
                vec3 straightRgb = bloom.a > 1e-5 ? bloom.rgb / bloom.a : vec3(0.0);
                gl_FragColor = vec4(straightRgb, bloom.a);
            }
            """;

    private PrismalBloomShaderSources() {}
}
