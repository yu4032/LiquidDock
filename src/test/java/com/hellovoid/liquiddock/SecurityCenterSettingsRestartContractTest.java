package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for the serialized Security Center + Launcher restart action. */
public class SecurityCenterSettingsRestartContractTest {
    private static final Path MAIN = Path.of("src/main");

    @Test
    public void sidebarPageUsesOneCombinedRestartAction() throws Exception {
        String compose = Files.readString(
                MAIN.resolve("kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String marker = "if (page == Page.SecurityCenterSidebar)";
        int sidebarGuard = compose.indexOf(marker);
        assertTrue("Security Center sidebar page must own the combined restart action",
                sidebarGuard >= 0);

        int combined = compose.indexOf(
                "R.string.action_restart_security_center_and_launcher", sidebarGuard);
        assertTrue("combined restart button must be rendered on the Security Center page",
                combined > sidebarGuard);
        assertTrue("combined button must call the serialized activity action",
                compose.indexOf("activity.restartSecurityCenterAndLauncher()", combined) > combined);

        int nextBranch = compose.indexOf("} else if (descriptor != null)", combined);
        String sidebarBranch = compose.substring(sidebarGuard, nextBranch);
        assertFalse("Security Center page must not expose a second standalone Launcher restart",
                sidebarBranch.contains("activity.restartLauncher()"));
        assertFalse("old standalone Security Center action must be removed",
                compose.contains("activity.restartSecurityCenter()"));
    }

    @Test
    public void animationPageAlsoUsesCombinedRestartAction() throws Exception {
        String compose = Files.readString(
                MAIN.resolve("kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        int animationGuard = compose.indexOf("else if (page == Page.Animation)");
        assertTrue("Animation page must expose the combined restart for its Security Center fade",
                animationGuard >= 0);
        int combined = compose.indexOf(
                "R.string.action_restart_security_center_and_launcher", animationGuard);
        assertTrue("Animation page must render the combined restart action",
                combined > animationGuard);
        assertTrue("Animation page combined button must call the serialized action",
                compose.indexOf("activity.restartSecurityCenterAndLauncher()", combined) > combined);
    }

    @Test
    public void combinedRestartSerializesLauncherBeforeSecurityCenter() throws Exception {
        String activity = Files.readString(
                MAIN.resolve("java/com/hellovoid/liquiddock/SettingsActivity.java"));

        int method = activity.indexOf("void restartSecurityCenterAndLauncher()");
        int launcherKill = activity.indexOf("am force-stop com.miui.home", method);
        int startHome = activity.indexOf(
                "am start -a android.intent.action.MAIN -c android.intent.category.HOME",
                launcherKill);
        int waitForLauncher = activity.indexOf("while [ $i -lt 30 ]", startHome);
        int settle = activity.indexOf("sleep 0.8", waitForLauncher);
        int securityPid = activity.indexOf("pidof com.miui.securitycenter:ui", settle);
        int securityKill = activity.indexOf("kill -TERM $SC_PIDS", securityPid);

        assertTrue("Launcher must restart before Security Center", launcherKill > method);
        assertTrue("HOME must start after Launcher restart", startHome > launcherKill);
        assertTrue("must wait for the new Launcher process", waitForLauncher > startHome);
        assertTrue("must leave a settle window before Security Center restart", settle > waitForLauncher);
        assertTrue("Security Center must restart last", securityPid > settle && securityKill > securityPid);
        assertFalse("must not force-stop the whole Security Center package",
                activity.contains("am force-stop com.miui.securitycenter"));
    }

    @Test
    public void combinedRestartHasEnglishAndChineseLabels() throws Exception {
        String english = Files.readString(MAIN.resolve("res/values/strings.xml"));
        String chinese = Files.readString(MAIN.resolve("res/values-zh-rCN/strings.xml"));
        assertTrue(english.contains(
                "<string name=\"action_restart_security_center_and_launcher\">"
                        + "Restart Security Center &amp; desktop</string>"));
        assertTrue(chinese.contains(
                "<string name=\"action_restart_security_center_and_launcher\">"
                        + "重启安全中心与桌面</string>"));
        assertFalse("obsolete standalone Security Center label should be removed",
                english.contains("name=\"action_restart_security_center\""));
    }
}
