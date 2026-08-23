package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Runtime disable/reparent must restore vendor visuals after our transparent material handoff. */
public class GlassRuntimeVisualRestoreContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void dockRestoresVendorMaterialBeforeClearingRefsOrRemovingHost() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        int start = hook.indexOf("static void onRuntimeGlassDisabled()");
        int end = hook.indexOf("static void onHostDetached", start);
        assertTrue(start >= 0 && end > start);
        String region = hook.substring(start, end);
        int restore = region.indexOf("restoreVendorMaterialBody();");
        int clear = region.indexOf("clearTrackedViews();");
        int remove = region.indexOf("removeView(host)");
        assertTrue("vendor material must be restored before refs are cleared", restore >= 0 && clear > restore);
        assertTrue("host removal must happen only after restore/clear made detach idempotent",
                remove > clear);
    }

    @Test public void folderMaterialRestoresWhenItLeavesWorkspace() throws Exception {
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));
        assertTrue(folder.contains("ORIGINAL_IMAGE"));
        assertTrue(folder.contains("ORIGINAL_BACKGROUND"));
        assertTrue(folder.contains("restoreMaterial(material)"));
        assertTrue(folder.contains("static void onRuntimeGlassDisabled()"));
    }

    @Test public void runtimeDisableInvokesFolderAndStaticHookCleanup() throws Exception {
        String state = Files.readString(MAIN.resolve("GlassRuntimeState.java"));
        String staticHook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        assertTrue(state.contains("MiuixFolderGlassHook.onRuntimeGlassDisabled()"));
        assertTrue(state.contains("MiuixLauncherStaticGlassHook.onRuntimeGlassDisabled()"));
        assertTrue(staticHook.contains("static void onRuntimeGlassDisabled()"));
    }
}
