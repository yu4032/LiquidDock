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

    @Test
    public void orientationMemoryDefersLandscapeTargetToActiveStage() throws Exception {
        String source = read(
                "src/main/java/com/hellovoid/liquiddock/HomeGridOrientationMemoryHook.java");
        assertTrue(source.contains("StageWorkspacePolicy.orientationMemoryOwnsTarget"));
        assertTrue(source.contains("StageWorkspaceRuntime.isActiveForOrdinaryPlacement()"));
    }

    @Test
    public void settledMutationRefreshesStageMappedTarget() throws Exception {
        String source = read(
                "src/main/java/com/hellovoid/liquiddock/HomeGridMutationCaptureHook.java");
        assertTrue(source.contains("StageWorkspaceRuntime.recordSettledOrdinaryLayout"));
    }

    @Test
    public void moduleDeclaresStageOverlayRuntimeInstallation() throws Exception {
        String source = read("src/main/java/com/hellovoid/liquiddock/ModuleMain.java");
        assertTrue(source.contains("StageWorkspaceOverlayRuntime.install"));
    }

    @Test
    public void overlayRuntimeUsesTopLevelContentHostAndLiveCellGeometry() throws Exception {
        String source = read(
                "src/main/java/com/hellovoid/liquiddock/StageWorkspaceOverlayRuntime.java");
        assertTrue(source.contains("com.miui.home.launcher.Launcher"));
        assertTrue(source.contains("com.miui.home.launcher.CellLayout"));
        assertTrue(source.contains("setupViews"));
        assertTrue(source.contains("onConfigurationChanged"));
        assertTrue(source.contains("onLayout"));
        assertTrue(source.contains("findViewById(android.R.id.content)"));
        assertTrue(source.contains("mWorkspace"));
        assertTrue(source.contains("mXs"));
        assertTrue(source.contains("mHCells"));
        assertTrue(source.contains("mVCells"));
        assertTrue(source.contains("getLocationOnScreen"));
        assertTrue(source.contains("StageWorkspaceOverlayGeometry.resolve"));
        assertTrue(source.contains("StageWorkspaceRuntime.isActiveForOrdinaryPlacement()"));
        assertTrue(source.contains("StageWorkspaceOverlayHostView"));
        assertFalse(source.contains("postDelayed("));
        assertFalse(source.contains("workspace.addView("));
    }

    @Test
    public void overlayHostIsStructuralAndNonInteractiveByDefault() throws Exception {
        String source = read(
                "src/main/java/com/hellovoid/liquiddock/StageWorkspaceOverlayHostView.java");
        assertTrue(source.contains("extends FrameLayout"));
        assertTrue(source.contains("setClickable(false)"));
        assertTrue(source.contains("setFocusable(false)"));
        assertTrue(source.contains("setClipChildren(false)"));
        assertTrue(source.contains("setClipToPadding(false)"));
        assertFalse(source.contains("setBackgroundColor"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }
}
