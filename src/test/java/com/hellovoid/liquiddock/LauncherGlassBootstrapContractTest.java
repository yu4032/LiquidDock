package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Startup/return-to-HOME must reconcile nodes before requesting and revealing a fresh backdrop. */
public class LauncherGlassBootstrapContractTest {
    @Test public void bootstrapOwnsReconciliationAndFreshFrameBarrier() throws Exception {
        Path controllerPath = Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassSceneController.java");
        assertTrue("missing scene controller", Files.exists(controllerPath));
        String controller = Files.readString(controllerPath);
        String session = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java"));
        String press = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java"));

        assertTrue(controller.contains("BOOTSTRAPPING"));
        assertTrue(controller.contains("reconcileExistingWorkspace"));
        assertTrue(controller.contains("requestFreshBackdrop"));
        assertTrue(session.contains("requestFreshBackdrop(long generation)"));
        assertTrue(session.contains("invalidateGeneration(long generation)"));
        assertTrue(session.contains("requestSceneRedraw()"));
        assertFalse(session.contains("LauncherGlassStaticLayer.acquire(root, this)"));

        int pressStart = press.indexOf("updateFolderPressAfterDispatch");
        String pressTail = pressStart >= 0 ? press.substring(pressStart,
                Math.min(press.length(), pressStart + 3200)) : "";
        assertFalse(pressTail.contains("requestFreshBackdrop"));
        assertFalse(pressTail.contains("bindProducer"));
        assertFalse(pressTail.contains("scheduleFolderRecovery"));
    }
}
