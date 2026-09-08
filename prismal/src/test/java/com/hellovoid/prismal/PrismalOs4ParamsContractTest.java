package com.hellovoid.prismal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class PrismalOs4ParamsContractTest {
    @Test
    public void defaultsExposeOs4ControlsIndependentlyFromLegacyHighlightWidth() {
        PrismalParams params = PrismalParams.builder().build();

        assertEquals(0f, params.os4EdgeWidthPx, 0f);
        assertEquals(0f, params.os4ThicknessPx, 0f);
        assertEquals(0f, params.os4ReflectOffsetPx, 0f);
        assertEquals(0f, params.os4BloomWidthPx, 0f);
        assertEquals(0.62f, params.os4BloomIntensity, 0.0001f);
        assertEquals(5f, params.os4BloomBlurRadiusPx, 0.0001f);
        assertTrue(params.os4BloomEnabled);

        assertFalse(params.highlightWidth == params.os4EdgeWidthPx);
        assertFalse(params.highlightWidth == params.os4BloomWidthPx);
    }

    @Test
    public void rendererBindsOs4ControlsAndDisablesEmbeddedBloomWhenLocalPassIsEnabled()
            throws Exception {
        String source = Files.readString(rendererSource());

        assertTrue(source.contains("uniform1f(\"u_os4EdgeWidthPx\", p.os4EdgeWidthPx)"));
        assertTrue(source.contains("uniform1f(\"u_os4ThicknessPx\", p.os4ThicknessPx)"));
        assertTrue(source.contains("uniform1f(\"u_os4ReflectOffsetPx\", p.os4ReflectOffsetPx)"));
        assertTrue(source.contains("uniform1f(\"u_os4BloomWidthPx\", p.os4BloomWidthPx)"));
        assertTrue(source.contains("uniform1f(\"u_os4BloomIntensity\", p.os4BloomIntensity)"));
        assertTrue(source.contains(
                "uniform1f(\"u_os4BloomInMainPass\", p.os4BloomEnabled ? 0f : 1f)"));
    }

    private static Path rendererSource() {
        Path moduleRelative = Path.of("src/main/java/com/hellovoid/prismal/PrismalRenderer.java");
        Path repoRelative = Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java");
        return Files.exists(moduleRelative) ? moduleRelative : repoRelative;
    }
}
