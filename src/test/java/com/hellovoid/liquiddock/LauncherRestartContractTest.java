package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contracts for the independent desktop and SystemUI restart controls. */
public class LauncherRestartContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/hellovoid/liquiddock/SettingsActivity.java");
    private static final Path COMPOSE = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");
    private static final Path STRINGS = Path.of("src/main/res/values/strings.xml");
    private static final Path STRINGS_ZH = Path.of("src/main/res/values-zh-rCN/strings.xml");

    @Test public void restartUsesHomeIntentInsteadOfDirectLauncherComponent() throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("android.intent.action.MAIN"));
        assertTrue(source.contains("android.intent.category.HOME"));
        assertFalse(source.contains("am start -n com.miui.home/.launcher.Launcher"));
    }

    @Test public void topBarOffersIndependentDesktopAndSystemUiRestartButtons() throws Exception {
        String compose = Files.readString(COMPOSE);
        String strings = Files.readString(STRINGS);
        String stringsZh = Files.readString(STRINGS_ZH);

        assertTrue(compose.contains("R.string.action_restart_launcher"));
        assertTrue(compose.contains("activity.restartLauncher()"));
        assertTrue(compose.contains("R.string.action_restart_system_ui"));
        assertTrue(compose.contains("activity.restartSystemUi()"));
        assertTrue(strings.contains("name=\"action_restart_system_ui\""));
        assertTrue(stringsZh.contains("name=\"action_restart_launcher\">重启桌面<"));
        assertTrue(stringsZh.contains("name=\"action_restart_system_ui\">重启系统界面<"));
    }

    @Test public void systemUiRestartTargetsOnlySystemUiProcess() throws Exception {
        String source = Files.readString(SOURCE);
        int start = source.indexOf("void restartSystemUi()");
        assertTrue(start >= 0);
        int end = source.indexOf("public static class SettingsFragment", start);
        assertTrue(end > start);
        String method = source.substring(start, end);

        assertTrue(method.contains("pidof com.android.systemui"));
        assertTrue(method.contains("kill -TERM"));
        assertFalse(method.contains("com.miui.home"));
        assertFalse(method.contains("am force-stop com.android.systemui"));
    }
}
