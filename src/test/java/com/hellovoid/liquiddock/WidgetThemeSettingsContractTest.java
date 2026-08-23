package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class WidgetThemeSettingsContractTest {
    @Test public void widgetThemeModeIsSchemaBackedAndUserSelectable() throws Exception {
        String schema = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String preferences = Files.readString(Path.of("src/main/res/xml/preferences.xml"));
        String arrays = Files.readString(Path.of("src/main/res/values/arrays.xml"));

        assertTrue(schema.contains("widget_theme_mode"));
        assertTrue(schema.contains("Widgets.THEME_MODE"));
        assertTrue(preferences.contains("android:key=\"widget_theme_mode\""));
        assertTrue(preferences.contains("@array/widget_theme_entries"));
        assertTrue(preferences.contains("@array/widget_theme_values"));
        assertTrue(arrays.contains("<item>auto</item>"));
        assertTrue(arrays.contains("<item>light</item>"));
        assertTrue(arrays.contains("<item>dark</item>"));
    }

    @Test public void changingWidgetThemeRestartsLauncherAfterPersistence() throws Exception {
        String settings = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/SettingsActivity.java"));

        assertTrue(settings.contains("findPreference(\"widget_theme_mode\")"));
        assertTrue(settings.contains("activity.restartLauncher()"));
    }
}
