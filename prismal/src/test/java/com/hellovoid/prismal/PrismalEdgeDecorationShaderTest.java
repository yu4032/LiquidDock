package com.hellovoid.prismal;

import org.junit.Test;
import static org.junit.Assert.*;

public class PrismalEdgeDecorationShaderTest {
    @Test public void usesUpstreamLightAndSdfWithoutTouchingLensOrOs4() {
        String original = PrismalOs4InvariantShader.apply(
                PrismalComponentGateShader.apply(
                        PrismalOpticalEdgeShader.apply(
                                PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT))));
        String decorated = PrismalEdgeDecorationShader.apply(original);
        assertTrue(decorated.contains("vec2 cast = -light * u_dropShadowRadiusPx"));
        assertTrue(decorated.contains("u_dropShadowLightFollow"));
        assertTrue(decorated.contains("gradSdRoundedRectRealistic(cKy, halfSz, radCorner)"));
        assertTrue(decorated.contains("u_strokeLightResponse"));
        assertTrue(decorated.contains("if (distMask > 0.0)"));
        assertTrue(decorated.contains("vec2 baseOffset = edgeRefractionUv;"));
        assertTrue(decorated.contains("u_os4DirectionalIntensity"));
        assertEquals(1, count(decorated, "uniform float u_dropShadowRadiusPx;"));
    }

    @Test public void guardExpandsBothRasterAndScissorOnlyWhenActive() {
        String vertex = PrismalRasterGuardShader.apply(PrismalShaderSources.VERTEX);
        assertFalse(vertex.contains("u_edgeRasterGuardPx"));
        String decoratedVertex =
                PrismalRasterGuardShader.applyWithEdgeGuard(PrismalShaderSources.VERTEX);
        assertTrue(decoratedVertex.contains("u_edgeRasterGuardPx"));
        PrismalGeometry geometry = new PrismalGeometry(1000, 800, 500, 300, 100, 80, 16);
        PrismalNodeScissor.Rect baseline = PrismalNodeScissor.compute(geometry, 1000, 800);
        PrismalNodeScissor.Rect expanded = new PrismalNodeScissor.Rect(0, 0, 0, 0);
        assertTrue(PrismalNodeScissor.computeInto(geometry, 1000, 800, expanded, 22f));
        assertEquals(baseline.width + 44, expanded.width);
        assertEquals(baseline.height + 44, expanded.height);
    }

    @Test public void disabledStyleHasZeroExtraOverdraw() {
        assertEquals(0f, PrismalEdgeDecoration.NONE.extraGuardPx(3f), 0f);
        assertFalse(PrismalEdgeDecoration.NONE.hasShadow());
        assertFalse(PrismalEdgeDecoration.NONE.hasStroke());
    }

    private static int count(String text, String pattern) {
        int n = 0, offset = 0;
        while ((offset = text.indexOf(pattern, offset)) >= 0) {
            n++;
            offset += pattern.length();
        }
        return n;
    }
}
