package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks the producer-space fix for the remaining uniform Launcher wallpaper offset. */
public class LauncherGlassProducerContentRectContractTest {
    private static final Path SESSION = Path.of(
            "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java");

    @Test
    public void sessionReadsViewRootSurfaceInsetsAndUsesContentUvRect() throws Exception {
        String source = Files.readString(SESSION);
        assertTrue(source.contains("mWindowAttributes"));
        assertTrue(source.contains("surfaceInsets"));
        assertTrue(source.contains("LauncherGlassSurfaceContentRect"));
        assertTrue(source.contains("contentRect"));
        assertTrue(source.contains("contentRect.left"));
        assertTrue(source.contains("contentRect.bottom"));
        assertTrue(source.contains("contentRect.width"));
        assertTrue(source.contains("contentRect.height"));
    }

    @Test
    public void rootNormalizationDoesNotHardcodeWholeOesBufferAsDecorContent() throws Exception {
        String source = Files.readString(SESSION);
        int method = source.indexOf("private void renderNormalizationRoot()");
        assertTrue(method >= 0);
        int end = source.indexOf("private ", method + 10);
        String body = end > method ? source.substring(method, end) : source.substring(method);
        assertFalse(body.contains(
                "glUniform4f(requireUniform(normalizeProgram, \"uBackdropRect\"), 0f, 0f, 1f, 1f)"));
    }
}
