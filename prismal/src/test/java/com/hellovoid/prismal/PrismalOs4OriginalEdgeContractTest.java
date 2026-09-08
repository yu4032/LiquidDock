package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class PrismalOs4OriginalEdgeContractTest {
    private static Path mainDir() {
        Path module = Path.of("src/main/java/com/hellovoid/prismal");
        return Files.exists(module) ? module
                : Path.of("prismal/src/main/java/com/hellovoid/prismal");
    }

    private static String read(String name) throws Exception {
        return Files.readString(mainDir().resolve(name), StandardCharsets.UTF_8);
    }

    private static String patchedShader() {
        return PrismalOpticalEdgeShader.apply(PrismalShaderSources.FRAGMENT);
    }

    @Test
    public void exposesOriginalOs4EdgeControlsAndBindsThem() throws Exception {
        String params = read("PrismalParams.java");
        String renderer = read("PrismalRenderer.java");

        String[] fields = {
                "os4EdgeWidthPx",
                "os4ReflectOffsetPx",
                "os4ReflectionStrength",
                "os4ReflectionLighten",
                "os4DirectionalAngleRange",
                "os4DirectionalIntensity",
                "os4DirectionalOppositeIntensity"
        };
        for (String field : fields) {
            assertTrue("PrismalParams missing " + field,
                    params.contains("public final float " + field + ";"));
        }

        assertTrue(renderer.contains("uniform1f(\"u_os4EdgeWidthPx\", p.os4EdgeWidthPx)"));
        assertTrue(renderer.contains("uniform1f(\"u_os4ReflectOffsetPx\", p.os4ReflectOffsetPx)"));
        assertTrue(renderer.contains("uniform1f(\"u_os4ReflectionStrength\", p.os4ReflectionStrength)"));
        assertTrue(renderer.contains("uniform1f(\"u_os4ReflectionLighten\", p.os4ReflectionLighten)"));
        assertTrue(renderer.contains("uniform1f(\"u_os4DirectionalAngleRange\", p.os4DirectionalAngleRange)"));
        assertTrue(renderer.contains("uniform1f(\"u_os4DirectionalIntensity\", p.os4DirectionalIntensity)"));
        assertTrue(renderer.contains("uniform1f(\"u_os4DirectionalOppositeIntensity\", p.os4DirectionalOppositeIntensity)"));
    }

    @Test
    public void reflectsTheActiveBackdropInsideTheSdfEdgeBand() {
        String shader = patchedShader();

        assertTrue(shader.contains("float os4EdgeRemain = 1.0 - os4EdgeT;"));
        assertTrue(shader.contains("vec2 os4ReflectUvOffset = os4EdgeNormal3.xy"));
        assertTrue(shader.contains("* (2.0 * os4EdgeNormal3.z)"));
        assertTrue(shader.contains("vec2 os4ReflectUv = clamp("));
        assertTrue(shader.contains("texture2D(u_blurredTexture, os4ReflectUv)"));
        assertTrue(shader.contains("texture2D(u_backgroundTexture, os4ReflectUv)"));
        assertTrue(shader.contains("u_os4ReflectionStrength"));
        assertFalse(shader.contains("vec2 os4ReflectUvOffset = opticalEdgeNormal"));
    }

    @Test
    public void usesDirectionalSoftLightAndBackgroundDependentLift() {
        String shader = patchedShader();

        assertTrue(shader.contains("u_os4DirectionalAngleRange"));
        assertTrue(shader.contains("u_os4DirectionalIntensity"));
        assertTrue(shader.contains("u_os4DirectionalOppositeIntensity"));
        assertTrue(shader.contains("float os4DirectionalMain ="));
        assertTrue(shader.contains("float os4DirectionalOpposite ="));
        assertTrue(shader.contains("float os4DirectionalGain ="));
        assertTrue(shader.contains("float os4DarkResponse ="));
        assertTrue(shader.contains("u_os4ReflectionLighten * os4DarkResponse"));
    }

    @Test
    public void removesIndependentBloomHaloAndDoesNotExpandOpacity() {
        String shader = patchedShader();

        assertFalse(shader.contains("os4BloomRing"));
        assertFalse(shader.contains("os4BloomColor"));
        assertFalse(shader.contains("opacity +="));
        assertFalse(shader.contains("opacity = max(opacity"));
    }
}
