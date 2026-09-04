package com.hellovoid.liquiddock.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class ConfigPresetTest {
    @Test
    public void defaultPresetPreservesIntentionalOverridesAndIncludesNewSchemaKeys() {
        Map<String, Object> defaults = PresetManager.defaultValues();

        assertEquals(Boolean.TRUE, defaults.get(ConfigSchema.Glass.ENABLED.name()));
        assertEquals(Integer.valueOf(30), defaults.get(ConfigSchema.Glass.CAPTURE_FPS.name()));
        assertEquals(Integer.valueOf(100), defaults.get(ConfigSchema.Glass.CAPTURE_SCALE.name()));
        assertEquals(Integer.valueOf(13), defaults.get(ConfigSchema.Glass.LENS_REFRACTION.name() + "_tenths"));
        assertEquals(Integer.valueOf(-88), defaults.get(ConfigSchema.Grid.LANDSCAPE_INDICATOR_Y.name() + "_tenths"));
        assertEquals(Integer.valueOf(118), defaults.get(ConfigSchema.Grid.PORTRAIT_INDICATOR_Y.name() + "_tenths"));
        assertEquals(Integer.valueOf(180), defaults.get(ConfigSchema.Dock.STROKE_RED.name()));
        assertEquals(Integer.valueOf(119), defaults.get(ConfigSchema.Dock.STROKE_ALPHA.name()));
        assertEquals(Boolean.TRUE, defaults.get(ConfigSchema.Dock.SQUIRCLE.name()));
        assertEquals(Integer.valueOf(47), defaults.get(ConfigSchema.Dock.SHADOW_SIZE.name() + "_tenths"));
        assertEquals("8x4", defaults.get(ConfigSchema.Grid.PROFILE.name()));
        assertEquals(Boolean.FALSE, defaults.get(ConfigSchema.Dock.HIDE_MIRROR_SHORTCUT.name()));
        assertEquals(Boolean.TRUE, defaults.get(ConfigSchema.LauncherHighlight.SKY_HAZE.name()));
        assertEquals(Boolean.TRUE, defaults.get(ConfigSchema.LauncherHighlight.LARGE_PRESS_GLOW.name()));
    }

    @Test
    public void defaultPresetWritesEveryDerivedValueAndCommits() {
        RecordingEditor editor = new RecordingEditor();

        PresetManager.applyDefault(editor);

        assertEquals(PresetManager.defaultValues(), editor.values);
        assertTrue(editor.committed);
    }

    private static final class RecordingEditor implements SharedPreferences.Editor {
        final Map<String, Object> values = new LinkedHashMap<>();
        boolean committed;

        @Override public SharedPreferences.Editor putString(String key, String value) { values.put(key, value); return this; }
        @Override public SharedPreferences.Editor putStringSet(String key, Set<String> values) { this.values.put(key, values); return this; }
        @Override public SharedPreferences.Editor putInt(String key, int value) { values.put(key, value); return this; }
        @Override public SharedPreferences.Editor putLong(String key, long value) { values.put(key, value); return this; }
        @Override public SharedPreferences.Editor putFloat(String key, float value) { values.put(key, value); return this; }
        @Override public SharedPreferences.Editor putBoolean(String key, boolean value) { values.put(key, value); return this; }
        @Override public SharedPreferences.Editor remove(String key) { values.remove(key); return this; }
        @Override public SharedPreferences.Editor clear() { values.clear(); return this; }
        @Override public boolean commit() { committed = true; return true; }
        @Override public void apply() {}
    }
}
