package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for the global multi-scope restart action. */
public class SecurityCenterSettingsRestartContractTest {
    private static final Path MAIN = Path.of("src/main");

    @Test
    public void settingsUseOneGlobalRestartScopeAction() throws Exception {
        String compose = Files.readString(
                MAIN.resolve("kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        assertTrue(compose.contains("R.string.action_restart_scopes"));
        assertTrue(compose.contains("RestartScopesDialog("));
        assertTrue(compose.contains("activity.restartHookScopes(selected)"));
        assertFalse(compose.contains("activity.restartSecurityCenterAndLauncher()"));
        assertFalse(compose.contains("activity.restartSystemUi()"));
        assertFalse(compose.contains("activity.restartPackageProcess("));
    }

    @Test
    public void restartDialogIncludesEveryRestartableXposedScopeExceptSystem() throws Exception {
        String compose = Files.readString(
                MAIN.resolve("kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String scope = Files.readString(
                MAIN.resolve("resources/META-INF/xposed/scope.list"));

        assertTrue(scope.contains("system"));
        assertTrue(compose.contains("RestartScopeItem(\"com.miui.home\""));
        assertTrue(compose.contains("RestartScopeItem(\"com.android.systemui\""));
        assertTrue(compose.contains("RestartScopeItem(\"com.miui.securitycenter\""));
        assertTrue(compose.contains("RestartScopeItem(\"com.google.android.inputmethod.latin\""));
        assertTrue(compose.contains("RestartScopeItem(\"com.android.quicksearchbox\""));
        assertFalse(compose.contains("RestartScopeItem(\"system\""));
    }

    @Test
    public void batchRestartKeepsLauncherBeforeSecurityCenterAndNeverForceStopsSecurityCenter() throws Exception {
        String activity = Files.readString(
                MAIN.resolve("java/com/hellovoid/liquiddock/SettingsActivity.java"));

        String shell = Files.readString(
                MAIN.resolve("java/com/hellovoid/liquiddock/HookScopeRestartShell.java"));

        assertTrue(activity.contains("void restartHookScopes(Set<String> scopes)"));
        assertTrue(activity.contains("HookScopeRestartShell.buildScript(selected)"));
        assertTrue(activity.contains("HookScopeRestartShell.parseResults("));
        assertTrue(activity.contains("new InputStreamReader(p.getInputStream()"));
        assertTrue(shell.contains("am force-stop com.miui.home"));
        assertTrue(shell.contains("restart_running com.miui.securitycenter com.miui.securitycenter:ui"));
        assertTrue(shell.contains("kill -TERM $before"));
        assertFalse(shell.contains("am force-stop com.miui.securitycenter"));
    }

    @Test
    public void restartDialogIsScrollableAndUsesCenteredRedConfirmButton() throws Exception {
        String ui = Files.readString(
                MAIN.resolve("kotlin/com/hellovoid/liquiddock/ModernSettingsUi.kt"));

        assertTrue(ui.contains("verticalScroll(rememberScrollState())"));
        assertTrue(ui.contains("System Framework（system）需要重启设备"));
        assertTrue(ui.contains("tint = Color(0xFFD73333)"));
        assertTrue(ui.contains("contentAlignment = Alignment.Center"));
        assertTrue(ui.contains("text = \"重启\""));
    }
}
