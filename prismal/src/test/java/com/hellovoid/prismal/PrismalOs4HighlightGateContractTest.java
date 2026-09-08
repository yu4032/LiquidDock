package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Mutual-exclusion contract: OS4 edge mode owns its lighting while legacy layers stay independent. */
public class PrismalOs4HighlightGateContractTest {
    private static String combinedShader() {
        String optical = PrismalOpticalEdgeShader.apply(PrismalShaderSources.FRAGMENT);
        return PrismalComponentGateShader.apply(optical);
    }

    @Test
    public void os4ModeHasAnIndependentShaderGate() {
        String shader = combinedShader();
        assertTrue(shader.contains("uniform float u_os4SoftEdgeEnabled;"));
        assertTrue(shader.contains("float os4Mode = step(0.5, u_os4SoftEdgeEnabled);"));
        assertTrue(shader.contains("os4EdgeBand(edgeDist, os4EdgePx, edgeAa) * os4Mode"));
    }

    @Test
    public void os4DirectionalLightDoesNotDependOnLegacyLitRimGates() {
        String shader = combinedShader();
        assertTrue(shader.contains(
                "max(u_os4DirectionalIntensity, 0.0) * os4MainFalloff"));
        assertTrue(shader.contains(
                "max(u_os4DirectionalOppositeIntensity, 0.0) * os4OppositeFalloff"));
        assertFalse(shader.contains("u_os4DirectionalIntensity, 0.0) * u_componentLitRim"));
        assertFalse(shader.contains(
                "u_os4DirectionalOppositeIntensity, 0.0) * u_componentOppositeRim"));
    }

    @Test
    public void backgroundReflectionIsOwnedByOs4ModeNotLegacyHighlightGates() {
        String shader = combinedShader();
        assertTrue(shader.contains("texture2D(u_blurredTexture, os4ReflectUv)"));
        assertTrue(shader.contains("texture2D(u_backgroundTexture, os4ReflectUv)"));
        assertTrue(shader.contains(
                "* max(u_os4ReflectionStrength, 0.0), 0.0, 1.0);"));
        assertFalse(shader.contains("u_os4ReflectionStrength * u_component"));
    }
}
