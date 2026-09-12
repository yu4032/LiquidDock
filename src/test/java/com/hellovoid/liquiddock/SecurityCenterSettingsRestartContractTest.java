package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for the Liquid glass page Security Center restart action. */
public class SecurityCenterSettingsRestartContractTest {
    private static final Path MAIN = Path.of("src/main");

    @Test
    public void liquidPageShowsSecurityCenterRestartImmediatelyBeforeLauncherRestart() throws Exception {
        String compose = Files.readString(
                MAIN.resolve("kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String marker = "if (page == Page.Liquid)";
        int liquidGuard = compose.indexOf(marker);
        assertTrue("Liquid glass page must own a Security Center restart action", liquidGuard >= 0);

        int securityCenter = compose.indexOf("R.string.action_restart_security_center", liquidGuard);
        int launcher = compose.indexOf("R.string.action_restart_launcher", liquidGuard);
        assertTrue("Security Center restart must be rendered on the Liquid glass page",
                securityCenter > liquidGuard);
        assertTrue("Security Center restart must appear to the left of Restart desktop",
                launcher > securityCenter);
        assertTrue("Security Center restart button must call the dedicated activity action",
                compose.indexOf("activity.restartSecurityCenter()", securityCenter) > securityCenter);
    }

    @Test
    public void restartTargetsOnlySecurityCenterUiProcessWithoutForceStoppingPackage() throws Exception {
        String activity = Files.readString(
                MAIN.resolve("java/com/hellovoid/liquiddock/SettingsActivity.java"));
        assertTrue("SettingsActivity must expose a dedicated Security Center restart action",
                activity.contains("void restartSecurityCenter()"));
        assertTrue("restart must target the :ui process that hosts the sidebar hook",
                activity.contains("pidof com.miui.securitycenter:ui"));
        assertTrue("restart must terminate the current :ui process",
                activity.contains("kill -TERM $PIDS"));
        assertFalse("restart must not force-stop the whole Security Center package",
                activity.contains("am force-stop com.miui.securitycenter"));
    }

    @Test
    public void restartActionHasEnglishAndChineseLabels() throws Exception {
        String english = Files.readString(MAIN.resolve("res/values/strings.xml"));
        String chinese = Files.readString(MAIN.resolve("res/values-zh-rCN/strings.xml"));
        assertTrue(english.contains(
                "<string name=\"action_restart_security_center\">Restart Security Center</string>"));
        assertTrue(chinese.contains(
                "<string name=\"action_restart_security_center\">重启安全中心</string>"));
    }
}
