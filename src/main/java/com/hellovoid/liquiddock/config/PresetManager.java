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
        for (Map.Entry<String, Object> entry : DEFAULT_VALUES.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
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
        Map<String, Object> values = new LinkedHashMap<>();
        for (ConfigKey<?> key : ConfigSchema.all()) {
            if (key.exportMode() != ConfigKey.ExportMode.ALWAYS) continue;
            putSchemaDefault(values, key);
        }

        // Explicit preset inclusions/deltas only. IF_PRESENT keys stay absent unless they were
        // historically part of the preset; values that differ from UI defaults are typed here.
        put(values, ConfigSchema.Divider.ENABLED, false);

        putDp(values, ConfigSchema.Grid.LANDSCAPE_MARGIN_LEFT, 30f);
        putDp(values, ConfigSchema.Grid.LANDSCAPE_MARGIN_RIGHT, 30f);
        putDp(values, ConfigSchema.Grid.LANDSCAPE_MARGIN_TOP, -35f);
        putDp(values, ConfigSchema.Grid.LANDSCAPE_MARGIN_BOTTOM, 20f);
        putDp(values, ConfigSchema.Grid.PORTRAIT_MARGIN_BOTTOM, 100f);
        putDp(values, ConfigSchema.Grid.PORTRAIT_ROW_GAP, -16f);
        putDp(values, ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE, 30f);
        putDp(values, ConfigSchema.Grid.LANDSCAPE_BOTTOM_DISTANCE, 20f);
        putDp(values, ConfigSchema.Grid.PORTRAIT_TOP_DISTANCE, 10.3f);
        putDp(values, ConfigSchema.Grid.LANDSCAPE_INDICATOR_Y, -8.8f);
        putDp(values, ConfigSchema.Grid.PORTRAIT_INDICATOR_Y, 11.8f);

        put(values, ConfigSchema.Glass.ENABLED, true);
        put(values, ConfigSchema.Glass.CAPTURE_FPS, 30);
        put(values, ConfigSchema.Glass.CAPTURE_SCALE, 100);
        putDp(values, ConfigSchema.Glass.LENS_REFRACTION, 1.3f);
        putDp(values, ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING, 1.8f);

        put(values, ConfigSchema.Dock.STROKE_RED, 180);
        put(values, ConfigSchema.Dock.STROKE_GREEN, 180);
        put(values, ConfigSchema.Dock.STROKE_BLUE, 180);
        put(values, ConfigSchema.Dock.STROKE_ALPHA, 119);
        put(values, ConfigSchema.Dock.SQUIRCLE, true);
        put(values, ConfigSchema.Dock.SQUIRCLE_CONTROL_POINT, 65);
        put(values, ConfigSchema.Dock.FILL_DIFF, true);
        put(values, ConfigSchema.Dock.SHADOW_ALPHA, 64);
        putDp(values, ConfigSchema.Dock.HEIGHT_OFFSET, 2.2f);
        putDp(values, ConfigSchema.Dock.CORNER_OFFSET, 1f);
        putDp(values, ConfigSchema.Dock.BLUR_CORNER_OFFSET, -1f);
        putDp(values, ConfigSchema.Dock.SQUIRCLE_STROKE_OFFSET, 0f);
        putDp(values, ConfigSchema.Dock.SHADOW_RADIUS, 10f);
        putDp(values, ConfigSchema.Dock.SHADOW_SIZE, 4.7f);
        putDp(values, ConfigSchema.Dock.SHADOW_Y, 0f);
        putDp(values, ConfigSchema.Dock.BOTTOM_OFFSET, -2f);
        return Collections.unmodifiableMap(values);
    }

    private static void putSchemaDefault(Map<String, Object> values, ConfigKey<?> key) {
        Object value = key.uiDefault();
        if (value == null) return;
        if (key.storageMode() == ConfigKey.StorageMode.DP_TENTHS) {
            int dp = (Integer) value;
            values.put(key.name(), dp);
            values.put(key.name() + "_tenths", dp * 10);
        } else {
            values.put(key.name(), value);
        }
    }

    private static <T> void put(Map<String, Object> values, ConfigKey<T> key, T value) {
        values.put(key.name(), value);
    }

    private static void putDp(Map<String, Object> values, ConfigKey<Integer> key, float value) {
        float clamped = value;
        if (key.minInt() != null) clamped = Math.max(key.minInt(), clamped);
        if (key.maxInt() != null) clamped = Math.min(key.maxInt(), clamped);
        values.put(key.name(), Math.round(clamped));
        values.put(key.name() + "_tenths", Math.round(clamped * 10f));
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
