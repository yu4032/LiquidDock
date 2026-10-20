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
    private static final Path DOCK_WORKSTATION_PAGES = MAIN.resolve(
            "kotlin/com/hellovoid/liquiddock/DockWorkstationSettingsPages.kt");





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


}
