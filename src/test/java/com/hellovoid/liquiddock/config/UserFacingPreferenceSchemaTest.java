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
    public void defaultConfigurationUsesOnlyRegisteredOrBoundedProfileKeys() {
        Map<String, Object> defaults = PresetManager.defaultValues();
        Set<String> allowedPersistedNames = new HashSet<>();

        for (ConfigKey<?> key : ConfigSchema.all()) {
            allowedPersistedNames.add(key.name());
            if (key.storageMode() == ConfigKey.StorageMode.DP_TENTHS) {
                allowedPersistedNames.add(key.name() + "_tenths");
            }
            if (key.exportMode() == ConfigKey.ExportMode.ALWAYS) {
                assertTrue("default configuration missing ALWAYS schema key: " + key.name(),
                        defaults.containsKey(key.name()));
            }
        }

        for (String name : defaults.keySet()) {
            assertTrue("default configuration contains unmanaged key: " + name,
                    allowedPersistedNames.contains(name)
                            || name.startsWith("third_party_glass."));
        }

        assertEquals(Boolean.FALSE,
                defaults.get(ConfigSchema.Glass.SECURITY_CENTER_GLASS.name()));
        assertEquals(Boolean.FALSE,
                defaults.get(ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS.name()));
        assertEquals(Boolean.FALSE, defaults.get(ConfigSchema.Debug.LOGGING.name()));
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
        assertTrue("settings must expose the single default configuration",
                source.contains("\"应用默认配置\""));
        assertFalse("settings must not expose a second tuned/default preset",
                source.contains("\"应用调校预设\"") || source.contains("applyTunedPreset"));
    }
}
