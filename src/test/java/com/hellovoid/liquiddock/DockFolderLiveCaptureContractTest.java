package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Prevent a modal expanded folder from globally disabling Dock's live GPU source.
 * Hardware/OEM window visibility remains independent of our Workspace capture gate.
 */
public class DockFolderLiveCaptureContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void folderLifecycleMustNotOwnDockRenderOrProducerUpdates() throws Exception {
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));
        String coordinator = Files.readString(MAIN.resolve("OpenFolderGlassCoordinator.java"));
        String dock = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String scene = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));

        assertTrue(folder.contains("LauncherGlassSceneController.setFolderCoveredForAll(true)"));
        assertTrue(folder.contains("LauncherGlassSceneController.setFolderCoveredForAll(false)"));
        assertTrue(coordinator.contains("retireFolderCapture"));
        assertFalse("Folder opening must not disable Dock rendering",
                folder.contains("Miuix307ZeroCopyRenderer.setFolderFrozen"));
        assertFalse("Dock must not inherit FolderCling/Recents capture ownership",
                dock.contains("setFolderRenderFrozen"));
        assertFalse("Native Recents must not toggle an extra Dock draw gate",
                scene.contains("Miuix307ZeroCopyRenderer.setRecentsCovered"));
    }

    @Test public void dockAlwaysConsumesFreshFramesAndMayPresentDuringFolderGesture()
            throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        assertTrue(view.contains("input.setOnFrameAvailableListener"));
        assertTrue(view.contains("frameAvailable.set(true)"));
        assertTrue(view.contains("Miuix307PassBlurBridge.renewForceRefresh(binding)"));
        assertTrue(view.contains("scheduleRender(true)"));
        assertTrue(view.contains("EGL14.eglSwapBuffers"));
        assertFalse("Never drain/drop fresh OES frames just because a folder is open",
                view.contains("discardFrozenProducerFrame"));
        assertFalse("Dock compositor and render scheduling must not be blocked by folder",
                view.contains("folderRenderFrozen"));
        assertFalse("Do not resurrect a special post-folder producer wake-up",
                bridge.contains("requestFreshDockFrameAfterFolder"));
    }
}
