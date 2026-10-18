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

    @Test public void keepsUpstreamHighlightsAndShadesOnlyTheExistingOpticalBand() {
        String result = PrismalEdgeDecorationShader.apply(upstream());
        assertTrue(result.contains("float os4SoftLight"));
        assertTrue(result.contains("u_os4DirectionalIntensity"));
        assertTrue(result.contains("u_os4DirectionalOppositeIntensity"));
        assertTrue(result.contains("float edgeBand = os4EdgeBand("));
        assertTrue(result.contains("edgeDist, u_backlitWidthPx, edgeAa));"));
        assertTrue(result.contains("color *= 1.0 - darken;"));
        assertTrue(result.contains("smoothstep("));
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

    @Test public void backlitIrradianceIsSmoothAndRespondsToLight() {
        // Reference scalar model for the constant-cost GLSL optical response.
        assertEquals(0f, darkness(1f, 0.6f, 0.2f, 0.7f), 0.00001f);
        assertTrue(darkness(-1f, 0.6f, 0.2f, 0.7f)
                > darkness(1f, 0.6f, 0.2f, 0.7f));
        assertTrue(darkness(-1f, 0.6f, 0.2f, 0.7f)
                > darkness(0.0f, 0.6f, 0.2f, 0.2f));
        assertTrue(darkness(1f, 0.2f, 0.6f, 0.7f) > 0f);
        assertEquals(0f, darkness(-1f, 0f, 0f, 0.7f), 0.00001f);
    }

    private static float darkness(float facing, float main, float fill, float strength) {
        float max = Math.max(main, fill);
        if (max <= 0.001f) return 0f;
        float lit = main * Math.max(facing, 0f) + fill * Math.max(-facing, 0f);
        float deficit = Math.max(0f, Math.min(1f, (max-lit)/max));
        float t = Math.min(1f, deficit / .6f);
        return t*t*(3f-2f*t) * strength * max * .34f;
    }

    private static int occurrences(String s, String needle) {
        int n=0, i=0;
        while ((i=s.indexOf(needle,i))>=0) { n++; i+=needle.length(); }
        return n;
    }
}
