package com.hellovoid.prismal;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class PrismalIos27DockShaderTest {
    @Test
    public void shaderKeepsOneSdfAndIsGatedPerDockDraw() {
        String stock = PrismalOs4InvariantShader.apply(
                PrismalComponentGateShader.apply(
                        PrismalOpticalEdgeShader.apply(
                                PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT))));
        String shader = PrismalIos27DockShader.apply(stock);
        assertTrue(shader.contains("uniform float u_ios27DockEnabled;"));
        assertTrue(shader.contains("iosOuterRamp * iosOuterTail"));
        assertTrue(shader.contains("vec2 iosNormal = normalize(os4SdfGradient"));
        assertTrue(shader.contains("iosTopLight + iosBottomLight"));
        assertTrue(shader.contains("u_ios27DockEnabled < 0.5 || distMask > 1.7"));
        assertTrue(shader.contains("gl_FragColor = vec4(color, iosAlpha);"));
        assertFalse(shader.contains("gl_FragColor = vec4(color, opacity * u_transmittance);"));
    }

    @Test
    public void profileDoesNotLeakToOtherGlassNodes() {
        PrismalHighlightProfile selected = PrismalHighlightProfile.ALL_ENABLED
                .withIos27DockOptics().withOs4EdgeReplacingLegacyEdge();
        assertTrue(selected.ios27Dock);
        assertTrue(selected.os4Edge);
        assertFalse(selected.specular);
        assertFalse(PrismalHighlightProfile.ALL_ENABLED.ios27Dock);
    }
}
