package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.hellovoid.liquiddock.config.ConfigSchema;

/** Process-local state for visual switches that can be changed safely without restarting Launcher. */
final class VisualRuntimeState {
    private static volatile boolean coreEnabled;
    private static volatile boolean dockCustomizationEnabled;
    private static volatile boolean dockStrokeEnabled;
    private static volatile boolean dockShadowEnabled;
    private static volatile boolean strokeShadowEnabled;
    private static volatile boolean dividerEnabled;

    private static SharedPreferences prefs;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;

    private VisualRuntimeState() {}

    static synchronized void initialize(
            SharedPreferences nextPrefs,
            boolean initialCoreEnabled,
            boolean initialDockCustomizationEnabled,
            boolean initialDockStrokeEnabled,
            boolean initialDockShadowEnabled,
            boolean initialStrokeShadowEnabled,
            boolean initialDividerEnabled) {
        if (prefs != null && listener != null) {
            try { prefs.unregisterOnSharedPreferenceChangeListener(listener); }
            catch (Throwable ignored) {}
        }
        prefs = nextPrefs;
        coreEnabled = initialCoreEnabled;
        dockCustomizationEnabled = initialDockCustomizationEnabled;
        dockStrokeEnabled = initialDockStrokeEnabled;
        dockShadowEnabled = initialDockShadowEnabled;
        strokeShadowEnabled = initialStrokeShadowEnabled;
        dividerEnabled = initialDividerEnabled;
        if (nextPrefs == null) return;

        listener = (sharedPreferences, key) -> {
            if (!ConfigSchema.Core.ENABLED.name().equals(key)
                    && !ConfigSchema.Dock.ENABLED.name().equals(key)
                    && !ConfigSchema.Dock.STROKE_ENABLED.name().equals(key)
                    && !ConfigSchema.Dock.SHADOW_ENABLED.name().equals(key)
                    && !ConfigSchema.Dock.STROKE_SHADOW.name().equals(key)
                    && !ConfigSchema.Divider.ENABLED.name().equals(key)) return;

            boolean nextCoreEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Core.ENABLED.name(),
                    ConfigSchema.Core.ENABLED.runtimeFallback());
            boolean nextDockCustomizationEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Dock.ENABLED.name(), dockCustomizationEnabled);
            boolean nextDockStrokeEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Dock.STROKE_ENABLED.name(), dockStrokeEnabled);
            boolean nextDockShadowEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Dock.SHADOW_ENABLED.name(), dockShadowEnabled);
            boolean nextStrokeShadowEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Dock.STROKE_SHADOW.name(), strokeShadowEnabled);
            // Divider runtimeFallback is intentionally nullable for legacy import compatibility.
            // Preserve the already-resolved runtime value when the explicit key is absent.
            boolean nextDividerEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Divider.ENABLED.name(), dividerEnabled);
            apply(nextCoreEnabled, nextDockCustomizationEnabled, nextDockStrokeEnabled,
                    nextDockShadowEnabled, nextStrokeShadowEnabled, nextDividerEnabled);
        };
        nextPrefs.registerOnSharedPreferenceChangeListener(listener);
        logState("initialized");
    }

    static boolean isDockCustomizationEnabled() {
        return coreEnabled && dockCustomizationEnabled;
    }

    static boolean isDockStrokeEnabled() {
        return coreEnabled && dockStrokeEnabled;
    }

    static boolean isDockShadowEnabled() {
        return coreEnabled && dockCustomizationEnabled && dockShadowEnabled;
    }

    static boolean isStrokeShadowEnabled() {
        return coreEnabled && dockStrokeEnabled && strokeShadowEnabled;
    }

    static boolean isDividerEnabled() {
        return coreEnabled && dividerEnabled;
    }

    private static void apply(
            boolean nextCoreEnabled,
            boolean nextDockCustomizationEnabled,
            boolean nextDockStrokeEnabled,
            boolean nextDockShadowEnabled,
            boolean nextStrokeShadowEnabled,
            boolean nextDividerEnabled) {
        if (coreEnabled == nextCoreEnabled
                && dockCustomizationEnabled == nextDockCustomizationEnabled
                && dockStrokeEnabled == nextDockStrokeEnabled
                && dockShadowEnabled == nextDockShadowEnabled
                && strokeShadowEnabled == nextStrokeShadowEnabled
                && dividerEnabled == nextDividerEnabled) return;

        boolean wasDockCustomizationEnabled = isDockCustomizationEnabled();
        boolean wasDockStrokeEnabled = isDockStrokeEnabled();
        boolean wasDockShadowEnabled = isDockShadowEnabled();
        boolean wasStrokeShadowEnabled = isStrokeShadowEnabled();
        boolean wasDividerEnabled = isDividerEnabled();

        // Publish the new booleans before scheduling teardown. Any callback that was queued before
        // the preference change must observe the new false value and become inert immediately.
        coreEnabled = nextCoreEnabled;
        dockCustomizationEnabled = nextDockCustomizationEnabled;
        dockStrokeEnabled = nextDockStrokeEnabled;
        dockShadowEnabled = nextDockShadowEnabled;
        strokeShadowEnabled = nextStrokeShadowEnabled;
        dividerEnabled = nextDividerEnabled;

        boolean nextLiveDockCustomizationEnabled = isDockCustomizationEnabled();
        boolean nextLiveDockStrokeEnabled = isDockStrokeEnabled();
        boolean nextLiveDockShadowEnabled = isDockShadowEnabled();
        boolean nextLiveStrokeShadowEnabled = isStrokeShadowEnabled();
        boolean nextLiveDividerEnabled = isDividerEnabled();
        logState("updated");

        if (wasDockCustomizationEnabled && !nextLiveDockCustomizationEnabled) {
            runOnMain(() -> MainHook.onRuntimeDockCustomizationDisabled());
        }
        if (wasDockStrokeEnabled && !nextLiveDockStrokeEnabled) {
            runOnMain(() -> DockStrokeRenderer.onRuntimeStrokeDisabled());
        }
        if (wasDockShadowEnabled && !nextLiveDockShadowEnabled) {
            runOnMain(() -> MainHook.onRuntimeDockShadowDisabled());
        }
        if (wasStrokeShadowEnabled && !nextLiveStrokeShadowEnabled) {
            runOnMain(() -> DockStrokeRenderer.refreshInstalledFromCurrentConfig());
        }
        if (wasDividerEnabled && !nextLiveDividerEnabled) {
            runOnMain(() -> DockDividerHook.onRuntimeDividerDisabled());
        }
    }

    private static void runOnMain(Runnable action) {
        Looper main = Looper.getMainLooper();
        if (Looper.myLooper() == main) action.run();
        else new Handler(main).post(action);
    }

    private static void logState(String phase) {
        MainHook.log("[DC][VisualRuntime] " + phase
                + " dock=" + isDockCustomizationEnabled()
                + " stroke=" + isDockStrokeEnabled()
                + " dockShadow=" + isDockShadowEnabled()
                + " strokeShadow=" + isStrokeShadowEnabled()
                + " divider=" + isDividerEnabled());
    }
}
