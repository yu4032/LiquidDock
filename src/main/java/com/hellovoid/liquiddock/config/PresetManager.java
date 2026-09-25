package com.hellovoid.liquiddock.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Owns persisted preset values and calculations, without UI side effects. */
public final class PresetManager {
    private static final Map<String, Object> DEFAULT_VALUES = createDefaultValues();

    private PresetManager() {}

    public static Map<String, Object> defaultValues() {
        return DEFAULT_VALUES;
    }

    public static void applyDefault(SharedPreferences.Editor editor) {
        for (ConfigKey<?> key : ConfigSchema.all()) {
            editor.remove(key.name());
            if (key.storageMode() == ConfigKey.StorageMode.DP_TENTHS) {
                editor.remove(key.name() + "_tenths");
            }
        }
        String profile = "third_party_glass.systemui.lockscreen_clock.";
        editor.remove(profile + "enabled");
        editor.remove(profile + "blur");
        editor.remove(profile + "tint_r");
        editor.remove(profile + "tint_g");
        editor.remove(profile + "tint_b");
        editor.remove(profile + "tint_alpha");

        for (Map.Entry<String, Object> entry : DEFAULT_VALUES.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
            } else if (value instanceof Float) {
                editor.putFloat(entry.getKey(), (Float) value);
            }
        }
        editor.commit();
    }

    public static IpadPresetResult applyIpad(Context context, SharedPreferences preferences) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float density = Math.max(0.1f, dm.density);
        float shortSideDp = Math.min(dm.widthPixels, dm.heightPixels) / density;
        float displayScale = Math.max(0.90f, Math.min(1.20f, shortSideDp / 668f));

        Resources launcherRes = null;
        String launcherPackage = "com.miui.home";
        try {
            launcherRes = context.createPackageContext(launcherPackage,
                    Context.CONTEXT_IGNORE_SECURITY).getResources();
        } catch (PackageManager.NameNotFoundException ignored) {}

        int icon = dimenPx(launcherRes, launcherPackage,
                "config_hotseats_icon_content_default_height", 60f, density);
        int cell = dimenPx(launcherRes, launcherPackage,
                "hotseats_list_content_cell_width", 80f, density);
        int dockHeight = dimenPx(launcherRes, launcherPackage,
                "hotseats_height_land", 78f, density);
        int dockRadius = dimenPx(launcherRes, launcherPackage,
                "hotseats_list_content_background_radius", 21f, density);
        int sidePadding = dimenPx(launcherRes, launcherPackage,
                "hotseats_list_content_padding_side", 9.3f, density);

        int targetGap = Math.round(14f * density * displayScale);
        int targetHeight = icon + Math.round(20f * density * displayScale);
        int targetRadius = Math.round(22f * density * displayScale);
        int targetSidePadding = Math.round(14f * density * displayScale);
        int spacing = Math.round((icon + targetGap - cell) / 2f);
        int heightOffset = targetHeight - dockHeight;
        int widthOffset = 2 * (targetSidePadding - sidePadding);
        int cornerOffset = targetRadius - dockRadius;
        int oneDp = Math.max(1, Math.round(displayScale));
        int bottomOffset = Math.round(10f * density * displayScale);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(ConfigSchema.Dock.BLUR_RADIUS.name(), 100);
        editor.putBoolean(ConfigSchema.Dock.DIMENSIONS_DP.name(), true);
        editor.putBoolean(ConfigSchema.Dock.CORNERS_DP.name(), true);
        putDp(editor, ConfigSchema.Dock.HEIGHT_OFFSET, heightOffset / density);
        putDp(editor, ConfigSchema.Dock.WIDTH_OFFSET, widthOffset / density);
        putDp(editor, ConfigSchema.Dock.CORNER_OFFSET, cornerOffset / density);
        putDp(editor, ConfigSchema.Dock.BLUR_CORNER_OFFSET, -1f);
        editor.putBoolean(ConfigSchema.Grid.ENABLED.name(), true);
        editor.putBoolean(ConfigSchema.Grid.MARGINS_DP.name(), true);
        editor.putBoolean(ConfigSchema.Grid.MARGINS_OFFSET.name(), true);
        putDp(editor, ConfigSchema.Grid.LANDSCAPE_MARGIN_LEFT, 0f);
        putDp(editor, ConfigSchema.Grid.LANDSCAPE_MARGIN_RIGHT, 0f);
        putDp(editor, ConfigSchema.Grid.LANDSCAPE_MARGIN_TOP, 0f);
        putDp(editor, ConfigSchema.Grid.LANDSCAPE_MARGIN_BOTTOM, 0f);
        putDp(editor, ConfigSchema.Grid.PORTRAIT_MARGIN_LEFT, 0f);
        putDp(editor, ConfigSchema.Grid.PORTRAIT_MARGIN_RIGHT, 0f);
        putDp(editor, ConfigSchema.Grid.PORTRAIT_MARGIN_TOP, 0f);
        putDp(editor, ConfigSchema.Grid.PORTRAIT_MARGIN_BOTTOM, 0f);
        putDp(editor, ConfigSchema.Grid.LANDSCAPE_ROW_GAP, 0f);
        putDp(editor, ConfigSchema.Grid.PORTRAIT_ROW_GAP, 0f);
        putDp(editor, ConfigSchema.Grid.LANDSCAPE_INDICATOR_Y, 0f);
        putDp(editor, ConfigSchema.Grid.PORTRAIT_INDICATOR_Y, 0f);
        editor.putBoolean(ConfigSchema.Dock.ENABLED.name(), true);
        editor.putBoolean(ConfigSchema.Dock.STROKE_ENABLED.name(), true);
        editor.putInt(ConfigSchema.Dock.STROKE_RED.name(), 255);
        editor.putInt(ConfigSchema.Dock.STROKE_GREEN.name(), 255);
        editor.putInt(ConfigSchema.Dock.STROKE_BLUE.name(), 255);
        editor.putInt(ConfigSchema.Dock.STROKE_ALPHA.name(), 255);
        editor.putBoolean(ConfigSchema.Dock.SQUIRCLE.name(), true);
        putDp(editor, ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH, oneDp);
        putDp(editor, ConfigSchema.Dock.SQUIRCLE_STROKE_OFFSET, 0f);
        editor.putInt(ConfigSchema.Dock.SQUIRCLE_CONTROL_POINT.name(), 65);
        editor.putBoolean(ConfigSchema.Dock.FILL_DIFF.name(), true);
        putDp(editor, ConfigSchema.Dock.FILL_DIFF_STROKE_WIDTH, oneDp);
        putDp(editor, ConfigSchema.Dock.STANDARD_STROKE_WIDTH, oneDp);
        editor.putBoolean(ConfigSchema.Dock.SHADOW_ENABLED.name(), true);
        putDp(editor, ConfigSchema.Dock.SHADOW_RADIUS, 10f * displayScale);
        putDp(editor, ConfigSchema.Dock.SHADOW_SIZE, 13f * displayScale);
        editor.putInt(ConfigSchema.Dock.SHADOW_ALPHA.name(), 140);
        putDp(editor, ConfigSchema.Dock.SHADOW_Y, 3f * displayScale);
        editor.putBoolean(ConfigSchema.Dock.STROKE_SHADOW.name(), false);
        putDp(editor, ConfigSchema.Dock.STROKE_SHADOW_RADIUS, 3f * displayScale);
        editor.putInt(ConfigSchema.Dock.STROKE_SHADOW_ALPHA.name(), 70);
        putDp(editor, ConfigSchema.Dock.SPACING, spacing / density);
        putDp(editor, ConfigSchema.Dock.BOTTOM_OFFSET, bottomOffset / density);
        editor.commit();

        return new IpadPresetResult(spacing, heightOffset, widthOffset, cornerOffset, bottomOffset);
    }

    private static Map<String, Object> createDefaultValues() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("liquiddock_enabled", Boolean.TRUE);
        source.put("animation_workspace_visibility_ms", 0);
        source.put("animation_dock_icon_reveal_ms", 278);
        source.put("animation_press_in_ms", 90);
        source.put("animation_press_out_ms", 160);
        source.put("animation_dock_resize_ms", 180);
        source.put("animation_settings_page_ms", 300);
        source.put("home_grid_8x4", Boolean.FALSE);
        source.put("grid_profile", "8x4");
        source.put("grid_widget_adaptation", Boolean.FALSE);
        source.put("launcher450_icon_size_enabled", Boolean.TRUE);
        source.put("launcher450_icon_size_percent", 100);
        source.put("grid_margins_dp", Boolean.TRUE);
        source.put("grid_margins_offset", Boolean.TRUE);
        source.put("grid_landscape_horizontal_distance", 0);
        source.put("grid_landscape_top_distance", -8);
        source.put("grid_landscape_bottom_distance", 8);
        source.put("grid_portrait_horizontal_distance", 0);
        source.put("grid_portrait_top_distance", 30);
        source.put("grid_portrait_bottom_distance", 0);
        source.put("grid_landscape_margin_left", 30);
        source.put("grid_landscape_margin_right", 30);
        source.put("grid_landscape_margin_top", -35);
        source.put("grid_landscape_margin_bottom", 20);
        source.put("grid_portrait_margin_left", 0);
        source.put("grid_portrait_margin_right", 0);
        source.put("grid_portrait_margin_top", 0);
        source.put("grid_portrait_margin_bottom", 100);
        source.put("grid_landscape_row_gap", 0);
        source.put("grid_portrait_row_gap", -15);
        source.put("indicator_landscape_y", -9.4d);
        source.put("indicator_portrait_y", 11.8d);
        source.put("dock_customization", Boolean.FALSE);
        source.put("dock_hide_mirror_shortcut", Boolean.FALSE);
        source.put("dock_resize_animation", Boolean.FALSE);
        source.put("dock_smooth_resize_animation", Boolean.TRUE);
        source.put("dock_dimensions_dp", Boolean.TRUE);
        source.put("width_offset", 0);
        source.put("height_offset", 0);
        source.put("dock_spacing", 0);
        source.put("dock_bottom_offset", 0);
        source.put("blur_radius", 100);
        source.put("corners_dp", Boolean.TRUE);
        source.put("corner_offset", 3.4d);
        source.put("blur_corner_offset", 0);
        source.put("squircle", Boolean.FALSE);
        source.put("fill_diff", Boolean.FALSE);
        source.put("dock_stroke", Boolean.FALSE);
        source.put("sq_outer_cp", 65);
        source.put("sq_stroke_w", 1);
        source.put("sq_stroke_off", 0);
        source.put("stroke_w", 1);
        source.put("std_stroke_w", 0.9d);
        source.put("stroke_base_r", 175);
        source.put("stroke_base_g", 163);
        source.put("stroke_base_b", 171);
        source.put("stroke_base_alpha", 74);
        source.put("stroke_shadow", Boolean.FALSE);
        source.put("shadow_radius", 3);
        source.put("shadow_alpha", 70);
        source.put("dock_shadow", Boolean.FALSE);
        source.put("dock_shadow_radius", 10);
        source.put("dock_shadow_size", 4.7d);
        source.put("dock_shadow_alpha", 64);
        source.put("dock_shadow_y", 0);
        source.put("dock_divider_enabled", Boolean.TRUE);
        source.put("dock_divider_width_dp", 10);
        source.put("dock_divider_height_scale", 71);
        source.put("dock_divider_y_offset", 0);
        source.put("dock_divider_color_r", 255);
        source.put("dock_divider_color_g", 255);
        source.put("dock_divider_color_b", 255);
        source.put("dock_divider_alpha", 128);
        source.put("liquid_glass", Boolean.TRUE);
        source.put("liquid_security_center_glass", Boolean.FALSE);
        source.put("liquid_systemui_handle_menu_glass", Boolean.FALSE);
        source.put("liquid_shortcut_popup_glass", Boolean.TRUE);
        source.put("liquid_shortcut_popup_dark_text", Boolean.TRUE);
        source.put("liquid_uninstall_dialog_glass", Boolean.TRUE);
        source.put("liquid_dialog_disable_dimming", Boolean.FALSE);
        source.put("liquid_dialog_dark_mode", Boolean.TRUE);
        source.put("liquid_dialog_blur", 6);
        source.put("liquid_dialog_tint_b", 0);
        source.put("liquid_dialog_tint_alpha", 0);
        source.put("liquid_folder_glass", Boolean.TRUE);
        source.put("liquid_widget_glass", Boolean.TRUE);
        source.put("liquid_widget_dark_content", Boolean.TRUE);
        source.put("liquid_icon_glass", Boolean.FALSE);
        source.put("liquid_functional_dock_icon_glass", Boolean.TRUE);
        source.put("liquid_recents_capsule_glass", Boolean.TRUE);
        source.put("liquid_folder_corner_radius", 0);
        source.put("liquid_icon_size_offset", 0);
        source.put("liquid_icon_corner_radius", 16.5d);
        source.put("liquid_widget_size_offset", 0);
        source.put("liquid_widget_corner_radius", 20);
        source.put("liquid_small_folder_glass", Boolean.TRUE);
        source.put("liquid_small_folder_size_offset", -0.5d);
        source.put("liquid_small_folder_corner_radius", 0);
        source.put("liquid_large_folder_glass", Boolean.TRUE);
        source.put("liquid_large_folder_size_offset", 0);
        source.put("liquid_large_folder_corner_radius", 16);
        source.put("liquid_dimensions_dp", Boolean.TRUE);
        source.put("liquid_blur_mode", "advanced_material");
        source.put("liquid_miuix_307_pipeline", Boolean.FALSE);
        source.put("liquid_blur", 7.2d);
        source.put("liquid_chromatic", 9);
        source.put("liquid_tint_alpha", 0);
        source.put("liquid_capture_power_limit_fps", 30);
        source.put("liquid_capture_stop_delay", 150);
        source.put("liquid_sampling_extra_top", -256);
        source.put("liquid_sampling_extra_bottom", -256);
        source.put("liquid_sampling_extra_left", -256);
        source.put("liquid_sampling_extra_right", -256);
        source.put("liquid_thickness", 18);
        source.put("liquid_ior", 155);
        source.put("liquid_normal_strength", 115);
        source.put("liquid_dome", 130);
        source.put("liquid_lens_refraction", 1.3d);
        source.put("liquid_capture_scale", 100);
        source.put("liquid_passblur_capture_scale", 50);
        source.put("liquid_passblur_render_fps", 0);
        source.put("liquid_dynamic_app_capture", Boolean.TRUE);
        source.put("liquid_dynamic_app_probe_fps", 3);
        source.put("liquid_dynamic_motion_threshold", 12);
        source.put("liquid_dynamic_bit_threshold", 18);
        source.put("liquid_dynamic_hold_ms", 900);
        source.put("liquid_black_threshold", 10);
        source.put("liquid_home_settle_delay", 1200);
        source.put("liquid_highlight_width", 100);
        source.put("liquid_tint_r", 0);
        source.put("liquid_tint_g", 0);
        source.put("liquid_tint_b", 0);
        source.put("liquid_depth_effect", 0);
        source.put("liquid_brightness", 108);
        source.put("liquid_specular_sharp", 88);
        source.put("liquid_specular_strength", 152);
        source.put("liquid_rim_light", 122);
        source.put("liquid_caustics", 0);
        source.put("liquid_edge_band", 32);
        source.put("liquid_highlight_alpha", 100);
        source.put("liquid_recents_prearm_distance", 8);
        source.put("liquid_prismal_refraction_inset", 20);
        source.put("liquid_prismal_displacement_scale", 115);
        source.put("liquid_prismal_height_transition_width", 8);
        source.put("liquid_prismal_smin_smoothing", 1.8d);
        source.put("liquid_prismal_edge_refraction_falloff", 400);
        source.put("liquid_prismal_fresnel_reflect", 198);
        source.put("liquid_prismal_dispersion_r", 100);
        source.put("liquid_prismal_dispersion_b", 100);
        source.put("liquid_prismal_vibrancy", 128);
        source.put("liquid_prismal_plain_highlight", 8);
        source.put("liquid_os4_edge_width_px", 6);
        source.put("liquid_os4_reflect_offset_px", 0);
        source.put("liquid_os4_reflection_strength", 28);
        source.put("liquid_os4_reflection_lighten", 16);
        source.put("liquid_os4_directional_angle_range", 52);
        source.put("liquid_os4_directional_intensity", 36);
        source.put("liquid_os4_directional_opposite_intensity", 23);
        source.put("liquid_prismal_light_dir_x", 0);
        source.put("liquid_prismal_light_dir_y", -80);
        source.put("liquid_prismal_shadow_r", 0);
        source.put("liquid_prismal_shadow_g", 0);
        source.put("liquid_prismal_shadow_b", 0);
        source.put("liquid_prismal_shadow_alpha", 35);
        source.put("liquid_prismal_shadow_softness", 1000);
        source.put("liquid_prismal_transmittance", 100);
        source.put("liquid_prismal_backdrop_scale_x", 100);
        source.put("liquid_prismal_backdrop_scale_y", 100);
        source.put("liquid_prismal_parallax_scale", 100);
        source.put("liquid_prismal_show_normals", Boolean.FALSE);
        source.put("liquid_gboard_floating_glass", Boolean.FALSE);
        source.put("liquid_gboard_tint_r", 10);
        source.put("liquid_gboard_tint_g", 15);
        source.put("liquid_gboard_tint_b", 15);
        source.put("liquid_gboard_tint_alpha", 126);
        source.put("launcher_surface_component_sky_haze", Boolean.TRUE);
        source.put("launcher_surface_component_specular", Boolean.TRUE);
        source.put("launcher_surface_component_lit_rim", Boolean.TRUE);
        source.put("launcher_surface_component_opposite_rim", Boolean.TRUE);
        source.put("launcher_surface_component_corner_rim", Boolean.TRUE);
        source.put("launcher_surface_component_face_sheen", Boolean.TRUE);
        source.put("launcher_surface_component_plain_highlight", Boolean.TRUE);
        source.put("launcher_surface_component_caustics", Boolean.TRUE);
        source.put("launcher_surface_component_press_glow", Boolean.FALSE);
        source.put("launcher_large_surface_component_sky_haze", Boolean.TRUE);
        source.put("launcher_large_surface_component_specular", Boolean.TRUE);
        source.put("launcher_large_surface_component_lit_rim", Boolean.TRUE);
        source.put("launcher_large_surface_component_opposite_rim", Boolean.TRUE);
        source.put("launcher_large_surface_component_corner_rim", Boolean.TRUE);
        source.put("launcher_large_surface_component_face_sheen", Boolean.TRUE);
        source.put("launcher_large_surface_component_plain_highlight", Boolean.TRUE);
        source.put("launcher_large_surface_component_caustics", Boolean.TRUE);
        source.put("launcher_large_surface_component_press_glow", Boolean.FALSE);
        source.put("workstation_dock_customization", Boolean.FALSE);
        source.put("workstation_dock_width_offset", 26.5d);
        source.put("workstation_dock_icon_glass_corner_radius", 12);
        source.put("workstation_grid_horizontal_offset", 0);
        source.put("workstation_all_apps_landscape_horizontal_offset", 41.1d);
        source.put("workstation_all_apps_landscape_vertical_offset", 0);
        source.put("workstation_all_apps_landscape_top_spacing", 90);
        source.put("workstation_all_apps_landscape_bottom_spacing", 118);
        source.put("workstation_all_apps_portrait_horizontal_offset", 41.2d);
        source.put("workstation_all_apps_portrait_vertical_offset", 0);
        source.put("workstation_all_apps_portrait_top_spacing", 132);
        source.put("workstation_all_apps_portrait_bottom_spacing", 60.8d);
        source.put("workstation_dock_icon_top_offset", 0);
        source.put("workstation_dock_icon_bottom_offset", 0);
        source.put("recents_background_blur_percent", 36);
        source.put("recents_disable_wallpaper_dimming", Boolean.TRUE);
        source.put("third_party_glass.systemui.lockscreen_clock.tint_alpha", 0);
        source.put("third_party_glass.systemui.lockscreen_clock.tint_g", 241);
        source.put("third_party_glass.systemui.lockscreen_clock.tint_r", 244);
        source.put("third_party_glass.systemui.lockscreen_clock.enabled", Boolean.FALSE);
        source.put("third_party_glass.systemui.lockscreen_clock.blur", 3.3722174d);
        source.put("third_party_glass.systemui.lockscreen_clock.tint_b", 239);
        Map<String, Object> values = ConfigCodec.importValues(source);
        values.put(ConfigSchema.Debug.LOGGING.name(), Boolean.FALSE);
        return Collections.unmodifiableMap(values);
    }

    private static void putDp(SharedPreferences.Editor editor, ConfigKey<Integer> key,
                              float value) {
        float clamped = value;
        if (key.minInt() != null) clamped = Math.max(key.minInt(), clamped);
        if (key.maxInt() != null) clamped = Math.min(key.maxInt(), clamped);
        editor.putInt(key.name(), Math.round(clamped));
        editor.putInt(key.name() + "_tenths", Math.round(clamped * 10f));
    }

    private static int dimenPx(Resources resources, String packageName,
                               String name, float fallbackDp, float density) {
        if (resources != null) {
            int id = resources.getIdentifier(name, "dimen", packageName);
            if (id != 0) {
                try { return resources.getDimensionPixelSize(id); }
                catch (Resources.NotFoundException ignored) {}
            }
        }
        return Math.round(fallbackDp * density);
    }

    public static final class IpadPresetResult {
        public final int spacing;
        public final int heightOffset;
        public final int widthOffset;
        public final int cornerOffset;
        public final int bottomOffset;

        private IpadPresetResult(int spacing, int heightOffset, int widthOffset,
                                 int cornerOffset, int bottomOffset) {
            this.spacing = spacing;
            this.heightOffset = heightOffset;
            this.widthOffset = widthOffset;
            this.cornerOffset = cornerOffset;
            this.bottomOffset = bottomOffset;
        }
    }
}
