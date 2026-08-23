package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class WidgetThemeSettingsContractTest {
    @Test public void composeSettingsExposeWidgetThemeModeAndRemoteSync() throws Exception {
        String compose = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String arrays = Files.readString(Path.of("src/main/res/values/arrays.xml"));
        String app = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LiquidDockApp.java"));
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));

        assertTrue(compose.contains("widget_theme_mode"));
        assertTrue(compose.contains("R.array.widget_theme_entries"));
        assertTrue(compose.contains("R.array.widget_theme_values"));
        assertTrue(compose.contains("小部件外观"));
        assertTrue(arrays.contains("<item>auto</item>"));
        assertTrue(arrays.contains("<item>light</item>"));
        assertTrue(arrays.contains("<item>dark</item>"));
        assertTrue(app.contains("onSharedPreferenceChanged"));
        assertTrue(app.contains("syncKeyToRemote(key, sharedPreferences)"));
        assertTrue(module.contains("configReader.s(\"widget_theme_mode\", \"auto\")"));
    }

    @Test public void composeThemeChangeRestartsLauncherAfterPersistence() throws Exception {
        String compose = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        assertTrue(compose.contains("onChanged: (String) -> Unit = {}"));
        assertTrue(compose.contains("prefs.edit().putString(key, next).apply()"));
        assertTrue(compose.contains("activity.restartLauncher()"));
    }
}
