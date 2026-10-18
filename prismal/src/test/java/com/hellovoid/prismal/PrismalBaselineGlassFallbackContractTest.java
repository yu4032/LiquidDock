package com.hellovoid.prismal;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

/** Regression: failed optional shadow shaders must not blank every glass component. */
public class PrismalBaselineGlassFallbackContractTest {
    @Test public void originalShaderAlwaysLoadsWithoutOptionalEdgeSource() throws Exception {
        String source = Files.readString(Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java"));
        String vertex = PrismalRasterGuardShader.apply(PrismalShaderSources.VERTEX);
        assertFalse(vertex.contains("u_edgeRasterGuardPx"));
        assertTrue(source.contains("baselineGlassProgram = glassProgram;"));
        assertTrue(source.contains("if (wantsDecorations && decoratedGlassProgram == 0"));
        assertTrue(source.contains("decorationProgramUnavailable = true;"));
        assertTrue(source.contains("selected == decoratedGlassProgram && selected != 0"));
        assertTrue(source.contains("selected = wantsDecorations && decoratedGlassProgram != 0"));
        assertFalse(source.contains(
                "glassFragment = PrismalEdgeDecorationShader.apply(glassFragment);"));
    }

    @Test public void baselineRasterStillMatchesOriginalTwoPixelGuard() {
        String baseline = PrismalRasterGuardShader.apply(PrismalShaderSources.VERTEX);
        assertTrue(baseline.contains(
                "vec2 rasterScale = (safeGlassSize + vec2(4.0)) / safeGlassSize;"));
        assertTrue(PrismalRasterGuardShader.applyWithEdgeGuard(
                PrismalShaderSources.VERTEX).contains("2.0 * u_edgeRasterGuardPx"));
    }
}
