package com.hellovoid.liquiddock.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

/** Guards the rule that user-facing persisted settings are owned by ConfigSchema. */
public class UserFacingPreferenceSchemaTest {
    private static final List<String> RECENT_USER_FACING_KEYS = List.of(
            "grid_profile",
            "dock_hide_mirror_shortcut",
            "launcher_surface_component_sky_haze",
            "launcher_surface_component_specular",
            "launcher_surface_component_lit_rim",
            "launcher_surface_component_opposite_rim",
            "launcher_surface_component_corner_rim",
            "launcher_surface_component_face_sheen",
            "launcher_surface_component_plain_highlight",
            "launcher_surface_component_caustics",
            "launcher_surface_component_press_glow",
            "launcher_large_surface_component_sky_haze",
            "launcher_large_surface_component_specular",
            "launcher_large_surface_component_lit_rim",
            "launcher_large_surface_component_opposite_rim",
            "launcher_large_surface_component_corner_rim",
            "launcher_large_surface_component_face_sheen",
            "launcher_large_surface_component_plain_highlight",
            "launcher_large_surface_component_caustics",
            "launcher_large_surface_component_press_glow");

    @Test
    public void recentUserFacingPreferencesAreRegisteredInSchema() {
        Set<String> schemaNames = new HashSet<>();
        for (ConfigKey<?> key : ConfigSchema.all()) schemaNames.add(key.name());

        for (String name : RECENT_USER_FACING_KEYS) {
            assertTrue("user-facing preference must be registered in ConfigSchema: " + name,
                    schemaNames.contains(name));
        }
    }

    @Test
    public void schemaManagedPreferencesRoundTripThroughCodec() {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("grid_profile", "10x6");
        preferences.put("dock_hide_mirror_shortcut", true);
        for (String name : RECENT_USER_FACING_KEYS) {
            if (name.startsWith("launcher_")) preferences.put(name, false);
        }

        Map<String, Object> exported = ConfigCodec.exportValues(preferences);
        Map<String, Object> imported = ConfigCodec.importValues(exported);

        assertEquals("10x6", exported.get("grid_profile"));
        assertEquals(Boolean.TRUE, exported.get("dock_hide_mirror_shortcut"));
        assertEquals("10x6", imported.get("grid_profile"));
        assertEquals(Boolean.TRUE, imported.get("dock_hide_mirror_shortcut"));
        for (String name : RECENT_USER_FACING_KEYS) {
            if (!name.startsWith("launcher_")) continue;
            assertEquals(name, Boolean.FALSE, exported.get(name));
            assertEquals(name, Boolean.FALSE, imported.get(name));
        }
    }

    @Test
    public void defaultPresetIsDerivedFromAlwaysSchemaKeysOnly() {
        Map<String, Object> defaults = PresetManager.defaultValues();
        Set<String> allowedPersistedNames = new HashSet<>();

        for (ConfigKey<?> key : ConfigSchema.all()) {
            if (key.exportMode() != ConfigKey.ExportMode.ALWAYS) continue;
            allowedPersistedNames.add(key.name());
            assertTrue("default preset missing ALWAYS schema key: " + key.name(),
                    defaults.containsKey(key.name()));
            if (key.storageMode() == ConfigKey.StorageMode.DP_TENTHS) {
                allowedPersistedNames.add(key.name() + "_tenths");
                assertTrue("default preset missing DP sidecar: " + key.name(),
                        defaults.containsKey(key.name() + "_tenths"));
            }
        }

        // Historical explicit inclusion: the default preset reset the divider master switch,
        // but optional divider geometry/color keys must remain absent until the user sets them.
        allowedPersistedNames.add(ConfigSchema.Divider.ENABLED.name());
        assertEquals(Boolean.FALSE, defaults.get(ConfigSchema.Divider.ENABLED.name()));
        assertFalse(defaults.containsKey(ConfigSchema.Divider.WIDTH_DP.name()));
        assertFalse(defaults.containsKey(ConfigSchema.Divider.HEIGHT_SCALE.name()));
        assertFalse(defaults.containsKey(ConfigSchema.Divider.Y_OFFSET_DP.name()));
        assertFalse(defaults.containsKey(ConfigSchema.Divider.COLOR_RED.name()));
        assertFalse(defaults.containsKey(ConfigSchema.Divider.COLOR_GREEN.name()));
        assertFalse(defaults.containsKey(ConfigSchema.Divider.COLOR_BLUE.name()));
        assertFalse(defaults.containsKey(ConfigSchema.Divider.ALPHA.name()));
        assertFalse(defaults.containsKey(ConfigSchema.Glass.FOLDER_CORNER_RADIUS.name()));

        for (String name : defaults.keySet()) {
            assertTrue("default preset contains schema-external or optional key: " + name,
                    allowedPersistedNames.contains(name));
        }
    }

    @Test
    public void composeSettingsHasNoRawUserPreferenceWriter() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        assertFalse("boolean settings must use ConfigKey<Boolean>",
                source.contains("RawBooleanSetting("));
        assertFalse("string settings must not accept an untyped persisted key",
                source.contains("prefs: SharedPreferences, key: String, title: String, default: String"));
        assertFalse("settings must not write literal boolean keys directly",
                source.contains("prefs.edit().putBoolean(\""));
        assertFalse("settings must not write literal integer keys directly",
                source.contains("prefs.edit().putInt(\""));
        assertFalse("settings must not write literal string keys directly",
                source.contains("prefs.edit().putString(\""));
    }
}
