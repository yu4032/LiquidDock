package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts configuration/UI exposure for widget and icon static glass. */
public class LauncherGlassWidgetIconSettingsContractTest {
    @Test
    public void schemaRuntimeAndBothSettingsUisExposeWidgetAndIconGlass() throws Exception {
        String schema = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String config = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java"));
        String compose = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String legacy = Files.readString(Path.of("src/main/res/xml/preferences.xml"));

        assertTrue(schema.contains("liquid_widget_glass"));
        assertTrue(schema.contains("liquid_icon_glass"));
        assertTrue(schema.contains("Glass.WIDGET_GLASS"));
        assertTrue(schema.contains("Glass.ICON_GLASS"));
        assertTrue(config.contains("widgetEnabled"));
        assertTrue(config.contains("iconEnabled"));
        assertTrue(compose.contains("ConfigSchema.Glass.WIDGET_GLASS"));
        assertTrue(compose.contains("ConfigSchema.Glass.ICON_GLASS"));
        assertTrue(legacy.contains("android:key=\"liquid_widget_glass\""));
        assertTrue(legacy.contains("android:key=\"liquid_icon_glass\""));
    }

    @Test
    public void dragOverlayIsNotFolderOnlyWhenWidgetOrIconGlassIsEnabled() throws Exception {
        String drag = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixLauncherDragOverlayHook.java"));
        assertTrue(drag.contains("widgetEnabled"));
        assertTrue(drag.contains("iconEnabled"));
        assertTrue(drag.contains("folderEnabled || runtimeConfig.glass.widgetEnabled")
                || drag.contains("folderEnabled || glassConfig.widgetEnabled")
                || drag.contains("folderEnabled || widgetEnabled"));
    }
}
