package com.hellovoid.prismal;

import static org.junit.Assert.*;
import org.junit.Test;

/** Dock side shader must retain the OS4 highlights and never alter baseline glass. */
public class PrismalDockSideShadeShaderTest {
    private static String base() {
        return PrismalOs4InvariantShader.apply(
                PrismalComponentGateShader.apply(
                        PrismalOpticalEdgeShader.apply(
                                PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT))));
    }

    @Test public void darkensOnlyLateralEdgesAndSuppressesIlluminatedSide() {
        String source = PrismalDockSideShadeShader.apply(base());
        assertTrue(source.contains("verticalSide = smoothstep"));
        assertTrue(source.contains("abs(sideNormal.x)"));
        assertTrue(source.contains("abs(sideNormal.y)"));
        assertTrue(source.contains("illuminated * u_dockSideShadeLightSuppression"));
        assertTrue(source.contains("os4EdgeBand(edgeDist"));
        assertTrue(source.contains("color *= 1.0 - darkness;"));
        assertTrue(source.contains("gl_FragColor = vec4(color, opacity * u_transmittance);"));
        assertTrue(source.contains("os4SoftLight"));
        assertTrue(source.contains("u_os4DirectionalIntensity"));
    }

    @Test public void originalShaderAndRasterRemainIdenticalWhenDisabled() {
        String original = base();
        assertFalse(original.contains("u_dockSideShadeAlpha"));
        String adapted = PrismalDockSideShadeShader.apply(original);
        assertTrue(adapted.length() > original.length());
        assertEquals(1, occurrences(adapted, "uniform float u_dockSideShadeAlpha;"));
        assertFalse(PrismalRasterGuardShader.apply(
                PrismalShaderSources.VERTEX).contains("u_dockSideShadeAlpha"));
    }

    private static int occurrences(String value, String term) {
        int count = 0, at = 0;
        while ((at = value.indexOf(term, at)) >= 0) {
            count++;
            at += term.length();
        }
        return count;
    }
}
