package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

/**
 * Symmetric release for the two stable blur paths used by SearchActivityBackground.
 * This is intentionally Searchbox-specific: it does not discover arbitrary vendor methods.
 */
final class MiuiSearchboxVendorMaterial {
    private static final String TAG = "[DC][MiuiSearchboxGlass]";
    private static final float[] NO_CORNERS = new float[]{0f, 0f, 0f, 0f};
    private static final int[][] NO_BLEND_LAYERS = new int[0][];

    private MiuiSearchboxVendorMaterial() {}

    static void release(View background) {
        if (background == null) return;

        // Clear the framework/MIUI pass-window paths known to LiquidDock first.
        MiBlurBridge.clearContentBlur(background);

        // Legacy com.miui.blur.sdk.backdrop.BackdropBlurRelativeLayout path.
        invokeBlurRadiusZero(background);

        // Android 35+ Searchbox BlurTransition path:
        // setBackgroundBlur(int radius, float[] corners, int[][] blendLayers).
        invokeBackgroundBlurZero(background);
    }

    private static void invokeBlurRadiusZero(View background) {
        try {
            Method method = background.getClass().getMethod("setBlurRadius", Integer.TYPE);
            method.invoke(background, 0);
        } catch (NoSuchMethodException ignored) {
            // Not every Searchbox build exposes the legacy backdrop SDK path.
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " legacy vendor blur release failed", error);
        }
    }

    private static void invokeBackgroundBlurZero(View background) {
        try {
            Method method = background.getClass().getMethod(
                    "setBackgroundBlur", Integer.TYPE, float[].class, int[][].class);
            method.invoke(background, 0, NO_CORNERS, NO_BLEND_LAYERS);
        } catch (NoSuchMethodException ignored) {
            // Expected on Searchbox builds using only BackdropBlurRelativeLayout.
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " background vendor blur release failed", error);
        }
    }
}
