package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Device regression: Dock is the proven legacy independent continuous layer, not a Workspace scene. */
public class DockGlassSceneContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void dockRendererUsesLegacyIndependentContinuousPipeline() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        assertTrue(view.contains("input.setOnFrameAvailableListener"));
        assertTrue(view.contains("drawLatestFrame(true);"));
        assertTrue(view.contains("int prismalTexture = prismalRenderer.render("));
        assertTrue(renderer.contains("gpuBackdrop.rebindProducer(reason)"));
        assertFalse(view.contains("DockGlassCompositor"));
        assertFalse(view.contains("DockGlassSceneSnapshot"));
        assertFalse(view.contains("replaceProducerGeneration"));
        assertFalse(view.contains("LauncherGlassSession"));
        assertFalse(view.contains("LauncherGlassSceneController"));
    }

    @Test public void dockHasNoSchemeAItemCompositorOrWorkspaceIconRegistry() throws Exception {
        assertFalse(Files.exists(MAIN.resolve("DockGlassCompositor.java")));
        assertFalse(Files.exists(MAIN.resolve("DockGlassItemNode.java")));
        assertFalse(Files.exists(MAIN.resolve("DockGlassItemRegistry.java")));
        assertFalse(Files.exists(MAIN.resolve("DockGlassSceneSnapshot.java")));

        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        assertFalse(hook.contains("DockGlassItemRegistry.register"));
    }

    @Test public void dockKeepsItsOwnPassBlurBindingAndNeverUsesWorkspaceSceneGeneration() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(bridge.contains("mode=\" + (callerManagedUpdates ? \"caller-managed\" : \"continuous\")"));
        assertTrue(view.contains("Miuix307PassBlurBridge.Binding binding"));
        assertFalse(view.contains("sceneGeneration"));
        assertFalse(view.contains("requestFreshBackdrop"));
    }
}
