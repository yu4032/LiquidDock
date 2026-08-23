package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Ownership contract: Workspace layer visibility belongs to SceneController, never node registration. */
public class LauncherGlassSceneOwnershipContractTest {
    @Test public void staticLayerIsPassiveAndControllerOwned() throws Exception {
        String layer = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticLayer.java"));
        String session = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java"));
        String registry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassSessionRegistry.java"));

        assertTrue(layer.contains("setSceneVisible"));
        assertTrue(registry.contains("LauncherGlassSceneController"));
        assertFalse(session.contains("LauncherGlassStaticLayer.acquire(root, this)"));
    }
}
