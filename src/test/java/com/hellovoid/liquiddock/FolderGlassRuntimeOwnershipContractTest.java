package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for live small/large folder glass ownership. */
public class FolderGlassRuntimeOwnershipContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void folderHooksStayInstalledWhileOwnershipUsesLiveComponentState() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));

        assertFalse(hook.contains("|| !runtimeConfig.glass.folderEnabled)"));
        assertTrue(hook.contains("GlassRuntimeState.isSmallFolderEnabled()"));
        assertTrue(hook.contains("GlassRuntimeState.isLargeFolderEnabled()"));
        assertTrue(hook.contains("isFolderLiveEnabled("));
    }

    @Test
    public void smallAndLargeFolderDisableHaveSelectiveTeardown() throws Exception {
        String state = Files.readString(MAIN.resolve("GlassRuntimeState.java"));
        String hook = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));

        assertTrue(state.contains("onRuntimeSmallFolderGlassDisabled()"));
        assertTrue(state.contains("onRuntimeLargeFolderGlassDisabled()"));
        assertTrue(hook.contains("static void onRuntimeSmallFolderGlassDisabled()"));
        assertTrue(hook.contains("static void onRuntimeLargeFolderGlassDisabled()"));
        assertTrue(hook.contains("releaseFolderStyleOwnership(true)"));
        assertTrue(hook.contains("releaseFolderStyleOwnership(false)"));
    }

    @Test
    public void queuedRecoveryAndLargeFolderDrawCannotReclaimDisabledOwnership() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));

        assertTrue(hook.contains("if (!isFolderLiveEnabled(material))"));
        assertTrue(hook.contains("if (!isFolderLiveEnabled(value))"));
        assertTrue(hook.contains("GlassRuntimeState.isLargeFolderEnabled()"));
        assertTrue(hook.contains("FOLDER_RECOVERY_PENDING.remove"));
        assertTrue(hook.contains("restoreMaterial(material)"));
    }
}
