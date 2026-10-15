package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Folder GPU freeze must preserve visible caches and not publish stale Dock frames. */
public class FolderFrozenGlassHandoffContractTest {
    private static final Path SOURCE =
            Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String source(String filename) throws Exception {
        return Files.readString(SOURCE.resolve(filename));
    }

    @Test public void folderFreezesBothOutputsBeforeNativeOpen() throws Exception {
        String hook = source("OpenFolderGlassHook.java");
        String owner = source("MiuixFolderGlassHook.java");
        assertTrue(hook.contains("MiuixFolderGlassHook.onFolderOpeningBeforeNativeAnimation();"));
        assertTrue(hook.indexOf("MiuixFolderGlassHook.onFolderOpeningBeforeNativeAnimation();")
                < hook.indexOf("return chain.proceed(args);", hook.indexOf("new Class<?>[]{info, View.class}")));
        assertTrue(owner.contains("Miuix307ZeroCopyRenderer.setFolderFrozen(true);"));
        assertTrue(owner.contains("LauncherGlassSceneController.setFolderCoveredForAll(true);"));
        assertFalse(hook.contains("FolderDockVisibilityOwner"));
    }

    @Test public void frozenWorkspaceDoesNotInheritNativeFolderOpacity() throws Exception {
        String layer = source("LauncherGlassStaticLayer.java");
        String scene = source("LauncherGlassSceneController.java");
        assertTrue(layer.contains("isFolderBackdropFrozenForRoot(root)"));
        assertTrue(layer.contains("float alpha = frozenFolder ? 1f"));
        assertTrue(scene.contains("session.setFolderFrameFrozen(true)"));
        assertTrue(scene.contains("state.setFolderCovered(true, true)"));
    }

    @Test public void dockWaitsForFreshProducerFrameBeforeRedrawing() throws Exception {
        String renderer = source("Miuix307ZeroCopyRenderer.java");
        String output = source("Miuix307PassBlurTextureView.java");
        assertFalse(renderer.contains("if (!frozen) requestDockSceneRefresh()"));
        assertTrue(output.contains("awaitingPostFolderProducer"));
        assertTrue(output.contains("frameAvailable.set(false)"));
        assertTrue(output.contains("if (folderRenderFrozen) {"));
        assertTrue(output.contains("awaitingPostFolderProducer && !frameAvailable.get()"));
        assertTrue(output.contains("if (folderRenderFrozen) return;"));
    }

    @Test public void folderGlassKeepsOneShotBlurAndRestrictedLensShader() throws Exception {
        String session = source("ShortcutPopupGlassSession.java");
        String compositor = Files.readString(Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java"));
        assertTrue(session.contains("sourceBackend.setUpdatesEnabled(false, \"shortcut-popup-frozen\")"));
        assertTrue(session.contains("Float.compare(appliedBlur, next.blurRadiusPx) != 0"));
        assertTrue(compositor.contains("PrismalNodeScissor.computeInto("));
    }
}
