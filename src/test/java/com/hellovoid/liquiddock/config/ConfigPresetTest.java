package com.hellovoid.liquiddock.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class ConfigPresetTest {
    @Test
    public void defaultPresetIsNeutralZeroSnapshot() {
        Map<String, Object> defaults = PresetManager.defaultValues();

        for (ConfigKey<?> key : ConfigSchema.all()) {
            if (key.exportMode() == ConfigKey.ExportMode.NEVER) continue;
            assertTrue("neutral preset missing " + key.name(), defaults.containsKey(key.name()));
            if (key.type() == ConfigKey.Type.BOOLEAN) {
                assertEquals(key.name(), Boolean.FALSE, defaults.get(key.name()));
            } else if (key.type() == ConfigKey.Type.INT) {
                assertEquals(key.name(), Integer.valueOf(0), defaults.get(key.name()));
                if (key.storageMode() == ConfigKey.StorageMode.DP_TENTHS) {
                    assertEquals(key.name() + "_tenths", Integer.valueOf(0),
                            defaults.get(key.name() + "_tenths"));
                }
            } else if (key.type() == ConfigKey.Type.STRING) {
                assertEquals(key.name(), key.uiDefault(), defaults.get(key.name()));
            }
        }

        assertEquals(Boolean.FALSE,
                defaults.get("third_party_glass.systemui.lockscreen_clock.enabled"));
        assertEquals(Float.valueOf(0f),
                defaults.get("third_party_glass.systemui.lockscreen_clock.blur"));
        assertEquals(Integer.valueOf(0),
                defaults.get("third_party_glass.systemui.lockscreen_clock.tint_r"));
        assertEquals(Integer.valueOf(0),
                defaults.get("third_party_glass.systemui.lockscreen_clock.tint_g"));
        assertEquals(Integer.valueOf(0),
                defaults.get("third_party_glass.systemui.lockscreen_clock.tint_b"));
        assertEquals(Integer.valueOf(0),
                defaults.get("third_party_glass.systemui.lockscreen_clock.tint_alpha"));
    }

    @Test
    public void tunedPresetMatchesBundledProfileIncludingSwitchesAndFractions() {
        Map<String, Object> tuned = PresetManager.tunedValues();

        assertEquals(Boolean.TRUE, tuned.get("liquiddock_enabled"));
        assertEquals(Boolean.TRUE, tuned.get("launcher450_icon_size_enabled"));
        assertEquals(Boolean.FALSE, tuned.get("dock_customization"));
        assertEquals(Boolean.TRUE, tuned.get("dock_divider_enabled"));
        assertEquals(Boolean.TRUE, tuned.get("liquid_glass"));
        assertEquals(Boolean.FALSE, tuned.get("liquid_security_center_glass"));
        assertEquals(Boolean.FALSE, tuned.get("liquid_systemui_handle_menu_glass"));
        assertEquals(Boolean.TRUE, tuned.get("liquid_shortcut_popup_glass"));
        assertEquals("Security Center glass must remain explicit opt-in",
                Boolean.FALSE, tuned.get("liquid_security_center_glass"));
        assertEquals("SystemUI handle-menu glass must remain explicit opt-in",
                Boolean.FALSE, tuned.get("liquid_systemui_handle_menu_glass"));
        assertEquals(Boolean.TRUE, tuned.get("liquid_dialog_dark_mode"));
        assertEquals(Boolean.FALSE, tuned.get("liquid_icon_glass"));
        assertEquals(Boolean.TRUE, tuned.get("liquid_functional_dock_icon_glass"));
        assertEquals(Boolean.TRUE, tuned.get("liquid_dynamic_app_capture"));
        assertEquals(Boolean.TRUE, tuned.get("recents_disable_wallpaper_dimming"));

        assertEquals("8x4", tuned.get("grid_profile"));
        assertEquals("advanced_material", tuned.get("liquid_blur_mode"));

        assertEquals(Integer.valueOf(-94), tuned.get("indicator_landscape_y_tenths"));
        assertEquals(Integer.valueOf(118), tuned.get("indicator_portrait_y_tenths"));
        assertEquals(Integer.valueOf(34), tuned.get("corner_offset_tenths"));
        assertEquals(Integer.valueOf(47), tuned.get("dock_shadow_size_tenths"));
        assertEquals(Integer.valueOf(165), tuned.get("liquid_icon_corner_radius_tenths"));
        assertEquals(Integer.valueOf(-5), tuned.get("liquid_small_folder_size_offset_tenths"));
        assertEquals(Integer.valueOf(72), tuned.get("liquid_blur_tenths"));
        assertEquals(Integer.valueOf(13), tuned.get("liquid_lens_refraction_tenths"));
        assertEquals(Integer.valueOf(18), tuned.get("liquid_prismal_smin_smoothing_tenths"));
        assertEquals(Integer.valueOf(265), tuned.get("workstation_dock_width_offset_tenths"));
        assertEquals(Integer.valueOf(411),
                tuned.get("workstation_all_apps_landscape_horizontal_offset_tenths"));
        assertEquals(Integer.valueOf(412),
                tuned.get("workstation_all_apps_portrait_horizontal_offset_tenths"));
        assertEquals(Integer.valueOf(608),
                tuned.get("workstation_all_apps_portrait_bottom_spacing_tenths"));

        assertEquals(Integer.valueOf(50), tuned.get("liquid_passblur_capture_scale"));
        assertEquals(Integer.valueOf(0), tuned.get("liquid_passblur_render_fps"));
        assertEquals(Integer.valueOf(36), tuned.get("recents_background_blur_percent"));

        assertEquals(Boolean.FALSE,
                tuned.get("third_party_glass.systemui.lockscreen_clock.enabled"));
        assertEquals(Float.valueOf(3.3722174f),
                tuned.get("third_party_glass.systemui.lockscreen_clock.blur"));
        assertEquals(Integer.valueOf(244),
                tuned.get("third_party_glass.systemui.lockscreen_clock.tint_r"));
        assertEquals(Integer.valueOf(241),
                tuned.get("third_party_glass.systemui.lockscreen_clock.tint_g"));
        assertEquals(Integer.valueOf(239),
                tuned.get("third_party_glass.systemui.lockscreen_clock.tint_b"));
        assertEquals(Integer.valueOf(0),
                tuned.get("third_party_glass.systemui.lockscreen_clock.tint_alpha"));
    }

    @Test
    public void migrationPresetKeepsHistoricalSafeDefaults() {
        Map<String, Object> values = PresetManager.migrationValues();

        assertEquals(Boolean.TRUE, values.get(ConfigSchema.Glass.ENABLED.name()));
        assertEquals(Integer.valueOf(26), values.get(ConfigSchema.Glass.CHROMATIC.name()));
        assertEquals(Integer.valueOf(20), values.get(ConfigSchema.Glass.BLUR.name() + "_tenths"));
        assertEquals(Integer.valueOf(155), values.get(ConfigSchema.Glass.IOR.name()));
        assertEquals(Boolean.TRUE, values.get(ConfigSchema.Dock.SQUIRCLE.name()));
        assertFalse(values.equals(PresetManager.defaultValues()));
    }

    @Test
    public void applyDefaultAndTunedWriteCompleteSnapshots() {
        RecordingEditor neutralEditor = new RecordingEditor();
        neutralEditor.values.put("liquid_dialog_tint_r", 123);
        PresetManager.applyDefault(neutralEditor);
        assertEquals(PresetManager.defaultValues(), neutralEditor.values);
        assertTrue(neutralEditor.committed);

        RecordingEditor tunedEditor = new RecordingEditor();
        tunedEditor.values.put("liquid_dialog_tint_r", 123);
        PresetManager.applyTuned(tunedEditor);
        assertEquals(PresetManager.tunedValues(), tunedEditor.values);
        assertTrue(tunedEditor.committed);
    }

    private static final class RecordingEditor implements SharedPreferences.Editor {
        final Map<String, Object> values = new LinkedHashMap<>();
        boolean committed;

        @Override public SharedPreferences.Editor putString(String key, String value) {
            values.put(key, value); return this;
        }
        @Override public SharedPreferences.Editor putStringSet(String key, Set<String> values) {
            this.values.put(key, values); return this;
        }
        @Override public SharedPreferences.Editor putInt(String key, int value) {
            values.put(key, value); return this;
        }
        @Override public SharedPreferences.Editor putLong(String key, long value) {
            values.put(key, value); return this;
        }
        @Override public SharedPreferences.Editor putFloat(String key, float value) {
            values.put(key, value); return this;
        }
        @Override public SharedPreferences.Editor putBoolean(String key, boolean value) {
            values.put(key, value); return this;
        }
        @Override public SharedPreferences.Editor remove(String key) {
            values.remove(key); return this;
        }
        @Override public SharedPreferences.Editor clear() {
            values.clear(); return this;
        }
        @Override public boolean commit() {
            committed = true; return true;
        }
        @Override public void apply() {}
    }
}
