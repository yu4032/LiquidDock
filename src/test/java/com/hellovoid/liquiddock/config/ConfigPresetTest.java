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
    public void defaultConfigurationMatchesBundledProfileAndSafetyOverrides() {
        Map<String, Object> defaults = PresetManager.defaultValues();

        assertEquals(Boolean.TRUE, defaults.get(ConfigSchema.Core.ENABLED.name()));
        assertEquals(Boolean.TRUE, defaults.get(ConfigSchema.Grid.ICON_SIZE_ENABLED.name()));
        assertEquals(Integer.valueOf(100), defaults.get(ConfigSchema.Grid.ICON_SIZE_PERCENT.name()));
        assertEquals("8x4", defaults.get(ConfigSchema.Grid.PROFILE.name()));

        assertEquals(Boolean.FALSE, defaults.get(ConfigSchema.Dock.ENABLED.name()));
        assertEquals(Boolean.TRUE, defaults.get(ConfigSchema.Divider.ENABLED.name()));
        assertEquals(Boolean.FALSE, defaults.get(ConfigSchema.Dock.SQUIRCLE.name()));
        assertEquals(Integer.valueOf(175), defaults.get(ConfigSchema.Dock.STROKE_RED.name()));
        assertEquals(Integer.valueOf(74), defaults.get(ConfigSchema.Dock.STROKE_ALPHA.name()));
        assertEquals(Integer.valueOf(47),
                defaults.get(ConfigSchema.Dock.SHADOW_SIZE.name() + "_tenths"));

        assertEquals(Boolean.TRUE, defaults.get(ConfigSchema.Glass.ENABLED.name()));
        assertEquals(Integer.valueOf(9), defaults.get(ConfigSchema.Glass.CHROMATIC.name()));
        assertEquals(Integer.valueOf(72),
                defaults.get(ConfigSchema.Glass.BLUR.name() + "_tenths"));
        assertEquals(Integer.valueOf(13),
                defaults.get(ConfigSchema.Glass.LENS_REFRACTION.name() + "_tenths"));
        assertEquals(Integer.valueOf(50),
                defaults.get(ConfigSchema.Glass.PASSBLUR_CAPTURE_SCALE.name()));
        assertEquals(Integer.valueOf(0),
                defaults.get(ConfigSchema.Glass.PASSBLUR_RENDER_FPS.name()));
        assertEquals(Boolean.TRUE,
                defaults.get(ConfigSchema.Glass.SHORTCUT_POPUP_GLASS.name()));
        assertEquals(Boolean.TRUE,
                defaults.get(ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS.name()));
        assertEquals(Boolean.TRUE,
                defaults.get(ConfigSchema.Glass.DIALOG_DARK_MODE.name()));

        // Explicit safety overrides: these integrations and debug logging are never enabled by
        // the default configuration even though the supplied profile enabled the first two.
        assertEquals(Boolean.FALSE,
                defaults.get(ConfigSchema.Glass.SECURITY_CENTER_GLASS.name()));
        assertEquals(Boolean.FALSE,
                defaults.get(ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS.name()));
        assertEquals(Boolean.FALSE,
                defaults.get(ConfigSchema.Debug.LOGGING.name()));

        assertEquals(Boolean.TRUE,
                defaults.get(ConfigSchema.LauncherHighlight.SKY_HAZE.name()));
        assertEquals(Boolean.FALSE,
                defaults.get(ConfigSchema.LauncherHighlight.LARGE_PRESS_GLOW.name()));
        assertEquals(Boolean.FALSE, defaults.get(ConfigSchema.Gboard.ENABLED.name()));
        assertEquals(Integer.valueOf(10), defaults.get(ConfigSchema.Gboard.TINT_RED.name()));
        assertEquals(Integer.valueOf(126), defaults.get(ConfigSchema.Gboard.TINT_ALPHA.name()));

        assertEquals(Boolean.FALSE,
                defaults.get("third_party_glass.systemui.lockscreen_clock.enabled"));
        assertEquals(Float.valueOf(3.3722174f),
                defaults.get("third_party_glass.systemui.lockscreen_clock.blur"));
        assertEquals(Integer.valueOf(244),
                defaults.get("third_party_glass.systemui.lockscreen_clock.tint_r"));
        assertEquals(Integer.valueOf(241),
                defaults.get("third_party_glass.systemui.lockscreen_clock.tint_g"));
        assertEquals(Integer.valueOf(239),
                defaults.get("third_party_glass.systemui.lockscreen_clock.tint_b"));
        assertEquals(Integer.valueOf(0),
                defaults.get("third_party_glass.systemui.lockscreen_clock.tint_alpha"));
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
