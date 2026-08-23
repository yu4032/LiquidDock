package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Hardware regressions must follow the vendor Launcher state and real Dock output coordinates. */
public class LauncherVendorStateDockMappingPowerContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void recentsCoverageUsesVendorSemanticDispatcherNotViewVisibility() throws Exception {
        String hook = Files.readString(MAIN.resolve("LauncherGlassRecentsHook.java"));
        String controller = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(hook.contains("com.miui.home.recents.RecentsServiceDispatcher"));
        assertTrue(hook.contains("onRecentViewShow"));
        assertTrue(hook.contains("onRecentViewHide"));
        assertFalse(controller.contains("recents.getVisibility()"));
        assertFalse(controller.contains("recents.isShown()"));
        assertFalse(session.contains("syncRecentsForRoot(root)"));
    }

    @Test public void folderCoverageUsesVendorFolderStatusDispatcher() throws Exception {
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));

        assertTrue(folder.contains("com.miui.home.launcher.dock.v3.dependencies.FolderStatusServiceImpl"));
        assertTrue(folder.contains("dispatchFolderOpen"));
        assertTrue(folder.contains("dispatchFolderClose"));
    }

    @Test public void dockUsesHotSeatsOnlyForOwnershipAndMaterialHostForOutputCoordinates() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String compositor = Files.readString(MAIN.resolve("DockGlassCompositor.java"));
        String item = Files.readString(MAIN.resolve("DockGlassItemNode.java"));

        assertTrue(compositor.contains("ownershipRootRef"));
        assertTrue(compositor.contains("outputRootRef"));
        assertTrue(compositor.contains("outputRoot.transformMatrixToGlobal"));
        assertTrue(view.contains("new DockGlassCompositor("));
        assertTrue(view.contains("materialHost"));
        assertTrue(item.contains("belongsTo(ownershipRoot)"));
        assertFalse(compositor.contains("itemSpacing"));
        assertFalse(compositor.contains("dividerWidth"));
    }

    @Test public void dockReportsProducerAndGlDrawRatesWithoutDrivingExtraFrames() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("producerFrameCount"));
        assertTrue(view.contains("renderedFrameCount"));
        assertTrue(view.contains("[DC][PBTX][Power]"));
        assertFalse(view.contains("producerPump"));
        assertFalse(view.contains("Choreographer.getInstance().postFrameCallback"));
    }

    @Test public void prismalSmallNodesUseGeometryBoundedScissorInsteadOfFullFramebufferWork() throws Exception {
        String renderer = Files.readString(Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java"));

        assertTrue(renderer.contains("applyGlassNodeScissor"));
        assertTrue(renderer.contains("GLES20.glScissor("));
        assertTrue(renderer.contains("PrismalSampling.requiredGuardPx"));
    }
}
