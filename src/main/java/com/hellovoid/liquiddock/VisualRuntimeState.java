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
    private static volatile boolean hideMirrorShortcut;

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
        hideMirrorShortcut = nextPrefs != null
                && nextPrefs.getBoolean(ConfigSchema.Dock.HIDE_MIRROR_SHORTCUT.name(),
                        ConfigSchema.Dock.HIDE_MIRROR_SHORTCUT.runtimeFallback());
        if (nextPrefs == null) return;

        listener = (sharedPreferences, key) -> {
            boolean strokeStyleChanged = ConfigSchema.Dock.SQUIRCLE.name().equals(key)
                    || ConfigSchema.Dock.FILL_DIFF.name().equals(key);
            boolean dockShadowStyleChanged = ConfigSchema.Dock.SHADOW_RADIUS.name().equals(key)
                    || ConfigSchema.Dock.SHADOW_SIZE.name().equals(key)
                    || ConfigSchema.Dock.SHADOW_ALPHA.name().equals(key)
                    || ConfigSchema.Dock.SHADOW_Y.name().equals(key);
            boolean strokeShadowStyleChanged =
                    ConfigSchema.Dock.STROKE_SHADOW_RADIUS.name().equals(key)
                    || ConfigSchema.Dock.STROKE_SHADOW_ALPHA.name().equals(key);
            if (!ConfigSchema.Core.ENABLED.name().equals(key)
                    && !ConfigSchema.Dock.ENABLED.name().equals(key)
                    && !ConfigSchema.Dock.STROKE_ENABLED.name().equals(key)
                    && !ConfigSchema.Dock.SHADOW_ENABLED.name().equals(key)
                    && !ConfigSchema.Dock.STROKE_SHADOW.name().equals(key)
                    && !ConfigSchema.Divider.ENABLED.name().equals(key)
                    && !ConfigSchema.Dock.HIDE_MIRROR_SHORTCUT.name().equals(key)
                    && !strokeStyleChanged
                    && !dockShadowStyleChanged
                    && !strokeShadowStyleChanged) return;

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
            boolean nextHideMirrorShortcut = sharedPreferences.getBoolean(
                    ConfigSchema.Dock.HIDE_MIRROR_SHORTCUT.name(), hideMirrorShortcut);
            apply(nextCoreEnabled, nextDockCustomizationEnabled, nextDockStrokeEnabled,
                    nextDockShadowEnabled, nextStrokeShadowEnabled, nextDividerEnabled,
                    nextHideMirrorShortcut);

            if (strokeStyleChanged || strokeShadowStyleChanged) {
                runOnMain(() -> DockStrokeRenderer.refreshInstalledFromCurrentConfig());
            }
            if (dockShadowStyleChanged || strokeShadowStyleChanged) {
                runOnMain(() -> {
                    DockNativeShadowBridge.refreshConfig();
                    MainHook.onRuntimeDockShadowEnabled();
                });
            }
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

    static boolean isMirrorShortcutHidden() {
        return coreEnabled && hideMirrorShortcut;
    }

    private static VisualRuntimeTransitionPolicy.Snapshot snapshot() {
        return new VisualRuntimeTransitionPolicy.Snapshot(
                isDockCustomizationEnabled(),
                isDockStrokeEnabled(),
                isDockShadowEnabled(),
                isStrokeShadowEnabled(),
                isDividerEnabled(),
                isMirrorShortcutHidden());
    }

    private static void apply(
            boolean nextCoreEnabled,
            boolean nextDockCustomizationEnabled,
            boolean nextDockStrokeEnabled,
            boolean nextDockShadowEnabled,
            boolean nextStrokeShadowEnabled,
            boolean nextDividerEnabled,
            boolean nextHideMirrorShortcut) {
        if (coreEnabled == nextCoreEnabled
                && dockCustomizationEnabled == nextDockCustomizationEnabled
                && dockStrokeEnabled == nextDockStrokeEnabled
                && dockShadowEnabled == nextDockShadowEnabled
                && strokeShadowEnabled == nextStrokeShadowEnabled
                && dividerEnabled == nextDividerEnabled
                && hideMirrorShortcut == nextHideMirrorShortcut) return;

        VisualRuntimeTransitionPolicy.Snapshot before = snapshot();

        // Publish the new booleans before scheduling teardown/reapply. Any callback that was
        // queued before the preference change must observe the new effective value immediately.
        coreEnabled = nextCoreEnabled;
        dockCustomizationEnabled = nextDockCustomizationEnabled;
        dockStrokeEnabled = nextDockStrokeEnabled;
        dockShadowEnabled = nextDockShadowEnabled;
        strokeShadowEnabled = nextStrokeShadowEnabled;
        dividerEnabled = nextDividerEnabled;
        hideMirrorShortcut = nextHideMirrorShortcut;

        VisualRuntimeTransitionPolicy.Transition transition =
                VisualRuntimeTransitionPolicy.plan(before, snapshot());
        logState("updated");

        if (transition.dockCustomizationDisabled) {
            runOnMain(() -> MainHook.onRuntimeDockCustomizationDisabled());
        }
        if (transition.strokeDisabled) {
            runOnMain(() -> DockStrokeRenderer.onRuntimeStrokeDisabled());
        }
        if (transition.strokeEnabled) {
            runOnMain(() -> DockStrokeRenderer.refreshInstalledFromCurrentConfig());
        }
        if (transition.dockShadowDisabled) {
            runOnMain(() -> {
                DockNativeShadowBridge.refreshConfig();
                MainHook.onRuntimeDockShadowDisabled();
            });
        }
        if (transition.dockShadowEnabled) {
            runOnMain(() -> {
                DockNativeShadowBridge.refreshConfig();
                MainHook.onRuntimeDockShadowEnabled();
            });
        }
        if (transition.strokeShadowChanged) {
            runOnMain(() -> {
                DockStrokeRenderer.refreshInstalledFromCurrentConfig();
                DockNativeShadowBridge.refreshConfig();
                MainHook.onRuntimeDockShadowEnabled();
            });
        }
        if (transition.dividerDisabled) {
            runOnMain(() -> DockDividerHook.onRuntimeDividerDisabled());
        }
        if (transition.mirrorVisibilityChanged) {
            runOnMain(() -> DockMirrorShortcutHook.onRuntimeVisibilityChanged());
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
                + " divider=" + isDividerEnabled()
                + " hideMirror=" + isMirrorShortcutHidden());
    }
}
