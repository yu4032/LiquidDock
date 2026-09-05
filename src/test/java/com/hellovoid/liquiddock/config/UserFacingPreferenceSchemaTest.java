package com.hellovoid.liquiddock.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

/** Guards the rule that user-facing persisted settings are owned by ConfigSchema. */
public class UserFacingPreferenceSchemaTest {
    @Test
    public void everyDeclaredConfigKeyIsRegisteredExactlyOnce() throws Exception {
        Set<ConfigKey<?>> declared = new HashSet<>();
        for (Class<?> group : ConfigSchema.class.getDeclaredClasses()) {
            for (Field field : group.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                if (!ConfigKey.class.isAssignableFrom(field.getType())) continue;
                ConfigKey<?> key = (ConfigKey<?>) field.get(null);
                assertTrue("duplicate declared ConfigKey object: " + key.name(), declared.add(key));
            }
        }

        Set<ConfigKey<?>> registered = new HashSet<>(ConfigSchema.all());
        assertEquals("ConfigSchema.all() must not contain duplicate registrations",
                registered.size(), ConfigSchema.all().size());
        assertEquals("every ConfigKey declared by ConfigSchema must be registered",
                declared, registered);
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
    public void composeSettingsPersistsOnlyThroughConfigKeyBackedControls() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        assertTrue("integer settings must carry ConfigKey metadata",
                source.contains("val config: ConfigKey<Int>"));
        assertTrue("boolean settings must accept ConfigKey<Boolean>",
                source.contains("prefs: SharedPreferences, config: ConfigKey<Boolean>"));
        assertTrue("string settings must accept ConfigKey<String>",
                source.contains("prefs: SharedPreferences, config: ConfigKey<String>"));
        assertFalse("raw boolean setting APIs are forbidden",
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
