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
        assertTrue(patched.contains("float insetShiftPx = clamp((u_refractionInset - 20.0) * 0.25,"));
        assertTrue(patched.contains("edgeAa - insetShiftPx, distMask)"));
        assertFalse(patched.contains("smoothstep(-edgeAa, edgeAa, distMask)"));
        assertTrue(patched.contains("bandFracR * opticalEdgeScale"));
        assertTrue(patched.contains("0.09 * opticalEdgeScale"));
        assertTrue(patched.contains("tw * 0.42 * opticalEdgeScale"));
    }

    @Test
    public void meniscusReflectionUsesSharpSourceAndSkipsInvisiblePixels() {
        String patched = PrismalOpticalEdgeShader.apply(PrismalShaderSources.FRAGMENT);
        assertTrue(patched.contains("if (os4ReflectionWeight > 0.001) {"));
        assertTrue(patched.contains(
                "texture2D(u_backgroundTexture, os4ReflectUv).rgb;"));
        assertFalse(patched.contains(
                "texture2D(u_blurredTexture, os4ReflectUv)"));
        assertTrue(patched.indexOf("float os4ReflectionWeight =")
                < patched.indexOf("if (os4ReflectionWeight > 0.001)"));
        // Gaussian body blur and its user-configurable quality remain intact.
        assertTrue(patched.contains("texture2D(u_blurredTexture, uvCenter)"));
    }

    @Test
    public void shadowSoftnessIsMonotonicAndKeepsTheShippedDefault() {
        String patched = PrismalOpticalEdgeShader.apply(PrismalShaderSources.FRAGMENT);
        assertTrue(patched.contains("clamp(u_shadowSoftness / 20.0, 0.0, 1.0)"));
        assertFalse(patched.contains("u_shadowSoftness > 1.0"));
        float previous = -1f;
        for (int saved = 0; saved <= 2000; saved++) {
            float t = Math.min(1f, Math.max(0f, saved / 2000f));
            float extent = 0.15f + (0.60f - 0.15f) * t;
            assertTrue(extent >= previous);
            previous = extent;
            if (saved == 1000) org.junit.Assert.assertEquals(0.375f, extent, 0.00001f);
        }
    }

    @Test
    public void vibrancyWorksBelowNeutral100Percent() {
        String patched = PrismalOpticalEdgeShader.apply(PrismalShaderSources.FRAGMENT);
        assertTrue(patched.contains("if (abs(sat - 1.0) <= 0.001) return rgb;"));
        assertFalse(patched.contains("if (sat <= 1.001) return rgb;"));
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
    @Test
    public void os4NormalReusesPreDiscardSdfDerivativesInsteadOfFourExtraSdfEvaluations() {
        String patched = PrismalOpticalEdgeShader.apply(PrismalShaderSources.FRAGMENT);
        assertTrue(patched.contains(
                "dFdx(distMask) / max(edgePixelStep.x, 1e-3)"));
        assertTrue(patched.contains(
                "-dFdy(distMask) / max(edgePixelStep.y, 1e-3)"));
        assertTrue(patched.contains(
                "vec3 os4EdgeNormal3 = normalize(vec3(os4SdfGradient, 1.0));"));
        assertFalse(patched.contains("float sdfXp = sdRoundBox("));
        assertFalse(patched.contains("float sdfXn = sdRoundBox("));
        assertFalse(patched.contains("float sdfYp = sdRoundBox("));
        assertFalse(patched.contains("float sdfYn = sdRoundBox("));

        int derivative = patched.indexOf("vec2 os4SdfGradient = vec2(");
        int discard = patched.indexOf("if (opacity < 0.001) discard;");
        assertTrue("derivatives must be evaluated before divergent discard",
                derivative >= 0 && derivative < discard);
    }

    @Test
    public void normalDebugShowsActualOs4EdgeNormalAndDirectionalLighting() {
        String patched = PrismalComponentGateShader.apply(
                PrismalOpticalEdgeShader.apply(
                        PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)));

        assertFalse(patched.contains("gl_FragColor = vec4(N * 0.5 + 0.5, opacity);"));
        assertTrue(patched.contains("normalize(mix(N, os4EdgeNormal3, os4EdgeMask))"));
        assertTrue(patched.contains("mix(os4DebugColor, vec3(1.0), os4SoftLight * 0.7)"));
        assertTrue(patched.contains("gl_FragColor = vec4(os4DebugColor, opacity);"));
        assertTrue(patched.indexOf("vec3 os4EdgeNormal3 =") >= 0);
        assertTrue(patched.indexOf("float os4SoftLight =") > patched.indexOf("vec3 os4EdgeNormal3 ="));
        assertTrue(patched.indexOf("if (u_showNormals == 1)") > patched.indexOf("float os4SoftLight ="));
        assertTrue(patched.indexOf("gl_FragColor = vec4(color, opacity * u_transmittance);")
                > patched.indexOf("gl_FragColor = vec4(os4DebugColor, opacity);"));
    }


}
