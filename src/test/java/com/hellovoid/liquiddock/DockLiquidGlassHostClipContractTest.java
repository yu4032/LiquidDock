package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Keeps the pre-35939d6 host clip behavior on the v2.1.2 compatibility branch. */
public class DockLiquidGlassHostClipContractTest {
    private static final Path HOST = Path.of(
            "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java");

    @Test
    public void hostStillClipsChildrenToDockShape() throws Exception {
        String source = Files.readString(HOST);

        assertTrue(source.contains("ensureClipPath();"));
        assertTrue(source.contains("if (clipPath.isEmpty()) return;"));
        assertTrue(source.contains("canvas.clipPath(clipPath);"));
        assertTrue(source.contains("canvas.restoreToCount(save);"));
    }
}
