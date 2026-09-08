package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Source-level ownership contract for Milestone 2's renderer-local Bloom pipeline. */
public class PrismalBloomRendererContractTest {
    private static String source() throws Exception {
        Path moduleRelative = Path.of(
                "src/main/java/com/hellovoid/prismal/PrismalRenderer.java");
        Path repoRelative = Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java");
        Path path = Files.exists(moduleRelative) ? moduleRelative : repoRelative;
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    public void rendererOwnsOnlyLocalBloomProgramsAndScratchTargets() throws Exception {
        String source = source();

        assertTrue(source.contains("private int bloomMaskProgram;"));
        assertTrue(source.contains("private int bloomCompositeProgram;"));
        assertTrue(source.contains("private int bloomMaskTexture;"));
        assertTrue(source.contains("private int bloomMaskFramebuffer;"));
        assertTrue(source.contains("private int bloomBlurTextureH;"));
        assertTrue(source.contains("private int bloomBlurFramebufferH;"));
        assertTrue(source.contains("private int bloomBlurTextureV;"));
        assertTrue(source.contains("private int bloomBlurFramebufferV;"));
        assertTrue(source.contains("private int bloomTargetWidth;"));
        assertTrue(source.contains("private int bloomTargetHeight;"));

        assertFalse(source.contains("bloomBackgroundTexture"));
        assertFalse(source.contains("bloomOes"));
        assertFalse(source.contains("bloomSurfaceTexture"));
    }

    @Test
    public void rendererCompilesDedicatedMaskAndPositionedCompositePrograms() throws Exception {
        String source = source();

        assertTrue(source.contains(
                "createProgram(PrismalBloomShaderSources.MASK_VERTEX,\n"
                        + "                PrismalBloomShaderSources.MASK_FRAGMENT)"));
        assertTrue(source.contains(
                "createProgram(PrismalBloomShaderSources.COMPOSITE_VERTEX,\n"
                        + "                PrismalBloomShaderSources.COMPOSITE_FRAGMENT)"));
    }

    @Test
    public void zeroBloomWidthKeepsMilestoneOneAdaptiveDefault() throws Exception {
        String source = source();

        assertTrue(source.contains("private float effectiveOs4BloomWidthPx("));
        assertTrue(source.contains("p.os4BloomWidthPx > 0f"));
        assertTrue(source.contains("0.090f"));
        assertTrue(source.contains("9f"));
        assertTrue(source.contains("28f"));
    }

    @Test
    public void enabledBloomMovesOutOfMainPassAndRunsAfterGlassDraw() throws Exception {
        String source = source();
        int method = source.indexOf("private void renderGlassNode(");
        int nextMethod = source.indexOf("private void bindInterleavedQuad(", method);
        String renderGlass = source.substring(method, nextMethod);

        assertTrue(renderGlass.contains(
                "uniform1f(\"u_os4BloomInMainPass\", p.os4BloomEnabled ? 0f : 1f);"));
        int mainDraw = renderGlass.indexOf(
                "GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);");
        int overlay = renderGlass.indexOf("renderBloomOverlay(g, p, opacity);");
        assertTrue("local Bloom must be composited after the main glass raster",
                mainDraw >= 0 && overlay > mainDraw);
    }

    @Test
    public void overlayPlansGlassLocalTargetThenRunsMaskTwoBlurPassesAndComposite() throws Exception {
        String source = source();

        assertTrue(source.contains("PrismalBloomTarget.plan("));
        assertTrue(source.contains("g.glassWidth"));
        assertTrue(source.contains("g.glassHeight"));
        assertTrue(source.contains("p.os4BloomBlurRadiusPx"));
        assertTrue(source.contains("ensureBloomTargets(spec.width, spec.height);"));
        assertTrue(source.contains("renderBloomMask(g, p, bloomWidthPx, opacity);"));
        assertTrue(source.contains(
                "renderBloomBlurPass(blurHProgram, bloomMaskTexture, bloomBlurFramebufferH"));
        assertTrue(source.contains(
                "renderBloomBlurPass(blurVProgram, bloomBlurTextureH, bloomBlurFramebufferV"));
        assertTrue(source.contains("renderBloomComposite(g);"));
    }

    @Test
    public void bloomTargetsStayLocalAndAreReleasedWithRendererTargets() throws Exception {
        String source = source();
        int ensure = source.indexOf("private void ensureBloomTargets(");
        int renderMask = source.indexOf("private void renderBloomMask(", ensure);
        String ensureBloom = source.substring(ensure, renderMask);

        assertTrue(ensureBloom.contains("createTexture(bloomTargetWidth, bloomTargetHeight)"));
        assertFalse(ensureBloom.contains("createTexture(outputWidth, outputHeight)"));

        assertTrue(source.contains("releaseBloomTargets();"));
        assertTrue(source.contains("glDeleteFramebuffers(1, new int[]{bloomMaskFramebuffer}, 0)"));
        assertTrue(source.contains("glDeleteFramebuffers(1, new int[]{bloomBlurFramebufferH}, 0)"));
        assertTrue(source.contains("glDeleteFramebuffers(1, new int[]{bloomBlurFramebufferV}, 0)"));
        assertTrue(source.contains("glDeleteTextures(1, new int[]{bloomMaskTexture}, 0)"));
        assertTrue(source.contains("glDeleteTextures(1, new int[]{bloomBlurTextureH}, 0)"));
        assertTrue(source.contains("glDeleteTextures(1, new int[]{bloomBlurTextureV}, 0)"));
        assertTrue(source.contains("glDeleteProgram(bloomMaskProgram)"));
        assertTrue(source.contains("glDeleteProgram(bloomCompositeProgram)"));
    }
}
