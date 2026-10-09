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
        assertTrue(compose.contains("activity.restartHookScopes(confirmedSelection.toSet())"));
        assertFalse(compose.contains("activity.restartSecurityCenterAndLauncher()"));
        assertFalse(compose.contains("activity.restartSystemUi()"));
        assertFalse(compose.contains("activity.restartPackageProcess("));
    }

    @Test
    public void restartDialogSubmitsCurrentSelectionAndUsesIdempotentToggleState() throws Exception {
        String compose = Files.readString(
                MAIN.resolve("kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String dialog = Files.readString(
                MAIN.resolve("kotlin/com/hellovoid/liquiddock/ModernSettingsUi.kt"));

        assertTrue(compose.contains("onToggle = { id, checked ->"));
        assertTrue(compose.contains("selectedRestartScopes + id"));
        assertTrue(compose.contains("selectedRestartScopes - id"));
        assertTrue(compose.contains("onRestart = { confirmedSelection ->"));
        assertTrue(compose.contains("activity.restartHookScopes(confirmedSelection.toSet())"));
        assertTrue(compose.contains("UI_TOGGLE|$id|checked=$checked|selected=$selectedRestartScopes"));
        assertTrue(dialog.contains("onToggle: (String, Boolean) -> Unit"));
        assertTrue(dialog.contains("onRestart: (Set<String>) -> Unit"));
        assertTrue(dialog.contains("onCheckedChange = { next -> onToggle(item.id, next) }"));
        assertTrue(dialog.contains("onClick = { onToggle(item.id, !checked) }"));
        assertTrue(dialog.contains("val checked = item.id in selected"));
        assertTrue(dialog.contains("top.yukonga.miuix.kmp.basic.Switch("));
        // Interactive PrismalGlassSurface on the parent installs a whole-panel
        // gesture modifier which may consume taps intended for its children.
        assertTrue(dialog.contains(".widthIn(max = 520.dp)"));
        assertTrue(dialog.contains(".clickable(onClick = {})"));
        assertFalse(dialog.contains("onClick = {},"));
        assertTrue(dialog.contains("onRestart(selected.toSet())"));
        assertFalse(dialog.contains("onCheckedChange = { onToggle(item.id) }"));
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
        assertTrue(activity.contains("HookScopeRestartShell.buildScript(selected, diagnostic)"));
        assertTrue(activity.contains("HookScopeRestartShell.parseResults("));
        assertTrue(activity.contains("new InputStreamReader(p.getInputStream()"));
        assertTrue(shell.contains("am force-stop com.miui.home"));
        assertTrue(shell.contains("restart_running com.miui.securitycenter com.miui.securitycenter:ui"));
        assertTrue(shell.contains("kill -TERM $before"));
        assertFalse(shell.contains("am force-stop com.miui.securitycenter"));
    }

    @Test
    public void systemUiAlwaysRequestsVerifiedRestartWithoutSuccessPopup() throws Exception {
        String activity = Files.readString(
                MAIN.resolve("java/com/hellovoid/liquiddock/SettingsActivity.java"));
        String shell = Files.readString(
                MAIN.resolve("java/com/hellovoid/liquiddock/HookScopeRestartShell.java"));
        String compose = Files.readString(
                MAIN.resolve("kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        assertTrue(shell.contains("restart_systemui"));
        assertTrue(shell.contains("kill -KILL $before_ui"));
        assertTrue(shell.contains("report com.android.systemui RESTARTED"));
        assertTrue(activity.contains("HookScopeRestartShell.parseResults("));
        assertFalse(activity.contains(".setTitle(\"作用域重启结果\")"));
        assertTrue(activity.contains("以下作用域未能完成重启"));
        assertTrue(compose.contains("Page.AnimationSystem -> setOf(\"com.android.systemui\")"));
        assertTrue(compose.contains("activity.restartHookScopes(confirmedSelection.toSet())"));
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
