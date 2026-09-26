package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * The Prismal scene texture stores straight RGBA. The final TextureView pass must copy those
 * channels verbatim; applying SRC_ALPHA again darkens partially covered SDF edge pixels.
 */
public final class DockFinalCompositeAlphaContractTest {
    private static final Path PASS_BLUR_VIEW =
            Path.of("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java");

    @Test
    public void finalTextureViewCompositeDoesNotPremultiplyPrismalRgbAgain() throws Exception {
        String source = Files.readString(PASS_BLUR_VIEW);
        int start = source.indexOf("private boolean renderCompositePass");
        int end = source.indexOf("\n    private void logPrismalMapping", start);
        assertTrue(start >= 0 && end > start);

        String composite = source.substring(start, end);
        assertTrue(composite.contains("GLES20.glDisable(GLES20.GL_BLEND);"));
        assertFalse(composite.contains("GLES20.glBlendFuncSeparate("));
        assertFalse(composite.contains("GLES20.GL_SRC_ALPHA"));
    }
}
