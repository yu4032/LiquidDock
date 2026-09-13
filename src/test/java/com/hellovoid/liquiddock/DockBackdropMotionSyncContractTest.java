package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Dock backdrop sampling must not add an extra VSYNC behind vendor Dock motion. */
public class DockBackdropMotionSyncContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void sceneRefreshDoesNotDeferMappingToNextVsync() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String method = slice(view,
                "void requestDockSceneRefresh()",
                "/**\n     * Reconnect SurfaceFlinger's PassBlur producer");

        assertTrue(method.contains("dockCompositor.invalidateUiScene();"));
        assertTrue(method.contains("updateBackdropMapping();"));
        assertFalse("motion refresh must not add a postOnAnimation VSYNC before mapping",
                method.contains("postOnAnimation("));
    }

    @Test
    public void currentAnimationFrameRefreshesEvenWhenFadeLoopIsAlreadyScheduled() throws Exception {
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String method = slice(renderer,
                "static void requestDockAnimationFrames()",
                "static void clear()");

        int refresh = method.indexOf("gpuBackdrop.requestDockSceneRefresh();");
        int coalesce = method.indexOf("dockAnimationFrameScheduled");
        assertTrue("vendor animation frame must refresh backdrop before loop coalescing",
                refresh >= 0 && coalesce >= 0 && refresh < coalesce);
    }

    private static String slice(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        if (start < 0) throw new AssertionError("missing start token: " + startToken);
        int end = source.indexOf(endToken, start);
        if (end < 0) throw new AssertionError("missing end token: " + endToken);
        return source.substring(start, end);
    }
}
