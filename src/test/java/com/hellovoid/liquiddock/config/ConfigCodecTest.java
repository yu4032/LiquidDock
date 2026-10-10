package com.hellovoid.liquiddock.config;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ConfigCodecTest {
    @Test
    public void representativeExportPreservesCurrentFields() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("liquiddock_enabled", false);
        prefs.put("grid_enabled", true);
        prefs.put("grid_landscape_horizontal_distance", -3);
        prefs.put("grid_landscape_horizontal_distance_tenths", -27);
        prefs.put("indicator_landscape_y", -8);
        prefs.put("indicator_landscape_y_tenths", -77);
        prefs.put("dock_customization", false);
        prefs.put("width_offset", 12);
        prefs.put("width_offset_tenths", 125);
        prefs.put("sq_stroke_w", 3);
        prefs.put("sq_stroke_w_tenths", 25);
        prefs.put("dock_shadow_alpha", 130);
        prefs.put("dock_divider_enabled", true);
        prefs.put("dock_divider_width_dp", 8);
        prefs.put("dock_divider_width_dp_tenths", 83);
        prefs.put("workstation_dock_customization", true);
        prefs.put("workstation_all_apps_landscape_top_spacing", -4);
        prefs.put("workstation_all_apps_landscape_top_spacing_tenths", -36);
        prefs.put("liquid_glass", true);
        prefs.put("liquid_blur", 7);
        prefs.put("liquid_blur_tenths", 73);
        prefs.put("liquid_ior", 170);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("liquiddock_enabled", false);
        expected.put("grid_enabled", true);
        expected.put("grid_landscape_horizontal_distance", -2.7d);
        expected.put("indicator_landscape_y", -7.7d);
        expected.put("dock_customization", false);
        expected.put("width_offset", 12.5d);
        expected.put("sq_stroke_w", 2.5d);
        expected.put("dock_shadow_alpha", 130);
        expected.put("dock_divider_enabled", true);
        expected.put("dock_divider_width_dp", 8);
        expected.put("workstation_dock_customization", true);
        expected.put("workstation_all_apps_landscape_top_spacing", -3.6d);
        expected.put("liquid_glass", true);
        expected.put("liquid_blur", 7.3d);
        expected.put("liquid_ior", 170);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), exported.get(entry.getKey()));
        }
        assertFalse(exported.containsKey("dock_divider_height_scale"));
        assertFalse(exported.containsKey("dock_divider_y_offset"));
        assertFalse(exported.containsKey("dock_divider_color_r"));
        assertFalse(exported.containsKey("dock_divider_color_g"));
        assertFalse(exported.containsKey("dock_divider_color_b"));
        assertFalse(exported.containsKey("dock_divider_alpha"));
    }

    @Test
    public void currentImportIgnoresHistoricalAliasesAndClampsValues() {
        Map<String, Object> json = new HashMap<>();
        json.put("home_grid_8x4", true);
        json.put("grid_landscape_margin_horizontal", 41);
        json.put("workstation_all_apps_horizontal_offset", 2.6d);
        json.put("grid_enabled", true);
        json.put("liquid_capture_power_limit_fps", 100);
        json.put("dock_shadow_alpha", -3);
        json.put("dock_divider_width_dp", 999);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("grid_enabled", true);
        expected.put("liquid_capture_power_limit_fps", 60);
        expected.put("dock_shadow_alpha", 0);
        expected.put("dock_divider_width_dp", 160);
        assertEquals(expected, ConfigCodec.importValues(json));
    }

    @Test
    public void retiredWidgetAdaptationFieldIsIgnored() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("grid_widget_adaptation", true);
        assertFalse(ConfigCodec.exportValues(prefs).containsKey("grid_widget_adaptation"));

        Map<String, Object> json = new HashMap<>();
        json.put("grid_widget_adaptation", true);
        assertFalse(ConfigCodec.importValues(json).containsKey("grid_widget_adaptation"));
    }

    @Test
    public void decimalDpExportPrefersTenths() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("indicator_landscape_y", -9);
        prefs.put("indicator_landscape_y_tenths", -88);

        assertEquals(-8.8d,
                ((Number) ConfigCodec.exportValues(prefs).get("indicator_landscape_y")).doubleValue(),
                0.0001d);
    }

    @Test
    public void retiredUnitFlagsAreIgnoredByCurrentImport() {
        Map<String, Object> json = new LinkedHashMap<>();
        for (String key : new String[] {"grid_margins_dp", "grid_margins_offset",
                "dock_dimensions_dp", "corners_dp", "liquid_dimensions_dp"}) {
            json.put(key, false);
        }
        json.put(ConfigSchema.Glass.PRISMAL_VIBRANCY.name(), 180);
        Map<String, Object> imported = ConfigCodec.importValues(json);
        assertEquals(1, imported.size());
        assertEquals(180, imported.get(ConfigSchema.Glass.PRISMAL_VIBRANCY.name()));
    }

    @Test
    public void importClampsIntegersToExistingRanges() {
        Map<String, Object> json = new HashMap<>();
        json.put("liquid_capture_power_limit_fps", 100);
        json.put("dock_shadow_alpha", -3);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(60, imported.get("liquid_capture_power_limit_fps"));
        assertEquals(0, imported.get("dock_shadow_alpha"));
    }

    @Test
    public void dividerTenthsEncodedIntegersClampWithoutCreatingDpSidecars() {
        Map<String, Object> json = new HashMap<>();
        json.put("dock_divider_width_dp", 999);
        json.put("dock_divider_y_offset", -999);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(160, imported.get("dock_divider_width_dp"));
        assertEquals(-80, imported.get("dock_divider_y_offset"));
        assertFalse(imported.containsKey("dock_divider_width_dp_tenths"));
        assertFalse(imported.containsKey("dock_divider_y_offset_tenths"));
    }

    @Test
    public void dividerExportIgnoresUnhistoricalDpSidecars() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("dock_divider_width_dp", 11);
        prefs.put("dock_divider_width_dp_tenths", 999);
        prefs.put("dock_divider_y_offset", -7);
        prefs.put("dock_divider_y_offset_tenths", -999);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);

        assertEquals(11, exported.get("dock_divider_width_dp"));
        assertEquals(-7, exported.get("dock_divider_y_offset"));
    }

    @Test
    public void homeSettleDelayPreservesHistoricalTenthsRoundTrip() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("liquid_home_settle_delay", 1201);
        prefs.put("liquid_home_settle_delay_tenths", 12005);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(1200.5d,
                ((Number) exported.get("liquid_home_settle_delay")).doubleValue(), 0.0001d);

        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(1201, imported.get("liquid_home_settle_delay"));
        assertEquals(12005, imported.get("liquid_home_settle_delay_tenths"));
    }


    @Test
    public void absentPreferencesExportCompleteHistoricalDefaultsFromSchema() {
        Map<String, Object> exported = ConfigCodec.exportValues(new HashMap<>());
        Set<String> expectedAlwaysKeys = new HashSet<>();
        for (ConfigKey<?> key : ConfigSchema.all()) {
            if (key.exportMode() == ConfigKey.ExportMode.ALWAYS) {
                expectedAlwaysKeys.add(key.name());
            }
        }

        assertEquals(expectedAlwaysKeys, exported.keySet());
        assertEquals(Boolean.FALSE, exported.get(ConfigSchema.Glass.ENABLED.name()));
        assertEquals(Boolean.FALSE, exported.get(ConfigSchema.Glass.WIDGET_DARK_CONTENT.name()));
        assertEquals(Boolean.TRUE, exported.get(ConfigSchema.Glass.SMALL_FOLDER_GLASS.name()));
        assertEquals(Boolean.TRUE, exported.get(ConfigSchema.Glass.LARGE_FOLDER_GLASS.name()));
        assertEquals(0.0d,
                ((Number) exported.get(ConfigSchema.Glass.ICON_SIZE_OFFSET.name())).doubleValue(),
                0.0001d);
        assertEquals(0.0d,
                ((Number) exported.get(ConfigSchema.Glass.WIDGET_CORNER_RADIUS.name())).doubleValue(),
                0.0001d);
        assertEquals(0.0d,
                ((Number) exported.get(ConfigSchema.Glass.SMALL_FOLDER_CORNER_RADIUS.name())).doubleValue(),
                0.0001d);
        assertEquals(0.0d,
                ((Number) exported.get(ConfigSchema.Glass.LARGE_FOLDER_CORNER_RADIUS.name())).doubleValue(),
                0.0001d);
        assertEquals(0.0d,
                ((Number) exported.get(ConfigSchema.Workstation.DOCK_ICON_GLASS_CORNER_RADIUS.name())).doubleValue(),
                0.0001d);
        assertEquals(100, exported.get(ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT.name()));
        assertEquals(Boolean.FALSE,
                exported.get(ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING.name()));
        assertEquals(450, exported.get(ConfigSchema.Animation.WORKSPACE_VISIBILITY.name()));
        assertEquals(90, exported.get(ConfigSchema.Animation.SHORTCUT_POPUP_DISMISS_FADE.name()));
        assertEquals(80, exported.get(ConfigSchema.Animation.SECURITY_CENTER_EXIT_FADE.name()));
        assertEquals(300, exported.get(ConfigSchema.Animation.SETTINGS_PAGE.name()));
    }

    @Test
    public void widgetHorizontalStretchDefaultsOffAndRoundTrips() {
        Map<String, Object> emptyExport = ConfigCodec.exportValues(new HashMap<>());
        assertEquals(Boolean.FALSE,
                emptyExport.get(ConfigSchema.Grid.WIDGET_HORIZONTAL_STRETCH.name()));

        Map<String, Object> prefs = new HashMap<>();
        prefs.put(ConfigSchema.Grid.WIDGET_HORIZONTAL_STRETCH.name(), true);
        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(Boolean.TRUE,
                exported.get(ConfigSchema.Grid.WIDGET_HORIZONTAL_STRETCH.name()));

        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(Boolean.TRUE,
                imported.get(ConfigSchema.Grid.WIDGET_HORIZONTAL_STRETCH.name()));
    }

    @Test
    public void recentsWallpaperDimmingSwitchRoundTrips() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put(ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING.name(), true);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(Boolean.TRUE,
                exported.get(ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING.name()));

        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(Boolean.TRUE,
                imported.get(ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING.name()));
    }

    @Test
    public void prismalControlsRoundTripWithoutDisturbingLegacyStorageRules() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("liquid_prismal_displacement_scale", 135);
        prefs.put("liquid_prismal_refraction_inset", 7);
        prefs.put("liquid_prismal_refraction_inset_tenths", 72);
        prefs.put("liquid_prismal_show_normals", true);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(135, exported.get("liquid_prismal_displacement_scale"));
        assertEquals(7.2d,
                ((Number) exported.get("liquid_prismal_refraction_inset")).doubleValue(), 0.0001d);
        assertEquals(Boolean.TRUE, exported.get("liquid_prismal_show_normals"));

        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(135, imported.get("liquid_prismal_displacement_scale"));
        assertEquals(7, imported.get("liquid_prismal_refraction_inset"));
        assertEquals(72, imported.get("liquid_prismal_refraction_inset_tenths"));
        assertEquals(Boolean.TRUE, imported.get("liquid_prismal_show_normals"));
    }

    @Test
    public void absentOptionalDividerValuesAreNotSynthesized() {
        Map<String, Object> empty = new HashMap<>();

        Map<String, Object> exported = ConfigCodec.exportValues(empty);
        Map<String, Object> imported = ConfigCodec.importValues(empty);

        assertFalse(exported.containsKey("dock_divider_enabled"));
        assertFalse(exported.containsKey("dock_divider_width_dp"));
        assertFalse(imported.containsKey("dock_divider_enabled"));
        assertFalse(imported.containsKey("dock_divider_width_dp"));
    }
}
