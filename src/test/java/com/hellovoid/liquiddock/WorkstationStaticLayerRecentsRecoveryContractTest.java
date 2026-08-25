package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for the shared Launcher glass layer after Workstation Recents exit. */
public class WorkstationStaticLayerRecentsRecoveryContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void workstationRecentsExitRollsSharedProducerBeforeFreshFrameRecovery() throws Exception {
        String controller = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(controller.contains("session.prepareWorkstationRecentsReturn();"));
        assertTrue(controller.contains("MainHook.isWorkstationMode()"));
        assertTrue(session.contains("void prepareWorkstationRecentsReturn()"));
        assertTrue(session.contains("rebindProducer();"));

        String setRecents = methodSlice(controller,
                "private void setRecentsCovered(boolean covered)",
                "private void setEffectiveCovered(boolean covered)");
        int rebind = setRecents.indexOf("session.prepareWorkstationRecentsReturn();");
        int effective = setRecents.indexOf("setEffectiveCovered(");
        assertTrue("producer rollover must start before HOME requests its fresh frame",
                rebind >= 0 && effective > rebind);
    }

    @Test
    public void folderCoverageDoesNotRollWorkstationProducer() throws Exception {
        String controller = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));
        String folder = methodSlice(controller,
                "private void setFolderCovered(boolean covered)",
                "private void setRecentsCovered(boolean covered)");
        assertFalse(folder.contains("prepareWorkstationRecentsReturn"));
    }

    @Test
    public void recoveryNeverRevealsStaticLayerBeforeFreshOesFrame() throws Exception {
        String controller = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        String preparation = methodSlice(session,
                "void prepareWorkstationRecentsReturn()",
                "void attachOutput(");
        assertFalse("producer recovery must not bypass the scene freshness barrier",
                preparation.contains("setSceneVisible"));
        assertFalse(preparation.contains("onFreshFrameRendered"));

        assertTrue(controller.contains("state.onFreshFrameReady(generation);"));
        assertTrue(controller.contains("applyLayerVisibility();"));
    }

    private static String methodSlice(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + 1);
        if (start < 0 || end < 0 || end <= start) return "";
        return source.substring(start, end);
    }
}
