package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Every launcher glass object belongs to exactly one rendering domain. */
public class LauncherGlassDomainOwnershipContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void shortcutIconsAreClassifiedBeforeWorkspaceOrDockRegistration() throws Exception {
        assertTrue(Files.exists(MAIN.resolve("LauncherGlassHierarchy.java")));
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));

        assertTrue(hook.contains("LauncherGlassHierarchy.classify(host)"));
        assertTrue(hook.contains("LauncherGlassHierarchy.Domain.WORKSPACE"));
        assertTrue(hook.contains("LauncherGlassHierarchy.Domain.DOCK"));
        assertTrue(hook.contains("DockGlassItemRegistry.register(host)"));
        assertTrue(hook.contains("LauncherGlassStaticNode.attachToMaterial"));
    }

    @Test public void foldersAndWorkspaceNodesCannotRenderAfterLeavingWorkspace() throws Exception {
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));
        String node = Files.readString(MAIN.resolve("LauncherGlassStaticNode.java"));

        assertTrue(folder.contains("LauncherGlassHierarchy.isWorkspace"));
        assertTrue(node.contains("LauncherGlassHierarchy.isWorkspace(material)"));
        assertFalse("folder materials outside Workspace must not be claimed",
                folder.contains("// Recents folders are allowed in Workspace glass"));
    }

    @Test public void dockIconsShareTheSingleDockTextureViewEglOutput() throws Exception {
        assertTrue(Files.exists(MAIN.resolve("DockGlassCompositor.java")));
        assertTrue(Files.exists(MAIN.resolve("DockGlassItemNode.java")));
        assertTrue(Files.exists(MAIN.resolve("DockGlassItemRegistry.java")));
        assertTrue(Files.exists(MAIN.resolve("DockGlassSceneSnapshot.java")));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String item = Files.readString(MAIN.resolve("DockGlassItemNode.java"));

        assertTrue(view.contains("DockGlassCompositor"));
        assertTrue(view.contains("dockCompositor.drawFrame("));
        assertTrue(view.contains("prismalRenderer.prepareBackdrop("));
        assertFalse(item.contains("new SurfaceTexture("));
        assertFalse(item.contains("EGLSurface "));
        assertFalse(item.contains("extends TextureView"));
    }

    @Test public void recentsHidesWorkspaceStaticLayerAndHomeReturnRequiresFreshFrame() throws Exception {
        String controller = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));

        assertTrue(controller.contains("setRecentsCovered"));
        assertTrue(controller.contains("recentsCovered"));
        assertTrue(controller.contains("HOME_WAITING_FRESH_FRAME"));
        assertTrue(controller.contains("requestFreshBackdrop"));
    }
}
