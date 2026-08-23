package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Dock stays a continuous independent layer; item geometry is not part of wallpaper generation. */
public class DockContinuousLocalDomainContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void dockProducerStillRendersEveryProducerFrameIndependentlyOfWorkspace() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("input.setOnFrameAvailableListener"));
        assertTrue(view.contains("drawLatestFrame(true);"));
        assertFalse(view.contains("LauncherGlassSession"));
        assertFalse(view.contains("LauncherGlassSceneController"));
    }

    @Test public void dockItemsArePublishedSeparatelyFromBackdropGeneration() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String compositor = Files.readString(MAIN.resolve("DockGlassCompositor.java"));

        assertFalse("Dock item geometry must not be frozen inside wallpaper/backdrop snapshots",
                view.contains("final DockGlassSceneSnapshot dockScene;"));
        assertTrue(view.contains("dockCompositor.refreshUiSceneIfNeeded"));
        assertTrue(view.contains("dockCompositor.latestScene()"));
        assertTrue(compositor.contains("private volatile DockGlassSceneSnapshot latestScene"));
        assertTrue(compositor.contains("void refreshUiSceneIfNeeded"));
        assertTrue(compositor.contains("DockGlassSceneSnapshot latestScene()"));
    }

    @Test public void glDrawConsumesOnlyPublishedDockLocalGeometry() throws Exception {
        String compositor = Files.readString(MAIN.resolve("DockGlassCompositor.java"));
        int drawStart = compositor.indexOf("int drawFrame(");
        assertTrue(drawStart >= 0);
        String draw = compositor.substring(drawStart);

        assertFalse(draw.contains("ViewGroup"));
        assertFalse(draw.contains("transformMatrixToGlobal"));
        assertFalse(draw.contains("captureUiSnapshot"));
        assertFalse(draw.contains("refreshUiSceneIfNeeded"));
    }
}
