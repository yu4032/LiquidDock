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
    private static volatile boolean dockFrameSyncEnabled;

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
        dockFrameSyncEnabled = nextPrefs == null
                ? ConfigSchema.Dock.FRAME_SYNC.runtimeFallback()
                : nextPrefs.getBoolean(ConfigSchema.Dock.FRAME_SYNC.name(),
                        ConfigSchema.Dock.FRAME_SYNC.runtimeFallback());
        if (nextPrefs == null) return;

        listener = (sharedPreferences, key) -> {
            boolean strokeStyleChanged = key == null || matchesOptionChange(key, ConfigSchema.Dock.SQUIRCLE)
                    || matchesOptionChange(key, ConfigSchema.Dock.FILL_DIFF)
                    || matchesOptionChange(key, ConfigSchema.Dock.SQUIRCLE_CONTROL_POINT)
                    || matchesOptionChange(key, ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH)
                    || matchesOptionChange(key, ConfigSchema.Dock.SQUIRCLE_STROKE_OFFSET)
                    || matchesOptionChange(key, ConfigSchema.Dock.FILL_DIFF_STROKE_WIDTH)
                    || matchesOptionChange(key, ConfigSchema.Dock.STANDARD_STROKE_WIDTH)
                    || matchesOptionChange(key, ConfigSchema.Dock.STROKE_RED)
                    || matchesOptionChange(key, ConfigSchema.Dock.STROKE_GREEN)
                    || matchesOptionChange(key, ConfigSchema.Dock.STROKE_BLUE)
                    || matchesOptionChange(key, ConfigSchema.Dock.STROKE_ALPHA);
            boolean dockShadowStyleChanged = key == null || matchesOptionChange(key, ConfigSchema.Dock.SHADOW_RADIUS)
                    || matchesOptionChange(key, ConfigSchema.Dock.SHADOW_SIZE)
                    || matchesOptionChange(key, ConfigSchema.Dock.SHADOW_ALPHA)
                    || matchesOptionChange(key, ConfigSchema.Dock.SHADOW_Y);
            boolean strokeShadowStyleChanged = key == null
                    || matchesOptionChange(key, ConfigSchema.Dock.STROKE_SHADOW_RADIUS)
                    || matchesOptionChange(key, ConfigSchema.Dock.STROKE_SHADOW_ALPHA);
            if (key != null && !ConfigSchema.Core.ENABLED.name().equals(key)
                    && !ConfigSchema.Dock.ENABLED.name().equals(key)
                    && !ConfigSchema.Dock.STROKE_ENABLED.name().equals(key)
                    && !ConfigSchema.Dock.SHADOW_ENABLED.name().equals(key)
                    && !ConfigSchema.Dock.STROKE_SHADOW.name().equals(key)
                    && !ConfigSchema.Divider.ENABLED.name().equals(key)
                    && !ConfigSchema.Dock.HIDE_MIRROR_SHORTCUT.name().equals(key)
                    && !ConfigSchema.Dock.FRAME_SYNC.name().equals(key)
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
            boolean nextDockFrameSyncEnabled = sharedPreferences.getBoolean(
                    ConfigSchema.Dock.FRAME_SYNC.name(),
                    ConfigSchema.Dock.FRAME_SYNC.runtimeFallback());
            apply(nextCoreEnabled, nextDockCustomizationEnabled, nextDockStrokeEnabled,
                    nextDockShadowEnabled, nextStrokeShadowEnabled, nextDividerEnabled,
                    nextHideMirrorShortcut, nextDockFrameSyncEnabled);

            if (strokeStyleChanged || strokeShadowStyleChanged) {
                runOnMain(() -> DockStrokeRenderer.refreshInstalledFromCurrentConfig());
            }
            if (dockShadowStyleChanged || strokeShadowStyleChanged) {
                runOnMain(() -> {
                    DockNativeShadowBridge.refreshConfig();
                    DockShadowOwnership.onRuntimeDockShadowEnabled();
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

    static boolean isDockFrameSyncEnabled() {
        return coreEnabled && dockFrameSyncEnabled;
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
            boolean nextHideMirrorShortcut,
            boolean nextDockFrameSyncEnabled) {
        if (coreEnabled == nextCoreEnabled
                && dockCustomizationEnabled == nextDockCustomizationEnabled
                && dockStrokeEnabled == nextDockStrokeEnabled
                && dockShadowEnabled == nextDockShadowEnabled
                && strokeShadowEnabled == nextStrokeShadowEnabled
                && dividerEnabled == nextDividerEnabled
                && hideMirrorShortcut == nextHideMirrorShortcut
                && dockFrameSyncEnabled == nextDockFrameSyncEnabled) return;

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
        dockFrameSyncEnabled = nextDockFrameSyncEnabled;

        VisualRuntimeTransitionPolicy.Transition transition =
                VisualRuntimeTransitionPolicy.plan(before, snapshot());
        logState("updated");

        if (transition.dockCustomizationDisabled) {
            runOnMain(() -> DockShadowOwnership.onRuntimeDockCustomizationDisabled());
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
                DockShadowOwnership.onRuntimeDockShadowDisabled();
            });
        }
        if (transition.dockShadowEnabled) {
            runOnMain(() -> {
                DockNativeShadowBridge.refreshConfig();
                DockShadowOwnership.onRuntimeDockShadowEnabled();
            });
        }
        if (transition.strokeShadowChanged) {
            runOnMain(() -> {
                DockStrokeRenderer.refreshInstalledFromCurrentConfig();
                DockNativeShadowBridge.refreshConfig();
                DockShadowOwnership.onRuntimeDockShadowEnabled();
            });
        }
        if (transition.dividerDisabled) {
            runOnMain(() -> DockDividerHook.onRuntimeDividerDisabled());
        }
        if (transition.mirrorVisibilityChanged) {
            runOnMain(() -> DockMirrorShortcutHook.onRuntimeVisibilityChanged());
        }
    }

    /** Compose's DP_TENTHS storage updates the sidecar rather than the primary key. */
    static boolean matchesOptionChange(String changedKey,
            com.hellovoid.liquiddock.config.ConfigKey<?> option) {
        return changedKey != null && (option.name().equals(changedKey)
                || (option.name() + "_tenths").equals(changedKey));
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
