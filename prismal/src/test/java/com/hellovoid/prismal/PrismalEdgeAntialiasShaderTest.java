package com.hellovoid.prismal;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrismalEdgeAntialiasShaderTest {
    @Test
    public void injectsFillDiffAndStandardStrokeCoverage() {
        String source = "uniform float u_rimStrength;\n"
                + "void main() { float distMask = 0.0; float edgeDist = -distMask;\n"
                + "gl_FragColor = vec4(color, opacity * u_transmittance);\n}";

        String patched = PrismalEdgeAntialiasShader.apply(source);

        assertTrue(patched.contains("uniform int u_glassStrokeEnabled;"));
        assertTrue(patched.contains("float fillDiffMask = outerCoverage - innerCoverage;"));
        assertTrue(patched.contains("float standardMask"));
        assertTrue(patched.contains("u_glassStrokeFillDiff == 1"));
        assertTrue(patched.contains("float strokeAa = 0.75;"));
        assertTrue(patched.contains("mix(color, u_glassStrokeColor.rgb"));
        assertTrue(patched.indexOf("mix(color, u_glassStrokeColor.rgb")
                < patched.indexOf("gl_FragColor ="));
    }
}
