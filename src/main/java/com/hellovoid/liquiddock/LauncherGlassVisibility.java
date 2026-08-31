package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewParent;

/** Ancestor-aware visibility helper for Workspace glass nodes. */
final class LauncherGlassVisibility {
    private static final float EPSILON = 0.001f;

    private LauncherGlassVisibility() {}

    static float aggregate(boolean[] visible, float[] alpha) {
        if (visible == null || alpha == null || visible.length != alpha.length) return 0f;
        float result = 1f;
        for (int i = 0; i < visible.length; i++) {
            if (!visible[i] || !Float.isFinite(alpha[i]) || alpha[i] <= 0f) return 0f;
            result *= alpha[i];
            if (!Float.isFinite(result) || result <= EPSILON) return 0f;
        }
        return result;
    }

    static boolean isVisible(View host, View sceneRoot) {
        return effectiveAlpha(host, sceneRoot) > EPSILON;
    }

    static float effectiveAlpha(View host, View sceneRoot) {
        if (host == null || sceneRoot == null || !host.isAttachedToWindow()) return 0f;
        float result = 1f;
        View cursor = host;
        while (cursor != null) {
            if (cursor.getVisibility() != View.VISIBLE) return 0f;
            float alpha = cursor.getAlpha();
            if (!Float.isFinite(alpha) || alpha <= 0f) return 0f;
            result *= alpha;
            if (!Float.isFinite(result) || result <= EPSILON) return 0f;
            if (cursor == sceneRoot) return result;
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return 0f;
    }
}
