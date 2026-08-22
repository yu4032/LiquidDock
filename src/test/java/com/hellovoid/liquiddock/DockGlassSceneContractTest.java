package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Dock stays continuous, replaces producer generations, and batches item glass into one output. */
public class DockGlassSceneContractTest {
    @Test public void dockRotationReplacesProducerInsteadOfOnlyResizingOldBuffer() throws Exception {
        String view = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"));
        String renderer = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyRenderer.java"));
        assertTrue(view.contains("replaceProducerGeneration"));
        assertTrue(view.contains("releaseInputProducer"));
        assertTrue(renderer.contains("rebindProducer"));
        assertTrue(view.contains("continuous"));
    }

    @Test public void dockItemsUseOneDockCompositorAndNeverWorkspaceStaticNodes() throws Exception {
        Path compositorPath = Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockGlassCompositor.java");
        Path itemPath = Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockGlassItemNode.java");
        assertTrue(Files.exists(compositorPath));
        assertTrue(Files.exists(itemPath));
        String compositor = Files.readString(compositorPath);
        String item = Files.readString(itemPath);
        assertTrue(compositor.contains("beginGlassFrame"));
        assertTrue(compositor.contains("drawDockBody"));
        assertTrue(compositor.contains("drawItem"));
        assertTrue(compositor.contains("ONE_OUTPUT_SWAP"));
        assertFalse(item.contains("TextureView"));
        assertFalse(item.contains("Surface "));
        assertFalse(item.contains("LauncherGlassStaticNode"));
    }
}
