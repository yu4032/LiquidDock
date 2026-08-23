package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.hellovoid.liquiddock.config.ConfigSchema;

/** Process-local glass kill switch. Preference changes tear down GPU resources immediately. */
final class GlassRuntimeState {
    private static volatile boolean enabled;
    private static SharedPreferences prefs;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private GlassRuntimeState() {}

    static synchronized void initialize(SharedPreferences nextPrefs, boolean initialEnabled) {
        if (prefs != null && listener != null) {
            try { prefs.unregisterOnSharedPreferenceChangeListener(listener); } catch (Throwable ignored) {}
        }
        prefs = nextPrefs;
        enabled = initialEnabled;
        if (nextPrefs == null) return;
        listener = (sharedPreferences, key) -> {
            if (!ConfigSchema.Glass.ENABLED.name().equals(key)
                    && !ConfigSchema.Core.ENABLED.name().equals(key)) return;
            boolean next = sharedPreferences.getBoolean(ConfigSchema.Core.ENABLED.name(),
                    ConfigSchema.Core.ENABLED.runtimeFallback())
                    && sharedPreferences.getBoolean(ConfigSchema.Glass.ENABLED.name(),
                    ConfigSchema.Glass.ENABLED.runtimeFallback());
            apply(next);
        };
        nextPrefs.registerOnSharedPreferenceChangeListener(listener);
        MainHook.log("[DC][GlassRuntime] initialized enabled=" + enabled);
    }

    static boolean isEnabled() { return enabled; }

    private static void apply(boolean next) {
        if (enabled == next) return;
        enabled = next;
        MainHook.log("[DC][GlassRuntime] enabled=" + next);
        if (next) return;
        Runnable teardown = () -> {
            MiuixFolderGlassHook.onRuntimeGlassDisabled();
            MiuixLauncherStaticGlassHook.onRuntimeGlassDisabled();
            DockIconLaunchProxyBridge.clear();
            DockGlassItemRegistry.clear();
            LauncherGlassDragOverlay.releaseAll();
            LauncherGlassSessionRegistry.shutdownAll();
            Miuix307MaterialPipeline.onRuntimeGlassDisabled();
            MiuixGlassHook.onRuntimeGlassDisabled();
            MainHook.log("[DC][GlassRuntime] GPU glass teardown complete");
        };
        Looper main = Looper.getMainLooper();
        if (Looper.myLooper() == main) teardown.run();
        else new Handler(main).post(teardown);
    }
}
