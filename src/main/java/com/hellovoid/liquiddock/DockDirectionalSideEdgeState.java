package com.hellovoid.liquiddock;

import android.content.SharedPreferences;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalEdgeDecoration;

/** Immutable render-thread style for the Dock body, with no per-frame allocations. */
final class DockDirectionalSideEdgeState {
    private static volatile PrismalEdgeDecoration style = PrismalEdgeDecoration.NONE;

    private DockDirectionalSideEdgeState() {}

    static void refresh(SharedPreferences prefs) {
        if (prefs == null || !prefs.getBoolean(
                ConfigSchema.Dock.SIDE_DARK_EDGE_ENABLED.name(),
                ConfigSchema.Dock.SIDE_DARK_EDGE_ENABLED.runtimeFallback())) {
            style = PrismalEdgeDecoration.NONE;
            return;
        }
        float alpha = clamp(prefs.getInt(
                ConfigSchema.Dock.SIDE_DARK_EDGE_ALPHA.name(),
                ConfigSchema.Dock.SIDE_DARK_EDGE_ALPHA.runtimeFallback()), 0, 100) / 100f;
        float width = clamp(prefs.getInt(
                ConfigSchema.Dock.SIDE_DARK_EDGE_WIDTH.name(),
                ConfigSchema.Dock.SIDE_DARK_EDGE_WIDTH.runtimeFallback()), 1, 12);
        float suppression = clamp(prefs.getInt(
                ConfigSchema.Dock.SIDE_DARK_EDGE_LIGHT_SUPPRESSION.name(),
                ConfigSchema.Dock.SIDE_DARK_EDGE_LIGHT_SUPPRESSION.runtimeFallback()), 0, 100) / 100f;
        float angle = clamp(prefs.getInt(
                ConfigSchema.Dock.SIDE_DARK_EDGE_ANGLE_RANGE.name(),
                ConfigSchema.Dock.SIDE_DARK_EDGE_ANGLE_RANGE.runtimeFallback()), 5, 80);
        // The RenderNode MiShadow remains the whole-Dock shadow owner.
        style = new PrismalEdgeDecoration(
                width, 0f, 0f, 0f, alpha, 0f, 0f,
                0f, 0f, 0f, 0f, 0f, suppression, angle);
    }

    static PrismalEdgeDecoration current() {
        return VisualRuntimeState.isDockCustomizationEnabled()
                ? style : PrismalEdgeDecoration.NONE;
    }

    private static int clamp(int value, int lower, int upper) {
        return Math.max(lower, Math.min(upper, value));
    }
}
