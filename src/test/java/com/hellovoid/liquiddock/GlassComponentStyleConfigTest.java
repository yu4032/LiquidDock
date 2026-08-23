package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** The runtime/config/UI contract for four independently styled Launcher glass component classes. */
public class GlassComponentStyleConfigTest {
    @Test public void schemaContainsAllCanonicalStyleKeys() throws Exception {
        String schema = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String runtime = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java"));
        String preferences = Files.readString(Path.of("src/main/res/xml/preferences.xml"));

        String[] keys = {
                "liquid_icon_glass", "liquid_icon_size_offset", "liquid_icon_corner_radius",
                "liquid_widget_glass", "liquid_widget_size_offset", "liquid_widget_corner_radius",
                "liquid_small_folder_glass", "liquid_small_folder_size_offset",
                "liquid_small_folder_corner_radius",
                "liquid_large_folder_glass", "liquid_large_folder_size_offset",
                "liquid_large_folder_corner_radius"
        };
        for (String key : keys) {
            assertTrue("schema missing " + key, schema.contains("\"" + key + "\""));
            assertTrue("preferences missing " + key, preferences.contains("android:key=\"" + key + "\""));
        }
        assertTrue(runtime.contains("GlassComponentStyle iconStyle"));
        assertTrue(runtime.contains("GlassComponentStyle widgetStyle"));
        assertTrue(runtime.contains("GlassComponentStyle smallFolderStyle"));
        assertTrue(runtime.contains("GlassComponentStyle largeFolderStyle"));
    }

    @Test public void runtimeKeepsLegacyFolderFallback() throws Exception {
        String runtime = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java"));
        assertTrue(runtime.contains("liquid_folder_glass"));
        assertTrue(runtime.contains("liquid_folder_corner_radius"));
        assertTrue(runtime.contains("smallFolderStyle"));
        assertTrue(runtime.contains("largeFolderStyle"));
    }
}
