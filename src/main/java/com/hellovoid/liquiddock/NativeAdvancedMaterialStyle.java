package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.graphics.Point;
import android.view.View;

import com.hellovoid.liquiddock.config.ConfigSchema;

import java.lang.reflect.Method;
import java.util.ArrayList;

/** Immutable native-material appearance, cached per process and updated on preference changes. */
final class NativeAdvancedMaterialStyle {
    private static volatile NativeAdvancedMaterialStyle current;
    final int radiusPx, tintColor, fillColor, shadowColor, shadowRadiusDp;
    private static Method shadowMethod;

    private NativeAdvancedMaterialStyle(ConfigReader reader) {
        radiusPx = reader.i(ConfigSchema.Glass.ADVANCED_MATERIAL_BLUR.name(), 80);
        tintColor = color(reader,
                ConfigSchema.Glass.ADVANCED_MATERIAL_TINT_RED.name(),
                ConfigSchema.Glass.ADVANCED_MATERIAL_TINT_GREEN.name(),
                ConfigSchema.Glass.ADVANCED_MATERIAL_TINT_BLUE.name(),
                ConfigSchema.Glass.ADVANCED_MATERIAL_TINT_ALPHA.name());
        fillColor = color(reader,
                ConfigSchema.Glass.ADVANCED_MATERIAL_FILL_RED.name(),
                ConfigSchema.Glass.ADVANCED_MATERIAL_FILL_GREEN.name(),
                ConfigSchema.Glass.ADVANCED_MATERIAL_FILL_BLUE.name(),
                ConfigSchema.Glass.ADVANCED_MATERIAL_FILL_ALPHA.name());
        shadowColor = Color.argb(clamp(reader.i(ConfigSchema.Glass.ADVANCED_MATERIAL_SHADOW_ALPHA.name(), 0)),
                0, 0, 0);
        shadowRadiusDp = Math.max(0, Math.min(48,
                reader.i(ConfigSchema.Glass.ADVANCED_MATERIAL_SHADOW_RADIUS.name(), 5)));
    }

    static void update(ConfigReader prefs) {
        current = new NativeAdvancedMaterialStyle(prefs);
    }

    static NativeAdvancedMaterialStyle get() {
        NativeAdvancedMaterialStyle state = current;
        if (state == null) {
            state = new NativeAdvancedMaterialStyle(ConfigReader.load());
            current = state;
        }
        return state;
    }

    /** HyperOS setMiBackgroundBlendColors expects Point(color ARGB, PorterDuff native mode). */
    ArrayList<Point> blendColors() {
        if (Color.alpha(tintColor) == 0 && Color.alpha(fillColor) == 0) return null;
        ArrayList<Point> colors = new ArrayList<>(2);
        if (Color.alpha(tintColor) != 0) colors.add(new Point(tintColor, 3)); // SRC_OVER
        if (Color.alpha(fillColor) != 0) colors.add(new Point(fillColor, 3)); // SRC_OVER
        return colors;
    }

    void applyOwned(View view) {
        if (view == null) return;
        MiBlurBridge.applyPassWindowBlur(view, radiusPx, blendColors());
        applyShadow(view);
    }

    /** Do not disturb the native background mode or radius of an OEM-owned view. */
    void applyVendor(View view) {
        if (view == null) return;
        ArrayList<Point> colors = blendColors();
        if (colors != null) MiBlurBridge.setPassWindowBlurBlendColors(view, colors);
        applyShadow(view);
    }

    private void applyShadow(View view) {
        if (Color.alpha(shadowColor) == 0 || shadowRadiusDp == 0) return;
        try {
            Method setter = shadowMethod;
            if (setter == null) {
                setter = View.class.getMethod("setMiShadow", int.class, float.class,
                        float.class, float.class, float.class);
                shadowMethod = setter;
            }
            float radiusPx = shadowRadiusDp * view.getResources().getDisplayMetrics().density;
            setter.invoke(view, shadowColor, 0f, 0f, radiusPx, 0f);
        } catch (Throwable ignored) {
            // Some HyperOS generations do not expose the native shadow extension.
        }
    }

    private static int color(ConfigReader r, String red, String green, String blue, String alpha) {
        return Color.argb(clamp(r.i(alpha, 0)), clamp(r.i(red, 255)),
                clamp(r.i(green, 255)), clamp(r.i(blue, 255)));
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
}
