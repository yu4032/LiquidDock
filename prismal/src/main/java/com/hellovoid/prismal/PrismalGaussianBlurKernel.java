package com.hellovoid.prismal;

/**
 * Constant-weight equivalent of the vendored separable 31-tap Gaussian blur.
 *
 * <p>Horizontal blur reads the source adapter at 2x the destination resolution: its legacy
 * stride therefore spans TWO source texels and cannot safely bilinear-pair adjacent weights.
 * Vertical blur reads the intermediate at matching resolution, so two adjacent tap samples
 * can be reconstructed with one GL_LINEAR fetch at a weight-averaged fractional offset.
 * Neither logical sampling scale nor Gaussian radius changes.</p>
 */
final class PrismalGaussianBlurKernel {
    static final int RADIUS = 15;
    static final int PAIR_COUNT = 7;

    /** Normalized weights for offsets 0..15. */
    final float[] weights = new float[RADIUS + 1];
    /** Vertical sample pair: x = weighted offset, y = pair weight, for (1,2)...(13,14). */
    final float[] verticalPairs = new float[PAIR_COUNT * 2];

    void update(float sigma) {
        if (!Float.isFinite(sigma) || sigma <= 0f) {
            throw new IllegalArgumentException("sigma must be positive finite");
        }
        double sigma2 = 2.0 * (double) sigma * sigma;
        double[] unnormalized = new double[RADIUS + 1];
        double normalization = 0.0;
        for (int i = 0; i <= RADIUS; i++) {
            double weight = Math.exp(-((double) i * i) / sigma2);
            unnormalized[i] = weight;
            normalization += (i == 0 ? 1 : 2) * weight;
        }
        for (int i = 0; i <= RADIUS; i++) {
            weights[i] = (float) (unnormalized[i] / normalization);
        }
        for (int j = 0; j < PAIR_COUNT; j++) {
            int left = 2 * j + 1;
            int right = left + 1;
            double total = unnormalized[left] + unnormalized[right];
            verticalPairs[2 * j] = (float) ((left * unnormalized[left]
                    + right * unnormalized[right]) / total);
            verticalPairs[2 * j + 1] = (float) (total / normalization);
        }
    }

    /** Build once at class load; all array indices in the ES2 shaders are constants. */
    static final String HORIZONTAL_FRAGMENT = build(false);
    static final String VERTICAL_FRAGMENT = build(true);

    private static String build(boolean vertical) {
        StringBuilder shader = new StringBuilder(5500);
        shader.append("precision highp float;\n");
        shader.append("uniform sampler2D u_texture;\n");
        shader.append("uniform vec2 u_texelSize;\n");
        shader.append("uniform float u_weights[16];\n");
        if (vertical) shader.append("uniform vec2 u_pairs[7];\n");
        shader.append("varying vec2 v_texCoord;\nvoid main() {\n");
        shader.append("  vec3 col = texture2D(u_texture, v_texCoord).rgb * u_weights[0];\n");
        if (vertical) {
            for (int j = 0; j < PAIR_COUNT; j++) {
                shader.append("  vec2 d").append(j)
                        .append(" = vec2(0.0, u_pairs[").append(j)
                        .append("].x * u_texelSize.y);\n");
                appendSymmetricSample(shader, "d" + j, "u_pairs[" + j + "].y");
            }
            appendSymmetricSample(shader, "vec2(0.0, 15.0 * u_texelSize.y)",
                    "u_weights[15]");
        } else {
            // Exact legacy H tap positions: each 1/blurWidth offset covers two source
            // texels. Combining these with GL_LINEAR would silently alter the Gaussian.
            for (int i = 1; i <= RADIUS; i++) {
                appendSymmetricSample(shader, "vec2(" + i + ".0 * u_texelSize.x, 0.0)",
                        "u_weights[" + i + "]");
            }
        }
        shader.append("  gl_FragColor = vec4(col, 1.0);\n}\n");
        return shader.toString();
    }

    private static void appendSymmetricSample(StringBuilder shader, String delta, String weight) {
        shader.append("  col += (texture2D(u_texture, clamp(v_texCoord + ").append(delta)
                .append(", vec2(0.0), vec2(1.0))).rgb\n");
        shader.append("        + texture2D(u_texture, clamp(v_texCoord - ").append(delta)
                .append(", vec2(0.0), vec2(1.0))).rgb) * ").append(weight).append(";\n");
    }
}
