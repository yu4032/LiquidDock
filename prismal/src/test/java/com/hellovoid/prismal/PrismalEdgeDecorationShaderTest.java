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
        assertTrue(result.contains("color = mix(color, vec3(0.015), lineOpacity);"));
        assertFalse(result.contains("color *= 1.0 - darken;"));
        assertTrue(result.contains("smoothstep("));
        assertTrue(result.contains("float taper = darkness * darkness"));
        assertTrue(result.contains("os4Directional / lightReference"));
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

    @Test public void physicalLineTapersInBothThicknessAndOpacity() {
        // This models the OS4 directional irradiance already evaluated per pixel.
        float full = darkContour(0.0f, 1.5f, 0.85f, .60f);
        float grazing = darkContour(0.30f, 1.5f, 0.85f, .60f);
        float lit = darkContour(0.60f, 1.5f, 0.85f, .60f);
        assertTrue(full > grazing);
        assertTrue(grazing > lit);
        assertEquals(0f, lit, 0.0001f);
        assertEquals(0f, darkContour(0.0f, 0f, .85f, .60f), 0.0001f);
        // No signed main-minus-fill: two equal lights can still produce
        // grazing edges, as in the iOS reference screenshots.
        assertTrue(darkContour(.1f, 1f, .85f, .60f) > 0f);
    }

    @Test public void strokeIsInsideGlassAndDoesNotExpandRasterWhenShadowOff() {
        String decorated = PrismalEdgeDecorationShader.apply(upstream());
        assertTrue(decorated.contains("insideDistance = edgeDist - insetShiftPx"));
        assertTrue(decorated.contains("lineOpacity"));
        assertTrue(decorated.contains("u_os4EdgeEnabled > 0.5"));
        PrismalEdgeDecoration optical = new PrismalEdgeDecoration(
                1f, .85f, .6f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        assertTrue(optical.hasBacklit());
        assertFalse(optical.hasShadow());
        assertEquals(0f, optical.extraGuardPx(3f), 0f);
    }

    /** Scalar reference for a light-controlled near-black line of tapered width. */
    private static float darkContour(float relativeLight, float maxWidth,
                                     float maxAlpha, float softness) {
        if (maxWidth <= 0f || maxAlpha <= 0f) return 0f;
        float z = Math.max(0f, Math.min(1f,
                relativeLight / Math.max(softness, .05f)));
        float illuminated = z*z*(3f-2f*z);
        float darkness = 1f-illuminated;
        float taper = darkness*darkness*(3f-2f*darkness);
        float width = maxWidth * taper;
        return taper * maxAlpha * width;
    }

    private static int occurrences(String s, String needle) {
        int n=0, i=0;
        while ((i=s.indexOf(needle,i))>=0) { n++; i+=needle.length(); }
        return n;
    }
}
