package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import com.hellovoid.liquiddock.config.ConfigSchema;

/**
 * Lightweight render-thread readable style for the Dock BODY only. Changes
 * invalidate the existing Dock scene but never restart or resample PassBlur.
 */
final class DockDirectionalSideEdgeState {
    private static volatile boolean enabled;
    private static volatile float alpha;
    private static volatile float widthPx = 3f;
    private static volatile float litSuppression = 1f;

    private DockDirectionalSideEdgeState() {}

    static void refresh(SharedPreferences prefs) {
        if (prefs == null) return;
        boolean nextEnabled = prefs.getBoolean(
                ConfigSchema.Dock.SIDE_DARK_EDGE_ENABLED.name(),
                ConfigSchema.Dock.SIDE_DARK_EDGE_ENABLED.runtimeFallback());
        int nextAlpha = prefs.getInt(ConfigSchema.Dock.SIDE_DARK_EDGE_ALPHA.name(),
                ConfigSchema.Dock.SIDE_DARK_EDGE_ALPHA.runtimeFallback());
        int nextWidth = prefs.getInt(ConfigSchema.Dock.SIDE_DARK_EDGE_WIDTH.name(),
                ConfigSchema.Dock.SIDE_DARK_EDGE_WIDTH.runtimeFallback());
        int nextSuppression = prefs.getInt(
                ConfigSchema.Dock.SIDE_DARK_EDGE_LIGHT_SUPPRESSION.name(),
                ConfigSchema.Dock.SIDE_DARK_EDGE_LIGHT_SUPPRESSION.runtimeFallback());
        enabled = nextEnabled;
        alpha = Math.max(0, Math.min(100, nextAlpha)) * .01f;
        widthPx = Math.max(1f, Math.min(12f, nextWidth));
        litSuppression = Math.max(0, Math.min(100, nextSuppression)) * .01f;
    }

    static float alpha() {
        return enabled && VisualRuntimeState.isDockCustomizationEnabled() ? alpha : 0f;
    }

    static float widthPx() { return widthPx; }
    static float litSuppression() { return litSuppression; }
}
