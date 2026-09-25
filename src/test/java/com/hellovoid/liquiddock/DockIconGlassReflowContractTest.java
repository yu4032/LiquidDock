package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contract for Dock icon-glass reflow after hotseat membership/size changes. */
public class DockIconGlassReflowContractTest {
    private static final Path ITEM_NODE =
            Path.of("src/main/java/com/hellovoid/liquiddock/DockGlassItemNode.java");
    private static final Path COMPOSITOR =
            Path.of("src/main/java/com/hellovoid/liquiddock/DockGlassCompositor.java");
    private static final Path PASS_BLUR =
            Path.of("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java");

    @Test
    public void dockRootGeometryParticipatesInIconSceneFingerprint() throws Exception {
        String node = Files.readString(ITEM_NODE);

        assertTrue(node.contains("return mixViewGeometry(hash, dockRoot);"));
        assertTrue(node.contains("view.getLeft()"));
        assertTrue(node.contains("view.getRight()"));
        assertTrue(node.contains("view.getTranslationX()"));
        assertTrue(node.contains("view.getScaleX()"));
        assertTrue(node.contains("view.getPivotX()"));
    }

    @Test
    public void iconSceneChangeCanTriggerRenderWithoutBackdropMappingChange() throws Exception {
        String compositor = Files.readString(COMPOSITOR);
        String passBlur = Files.readString(PASS_BLUR);

        assertTrue(compositor.contains(
                "boolean refreshUiSceneIfNeeded(int framebufferWidth, int framebufferHeight,"));
        assertTrue(passBlur.contains(
                "boolean dockSceneChanged = dockCompositor.refreshUiSceneIfNeeded("));
        assertTrue(passBlur.contains("if (unchanged) {"));
        assertTrue(passBlur.contains(
                "if (dockSceneChanged && producerRecovery.hasFreshFrame())"));
        assertTrue(passBlur.contains("renderHandler.post(() -> drawLatestFrame(false));"));
    }
}
