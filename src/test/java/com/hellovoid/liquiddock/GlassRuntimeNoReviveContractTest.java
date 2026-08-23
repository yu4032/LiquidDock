package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Runtime-off glass must stay off even when Launcher constructs or reparents new views. */
public class GlassRuntimeNoReviveContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void staticHostObserverIsGatedBeforeListenerRegistration() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        int start = hook.indexOf("private static void observeHost(");
        int add = hook.indexOf("host.addOnAttachStateChangeListener(listener)", start);
        assertTrue(start >= 0 && add > start);
        String region = hook.substring(start, add);
        assertTrue(region.contains("if (!GlassRuntimeState.isEnabled() || host == null) return;"));
    }

    @Test public void folderObserverIsRegisteredOnlyInsideWorkspaceWhileEnabled() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));
        int start = hook.indexOf("private static void attachFromFolderIcon(");
        int end = hook.indexOf("private static void scheduleFolderRecovery", start);
        assertTrue(start >= 0 && end > start);
        String region = hook.substring(start, end);
        int gate = region.indexOf("!GlassRuntimeState.isEnabled()");
        int observe = region.indexOf("observeFolderIconAttach(icon, glassConfig)");
        assertTrue(gate >= 0 && observe > gate);
        assertTrue(region.contains("!LauncherGlassHierarchy.isWorkspace(icon)"));
    }

    @Test public void pendingFolderRecoveryStopsImmediatelyWhenDisabled() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));
        int start = hook.indexOf("private static void scheduleFolderRecovery(");
        int end = hook.indexOf("private static void observeFolderIconAttach", start);
        assertTrue(start >= 0 && end > start);
        String region = hook.substring(start, end);
        assertTrue(region.contains("if (!GlassRuntimeState.isEnabled() || icon == null)"));
        assertTrue(region.contains("FOLDER_RECOVERY_PENDING.remove(icon)"));
    }
}
