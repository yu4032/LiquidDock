package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Scene coverage and physical output-count contract for Scheme A. */
public class LauncherGlassCoverageAndSurfaceContractTest {
    @Test public void folderOpenAndHomeRestoreFeedSceneCoverage() throws Exception {
        String folder = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java"));
        Path controllerPath = Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassSceneController.java");
        assertTrue(Files.exists(controllerPath));
        String controller = Files.readString(controllerPath);
        assertTrue(folder.contains("setWorkspaceCovered"));
        assertTrue(controller.contains("setCovered"));
        assertTrue(controller.contains("HOME_WAITING_FRESH_FRAME"));
    }

    @Test public void outputDomainsStayConstantWithObjectCount() throws Exception {
        String layer = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticLayer.java"));
        String node = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java"));
        String dockItemPath = "src/main/java/com/hellovoid/liquiddock/DockGlassItemNode.java";
        assertTrue(Files.exists(Path.of(dockItemPath)));
        String dockItem = Files.readString(Path.of(dockItemPath));

        assertTrue(layer.contains("extends TextureView"));
        assertFalse(node.contains("extends TextureView"));
        assertFalse(node.contains("new Surface("));
        assertFalse(dockItem.contains("extends TextureView"));
        assertFalse(dockItem.contains("new Surface("));
    }

    @Test public void forbiddenCpuCapturePathsStayAbsentFromSceneArchitecture() throws Exception {
        String[] files = {
                "LauncherGlassSceneController.java",
                "LauncherGlassStaticNode.java",
                "DockGlassCompositor.java",
                "DockGlassItemNode.java"
        };
        for (String file : files) {
            Path path = Path.of("src/main/java/com/hellovoid/liquiddock/" + file);
            if (!Files.exists(path)) continue;
            String source = Files.readString(path);
            assertFalse(source.contains("PixelCopy"));
            assertFalse(source.contains("ImageReader"));
            assertFalse(source.contains("glReadPixels"));
            assertFalse(source.contains("MediaProjection"));
        }
    }
}
