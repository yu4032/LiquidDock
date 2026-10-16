package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contract for the OPEN_FOLDER Recents compositor isolation.
 * Native Recents owns full-screen dimming and wallpaper geometry; only
 * the Prismal lens may be presented from the retired frozen snapshot.
 */
public class OpenFolderRecentsCompositionContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void recentsSuppressesOnlyTheFullScreenWallpaperQuad() throws Exception {
        String session = Files.readString(MAIN.resolve("ShortcutPopupGlassSession.java"));
        assertTrue(session.contains("folderRecentsLensOnly"));
        assertTrue(session.contains("if (folderBackdrop && !lensOnly) {"));
        assertTrue(session.contains("if (lensOnly) {"));
        assertTrue(session.contains("GLES20.glClearColor(0f, 0f, 0f, 0f)"));
        assertTrue(session.contains("GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)"));
        assertTrue(session.contains("GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexture)"));
        assertTrue(session.contains("prismalRenderer.beginGlassFrame()"));
        // Frozen Gaussian remains a sampled Prismal input, not an extra
        // root-wide wallpaper in the native Recents compositor.
        assertTrue(session.contains("prismalRenderer.preparedBlurTexture()"));
        assertTrue(session.contains("sourceBackend.setUpdatesEnabled(false"));
    }

    @Test public void returnRevealsFullBackdropOnlyAfterTheReplacementEglSwap() throws Exception {
        String session = Files.readString(MAIN.resolve("ShortcutPopupGlassSession.java"));
        String coordinator = Files.readString(MAIN.resolve("OpenFolderGlassCoordinator.java"));
        assertTrue(session.contains("long before = presentedOutputFrameCount;"));
        assertTrue(session.contains("presentedOutputFrameCount > before"));
        assertTrue(session.contains("presentedOutputFrameCount++;"));
        assertTrue(coordinator.contains("state.session.setFolderRecentsLensOnly(false, () -> {"));
        assertTrue(coordinator.contains("state.recentsReturnRestoreQueued = true;"));
        assertTrue(coordinator.contains("OWNER.completeRecentsReturn(state.serial)"));
        assertTrue(coordinator.contains("state.layer.clearFolderBackdropOutline()"));
        assertFalse(coordinator.contains("state.layer.setAlpha(1f -"));
    }

    @Test public void earlyNativeBlurDoesNotDriveIndependentScreenZoom() throws Exception {
        String coordinator = Files.readString(MAIN.resolve("OpenFolderGlassCoordinator.java"));
        String blurHook = Files.readString(MAIN.resolve("RecentsBackgroundBlurHook.java"));
        assertTrue(coordinator.contains("enterRecentsLensOnly(state)"));
        assertTrue(coordinator.contains("OpenFolderGlassGeometry.recentsMaterialMask("));
        assertFalse(coordinator.contains("recentsBackdropMask("));
        assertTrue(blurHook.contains("OpenFolderGlassCoordinator.onNativeRecentsGestureProgress(nativeRatio)"));
    }
}
