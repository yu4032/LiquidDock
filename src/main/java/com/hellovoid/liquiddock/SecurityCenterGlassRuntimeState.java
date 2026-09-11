package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.hellovoid.liquiddock.config.ConfigSchema;

/** Process-local Security Center glass state that releases ownership when it becomes inactive. */
final class SecurityCenterGlassRuntimeState {
    interface Owner {
        void releaseAll();
    }

    private static volatile boolean coreEnabled;
    private static volatile boolean glassEnabled;
    private static volatile boolean securityCenterEnabled;
    private static volatile Owner owner;
    private static SharedPreferences preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;

    private SecurityCenterGlassRuntimeState() {}

    static synchronized void initialize(
            SharedPreferences nextPreferences,
            boolean initialCoreEnabled,
            boolean initialGlassEnabled,
            boolean initialSecurityCenterEnabled) {
        if (preferences != null && listener != null) {
            try {
                preferences.unregisterOnSharedPreferenceChangeListener(listener);
            } catch (Throwable ignored) {
                // Best-effort cleanup for a stale process-local preference listener.
            }
        }

        preferences = nextPreferences;
        listener = null;
        coreEnabled = initialCoreEnabled;
        glassEnabled = initialGlassEnabled;
        securityCenterEnabled = initialSecurityCenterEnabled;
        if (nextPreferences == null) return;

        listener = (sharedPreferences, key) -> {
            if (!ConfigSchema.Core.ENABLED.name().equals(key)
                    && !ConfigSchema.Glass.ENABLED.name().equals(key)
                    && !ConfigSchema.Glass.SECURITY_CENTER_GLASS.name().equals(key)) {
                return;
            }
            apply(
                    sharedPreferences.getBoolean(ConfigSchema.Core.ENABLED.name(),
                            ConfigSchema.Core.ENABLED.runtimeFallback()),
                    sharedPreferences.getBoolean(ConfigSchema.Glass.ENABLED.name(),
                            ConfigSchema.Glass.ENABLED.runtimeFallback()),
                    sharedPreferences.getBoolean(ConfigSchema.Glass.SECURITY_CENTER_GLASS.name(),
                            ConfigSchema.Glass.SECURITY_CENTER_GLASS.runtimeFallback()));
        };
        nextPreferences.registerOnSharedPreferenceChangeListener(listener);
    }

    static void setOwner(Owner nextOwner) {
        owner = nextOwner;
    }

    static boolean isEnabled() {
        return coreEnabled && glassEnabled && securityCenterEnabled;
    }

    static void bindAssistant(View turbo, View dock, View box, int type) {
        Owner currentOwner = owner;
        if (currentOwner instanceof SecurityCenterGlassCoordinator) {
            ((SecurityCenterGlassCoordinator) currentOwner).bindAssistant(turbo, dock, box, type);
        }
    }

    private static synchronized void apply(
            boolean nextCoreEnabled,
            boolean nextGlassEnabled,
            boolean nextSecurityCenterEnabled) {
        SecurityCenterGlassRuntimeTransitionPolicy.Snapshot before = snapshot();
        SecurityCenterGlassRuntimeTransitionPolicy.Snapshot after =
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(
                        nextCoreEnabled, nextGlassEnabled, nextSecurityCenterEnabled);

        coreEnabled = nextCoreEnabled;
        glassEnabled = nextGlassEnabled;
        securityCenterEnabled = nextSecurityCenterEnabled;

        if (SecurityCenterGlassRuntimeTransitionPolicy.plan(before, after).releaseAll) {
            runOnMain(() -> {
                Owner currentOwner = owner;
                if (currentOwner != null && !isEnabled()) currentOwner.releaseAll();
            });
        }
    }

    private static SecurityCenterGlassRuntimeTransitionPolicy.Snapshot snapshot() {
        return new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(
                coreEnabled, glassEnabled, securityCenterEnabled);
    }

    private static void runOnMain(Runnable action) {
        Looper main = Looper.getMainLooper();
        if (Looper.myLooper() == main) action.run();
        else new Handler(main).post(action);
    }
}
