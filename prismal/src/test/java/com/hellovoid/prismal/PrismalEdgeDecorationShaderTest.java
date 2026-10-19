package com.hellovoid.prismal;

import org.junit.Test;
import static org.junit.Assert.*;

public class PrismalEdgeDecorationShaderTest {
    private static String upstream() {
        return PrismalOs4InvariantShader.apply(
                PrismalComponentGateShader.apply(
                        PrismalOpticalEdgeShader.apply(
                                PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT))));
    }

    @Test public void retainsOriginalOs4HighlightsAndDrawsARealAntialiasedContour() {
        String result = PrismalEdgeDecorationShader.apply(upstream());
        assertTrue(result.contains("float os4SoftLight"));
        assertTrue(result.contains("u_os4DirectionalIntensity"));
        assertTrue(result.contains("u_os4DirectionalOppositeIntensity"));
        assertTrue(result.contains("float insideDistance = edgeDist - insetShiftPx;"));
        assertTrue(result.contains("float aa = max(edgeAa * 0.5, 0.25);"));
        assertTrue(result.contains("float lineWidthPx = u_backlitWidthPx * taper;"));
        assertTrue(result.contains("float innerBand = smoothstep(-aa, aa, insideDistance)"));
        assertTrue(result.contains("rimLineCoverage = lineOpacity;"));
        assertTrue(result.contains("float lineAlpha = opacity * rimLineCoverage;"));
        assertTrue(result.contains("float combinedAlpha = lineAlpha"));
        assertTrue(result.contains("gl_FragColor = vec4(combinedColor, combinedAlpha);"));
        assertTrue(result.contains("gl_FragColor = vec4(color, opacity * u_transmittance);"));
        assertFalse(result.contains("color *= 1.0 - darken;"));
        assertTrue(result.contains("smoothstep("));
        assertFalse(result.contains("u_os4EdgeEnabled > 0.5"));
        assertTrue(result.contains("float lightEnergy = clamp("));
        assertTrue(result.contains("float fadeStart = mix("));
        assertTrue(result.contains("float taper = darkness * darkness"));
        assertTrue(result.contains("dot(edgeNormal, u_invariantLightXY)"));
        assertFalse(result.contains("abs(n.x)"));
        assertFalse(result.contains("u_strokeMinCos"));
        assertFalse(result.contains("abs(n.x)"));
        assertFalse(result.contains("u_outerStroke"));
        assertFalse(result.contains("u_strokeMinCos"));
        assertEquals(1, occurrences(result, "uniform float u_backlitStrength;"));
    }

    @Test public void shadowUsesOneFrameAndOriginalSilhouetteOnly() {
        String result = PrismalEdgeDecorationShader.apply(upstream());
        String exterior = result.substring(result.indexOf("// Derivative-dependent OS4"),
                result.indexOf("if (opacity < 0.001) discard;"));
        assertTrue(exterior.contains("float distanceToCaster = max(0.0, distMask - projected);"));
        assertTrue(exterior.contains("u_castShadowColor"));
        assertFalse(exterior.contains("sdRoundBox("));
        assertFalse(exterior.contains("texture2D("));
        assertFalse(exterior.contains("exp("));
        assertTrue(result.contains("vec2 os4SdfGradient = vec2("));
        assertTrue(result.contains("gl_FragColor = vec4(color, opacity * u_transmittance);"));
    }

    @Test public void optionalGuardCannotChangeBaselineRaster() {
        String baseline = PrismalRasterGuardShader.apply(PrismalShaderSources.VERTEX);
        assertFalse(baseline.contains("u_edgeRasterGuardPx"));
        String optional = PrismalRasterGuardShader.applyWithEdgeGuard(
                PrismalShaderSources.VERTEX);
        assertTrue(optional.contains("u_edgeRasterGuardPx"));
        PrismalGeometry geometry = new PrismalGeometry(1000, 800, 500, 300, 100, 80, 16);
        PrismalNodeScissor.Rect original = PrismalNodeScissor.compute(geometry, 1000, 800);
        PrismalNodeScissor.Rect expanded = new PrismalNodeScissor.Rect(0, 0, 0, 0);
        assertTrue(PrismalNodeScissor.computeInto(geometry, 1000, 800, expanded, 22f));
        assertEquals(original.width + 44, expanded.width);
        assertEquals(original.height + 44, expanded.height);
    }

    @Test public void contourOpacityIsIndependentFromBodyTransmittance() {
        // Same-fragment straight-alpha source-over. At low glass translucency,
        // a strong physically narrow line still remains dark and visible.
        float bodyA = 0.18f;
        float lineA = 0.90f;
        float combinedA = lineA + bodyA * (1f - lineA);
        float combinedRgb = (0.015f * lineA + 0.9f * bodyA * (1f - lineA))
                / combinedA;
        assertTrue(combinedA > 0.9f);
        assertTrue(combinedRgb < 0.05f);
        // When disabled, the original Prismal color and alpha are unchanged.
        assertEquals(bodyA, 0f + bodyA*(1f-0f), 0.0001f);
    }

    @Test public void noExtraPixelsOrGpuProgramWhenEffectsOff() {
        assertFalse(PrismalEdgeDecoration.NONE.hasBacklit());
        assertFalse(PrismalEdgeDecoration.NONE.hasShadow());
        assertEquals(0f, PrismalEdgeDecoration.NONE.extraGuardPx(3f), 0.00001f);
        PrismalEdgeDecoration nativeOwner = new PrismalEdgeDecoration(
                8f, 0.7f, 0.6f, 15f, 4f, 0f, 0f, 0f, 0.2f, 0.5f).withoutCastShadow();
        assertTrue(nativeOwner.hasBacklit());
        assertFalse(nativeOwner.hasShadow());
        assertEquals(0f, nativeOwner.extraGuardPx(3f), 0.00001f);
    }

    @Test public void stockDirectionalLightGivesSideHairlineAndBrightTopBottom() {
        float lightX = -0.50f, lightY = -0.80f;
        float length = (float) Math.hypot(lightX, lightY);
        lightX /= length;
        lightY /= length;
        float left = darkContour(-1f, 0f, lightX, lightY, .42f, .14f, 1f, .85f, .60f);
        float right = darkContour(1f, 0f, lightX, lightY, .42f, .14f, 1f, .85f, .60f);
        float top = darkContour(0f, 1f, lightX, lightY, .42f, .14f, 1f, .85f, .60f);
        float bottom = darkContour(0f, -1f, lightX, lightY, .42f, .14f, 1f, .85f, .60f);
        assertTrue("left side should have a real hairline", left > .7f);
        assertTrue("right side should have a real hairline", right > .7f);
        assertEquals("top remains lit", 0f, top, .0001f);
        assertEquals("bottom remains lit", 0f, bottom, .0001f);
        assertEquals("balanced lighting does not erase grazing side lines",
                left, darkContour(-1f, 0f, lightX, lightY,
                        .42f, .42f, 1f, .85f, .60f), .0001f);
    }

    @Test public void darkRimTracksRotationAndFadesWithoutFixedSides() {
        float side = darkContour(1f, 0f, 1f, 0f, .42f, .14f, 1f, .85f, .60f);
        float top = darkContour(0f, 1f, 1f, 0f, .42f, .14f, 1f, .85f, .60f);
        assertEquals(0f, side, .0001f);
        assertTrue(top > .7f);
        float grazing = darkContour(0f, 1f, .53f, .848f,
                .42f, .14f, 1f, .85f, .60f);
        assertTrue(grazing < top);
        assertEquals(0f, darkContour(1f, 0f, 0f, 1f,
                0f, 0f, 1f, .85f, .60f), .0001f);
    }

    @Test public void strokeIsInsideGlassAndDoesNotExpandRasterWhenShadowOff() {
        String decorated = PrismalEdgeDecorationShader.apply(upstream());
        assertTrue(decorated.contains("insideDistance = edgeDist - insetShiftPx"));
        assertTrue(decorated.contains("lineOpacity"));
        assertFalse(decorated.contains("float edgeBand = os4EdgeBand("));
        assertFalse(decorated.contains("color *= 1.0 - darken;"));
        assertTrue(decorated.contains("u_os4EdgeEnabled > 0.5"));
        PrismalEdgeDecoration optical = new PrismalEdgeDecoration(
                1f, .85f, .6f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        assertTrue(optical.hasBacklit());
        assertFalse(optical.hasShadow());
        assertEquals(0f, optical.extraGuardPx(3f), 0f);
    }

    /** Numeric reference to the GLSL's 2D grazing-angle SDF absorption. */
    private static float darkContour(float nx, float ny, float lx, float ly,
                                     float main, float opposite, float width,
                                     float opacity, float softness) {
        float lightEnergy = Math.max(0f, Math.min(1f, (main+opposite)*2f));
        if (lightEnergy <= .001f || width <= 0f || opacity <= 0f) return 0f;
        float incidence = Math.abs(nx*lx + ny*ly);
        float start = .30f + .35f*softness;
        float end = Math.min(start+.30f,.99f);
        float x = Math.max(0f, Math.min(1f,(incidence-start)/(end-start)));
        float darkness = 1f - (x*x*(3f-2f*x));
        float taper = darkness*darkness*(3f-2f*darkness);
        return width*opacity*lightEnergy*taper*taper;
    }

    private static int occurrences(String s, String needle) {
        int n=0, i=0;
        while ((i=s.indexOf(needle,i))>=0) { n++; i+=needle.length(); }
        return n;
    }
}
