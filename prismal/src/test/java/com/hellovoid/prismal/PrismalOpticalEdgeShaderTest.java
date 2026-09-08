package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrismalOpticalEdgeShaderTest {
    private static String patched() {
        return PrismalOpticalEdgeShader.apply(PrismalShaderSources.FRAGMENT);
    }

    @Test
    public void thickensOpticalBandsAndUsesDerivativeAntialiasing() {
        String patched = patched();

        assertTrue(patched.contains("#extension GL_OES_standard_derivatives : enable"));
        assertTrue(patched.contains("float opticalEdgeScale = clamp(u_highlightWidth, 0.5, 3.0);"));
        assertTrue(patched.contains("float edgeAa = max(fwidth(distMask), 0.75);"));
        assertTrue(patched.contains("smoothstep(-edgeAa, edgeAa, distMask)"));
        assertTrue(patched.contains("bandFracR * opticalEdgeScale"));
        assertTrue(patched.contains("0.09 * opticalEdgeScale"));
        assertTrue(patched.contains("tw * 0.42 * opticalEdgeScale"));
    }

    @Test
    public void derivesLogicalPixelFootprintFromRasterDerivatives() {
        String patched = patched();

        assertTrue(patched.contains("vec2 edgePixelStep = vec2("));
        assertTrue(patched.contains("length(dFdx(pPx))"));
        assertTrue(patched.contains("length(dFdy(pPx))"));
        assertTrue(patched.contains("float edgePixelFootprint = max(edgePixelStep.x, edgePixelStep.y);"));
    }

    @Test
    public void usesCentralDifferenceSdfNormalNearTheOpticalEdge() {
        String patched = patched();

        assertTrue(patched.contains("float sdfXp = sdRoundBox(pPx + vec2(edgePixelStep.x, 0.0)"));
        assertTrue(patched.contains("float sdfXn = sdRoundBox(pPx - vec2(edgePixelStep.x, 0.0)"));
        assertTrue(patched.contains("float sdfYp = sdRoundBox(pPx + vec2(0.0, edgePixelStep.y)"));
        assertTrue(patched.contains("float sdfYn = sdRoundBox(pPx - vec2(0.0, edgePixelStep.y)"));
        assertTrue(patched.contains("vec2 sdfEdgeGradient = 0.5 * vec2("));
        assertTrue(patched.contains("vec3 os4EdgeNormal3 = normalize(vec3(sdfEdgeGradient, 1.0));"));
        assertTrue(patched.contains("float sdfNormalBlend = os4EdgeBand(edgeDist"));
    }

    @Test
    public void usesOs4InspiredNonlinearBandShaping() {
        String patched = patched();

        assertTrue(patched.contains("float os4EdgeCurve(float t)"));
        assertTrue(patched.contains("if (t >= 0.85) return 1.0;"));
        assertTrue(patched.contains("sqrt(clamp(t, 0.0, 1.0))"));
        assertTrue(patched.contains("1.0 - pow(1.0 - shaped, 1.35)"));
    }

    @Test
    public void sharesAntialiasedCoverageAcrossBrightEdgeBands() {
        String patched = patched();

        assertTrue(patched.contains("float os4EdgeBand(float edgeDist, float width, float aa)"));
        assertTrue(patched.contains("reflShell = os4EdgeBand(edgeDist"));
        assertTrue(patched.contains("menBlend = os4EdgeBand(edgeDist"));
        assertTrue(patched.contains("edgeSil = os4EdgeBand(edgeDist"));
        assertTrue(patched.contains("shellRim = os4EdgeBand(edgeDist"));
        assertTrue(patched.contains("faceSheenSoft = os4EdgeBand(edgeDist"));
        assertTrue(patched.contains("plusHL = os4EdgeBand(edgeDist"));
        assertFalse(patched.contains("smoothstep(bandR, bandR * 0.06, edgeDist)"));
    }

    @Test
    public void modelsVolumetricEdgeThicknessSeparatelyFromHighlightWidth() {
        String patched = patched();

        assertTrue(patched.contains("float os4ThicknessPx ="));
        assertTrue(patched.contains("float os4EdgePx ="));
        assertTrue(patched.contains("float os4EdgeDepth ="));
        assertTrue(patched.contains("mix((os4ThicknessPx - os4EdgePx) * 2.0,"));
        assertTrue(patched.contains("os4ThicknessPx * 2.0, os4EdgeDepth)"));
    }

    @Test
    public void offsetsBackgroundReflectionAlongPseudoNormal() {
        String patched = patched();

        assertTrue(patched.contains("float os4ReflectOffsetPx ="));
        assertTrue(patched.contains("vec2 os4ReflectUvOffset = opticalEdgeNormal"));
        assertTrue(patched.contains("(2.0 * os4EdgeNormal3.z)"));
        assertTrue(patched.contains("os4ReflectOffsetPx * os4EdgeRemain"));
        assertTrue(patched.contains("texture2D(u_blurredTexture, os4ReflectUv)"));
        assertTrue(patched.contains("texture2D(u_backgroundTexture, os4ReflectUv)"));
    }

    @Test
    public void directionalEdgeLightUsesMainOppositeAndAngleFalloff() {
        String patched = patched();

        assertTrue(patched.contains("float os4DirectionalMain ="));
        assertTrue(patched.contains("float os4DirectionalOpposite ="));
        assertTrue(patched.contains("u_os4DirectionalAngleRange"));
        assertTrue(patched.contains("u_os4DirectionalIntensity"));
        assertTrue(patched.contains("u_os4DirectionalOppositeIntensity"));
    }

    @Test
    public void softHighlightModulatesBackdropColorWithoutGrowingSilhouette() {
        String patched = patched();

        assertTrue(patched.contains("float os4DirectionalGain ="));
        assertTrue(patched.contains("color *= os4DirectionalGain;"));
        assertTrue(patched.contains("float os4DarkResponse ="));
        assertTrue(patched.contains("u_os4ReflectionLighten * os4DarkResponse"));
        assertFalse(patched.contains("os4BloomRing"));
        assertFalse(patched.contains("os4BloomColor"));
        assertFalse(patched.contains("opacity +="));
    }
}
