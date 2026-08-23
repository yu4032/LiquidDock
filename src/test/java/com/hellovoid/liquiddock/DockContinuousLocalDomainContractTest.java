package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Dock stays an independent continuous TextureView/EGL domain and owns no Workspace scene nodes. */
public class DockContinuousLocalDomainContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void dockProducerRendersEveryProducerFrameIndependentlyOfWorkspace() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("input.setOnFrameAvailableListener"));
        assertTrue(view.contains("drawLatestFrame(true);"));
        assertFalse(view.contains("LauncherGlassSession"));
        assertFalse(view.contains("LauncherGlassSceneController"));
    }

    @Test public void dockOutputIsOneLegacyTextureViewWithoutPerItemGlassNodes() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("extends TextureView"));
        assertFalse(Files.exists(MAIN.resolve("DockGlassItemNode.java")));
        assertFalse(Files.exists(MAIN.resolve("DockGlassCompositor.java")));
        assertFalse(view.contains("DockGlassSceneSnapshot"));
    }
}
