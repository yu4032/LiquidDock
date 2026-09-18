package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Architecture coverage for the OS3 lockscreen clock replacement and settings surface. */
public class LockScreenClockGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path KOTLIN = Path.of("src/main/kotlin/com/hellovoid/liquiddock");

    @Test public void settingsLiveUnderThirdPartyAppsWithCustomizationAndSystemUiRestart() throws Exception {
        String pages = Files.readString(KOTLIN.resolve("GboardSettingsPages.kt"));
        String compose = Files.readString(KOTLIN.resolve("ComposeSettingsActivity.kt"));

        assertTrue(pages.contains("锁屏时钟"));
        assertTrue(pages.contains("LockScreenClockGlassPreferences.ENABLED_KEY"));
        assertTrue(pages.contains("恢复继承全局外观"));
        assertTrue(pages.contains("玻璃模糊"));
        assertTrue(compose.contains("Page.LockScreenClock"));
        assertTrue(compose.contains("activity.restartSystemUi()"));
        assertTrue(compose.contains("openLockScreenClock"));
    }

    @Test public void runtimeDirectlyReplacesClockWithoutVendorGlassCapability() throws Exception {
        String hook = Files.readString(MAIN.resolve("LockScreenClockGlassHook.java"));
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));

        assertTrue(hook.contains("com.miui.clock.MiuiClockController"));
        assertTrue(hook.contains("addClockView"));
        assertTrue(hook.contains("clockView.setAlpha(0f)"));
        assertTrue(hook.contains("clockView.setAlpha(originalAlpha)"));
        assertFalse(hook.contains("supportGlassEffect"));
        assertFalse(hook.contains("isGlassEffectEnable"));
        assertFalse(hook.contains("setMiGlass"));
        assertFalse(hook.contains("clockEffect"));
        assertTrue(module.contains("LockScreenClockGlassHook.install"));
    }
}
