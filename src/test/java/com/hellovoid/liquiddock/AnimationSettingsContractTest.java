package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Settings/runtime ownership contract for LiquidDock-controlled animation timings. */
public class AnimationSettingsContractTest {
    private static final Path MAIN = Path.of("src/main");
    private static final Path JAVA = MAIN.resolve("java/com/hellovoid/liquiddock");

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
