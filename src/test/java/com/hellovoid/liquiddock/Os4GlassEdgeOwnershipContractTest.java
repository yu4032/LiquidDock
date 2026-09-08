package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * OS4 soft edge is the sole visual edge owner on the zero-copy GlassHost.
 * The configurable Dock foreground stroke may remain saved, but must not be layered above it.
 */
public class Os4GlassEdgeOwnershipContractTest {
    private static final Path GLASS_HOOK =
            Path.of("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java");
    private static final Path STROKE_RENDERER =
            Path.of("src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java");

    @Test
    public void glassHostPassesOs4EdgeOwnershipIntoForegroundStrokeRenderer() throws Exception {
        String source = Files.readString(GLASS_HOOK);
        String call = "DockStrokeRenderer.configureReplacingForeground(";
        int count = source.split("DockStrokeRenderer\\.configureReplacingForeground\\(", -1).length - 1;

        assertEquals(2, count);

        int from = 0;
        for (int i = 0; i < count; i++) {
            int start = source.indexOf(call, from);
            int end = Math.min(source.length(), start + 260);
            String window = source.substring(start, end);
            assertTrue(window.contains("config.glass.os4SoftEdgeEnabled"));
            from = start + call.length();
        }
    }

    @Test
    public void foregroundStrokeRendererReleasesItsOwnerWhenShaderOwnsEdge() throws Exception {
        String source = Files.readString(STROKE_RENDERER);
        assertTrue(source.contains("boolean shaderOwnsEdge"));
        assertTrue(source.contains("if (shaderOwnsEdge)"));
        assertTrue(source.contains("releaseInstalledStroke(host);"));
    }
}
