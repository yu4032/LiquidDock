package com.hellovoid.prismal;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrismalOpticalEdgeShaderTest {
    @Test
    public void thickensOpticalBandsAndUsesDerivativeAntialiasing() {
        String patched = PrismalOpticalEdgeShader.apply(PrismalShaderSources.FRAGMENT);

        assertTrue(patched.contains("#extension GL_OES_standard_derivatives : enable"));
        assertTrue(patched.contains("float opticalEdgeScale = clamp(u_highlightWidth, 0.5, 3.0);"));
        assertTrue(patched.contains("float edgeAa = max(fwidth(distMask), 0.75);"));
        assertTrue(patched.contains("smoothstep(-edgeAa, edgeAa, distMask)"));
        assertTrue(patched.contains("bandFracR * opticalEdgeScale"));
        assertTrue(patched.contains("0.09 * opticalEdgeScale"));
        assertTrue(patched.contains("tw * 0.42 * opticalEdgeScale"));
    }

    @Test
    public void highlightHairlinesUseDerivativeAwareBandCoverage() {
        String patched = PrismalOpticalEdgeShader.apply(PrismalShaderSources.FRAGMENT);

        assertTrue(patched.contains("float aaDescendingBand(float outer, float inner, float value, float aa)"));
        assertTrue(patched.contains("aaDescendingBand(bandR, bandR * 0.06, edgeDist, edgeAa)"));
        assertTrue(patched.contains("aaDescendingBand(bandR * 1.8, bandR * 0.08, edgeDist, edgeAa)"));
        assertTrue(patched.contains("aaDescendingBand(bandR * 0.95, bandR * 0.05, edgeDist, edgeAa)"));
    }
}
