package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Folder opening must not leave the old Workspace glass visible during its animation. */
public class FolderSceneCoverVisibilityContractTest {
    @Test public void folderCoverageForcesImmediateGlobalHide() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassSceneController.java"));
        String layer = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticLayer.java"));

        assertTrue(controller.contains(
                "current.setSceneVisible(state.isLayerVisible(), state.consumeFadeReveal(),"
                        + " folderCovered)"));
        assertTrue(layer.contains(
                "void setSceneVisible(boolean visible, boolean fadeReveal, boolean immediateHide)"));
        assertTrue(layer.contains("if (!visible && immediateHide)"));
    }
}
