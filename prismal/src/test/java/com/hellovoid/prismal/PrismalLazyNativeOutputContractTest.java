package com.hellovoid.prismal;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Prevent eager fullscreen output buffer allocation from returning to the EGL surface path. */
public class PrismalLazyNativeOutputContractTest {
    private static String renderer() throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/prismal/PrismalRenderer.java"));
    }

    private static String between(String s, String start, String end) {
        int a = s.indexOf(start);
        assertTrue(start, a >= 0);
        int b = s.indexOf(end, a);
        assertTrue(end, b > a);
        return s.substring(a, b);
    }

    @Test public void backdropPrepareAllocatesSourceAndBlurButNoNativeOutput() throws Exception {
        String source = renderer();
        String prepare = between(source,
                "private void ensureTargets(int nextRenderWidth, int nextRenderHeight,",
                "private void ensureNativeOutputTarget()");
        assertTrue(prepare.contains("sourceTexture = createTexture(renderWidth, renderHeight)"));
        assertTrue(prepare.contains("blurFramebufferV = createFramebuffer(blurTextureV)"));
        assertFalse(prepare.contains("outputTexture = createTexture("));
        assertFalse(prepare.contains("outputFramebuffer = createFramebuffer("));
        assertTrue(prepare.contains("sourceFramebuffer != 0 && blurFramebufferH != 0"));
    }

    @Test public void textureRenderAllocatesOutputBeforeSelectingFramebuffer() throws Exception {
        String source = renderer();
        String region = between(source,
                "public void beginGlassFrameRegion(", "/** Append one glass node");
        assertTrue(region.indexOf("ensureNativeOutputTarget();")
                < region.indexOf("frameTarget.selectTexture(outputFramebuffer"));
        assertTrue(source.contains("return outputTexture;"));
        assertTrue(source.contains("outputNeedsFullClear = true;"));
    }

    @Test public void fullscreenEglWindowPathNeverNeedsNativeOutputTexture() throws Exception {
        String source = renderer();
        String surface = between(source,
                "public void beginGlassFrameOnSurface(", "/** Temporary lower-density");
        assertFalse(surface.contains("ensureNativeOutputTarget"));
        assertTrue(surface.contains("frameTarget.selectSurface(surfaceWidth, surfaceHeight)"));
        assertTrue(surface.contains("GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)"));
    }

    @Test public void failureAndResizeReleaseOldGpuResourcesSafely() throws Exception {
        String source = renderer();
        assertTrue(source.contains("if (outputFramebuffer != 0) GLES20.glDeleteFramebuffers"));
        assertTrue(source.contains("if (outputTexture != 0) GLES20.glDeleteTextures"));
        assertTrue(source.contains("GLES20.glDeleteTextures(1, new int[]{outputTexture}, 0);"));
    }
}
