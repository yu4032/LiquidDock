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

    private static volatile boolean coreEnabled;
    private static volatile boolean featureEnabled;
    private static volatile int fadeOutDurationMs;
    private static volatile Listener stateListener;
    private static SharedPreferences preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;

    private GestureHandleRuntimeState() {}

    static synchronized void initialize(SharedPreferences nextPreferences) {
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
        fadeOutDurationMs = nextPreferences == null
                ? ConfigSchema.Animation.GESTURE_HANDLE_FADE_OUT.runtimeFallback()
                : clampFadeDuration(nextPreferences.getInt(
                        ConfigSchema.Animation.GESTURE_HANDLE_FADE_OUT.name(),
                        ConfigSchema.Animation.GESTURE_HANDLE_FADE_OUT.runtimeFallback()));

        if (nextPreferences == null) return;
        preferenceListener = (sharedPreferences, key) -> {
            if (!ConfigSchema.Core.ENABLED.name().equals(key)
                    && !ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS.name().equals(key)
                    && !ConfigSchema.Animation.GESTURE_HANDLE_FADE_OUT.name().equals(key)) {
                return;
            }
            apply(
                    sharedPreferences.getBoolean(
                            ConfigSchema.Core.ENABLED.name(),
                            ConfigSchema.Core.ENABLED.runtimeFallback()),
                    sharedPreferences.getBoolean(
                            ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS.name(),
                            ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS.runtimeFallback()),
                    sharedPreferences.getInt(
                            ConfigSchema.Animation.GESTURE_HANDLE_FADE_OUT.name(),
                            ConfigSchema.Animation.GESTURE_HANDLE_FADE_OUT.runtimeFallback()));
        };
        nextPreferences.registerOnSharedPreferenceChangeListener(preferenceListener);
    }

    static boolean isEnabled() {
        return coreEnabled && featureEnabled;
    }

    static int fadeOutDurationMs() {
        return fadeOutDurationMs;
    }

    static synchronized void setListener(Listener listener) {
        stateListener = listener;
        if (listener != null) notifyListener(listener, isEnabled());
    }

    private static synchronized void apply(
            boolean nextCoreEnabled, boolean nextFeatureEnabled, int nextFadeOutDurationMs) {
        boolean before = isEnabled();
        coreEnabled = nextCoreEnabled;
        featureEnabled = nextFeatureEnabled;
        fadeOutDurationMs = clampFadeDuration(nextFadeOutDurationMs);
        boolean after = isEnabled();
        if (before == after) return;
        Listener listener = stateListener;
        if (listener != null) notifyListener(listener, after);
    }

    private static int clampFadeDuration(int durationMs) {
        Integer min = ConfigSchema.Animation.GESTURE_HANDLE_FADE_OUT.minInt();
        Integer max = ConfigSchema.Animation.GESTURE_HANDLE_FADE_OUT.maxInt();
        int lower = min == null ? 0 : min;
        int upper = max == null ? Integer.MAX_VALUE : max;
        return Math.max(lower, Math.min(upper, durationMs));
    }

    private static void notifyListener(Listener listener, boolean enabled) {
        MAIN_HANDLER.post(() -> {
            if (stateListener == listener) listener.onEnabledChanged(enabled);
        });
    }
}
