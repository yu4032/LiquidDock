package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * Static render-contract audit: the final TextureView pass copies Prismal straight RGBA and must
 * not apply another SRC_ALPHA multiply to partially covered SDF edge pixels.
 */
public final class DockFinalCompositeAlphaContractTest {
    private static final Path PASS_BLUR_VIEW =
            Path.of("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java");

    @Test
    public void finalTextureViewCompositeCopiesStraightRgba() throws Exception {
        String source = Files.readString(PASS_BLUR_VIEW);
        assertTrue(source.contains("private boolean renderCompositePass"));
        assertTrue(source.contains(
                "// Prismal output is already the final straight-RGBA material image."));
        assertFalse(source.contains(
                "GLES20.glBlendFuncSeparate(\n"
                        + "                GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA,\n"
                        + "                GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);"));
    }
}
