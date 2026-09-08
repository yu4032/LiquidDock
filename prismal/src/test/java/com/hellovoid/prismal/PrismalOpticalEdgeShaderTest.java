package com.hellovoid.prismal;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

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
    public void os4EdgeWidthStaysConstantAcrossGlassNodeSizes() {
        String patched = PrismalOpticalEdgeShader.apply(PrismalShaderSources.FRAGMENT);

        assertTrue(patched.contains("uniform float u_os4EdgeWidthPx;"));
        assertTrue(patched.contains("u_os4EdgeWidthPx > 0.0"));
        assertFalse(patched.contains("os4EdgeWidthPx = clamp(minDim"));
    }

    @Test
    public void os4OpticalEdgeIsDrivenByDedicatedCustomControls() {
        String patched = PrismalOpticalEdgeShader.apply(PrismalShaderSources.FRAGMENT);

        assertTrue(patched.contains("uniform float u_os4ReflectOffsetPx;"));
        assertTrue(patched.contains("uniform float u_os4ReflectionStrength;"));
        assertTrue(patched.contains("uniform float u_os4ReflectionLighten;"));
        assertTrue(patched.contains("uniform float u_os4DirectionalAngleRange;"));
        assertTrue(patched.contains("uniform float u_os4DirectionalIntensity;"));
        assertTrue(patched.contains("uniform float u_os4DirectionalOppositeIntensity;"));
        assertTrue(patched.contains("max(u_os4ReflectionStrength, 0.0)"));
        assertTrue(patched.contains("max(u_os4DirectionalIntensity, 0.0)"));
        assertTrue(patched.contains("max(u_os4DirectionalOppositeIntensity, 0.0)"));
    }
}
