package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

/**
 * Ownership handoff for SearchActivityBackground's stable BackdropBlurRelativeLayout API.
 * The Method is resolved once from the known semantic class during hook installation; this helper
 * never discovers methods from the runtime class.
 */
final class MiuiSearchboxVendorMaterial {
    private static final String TAG = "[DC][MiuiSearchboxGlass]";

    private MiuiSearchboxVendorMaterial() {}

    static void release(View background, Method blurEnabledMethod) {
        setBlurEnabled(background, blurEnabledMethod, false, "release");
    }

    static void restore(View background, Method blurEnabledMethod) {
        setBlurEnabled(background, blurEnabledMethod, true, "restore");
    }

    private static void setBlurEnabled(
            View background,
            Method blurEnabledMethod,
            boolean enabled,
            String stage) {
        if (background == null || blurEnabledMethod == null) return;
        try {
            if (enabled) {
                blurEnabledMethod.invoke(background, Boolean.TRUE);
            } else {
                blurEnabledMethod.invoke(background, Boolean.FALSE);
            }
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " vendor backdrop " + stage + " failed", error);
        }
    }
}
