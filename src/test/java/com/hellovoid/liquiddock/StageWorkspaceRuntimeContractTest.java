package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StageWorkspaceRuntimeContractTest {

    @Test
    public void moduleDeclaresStageRuntimeInstallation() throws Exception {
        String source = read("src/main/java/com/hellovoid/liquiddock/ModuleMain.java");
        assertTrue(source.contains("StageWorkspaceRuntime.install"));
    }

    @Test
    public void runtimeUsesLauncherAuthoritiesWithoutForbiddenMechanisms() throws Exception {
        String source = read("src/main/java/com/hellovoid/liquiddock/StageWorkspaceRuntime.java");
        assertTrue(source.contains("com.miui.home.launcher.Launcher"));
        assertTrue(source.contains("setupViews"));
        assertTrue(source.contains("onConfigurationChanged"));
        assertTrue(source.contains("mWorkspace"));
        assertTrue(source.contains("StageWorkspaceActivation"));
        assertTrue(source.contains("StageWorkspaceMappedLayoutStore"));
        assertTrue(source.contains("collectPositions"));
        assertTrue(source.contains("applyPositionsAtomically"));
        assertTrue(source.contains("Configuration.ORIENTATION_LANDSCAPE"));
        assertTrue(source.contains("MainHook.isWorkstationMode()"));
        assertFalse(source.contains("postDelayed("));
        assertFalse(source.contains("translationX"));
        assertFalse(source.contains("mOccupied"));
    }

    @Test
    public void stageMappedTargetUsesSynchronousDurablePreferenceCommit() throws Exception {
        String source = read(
                "src/main/java/com/hellovoid/liquiddock/StageWorkspaceSharedPreferencesStore.java");
        assertTrue(source.contains("commit()"));
        assertFalse(source.contains(".apply()"));
    }

    @Test
    public void dropHookDeclaresStageRuntimeAuthority() throws Exception {
        String source = read("src/main/java/com/hellovoid/liquiddock/WorkspaceDropRuleHook.java");
        assertTrue(source.contains("StageWorkspaceRuntime.isActiveForOrdinaryPlacement()"));
        assertTrue(source.contains("stageEnabled"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }
}
