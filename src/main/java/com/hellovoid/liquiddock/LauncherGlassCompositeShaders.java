package com.hellovoid.liquiddock;

/** Workspace-only upscale: smooth alpha contours along their tangent, preserving interior detail. */
final class LauncherGlassCompositeShaders {
    static final String FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform vec4 uCropRect;
            uniform vec2 uInputTexelSize;
            uniform float uEdgeFilterStrength;
            uniform float uInteriorAlpha;
            varying vec2 vUv;
            void main() {
                vec2 uv = uCropRect.xy + vUv * uCropRect.zw;
                vec4 center = texture2D(uTexture, uv);
                // Empty pixels and full-coverage glass need only the original texture fetch.
                // During node fades the gradient test below still protects flat interiors.
                if (uEdgeFilterStrength <= 0.0 || center.a <= 0.0001
                        || center.a >= uInteriorAlpha - 0.0001) {
                    gl_FragColor = center;
                    return;
                }
                float left = texture2D(uTexture, uv - vec2(uInputTexelSize.x, 0.0)).a;
                float right = texture2D(uTexture, uv + vec2(uInputTexelSize.x, 0.0)).a;
                float down = texture2D(uTexture, uv - vec2(0.0, uInputTexelSize.y)).a;
                float up = texture2D(uTexture, uv + vec2(0.0, uInputTexelSize.y)).a;
                vec2 gradient = vec2(right - left, up - down);
                float magnitude = length(gradient);
                if (magnitude < 0.015) {
                    gl_FragColor = center;
                    return;
                }
                vec2 tangent = vec2(-gradient.y, gradient.x) / magnitude;
                vec2 offset = tangent * uInputTexelSize * 0.5;
                vec4 along = 0.5 * (texture2D(uTexture, uv - offset)
                                  + texture2D(uTexture, uv + offset));
                // Positive weights filter premultiplied RGB and alpha together: no divide,
                // negative sharpening lobes, or added brightness on transparent borders.
                gl_FragColor = mix(center, along, clamp(uEdgeFilterStrength, 0.0, 0.5));
            }
            """;

    private LauncherGlassCompositeShaders() {}
}
