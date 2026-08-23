package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Dock is one independent output domain; body and Dock icon glass share that one compositor. */
public class DockGlassSceneContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void dockRendererUsesIndependentPassBlurPipelineWithOneLocalBatch() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("input.setOnFrameAvailableListener"));
        assertTrue(view.contains("drawLatestFrame(true);"));
        assertTrue(view.contains("prismalRenderer.prepareBackdrop("));
        assertTrue(view.contains("dockCompositor.drawFrame("));
        assertFalse(view.contains("LauncherGlassSession"));
        assertFalse(view.contains("LauncherGlassSceneController"));
    }

    @Test public void dockItemCompositorHasNoPerItemOutputResources() throws Exception {
        String compositor = Files.readString(MAIN.resolve("DockGlassCompositor.java"));
        String item = Files.readString(MAIN.resolve("DockGlassItemNode.java"));

        assertTrue(compositor.contains("renderer.beginGlassFrame()"));
        assertTrue(compositor.contains("renderer.drawGlass("));
        assertFalse(item.contains("extends TextureView"));
        assertFalse(item.contains("new TextureView("));
        assertFalse(item.contains("new SurfaceTexture("));
        assertFalse(item.contains("EGLSurface "));
    }

    @Test public void dockKeepsOwnPassBlurBindingAndNeverUsesWorkspaceGeneration() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(bridge.contains("mode=continuous-on-bind"));
        assertTrue(view.contains("Miuix307PassBlurBridge.Binding binding"));
        assertFalse(view.contains("sceneGeneration"));
        assertFalse(view.contains("requestFreshBackdrop"));
    }
}
