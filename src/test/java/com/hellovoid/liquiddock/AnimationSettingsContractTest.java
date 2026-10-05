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
    public void animationPageOnlyExposesLiquidDockOwnedTimings() throws Exception {
        String ui = Files.readString(UI);
        int start = ui.indexOf("private fun AnimationPage(");
        int end = ui.indexOf("private fun GridPage(", start);
        assertTrue(start >= 0 && end > start);
        String animationPage = ui.substring(start, end);

        assertTrue(animationPage.contains("ConfigSchema.Animation.WORKSPACE_VISIBILITY"));
        assertTrue(animationPage.contains("ConfigSchema.Animation.DOCK_ICON_REVEAL"));
        assertTrue(animationPage.contains("ConfigSchema.Animation.PRESS_IN"));
        assertTrue(animationPage.contains("ConfigSchema.Animation.PRESS_OUT"));
        assertTrue(animationPage.contains("ConfigSchema.Animation.SHORTCUT_POPUP_DISMISS_FADE"));
        assertTrue(animationPage.contains("ConfigSchema.Animation.SECURITY_CENTER_EXIT_FADE"));
        assertTrue(animationPage.contains("ConfigSchema.Animation.GESTURE_HANDLE_FADE_OUT"));
        assertTrue(animationPage.contains("ConfigSchema.Animation.SETTINGS_PAGE"));
        assertFalse("Gesture-handle enable switch belongs to the Home Screen Layout page",
                animationPage.contains("ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS"));
        assertTrue("Dock resize timing remains visible on the animation page",
                animationPage.contains("ConfigSchema.Animation.DOCK_RESIZE"));
        assertTrue(animationPage.contains("系统原生动画继续跟随原实现"));
    }

    @Test
    public void dockResizeTimingStaysOnAnimationPageAndUsesDockFeatureGate() throws Exception {
        String ui = Files.readString(UI);
        int animationStart = ui.indexOf("private fun AnimationPage(");
        int animationEnd = ui.indexOf("private fun GridPage(", animationStart);
        assertTrue(animationStart >= 0 && animationEnd > animationStart);
        String animationPage = ui.substring(animationStart, animationEnd);
        assertTrue(animationPage.contains(
                "masterEnabled && dockCustomizationEnabled"));
        assertTrue(animationPage.contains(
                "&& !systemResizeEnabled && smoothResizeEnabled"));

        int dockStart = ui.indexOf("private fun DockPage(");
        int dockEnd = ui.indexOf("private fun DividerPage(", dockStart);
        assertTrue(dockStart >= 0 && dockEnd > dockStart);
        String dockPage = ui.substring(dockStart, dockEnd);
        assertTrue(dockPage.contains("ConfigSchema.Dock.RESIZE_ANIMATION"));
        assertTrue(dockPage.contains("ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION"));
        assertFalse("Dock page keeps switches but must not duplicate the duration slider",
                dockPage.contains("ConfigSchema.Animation.DOCK_RESIZE"));
    }

    @Test
    public void gestureHandleSwitchLivesOnHomeScreenLayoutAndTimingLivesOnAnimationPage()
            throws Exception {
        String ui = Files.readString(UI);
        int gridStart = ui.indexOf("private fun GridPage(");
        int gridEnd = ui.indexOf("private fun DockPage(", gridStart);
        assertTrue(gridStart >= 0 && gridEnd > gridStart);
        String gridPage = ui.substring(gridStart, gridEnd);
        assertTrue(gridPage.contains("ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS"));

        String systemUiHook = Files.readString(
                JAVA.resolve("SystemUiGestureHandleFadeHook.java"));
        String runtime = Files.readString(
                JAVA.resolve("GestureHandleRuntimeState.java"));
        assertTrue(systemUiHook.contains("GestureHandleRuntimeState.fadeOutDurationMs()"));
        assertTrue(systemUiHook.contains("ValueAnimator.ofFloat"));
        assertTrue(systemUiHook.contains("DecelerateInterpolator"));
        assertTrue(systemUiHook.contains("animator.setDuration(durationMs)"));
        assertFalse("HyperOS 3 ButtonDispatcher has no duration overload",
                systemUiHook.contains("(long) durationOverrideMs"));
        assertTrue(runtime.contains("ConfigSchema.Animation.GESTURE_HANDLE_FADE_OUT"));
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
