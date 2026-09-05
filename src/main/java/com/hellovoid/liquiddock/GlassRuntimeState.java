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
    private static volatile boolean widgetDarkContentEnabled;
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
            boolean initialWidgetDarkContentEnabled,
            boolean initialSmallFolderEnabled,
            boolean initialLargeFolderEnabled) {
        if (prefs != null && listener != null) {
            try { prefs.unregisterOnSharedPreferenceChangeListener(listener); } catch (Throwable ignored) {}
        }
        prefs = nextPrefs;
        enabled = initialEnabled;
        iconEnabled = initialIconEnabled;
        widgetEnabled = initialWidgetEnabled;
        widgetDarkContentEnabled = initialWidgetDarkContentEnabled;
        smallFolderEnabled = initialSmallFolderEnabled;
        largeFolderEnabled = initialLargeFolderEnabled;
        if (nextPrefs == null) return;
        listener = (sharedPreferences, key) -> {
            if (!ConfigSchema.Glass.ENABLED.name().equals(key)
                    && !ConfigSchema.Core.ENABLED.name().equals(key)
                    && !ConfigSchema.Glass.ICON_GLASS.name().equals(key)
                    && !ConfigSchema.Glass.WIDGET_GLASS.name().equals(key)
                    && !ConfigSchema.Glass.WIDGET_DARK_CONTENT.name().equals(key)
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
            boolean nextWidgetDarkContentEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Glass.WIDGET_DARK_CONTENT.name(),
                    ConfigSchema.Glass.WIDGET_DARK_CONTENT.runtimeFallback());
            boolean nextSmallFolderEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Glass.SMALL_FOLDER_GLASS.name(),
                    ConfigSchema.Glass.SMALL_FOLDER_GLASS.runtimeFallback());
            boolean nextLargeFolderEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Glass.LARGE_FOLDER_GLASS.name(),
                    ConfigSchema.Glass.LARGE_FOLDER_GLASS.runtimeFallback());
            apply(nextEnabled, nextIconEnabled, nextWidgetEnabled, nextWidgetDarkContentEnabled,
                    nextSmallFolderEnabled, nextLargeFolderEnabled);
        };
        nextPrefs.registerOnSharedPreferenceChangeListener(listener);
        MainHook.log("[DC][GlassRuntime] initialized enabled=" + enabled
                + " iconEnabled=" + isIconEnabled()
                + " widgetEnabled=" + isWidgetEnabled()
                + " widgetDarkContentEnabled=" + isWidgetDarkContentEnabled()
                + " smallFolderEnabled=" + isSmallFolderEnabled()
                + " largeFolderEnabled=" + isLargeFolderEnabled());
    }

    static boolean isEnabled() { return enabled; }
    static boolean isIconEnabled() { return enabled && iconEnabled; }
    static boolean isWidgetEnabled() { return enabled && widgetEnabled; }
    static boolean isWidgetDarkContentEnabled() {
        return isWidgetEnabled() && widgetDarkContentEnabled;
    }
    static boolean isSmallFolderEnabled() { return enabled && smallFolderEnabled; }
    static boolean isLargeFolderEnabled() { return enabled && largeFolderEnabled; }

    private static GlassRuntimeTransitionPolicy.Snapshot snapshot() {
        return new GlassRuntimeTransitionPolicy.Snapshot(
                isEnabled(), isIconEnabled(), isWidgetEnabled(), isWidgetDarkContentEnabled(),
                isSmallFolderEnabled(), isLargeFolderEnabled());
    }

    private static void apply(
            boolean nextEnabled,
            boolean nextIconEnabled,
            boolean nextWidgetEnabled,
            boolean nextWidgetDarkContentEnabled,
            boolean nextSmallFolderEnabled,
            boolean nextLargeFolderEnabled) {
        if (enabled == nextEnabled
                && iconEnabled == nextIconEnabled
                && widgetEnabled == nextWidgetEnabled
                && widgetDarkContentEnabled == nextWidgetDarkContentEnabled
                && smallFolderEnabled == nextSmallFolderEnabled
                && largeFolderEnabled == nextLargeFolderEnabled) return;

        GlassRuntimeTransitionPolicy.Snapshot before = snapshot();

        enabled = nextEnabled;
        iconEnabled = nextIconEnabled;
        widgetEnabled = nextWidgetEnabled;
        widgetDarkContentEnabled = nextWidgetDarkContentEnabled;
        smallFolderEnabled = nextSmallFolderEnabled;
        largeFolderEnabled = nextLargeFolderEnabled;

        GlassRuntimeTransitionPolicy.Transition transition =
                GlassRuntimeTransitionPolicy.plan(before, snapshot());
        MainHook.log("[DC][GlassRuntime] enabled=" + enabled
                + " iconEnabled=" + isIconEnabled()
                + " widgetEnabled=" + isWidgetEnabled()
                + " widgetDarkContentEnabled=" + isWidgetDarkContentEnabled()
                + " smallFolderEnabled=" + isSmallFolderEnabled()
                + " largeFolderEnabled=" + isLargeFolderEnabled());

        if (transition.fullTeardown) {
            runOnMain(() -> {
                MiuixFolderGlassHook.onRuntimeGlassDisabled();
                MiuixLauncherStaticGlassHook.onRuntimeGlassDisabled();
                DockGlassItemRegistry.clear();
                LauncherGlassDragOverlay.releaseAll();
                LauncherGlassSessionRegistry.shutdownAll();
                Miuix307MaterialPipeline.onRuntimeGlassDisabled();
                MiuixGlassHook.onRuntimeGlassDisabled();
                // Teardown cleared the GlassHost binding; native BlurBackground2 can own
                // the edge again, so restore its remembered stroke immediately.
                DockStrokeRenderer.refreshInstalledFromCurrentConfig();
                MainHook.log("[DC][GlassRuntime] GPU glass teardown complete");
            });
            return;
        }

        if (transition.iconRelease) {
            runOnMain(() -> {
                MiuixLauncherStaticGlassHook.onRuntimeIconGlassDisabled();
                MiuixLauncherDragOverlayHook.onRuntimeIconGlassDisabled();
                MainHook.log("[DC][GlassRuntime] icon glass ownership released");
            });
        }
        if (transition.widgetRelease) {
            runOnMain(() -> {
                MiuixLauncherStaticGlassHook.onRuntimeWidgetGlassDisabled();
                MainHook.log("[DC][GlassRuntime] widget glass ownership released");
            });
        }
        if (transition.widgetDarkContentChanged) {
            runOnMain(() -> {
                MiuixLauncherStaticGlassHook.onRuntimeWidgetDarkContentChanged(
                        transition.nextWidgetDarkContent);
                MainHook.log("[DC][GlassRuntime] widget dark-content="
                        + transition.nextWidgetDarkContent);
            });
        }
        if (transition.smallFolderRelease) {
            runOnMain(() -> {
                MiuixFolderGlassHook.onRuntimeSmallFolderGlassDisabled();
                MainHook.log("[DC][GlassRuntime] small-folder glass ownership released");
            });
        }
        if (transition.largeFolderRelease) {
            runOnMain(() -> {
                MiuixFolderGlassHook.onRuntimeLargeFolderGlassDisabled();
                MainHook.log("[DC][GlassRuntime] large-folder glass ownership released");
            });
        }
    }

    private static void runOnMain(Runnable action) {
        Looper main = Looper.getMainLooper();
        if (Looper.myLooper() == main) action.run();
        else new Handler(main).post(action);
    }
}
