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
    private static boolean retiredForReload;
    private static final Runnable DISPATCH_TASK = ExternalGlassLiveConfigState::dispatch;

    private ExternalGlassLiveConfigState() {}

    static synchronized void initialize(String targetPackage) {
        if (retiredForReload) {
            throw new IllegalStateException("retired config observer cannot reinitialize");
        }
        if (remote != null && listener != null) {
            // Do not drop a registered listener on failure; that would leak the old loader.
            remote.unregisterOnSharedPreferenceChangeListener(listener);
        }
        remote = Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP);
        packageName = targetPackage;
        handler = new Handler(Looper.getMainLooper());
        PENDING.set(false);
        listener = (preferences, key) -> {
            if (!isRelevantKey(key)) return;
            synchronized (ExternalGlassLiveConfigState.class) {
                if (retiredForReload) return;
                Handler main = handler;
                if (main != null && PENDING.compareAndSet(false, true)
                        && !main.post(DISPATCH_TASK)) {
                    PENDING.set(false);
                }
            }
        };
        if (remote != null) remote.registerOnSharedPreferenceChangeListener(listener);
    }

    /**
     * Disarm Binder listeners and remove queued main-thread callbacks before the old
     * SystemUI module loader can be released. Never claim cleanup when unregister fails.
     */
    static synchronized boolean shutdownForFutureReload() {
        retiredForReload = true;
        if (handler != null) {
            try {
                handler.removeCallbacks(DISPATCH_TASK);
            } catch (Throwable error) {
                Api101Bridge.log("[DC][API102] external config queue cleanup failed", error);
                return false;
            }
        }
        if (remote != null && listener != null) {
            try {
                remote.unregisterOnSharedPreferenceChangeListener(listener);
            } catch (Throwable error) {
                Api101Bridge.log("[DC][API102] external config unregister failed", error);
                return false;
            }
        }
        remote = null;
        listener = null;
        handler = null;
        packageName = null;
        PENDING.set(false);
        return true;
    }

    static boolean isRelevantKey(String key) {
        return key == null
                || key.startsWith("liquid_")
                || key.startsWith("third_party_glass.")
                || key.startsWith("animation_")
                || key.startsWith("launcher_surface_component_")
                || key.startsWith("launcher_large_surface_component_")
                || key.equals("liquiddock_enabled");
    }

    private static synchronized void dispatch() {
        PENDING.set(false);
        if (retiredForReload) return;
        String target = packageName;
        if (target == null) return;
        try {
            ConfigReader reader = ConfigReader.load();
            GlassEdgeEffects.refresh(reader);
            LiquidDockConfig config = LiquidDockConfig.from(reader);
            if ("com.google.android.inputmethod.latin".equals(target)) {
                GboardFloatingGlassCoordinator.onLiveConfigChanged(reader, config);
            } else if ("com.android.quicksearchbox".equals(target)) {
                MiuiSearchboxGlassHook.onLiveConfigChanged(reader, config);
            } else if (SecurityCenterProcessPolicy.PACKAGE.equals(target)) {
                SecurityCenterGlassHook.onLiveGlassConfigChanged(reader, config);
                AnimationRuntimeState.configure(config.animation);
            } else if ("com.android.systemui".equals(target)) {
                SystemUiHandleMenuGlassHook.onLiveGlassConfigChanged(reader, config);
            }
        } catch (Throwable error) {
            Api101Bridge.log("[DC][ExternalLiveConfig] update failed target="
                    + target, error);
        }
    }
}
