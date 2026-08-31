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
        String recents = Files.readString(MAIN.resolve("LauncherGlassRecentsHook.java"));
        String registry = Files.readString(MAIN.resolve("LauncherGlassSessionRegistry.java"));

        assertTrue(recents.contains("LauncherGlassSessionRegistry.prepareWorkstationRecentsReturn();"));
        assertTrue(registry.contains("static synchronized void prepareWorkstationRecentsReturn()"));
        assertTrue(registry.contains("if (!MainHook.isWorkstationMode()) return;"));
        assertTrue(registry.contains("HookUtil.invoke(session, \"rebindProducer\")"));

        String hide = methodSlice(recents,
                "\"onRecentViewHide\"",
                "installed = true;");
        int rebind = hide.indexOf("prepareWorkstationRecentsReturn();");
        int uncover = hide.indexOf("setRecentsCoveredForAll(false);");
        assertTrue("producer rollover must start before HOME requests its fresh frame",
                rebind >= 0 && uncover > rebind);
    }

    @Test
    public void reflectiveRecoveryEntrySurvivesR8Optimization() throws Exception {
        Path keep = Path.of("src/main/keepRules/runtime-reflection.keep");
        assertTrue("AGP 9.3 optimization requires a keep rule for the reflective method",
                Files.exists(keep));
        String rules = Files.readString(keep);
        assertTrue(rules.contains("class com.hellovoid.liquiddock.LauncherGlassSession"));
        assertTrue(rules.contains("void rebindProducer();"));
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
        String recents = Files.readString(MAIN.resolve("LauncherGlassRecentsHook.java"));
        String registry = Files.readString(MAIN.resolve("LauncherGlassSessionRegistry.java"));
        String controller = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));

        String preparation = methodSlice(registry,
                "static synchronized void prepareWorkstationRecentsReturn()",
                "static synchronized void shutdownAll()");
        assertFalse("producer recovery must not bypass the scene freshness barrier",
                preparation.contains("setSceneVisible"));
        assertFalse(preparation.contains("onFreshFrameRendered"));
        assertFalse(recents.contains("setSceneVisible(true)"));

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
