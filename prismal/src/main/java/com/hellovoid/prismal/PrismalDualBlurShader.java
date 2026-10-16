package com.hellovoid.prismal;

/** Isolated opt-in shader adaptation for the closing-folder blur handoff.
 * The vendored Prismal source and every non-folder shader remain unchanged.
 */
final class PrismalDualBlurShader {
    private static final String SAMPLER = "uniform sampler2D u_blurredTexture;";
    private static final String ORIGINAL_SAMPLE = "texture2D(u_blurredTexture,";
    private static final String SAMPLE = "sampleFolderBlur(";
    private static final String FUNCTION = """
            vec4 sampleFolderBlur(vec2 uv) {
                if (u_workspaceBlurMix <= 0.0) return texture2D(u_blurredTexture, uv);
                if (u_workspaceBlurMix >= 1.0) return texture2D(u_workspaceBlurredTexture, uv);
                return mix(texture2D(u_blurredTexture, uv),
                           texture2D(u_workspaceBlurredTexture, uv), u_workspaceBlurMix);
            }

            """;

    private PrismalDualBlurShader() {}

    static String apply(String fragment) {
        if (fragment == null || !fragment.contains(SAMPLER)
                || !fragment.contains("void main() {")) {
            throw new IllegalStateException("Unsupported Prismal shader structure for dual blur");
        }
        int count = fragment.split(ORIGINAL_SAMPLE, -1).length - 1;
        if (count < 4) {
            throw new IllegalStateException("Unexpected Prismal blurred sample count " + count);
        }
        String changed = fragment.replace(ORIGINAL_SAMPLE, SAMPLE);
        changed = changed.replace(SAMPLER, SAMPLER + "\n"
                + "uniform sampler2D u_workspaceBlurredTexture;\n"
                + "uniform float u_workspaceBlurMix;");
        return changed.replace("void main() {", FUNCTION + "void main() {");
    }
}
