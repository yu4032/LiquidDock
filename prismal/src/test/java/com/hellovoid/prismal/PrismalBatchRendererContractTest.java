package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Keeps the folder atlas API additive: Dock retains the same single-edge renderer/model. */
public class PrismalBatchRendererContractTest {
    private static String source() throws Exception {
        Path moduleRelative = Path.of(
                "src/main/java/com/hellovoid/prismal/PrismalRenderer.java");
        Path repoRelative = Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java");
        Path path = Files.exists(moduleRelative) ? moduleRelative : repoRelative;
        return Files.readString(path, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    @Test
    public void rendererExposesOneBackdropManyGlassNodes() throws Exception {
        String source = source();
        assertTrue(source.contains("public void prepareBackdrop("));
        assertTrue(source.contains("public void beginGlassFrame()"));
        assertTrue(source.contains("public void drawGlass("));

        int render = source.indexOf("public int render(");
        int output = source.indexOf("public int outputTexture()", render);
        String legacy = source.substring(render, output);
        int prepare = legacy.indexOf("prepareBackdrop(");
        int begin = legacy.indexOf("beginGlassFrame();");
        int draw = legacy.indexOf("drawGlass(geometry, params);");
        assertTrue("legacy render must delegate to the shared-backdrop path",
                prepare >= 0 && begin > prepare && draw > begin);
    }

    @Test
    public void firstRegionClearAfterTargetAllocationFallsBackToFullFrame() throws Exception {
        String source = source();
        assertTrue(source.contains("private boolean outputNeedsFullClear = true;"));
        assertTrue(source.contains("if (outputNeedsFullClear) {"));
        assertTrue(source.contains("outputNeedsFullClear = false;"));
        assertTrue(source.contains("outputFramebuffer = createFramebuffer(outputTexture);\n"
                + "        outputNeedsFullClear = true;"));
        assertTrue(source.contains("glassPositionLocation = requireAttrib(glassProgram, \"a_position\")"));
        assertFalse(source.contains("int position = requireAttrib(glassProgram, \"a_position\")"));
    }

    @Test
    public void rendererCachesFrameInvariantGlassStateAcrossNodes() throws Exception {
        String source = source();

        assertTrue(source.contains("private PrismalParams cachedStaticParams;"));
        assertTrue(source.contains("if (cachedStaticParams != p) {"));
        assertTrue(source.contains("uploadStaticGlassParams(p);"));
        assertTrue(source.contains("if (cachedHighlightMask != highlightMask) {"));
        assertTrue(source.contains("if (!glassTexturesBound) {"));
        assertTrue(source.contains("glassTexturesBound = false;"));
        assertTrue(source.contains("GLES20.glUniform1i(glassUniformLocation(\"u_backgroundTexture\"), 0);"));
        assertTrue(source.contains("GLES20.glUniform1i(glassUniformLocation(\"u_blurredTexture\"), 1);"));
        assertTrue(source.contains("GLES20.glUniform1i(glassUniformLocation(\"u_useBlurredTexture\"), 1);"));
    }

    @Test
    public void rendererCachesStableBlurUniformsAndSamplerUnits() throws Exception {
        String source = source();
        assertTrue(source.contains("private int cachedBlurWidth = -1;"));
        assertTrue(source.contains("private float cachedBlurSigma = Float.NaN;"));
        assertTrue(source.contains("boolean updateTexelSize = cachedBlurWidth != blurWidth"));
        assertTrue(source.contains("boolean updateSigma = Float.compare(cachedBlurSigma, sigma) != 0"));
        assertTrue(source.contains("GLES20.glUniform1i(sourceTextureLocation, 0);"));
        assertTrue(source.contains("GLES20.glUniform1i(blurHTextureLocation, 0);"));
        assertTrue(source.contains("GLES20.glUniform1i(blurVTextureLocation, 0);"));

        int sourceStart = source.indexOf("private void renderSourceAdapter(");
        int sourceEnd = source.indexOf("private void renderBlur(", sourceStart);
        String sourcePass = source.substring(sourceStart, sourceEnd);
        assertFalse(sourcePass.contains("glUniform1i(sourceTextureLocation"));

        int blurStart = source.indexOf("private void renderBlurPass(");
        int blurEnd = source.indexOf("private void renderGlassNode(", blurStart);
        String blurPass = source.substring(blurStart, blurEnd);
        assertFalse(blurPass.contains("glUniform1i("));
        assertTrue(blurPass.contains("if (updateTexelSize)"));
        assertTrue(blurPass.contains("if (updateSigma)"));
    }

    @Test
    public void rendererCanClearOnlyOneDirtyGlassRegion() throws Exception {
        String source = source();
        assertTrue(source.contains("public void beginGlassFrameRegion("));
        assertTrue(source.contains("GLES20.glScissor("));
        assertTrue(source.contains("Math.floor(left * scaleX)"));
        assertTrue(source.contains("Math.ceil(right * scaleX)"));
        assertTrue(source.contains("beginGlassFrameRegion(0f, 0f, width, height)"));
    }

    @Test
    public void batchApiDoesNotReintroduceLauncherSpecificOptics() throws Exception {
        String source = source();
        assertTrue(source.contains(
                "PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)"));
        assertFalse(source.contains("enum Mode"));
        assertFalse(source.contains("LAUNCHER_COMPACT"));
        assertFalse(source.contains("PrismalLauncherCompactShader"));
    }

    @Test
    public void downsampledBackdropKeepsGlassOutputAtLogicalResolution() throws Exception {
        String source = source();

        assertTrue(source.contains("private int outputWidth;"));
        assertTrue(source.contains("private int outputHeight;"));
        assertTrue(source.contains(
                "ensureTargets(physicalWidth, physicalHeight,\n"
                        + "                logicalFramebufferWidth, logicalFramebufferHeight);"));
        assertTrue(source.contains("outputTexture = createTexture(outputWidth, outputHeight);"));
        assertTrue(source.contains("GLES20.glViewport(0, 0, outputWidth, outputHeight);"));
        assertFalse(source.contains("outputTexture = createTexture(renderWidth, renderHeight);"));
    }
}
