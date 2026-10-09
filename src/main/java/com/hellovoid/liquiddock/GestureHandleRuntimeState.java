package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.hellovoid.liquiddock.config.ConfigSchema;

/** Process-local live gate for the HOME/RECENTS system gesture-handle fade. */
final class GestureHandleRuntimeState {
    interface Listener {
        void onEnabledChanged(boolean enabled);
    }

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Api102ListenerEpoch EPOCH = new Api102ListenerEpoch();

    private static volatile boolean coreEnabled;
    private static volatile boolean featureEnabled;
    private static volatile Listener stateListener;
    private static SharedPreferences preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private static volatile long activeGeneration;

    private GestureHandleRuntimeState() {}

    static synchronized void initialize(SharedPreferences nextPreferences) {
        EPOCH.invalidate(); // disarm callbacks from an earlier preference registration
        if (preferences != null && preferenceListener != null) {
            try {
                preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
            } catch (Throwable ignored) {}
        }

        preferences = nextPreferences;
        preferenceListener = null;
        coreEnabled = nextPreferences == null
                ? ConfigSchema.Core.ENABLED.runtimeFallback()
                : nextPreferences.getBoolean(
                        ConfigSchema.Core.ENABLED.name(),
                        ConfigSchema.Core.ENABLED.runtimeFallback());
        featureEnabled = nextPreferences == null
                ? ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS.runtimeFallback()
                : nextPreferences.getBoolean(
                        ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS.name(),
                        ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS.runtimeFallback());

        final long generation = EPOCH.activate();
        activeGeneration = generation;
        if (nextPreferences == null) return;
        preferenceListener = (sharedPreferences, key) -> {
            if (!EPOCH.isCurrent(generation)) return;
            if (!ConfigSchema.Core.ENABLED.name().equals(key)
                    && !ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS.name().equals(key)) {
                return;
            }
            apply(
                    sharedPreferences.getBoolean(
                            ConfigSchema.Core.ENABLED.name(),
                            ConfigSchema.Core.ENABLED.runtimeFallback()),
                    sharedPreferences.getBoolean(
                            ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS.name(),
                            ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS.runtimeFallback()));
        };
        nextPreferences.registerOnSharedPreferenceChangeListener(preferenceListener);
    }

    /**
     * Detach the SystemUI-only preference observer before a future owner handoff.
     * A failed unregister retains the old references and returns false, but callback generations
     * are invalidated immediately to prevent stale preference or Handler events.
     */
    static synchronized boolean shutdownForFutureReload() {
        EPOCH.invalidate();
        stateListener = null;
        coreEnabled = false;
        featureEnabled = false;
        activeGeneration = 0L;
        if (preferences != null && preferenceListener != null) {
            try {
                preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
            } catch (Throwable error) {
                Api101Bridge.log("[DC][API102] gesture preference unregister failed", error);
                return false;
            }
        }
        preferences = null;
        preferenceListener = null;
        return true;
    }

    static boolean isEnabled() {
        return coreEnabled && featureEnabled;
    }

    static synchronized void setListener(Listener listener) {
        stateListener = listener;
        if (listener != null) notifyListener(listener, isEnabled());
    }

    private static synchronized void apply(boolean nextCoreEnabled, boolean nextFeatureEnabled) {
        boolean before = isEnabled();
        coreEnabled = nextCoreEnabled;
        featureEnabled = nextFeatureEnabled;
        boolean after = isEnabled();
        if (before == after) return;
        Listener listener = stateListener;
        if (listener != null) notifyListener(listener, after);
    }

    private static void notifyListener(Listener listener, boolean enabled) {
        // Posted notifications can outlive the owner and even the old module classloader.
        final long generation = activeGeneration;
        MAIN_HANDLER.post(() -> {
            // Hold the same lifecycle monitor as shutdown/initialize until invocation returns.
            // The stale callback cannot cross a completed shutdown even if it was already dequeued.
            synchronized (GestureHandleRuntimeState.class) {
                if (EPOCH.isCurrent(generation) && stateListener == listener) {
                    listener.onEnabledChanged(enabled);
                }
            }
        });
    }
}
