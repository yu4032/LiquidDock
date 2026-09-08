package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Contract for continuous OS4 straight-edge to rounded-corner lighting. */
public class PrismalOs4SdfNormalContinuityContractTest {
    private static String shader() {
        return PrismalOpticalEdgeShader.apply(PrismalShaderSources.FRAGMENT);
    }

    @Test
    public void os4NormalComesDirectlyFromCentralDifferenceSdfGradient() {
        String shader = shader();
        assertTrue(shader.contains("vec2 sdfEdgeGradient = 0.5 * vec2("));
        assertTrue(shader.contains("vec3 os4EdgeNormal3 = normalize(vec3(sdfEdgeGradient, 1.0));"));
        assertFalse(shader.contains("vec2 sdfEdgeNormal = normalize(os4EdgeNormal3.xy"));
        assertFalse(shader.contains("mix(outward, sdfEdgeNormal"));
        assertFalse(shader.contains("os4EdgeNormal3 = normalize(vec3(opticalEdgeNormal"));
    }

    @Test
    public void os4ReflectionUsesTheSameThreeDimensionalSdfNormal() {
        String shader = shader();
        assertTrue(shader.contains("vec2 os4ReflectUvOffset = os4EdgeNormal3.xy"));
        assertTrue(shader.contains("* (2.0 * os4EdgeNormal3.z)"));
        assertFalse(shader.contains("vec2 os4ReflectUvOffset = opticalEdgeNormal"));
    }

    @Test
    public void os4DirectionalLightUsesThatSameSdfNormalWhileLegacyOutwardRemainsAvailable() {
        String shader = shader();
        assertTrue(shader.contains("float os4A = clamp(dot(os4EdgeNormal3, os4LightDir3)"));
        assertTrue(shader.contains("vec2 outward = (length(gradLens) > 1e-4) ? normalize(gradLens)"));
        assertTrue(shader.contains("vec2 gDir = outward;"));
        assertTrue(shader.contains("vec2 gN = outward;"));
        assertFalse(shader.contains("vec2 gDir = opticalEdgeNormal;"));
        assertFalse(shader.contains("vec2 gN = opticalEdgeNormal;"));
    }
}
