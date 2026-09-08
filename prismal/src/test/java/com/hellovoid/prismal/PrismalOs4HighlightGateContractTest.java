package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Keeps the extracted OS4 reflection optical path independent from user-toggleable light layers. */
public class PrismalOs4HighlightGateContractTest {
    private static String combinedShader() {
        String optical = PrismalOpticalEdgeShader.apply(PrismalShaderSources.FRAGMENT);
        return PrismalComponentGateShader.apply(optical);
    }

    @Test
    public void litRimGateControlsOs4MainDirectionalLight() {
        String shader = combinedShader();
        assertTrue(shader.contains(
                "max(u_os4DirectionalIntensity, 0.0) * u_componentLitRim * os4MainFalloff"));
    }

    @Test
    public void oppositeRimGateControlsOs4OppositeDirectionalLight() {
        String shader = combinedShader();
        assertTrue(shader.contains(
                "max(u_os4DirectionalOppositeIntensity, 0.0) * u_componentOppositeRim * os4OppositeFalloff"));
    }

    @Test
    public void backgroundReflectionIsNotDisabledByHighlightComponentGates() {
        String shader = combinedShader();
        assertTrue(shader.contains("texture2D(u_blurredTexture, os4ReflectUv)"));
        assertTrue(shader.contains("texture2D(u_backgroundTexture, os4ReflectUv)"));
        assertTrue(shader.contains(
                "* max(u_os4ReflectionStrength, 0.0), 0.0, 1.0);"));
        assertFalse(shader.contains("u_os4ReflectionStrength * u_component"));
    }
}
