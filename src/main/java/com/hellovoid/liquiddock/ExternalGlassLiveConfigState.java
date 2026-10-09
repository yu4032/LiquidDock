package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Remote-preference notification for independent app scopes. Launcher has its own frame-
 * coalesced owner; these callbacks must run on each app's main thread, not on the Binder
 * notification thread. No hook installation or EGL ownership changes occur here.
 */
final class ExternalGlassLiveConfigState {
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static SharedPreferences remote;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private static Handler handler;
    private static String packageName;

    private ExternalGlassLiveConfigState() {}

    static synchronized void initialize(String targetPackage) {
        if (remote != null && listener != null) {
            remote.unregisterOnSharedPreferenceChangeListener(listener);
        }
        remote = Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP);
        packageName = targetPackage;
        handler = new Handler(Looper.getMainLooper());
        PENDING.set(false);
        listener = (preferences, key) -> {
            if (!isRelevantKey(key)) return;
            Handler main = handler;
            if (main != null && PENDING.compareAndSet(false, true)) {
                main.post(ExternalGlassLiveConfigState::dispatch);
            }
        };
        if (remote != null) remote.registerOnSharedPreferenceChangeListener(listener);
    }

    static boolean isRelevantKey(String key) {
        return key == null
                || key.startsWith("liquid_")
                || key.startsWith("launcher_surface_component_")
                || key.startsWith("launcher_large_surface_component_")
                || key.equals("liquiddock_enabled");
    }

    private static void dispatch() {
        PENDING.set(false);
        String target = packageName;
        if (target == null) return;
        try {
            ConfigReader reader = ConfigReader.load();
            LiquidDockConfig config = LiquidDockConfig.from(reader);
            if ("com.google.android.inputmethod.latin".equals(target)) {
                GboardFloatingGlassCoordinator.onLiveConfigChanged(reader, config);
            } else if ("com.android.quicksearchbox".equals(target)) {
                MiuiSearchboxGlassHook.onLiveConfigChanged(reader, config);
            } else if (SecurityCenterProcessPolicy.PACKAGE.equals(target)) {
                SecurityCenterGlassHook.onLiveGlassConfigChanged(config);
                AnimationRuntimeState.configure(config.animation);
            }
        } catch (Throwable error) {
            Api101Bridge.log("[DC][ExternalLiveConfig] update failed target="
                    + target, error);
        }
    }
}
