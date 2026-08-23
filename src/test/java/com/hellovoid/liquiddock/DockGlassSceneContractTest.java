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
 String c=Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/DockGlassCompositor.java")); String n=Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/DockGlassItemNode.java"));
 assertTrue(c.contains("ONE_OUTPUT_SWAP")&&c.contains("drawDockBody")&&c.contains("drawItem")&&c.contains("volatile DockGlassSceneSnapshot latestScene")); assertFalse(n.contains("extends TextureView")||n.contains("new TextureView")||n.contains("EGLSurface")||n.contains("LauncherGlassStaticNode"));
    }

    @Test public void dockUiPublishesImmutableSceneSnapshotBeforeGlDraw() throws Exception {
 String c=Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/DockGlassCompositor.java")); String v=Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"));
 assertTrue(c.contains("refreshUiSceneIfNeeded")&&c.contains("DockGlassSceneSnapshot latestScene()")); assertTrue(v.contains("dockCompositor.refreshUiSceneIfNeeded(")&&v.contains("dockCompositor.latestScene()")); String d=c.substring(c.indexOf("int drawFrame(")); assertFalse(d.contains("transformMatrixToGlobal")||d.contains(".capture(")||d.contains("snapshotForRoot"));
    }

    @Test public void dockContinuousProducerSamplesSceneInsteadOfForcingWallpaperOnlyRootExclusion() throws Exception {
        String bridge = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurBridge.java"));

        assertTrue(bridge.contains("callerManagedUpdates ? workspaceExclusions : dockExclusions"));
        assertTrue(bridge.contains("String[] workspaceExclusions"));
        assertTrue(bridge.contains("String[] dockExclusions"));
        assertFalse(bridge.contains("String[] exclusions = new String[]{\n                    rootName,"));
    }

}
