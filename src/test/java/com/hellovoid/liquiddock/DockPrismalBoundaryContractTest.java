package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class DockPrismalBoundaryContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void hostOutlineUsesDedicatedZeroInsetPrismalBoundary() throws Exception {
        String host = Files.readString(MAIN.resolve("DockLiquidGlassHostView.java"));
        String shape = Files.readString(MAIN.resolve("DockShapePath.java"));
        String prismal = Files.readString(MAIN.resolve("DockPrismalOutlinePath.java"));

        assertTrue(host.contains("DockPrismalOutlinePath.build"));
        assertFalse(host.contains("DockShapePath.build(outlinePath"));
        assertTrue(prismal.contains("new RectF(0f, 0f, width, height)"));
        assertFalse(prismal.contains("new RectF(.5f, .5f"));
        assertFalse(prismal.contains("width - .5f"));
        assertFalse(prismal.contains("height - .5f"));
        // Keep the half-pixel path available for stroke/pixel-center consumers.
        assertTrue(shape.contains("new RectF(.5f, .5f, width - .5f, height - .5f)"));
    }

    /**
     * Isolation experiment for the 2.1.2 regression: restore only the Host mask removed by
     * 35939d6, while keeping every later Prismal/shadow/stroke change unchanged.
     */
    @Test public void textureViewIsClippedByHostMaskAsBefore212HighlightFix() throws Exception {
        String host = Files.readString(MAIN.resolve("DockLiquidGlassHostView.java"));
        int dispatch = host.indexOf("dispatchDraw(Canvas canvas)");
        assertTrue(dispatch >= 0);
        String body = host.substring(dispatch);
        assertTrue(body.contains("ensureOutlinePath();"));
        assertTrue(body.contains("if (outlinePath.isEmpty()) return;"));
        assertTrue(body.contains("canvas.clipPath(outlinePath);"));
        assertTrue(body.contains("super.dispatchDraw(canvas);"));
        assertTrue(body.contains("canvas.restoreToCount(save);"));
    }
}
