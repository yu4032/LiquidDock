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
        assertTrue(patched.contains("vec2 sdfEdgeNormal = normalize(vec2(sdfXp - sdfXn, sdfYp - sdfYn));"));
        assertTrue(patched.contains("float sdfNormalBlend = os4EdgeBand(edgeDist"));
        assertTrue(patched.contains("outward = normalize(mix(outward, sdfEdgeNormal, sdfNormalBlend));"));
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
    public void declaresExplicitOs4ControlsAsUniforms() {
        String patched = patched();

        assertTrue(patched.contains("uniform float u_os4EdgeWidthPx;"));
        assertTrue(patched.contains("uniform float u_os4ThicknessPx;"));
        assertTrue(patched.contains("uniform float u_os4ReflectOffsetPx;"));
        assertTrue(patched.contains("uniform float u_os4BloomWidthPx;"));
        assertTrue(patched.contains("uniform float u_os4BloomIntensity;"));
        assertTrue(patched.contains("uniform float u_os4BloomInMainPass;"));
    }

    @Test
    public void modelsVolumetricEdgeThicknessSeparatelyFromHighlightWidth() {
        String patched = patched();

        assertTrue(patched.contains("float os4EdgePx = u_os4EdgeWidthPx > 0.0"));
        assertTrue(patched.contains("float os4ThicknessPx = u_os4ThicknessPx > 0.0"));
        assertTrue(patched.contains("float os4EdgeDepth ="));
        assertTrue(patched.contains("mix((os4ThicknessPx - os4EdgePx) * 2.0,"));
        assertTrue(patched.contains("os4ThicknessPx * 2.0, os4EdgeDepth)"));
        assertFalse(patched.contains("float os4EdgePx = clamp(minDim * 0.060"));
        assertFalse(patched.contains("float os4ThicknessPx = max(u_glassThickness"));
    }

    @Test
    public void offsetsEdgeReflectionAlongStabilizedSdfNormal() {
        String patched = patched();

        assertTrue(patched.contains("float os4ReflectOffsetPx = u_os4ReflectOffsetPx > 0.0"));
        assertTrue(patched.contains("vec2 os4ReflectUvOffset = opticalEdgeNormal"));
        assertTrue(patched.contains("os4ReflectOffsetPx * (1.0 - os4EdgeDepth)"));
        assertTrue(patched.contains("vec3 os4EdgeReflection = texture2D("));
        assertFalse(patched.contains("float os4ReflectOffsetPx = clamp(os4ThicknessPx * 0.38"));
    }

    @Test
    public void buildsFiniteWidthBloomRingFromOuterAndInnerCoverage() {
        String patched = patched();

        assertTrue(patched.contains("float os4BloomOuter ="));
        assertTrue(patched.contains("float os4BloomInner ="));
        assertTrue(patched.contains("float os4BloomRing = clamp(os4BloomOuter * (1.0 - os4BloomInner)"));
        assertTrue(patched.contains("float os4BloomEdgePx = u_os4BloomWidthPx > 0.0"));
        assertFalse(patched.contains("float os4BloomEdgePx = u_highlightWidth"));
        assertFalse(patched.contains("float os4BloomEdgePx = clamp(minDim * 0.090"));
    }

    @Test
    public void lightsBloomRingWithMainOppositeAndAmbientResponses() {
        String patched = patched();

        assertTrue(patched.contains("float os4BloomMain ="));
        assertTrue(patched.contains("float os4BloomOpposite ="));
        assertTrue(patched.contains("float os4BloomAmbient ="));
        assertTrue(patched.contains("float os4BloomLight = max(os4BloomAmbient,"));
    }

    @Test
    public void compositesBloomAsOpticalContributionNotSilhouetteExpansion() {
        String patched = patched();

        assertTrue(patched.contains("vec3 os4BloomColor ="));
        assertTrue(patched.contains(
                "color += os4BloomColor * os4BloomRing * u_os4BloomInMainPass"));
        assertTrue(patched.contains("u_os4BloomIntensity"));
        assertFalse(patched.contains("opacity += os4BloomRing"));
    }
}
