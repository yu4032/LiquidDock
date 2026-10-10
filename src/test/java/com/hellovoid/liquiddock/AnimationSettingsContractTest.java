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
    private static final Path ANIMATION_PAGES = MAIN.resolve(
            "kotlin/com/hellovoid/liquiddock/AnimationSettingsPages.kt");
    private static final Path OPTION_SPECS = MAIN.resolve(
            "kotlin/com/hellovoid/liquiddock/SettingsOptionSpecs.kt");

    @Test
    public void animationSettingsAreSplitByOwningDomain() throws Exception {
        String ui = Files.readString(UI);
        String pages = Files.readString(ANIMATION_PAGES);
        String specs = Files.readString(OPTION_SPECS);

        assertTrue(ui.contains("private fun AnimationPage("));
        assertTrue(pages.contains("internal fun AnimationWorkspacePage("));
        assertTrue(pages.contains("internal fun AnimationInteractionPage("));
        assertTrue(pages.contains("internal fun AnimationPopupsPage("));
        assertTrue(pages.contains("internal fun AnimationSystemPage("));
        assertTrue(pages.contains("internal fun AnimationGuiPage("));
        assertTrue(pages.contains("ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS"));
        assertTrue(ui.contains("Page.AnimationWorkspace -> AnimationWorkspacePage("));

        assertTrue(specs.contains("ConfigSchema.Animation.WORKSPACE_VISIBILITY"));
        assertTrue(specs.contains("ConfigSchema.Animation.DOCK_ICON_REVEAL"));
        assertTrue(specs.contains("ConfigSchema.Animation.PRESS_IN"));
        assertTrue(specs.contains("ConfigSchema.Animation.PRESS_OUT"));
        assertTrue(specs.contains("ConfigSchema.Animation.SHORTCUT_POPUP_DISMISS_FADE"));
        assertTrue(specs.contains("ConfigSchema.Animation.SECURITY_CENTER_EXIT_FADE"));
        assertTrue(specs.contains("ConfigSchema.Animation.SETTINGS_PAGE"));
        assertTrue(specs.contains("ConfigSchema.Animation.DOCK_RESIZE"));
        assertTrue(pages.contains("调整快捷菜单与安全中心玻璃的退出渐隐"));
    }

    @Test
    public void dockResizeTimingStaysOnAnimationPageAndUsesDockFeatureGate() throws Exception {
        String ui = Files.readString(UI);
        String pages = Files.readString(ANIMATION_PAGES);
        int animationStart = pages.indexOf("internal fun AnimationWorkspacePage(");
        int animationEnd = pages.indexOf("internal fun AnimationInteractionPage(", animationStart);
        assertTrue(animationStart >= 0 && animationEnd > animationStart);
        String animationPage = pages.substring(animationStart, animationEnd);
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
