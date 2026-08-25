package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.hellovoid.liquiddock.config.ConfigSchema;

/** Process-local glass runtime state. Preference changes release owned visuals immediately. */
final class GlassRuntimeState {
    private static volatile boolean enabled;
    private static volatile boolean widgetEnabled;
    private static SharedPreferences prefs;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private GlassRuntimeState() {}

    static synchronized void initialize(
            SharedPreferences nextPrefs, boolean initialEnabled, boolean initialWidgetEnabled) {
        if (prefs != null && listener != null) {
            try { prefs.unregisterOnSharedPreferenceChangeListener(listener); } catch (Throwable ignored) {}
        }
        prefs = nextPrefs;
        enabled = initialEnabled;
        widgetEnabled = initialWidgetEnabled;
        if (nextPrefs == null) return;
        listener = (sharedPreferences, key) -> {
            if (!ConfigSchema.Glass.ENABLED.name().equals(key)
                    && !ConfigSchema.Core.ENABLED.name().equals(key)
                    && !ConfigSchema.Glass.WIDGET_GLASS.name().equals(key)) return;
            boolean nextEnabled = sharedPreferences.getBoolean(ConfigSchema.Core.ENABLED.name(),
                    ConfigSchema.Core.ENABLED.runtimeFallback())
                    && sharedPreferences.getBoolean(ConfigSchema.Glass.ENABLED.name(),
                    ConfigSchema.Glass.ENABLED.runtimeFallback());
            boolean nextWidgetEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Glass.WIDGET_GLASS.name(),
                    ConfigSchema.Glass.WIDGET_GLASS.runtimeFallback());
            apply(nextEnabled, nextWidgetEnabled);
        };
        nextPrefs.registerOnSharedPreferenceChangeListener(listener);
        MainHook.log("[DC][GlassRuntime] initialized enabled=" + enabled
                + " widgetEnabled=" + isWidgetEnabled());
    }

    static boolean isEnabled() { return enabled; }
    static boolean isWidgetEnabled() { return enabled && widgetEnabled; }

    private static void apply(boolean nextEnabled, boolean nextWidgetEnabled) {
        boolean wasEnabled = enabled;
        boolean wasWidgetEnabled = isWidgetEnabled();
        if (enabled == nextEnabled && widgetEnabled == nextWidgetEnabled) return;

        enabled = nextEnabled;
        widgetEnabled = nextWidgetEnabled;
        boolean nextLiveWidgetEnabled = isWidgetEnabled();
        MainHook.log("[DC][GlassRuntime] enabled=" + enabled
                + " widgetEnabled=" + nextLiveWidgetEnabled);

        if (wasEnabled && !nextEnabled) {
            runOnMain(() -> {
                MiuixFolderGlassHook.onRuntimeGlassDisabled();
                MiuixLauncherStaticGlassHook.onRuntimeGlassDisabled();
                DockGlassItemRegistry.clear();
                LauncherGlassDragOverlay.releaseAll();
                LauncherGlassSessionRegistry.shutdownAll();
                Miuix307MaterialPipeline.onRuntimeGlassDisabled();
                MiuixGlassHook.onRuntimeGlassDisabled();
                MainHook.log("[DC][GlassRuntime] GPU glass teardown complete");
            });
            return;
        }

        if (wasWidgetEnabled && !nextLiveWidgetEnabled) {
            runOnMain(() -> {
                MiuixLauncherStaticGlassHook.onRuntimeWidgetGlassDisabled();
                MainHook.log("[DC][GlassRuntime] widget glass ownership released");
            });
        }
    }

    private static void runOnMain(Runnable action) {
        Looper main = Looper.getMainLooper();
        if (Looper.myLooper() == main) action.run();
        else new Handler(main).post(action);
    }
}
