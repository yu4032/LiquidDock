package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Settings/runtime ownership contract for LiquidDock-controlled animation timings. */
public class AnimationSettingsContractTest {
    private static final Path MAIN = Path.of("src/main");
    private static final Path UI = MAIN.resolve(
            "kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");
    private static final Path JAVA = MAIN.resolve("java/com/hellovoid/liquiddock");

    @Test
    public void animationSettingsAreSplitByOwningDomain() throws Exception {
        String ui = Files.readString(UI);

        assertTrue(ui.contains("private fun AnimationPage("));
        assertTrue(ui.contains("private fun AnimationWorkspacePage("));
        assertTrue(ui.contains("private fun AnimationInteractionPage("));
        assertTrue(ui.contains("private fun AnimationPopupsPage("));
        assertTrue(ui.contains("private fun AnimationSystemPage("));
        assertTrue(ui.contains("private fun AnimationGuiPage("));

        assertTrue(ui.contains("ConfigSchema.Animation.WORKSPACE_VISIBILITY"));
        assertTrue(ui.contains("ConfigSchema.Animation.DOCK_ICON_REVEAL"));
        assertTrue(ui.contains("ConfigSchema.Animation.PRESS_IN"));
        assertTrue(ui.contains("ConfigSchema.Animation.PRESS_OUT"));
        assertTrue(ui.contains("ConfigSchema.Animation.SHORTCUT_POPUP_DISMISS_FADE"));
        assertTrue(ui.contains("ConfigSchema.Animation.SECURITY_CENTER_EXIT_FADE"));
        assertTrue(ui.contains("ConfigSchema.Animation.SETTINGS_PAGE"));
        assertTrue(ui.contains("ConfigSchema.Animation.DOCK_RESIZE"));
        assertTrue(ui.contains("调整快捷菜单与安全中心玻璃的退出渐隐"));
    }

    @Test
    public void dockResizeTimingStaysOnAnimationPageAndUsesDockFeatureGate() throws Exception {
        String ui = Files.readString(UI);
        int animationStart = ui.indexOf("private fun AnimationWorkspacePage(");
        int animationEnd = ui.indexOf("private fun AnimationInteractionPage(", animationStart);
        assertTrue(animationStart >= 0 && animationEnd > animationStart);
        String animationPage = ui.substring(animationStart, animationEnd);
        assertTrue(animationPage.contains(
                "masterEnabled && dockCustomizationEnabled"));
        assertTrue(animationPage.contains(
                "&& !systemResizeEnabled && smoothResizeEnabled"));

        int dockStart = ui.indexOf("private fun DockBehaviorPage(");
        int dockEnd = ui.indexOf("private fun DockGeometryPage(", dockStart);
        assertTrue(dockStart >= 0 && dockEnd > dockStart);
        String dockPage = ui.substring(dockStart, dockEnd);
        assertTrue(dockPage.contains("ConfigSchema.Dock.RESIZE_ANIMATION"));
        assertTrue(dockPage.contains("ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION"));
        assertFalse("Dock behavior page keeps switches but must not duplicate the duration slider",
                dockPage.contains("ConfigSchema.Animation.DOCK_RESIZE"));
    }

    @Test
    public void settingsPageTimingIsNotDuplicatedIntoInjectedRuntimeConfig() throws Exception {
        String config = Files.readString(JAVA.resolve("LiquidDockConfig.java"));
        assertFalse(config.contains("settingsPageMs"));
        assertTrue(config.contains("shortcutPopupDismissFadeMs"));
        assertTrue(config.contains("securityCenterExitFadeMs"));
    }

    @Test
    public void ownedExitFadesReadRuntimeConfigurationInTheirOwningProcesses() throws Exception {
        String shortcut = Files.readString(JAVA.resolve("ShortcutPopupGlassLayer.java"));
        String security = Files.readString(JAVA.resolve("SecurityCenterGlassSinkView.java"));
        String module = Files.readString(JAVA.resolve("ModuleMain.java"));

        assertTrue(shortcut.contains(
                "AnimationRuntimeState.shortcutPopupDismissFadeDurationMs()"));
        assertTrue(security.contains(
                "AnimationRuntimeState.securityCenterExitFadeDurationMs()"));
        int securityBranch = module.indexOf(
                "if (SecurityCenterProcessPolicy.PACKAGE.equals(packageName))");
        int animationInit = module.indexOf(
                "AnimationRuntimeState.configure(runtimeConfig.animation);", securityBranch);
        assertTrue("Security Center process must initialize animation runtime state",
                securityBranch >= 0 && animationInit > securityBranch);
    }

    @Test
    public void nativeAndSourceMatchedAnimationAuthoritiesRemainUnconfigured() throws Exception {
        String sidebar = Files.readString(
                JAVA.resolve("Launcher450Os4SidebarConfirmationRenderer.java"));
        String home = Files.readString(
                JAVA.resolve("LauncherGlassHomePresentationHook.java"));
        String systemUi = Files.readString(
                JAVA.resolve("SystemUiHandleMenuSurfaceAnimationAuthority.java"));

        assertFalse(sidebar.contains("ConfigSchema.Animation"));
        assertFalse(home.contains("ConfigSchema.Animation"));
        assertFalse(systemUi.contains("ConfigSchema.Animation"));
    }
}
