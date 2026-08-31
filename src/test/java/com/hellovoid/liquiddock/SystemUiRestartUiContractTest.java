package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** SystemUI is no longer an Xposed scope, so the settings UI must not expose restart controls. */
public class SystemUiRestartUiContractTest {
    @Test public void settingsDoNotExposeSystemUiRestart() throws Exception {
        String compose = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String activity = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/SettingsActivity.java"));
        String strings = Files.readString(Path.of("src/main/res/values/strings.xml"));

        assertFalse(compose.contains("action_restart_system_ui"));
        assertFalse(compose.contains("restartSystemUi()"));
        assertFalse(activity.contains("void restartSystemUi()"));
        assertFalse(strings.contains("action_restart_system_ui"));
    }
}
