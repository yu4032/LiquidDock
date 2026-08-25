package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.hellovoid.liquiddock.config.ConfigSchema;

/** Process-local glass runtime state. Preference changes release owned visuals immediately. */
final class GlassRuntimeState {
    private static volatile boolean enabled;
    private static volatile boolean iconEnabled;
    private static volatile boolean widgetEnabled;
    private static volatile boolean smallFolderEnabled;
    private static volatile boolean largeFolderEnabled;
    private static SharedPreferences prefs;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private GlassRuntimeState() {}

    static synchronized void initialize(
            SharedPreferences nextPrefs,
            boolean initialEnabled,
            boolean initialIconEnabled,
            boolean initialWidgetEnabled,
            boolean initialSmallFolderEnabled,
            boolean initialLargeFolderEnabled) {
        if (prefs != null && listener != null) {
            try { prefs.unregisterOnSharedPreferenceChangeListener(listener); } catch (Throwable ignored) {}
        }
        prefs = nextPrefs;
        enabled = initialEnabled;
        iconEnabled = initialIconEnabled;
        widgetEnabled = initialWidgetEnabled;
        smallFolderEnabled = initialSmallFolderEnabled;
        largeFolderEnabled = initialLargeFolderEnabled;
        if (nextPrefs == null) return;
        listener = (sharedPreferences, key) -> {
            if (!ConfigSchema.Glass.ENABLED.name().equals(key)
                    && !ConfigSchema.Core.ENABLED.name().equals(key)
                    && !ConfigSchema.Glass.ICON_GLASS.name().equals(key)
                    && !ConfigSchema.Glass.WIDGET_GLASS.name().equals(key)
                    && !ConfigSchema.Glass.SMALL_FOLDER_GLASS.name().equals(key)
                    && !ConfigSchema.Glass.LARGE_FOLDER_GLASS.name().equals(key)) return;
            boolean nextEnabled = sharedPreferences.getBoolean(ConfigSchema.Core.ENABLED.name(),
                    ConfigSchema.Core.ENABLED.runtimeFallback())
                    && sharedPreferences.getBoolean(ConfigSchema.Glass.ENABLED.name(),
                    ConfigSchema.Glass.ENABLED.runtimeFallback());
            boolean nextIconEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Glass.ICON_GLASS.name(),
                    ConfigSchema.Glass.ICON_GLASS.runtimeFallback());
            boolean nextWidgetEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Glass.WIDGET_GLASS.name(),
                    ConfigSchema.Glass.WIDGET_GLASS.runtimeFallback());
            boolean nextSmallFolderEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Glass.SMALL_FOLDER_GLASS.name(),
                    ConfigSchema.Glass.SMALL_FOLDER_GLASS.runtimeFallback());
            boolean nextLargeFolderEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Glass.LARGE_FOLDER_GLASS.name(),
                    ConfigSchema.Glass.LARGE_FOLDER_GLASS.runtimeFallback());
            apply(nextEnabled, nextIconEnabled, nextWidgetEnabled,
                    nextSmallFolderEnabled, nextLargeFolderEnabled);
        };
        nextPrefs.registerOnSharedPreferenceChangeListener(listener);
        MainHook.log("[DC][GlassRuntime] initialized enabled=" + enabled
                + " iconEnabled=" + isIconEnabled()
                + " widgetEnabled=" + isWidgetEnabled()
                + " smallFolderEnabled=" + isSmallFolderEnabled()
                + " largeFolderEnabled=" + isLargeFolderEnabled());
    }

    static boolean isEnabled() { return enabled; }
    static boolean isIconEnabled() { return enabled && iconEnabled; }
    static boolean isWidgetEnabled() { return enabled && widgetEnabled; }
    static boolean isSmallFolderEnabled() { return enabled && smallFolderEnabled; }
    static boolean isLargeFolderEnabled() { return enabled && largeFolderEnabled; }

    private static void apply(
            boolean nextEnabled,
            boolean nextIconEnabled,
            boolean nextWidgetEnabled,
            boolean nextSmallFolderEnabled,
            boolean nextLargeFolderEnabled) {
        boolean wasEnabled = enabled;
        boolean wasWidgetEnabled = isWidgetEnabled();
        if (enabled == nextEnabled
                && iconEnabled == nextIconEnabled
                && widgetEnabled == nextWidgetEnabled
                && smallFolderEnabled == nextSmallFolderEnabled
                && largeFolderEnabled == nextLargeFolderEnabled) return;

        enabled = nextEnabled;
        iconEnabled = nextIconEnabled;
        widgetEnabled = nextWidgetEnabled;
        smallFolderEnabled = nextSmallFolderEnabled;
        largeFolderEnabled = nextLargeFolderEnabled;
        boolean nextLiveWidgetEnabled = isWidgetEnabled();
        MainHook.log("[DC][GlassRuntime] enabled=" + enabled
                + " iconEnabled=" + isIconEnabled()
                + " widgetEnabled=" + nextLiveWidgetEnabled
                + " smallFolderEnabled=" + isSmallFolderEnabled()
                + " largeFolderEnabled=" + isLargeFolderEnabled());

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
