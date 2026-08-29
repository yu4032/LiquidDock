package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
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
    public void descendingSmoothstepsUseDefinedEdgeOrdering() {
        String patched = PrismalOpticalEdgeShader.apply(PrismalShaderSources.FRAGMENT);

        assertFalse(patched.contains("smoothstep(128.0, 46.0, minDim * 2.0)"));
        assertFalse(patched.contains(
                "smoothstep(clamp(minDim * 0.09 * opticalEdgeScale, 1.8, 18.0), 0.0, edgeDist)"));
        assertFalse(patched.contains("smoothstep(tw * 0.42 * opticalEdgeScale, 0.0, edgeDist)"));
        assertFalse(patched.contains("smoothstep(refractionHeight, 0.0, edgeDist)"));
        assertFalse(patched.contains("smoothstep(silW, 0.0, edgeDist)"));
        assertFalse(patched.contains("smoothstep(chromaFar, 0.0, edgeDist)"));
        assertFalse(patched.contains("smoothstep(bandR, bandR * 0.06, edgeDist)"));
        assertFalse(patched.contains("smoothstep(bandR * 1.8, bandR * 0.08, edgeDist)"));
        assertFalse(patched.contains("smoothstep(bandR * 0.95, bandR * 0.05, edgeDist)"));
        assertFalse(patched.contains("smoothstep(glowR, glowR * 0.5, length(pPx - glowPx))"));

        assertTrue(patched.contains(
                "1.0 - smoothstep(0.0, tw * 0.42 * opticalEdgeScale, edgeDist)"));
        assertTrue(patched.contains(
                "1.0 - smoothstep(0.0, refractionHeight, edgeDist)"));
    }
}
