package com.hellovoid.liquiddock;

/**
 * Launcher process composition root.
 *
 * <p>Feature-specific state and hook implementation live in their typed owners. MainHook only
 * loads runtime configuration, preserves the established installation order and exposes the
 * process-wide logging / temporary workstation compatibility facade.</p>
 */
public class MainHook {
    public void install(ClassLoader classLoader) {
        // Sequence guard makes future hook-order drift fail fast in tests and debug builds.
        LauncherInstallSequence order = new LauncherInstallSequence();

        // Preserve the historical order: Workstation capability detection is installed before the
        // master preference read because vendor mode callbacks may arrive during Launcher startup.
        WorkstationModeHook.install(classLoader);
        order.advance(LauncherInstallSequence.Stage.WORKSTATION_RUNTIME);

        LiquidDockConfig config = LiquidDockConfig.load();
        order.advance(LauncherInstallSequence.Stage.CONFIG_LOADED);
        WidgetGridSizing.setWidgetAdaptationEnabled(
                WidgetGridSizing.shouldAdaptWidgets(
                        config.grid.enabled, config.grid.widgetAdaptation));
        debugLogging = config.debugLog;
        log("[DC] LiquidDock " + (debugLogging ? "debug logging ON" : "loaded"));
        if (!config.enabled) {
            log("[DC] LiquidDock master switch disabled");
            order.finishAfterConfigWhenDisabled();
            return;
        }

        float density =
                android.content.res.Resources.getSystem().getDisplayMetrics().density;

        // Workstation / Dock foundation.
        DockStrokeRenderer.installNativeHook(classLoader, config.dock);
        WorkstationDockCustomizationHook.install(classLoader, config.workstation);
        WorkstationDockGeometryHook.install(classLoader, config.workstation);
        if (!config.dock.resizeAnimation) {
            DockResizeAnimationHook.install(
                    classLoader,
                    config.dock.smoothResizeAnimation,
                    config.animation.dockResizeMs);
        }
        if (WorkstationRuntimeState.isActive()) {
            log("[DC] workstation active; using isolated workstation parameters");
        }
        order.advance(LauncherInstallSequence.Stage.DOCK_FOUNDATION);

        // Grid feature.
        DockDividerHook.install(classLoader);
        LauncherGridInstallConfig grid = LauncherGridInstallConfig.from(config, density);
        HomeGridHook.install(classLoader, grid.home);
        HomeGridHook.setWorkstationGeometryConfig(grid.workstation);
        order.advance(LauncherInstallSequence.Stage.GRID);

        // Vendor Dock presentation ownership must exist before glass/fallback customization.
        DockShadowOwnership.install(classLoader, config.dock);
        order.advance(LauncherInstallSequence.Stage.DOCK_SHADOW);

        // Keep the existing zero-copy early return exactly where it was: when this pipeline owns
        // the Dock, legacy/fallback customization hooks remain uninstalled.
        boolean zeroCopyOwned = false;
        if (config.glass.enabled) {
            zeroCopyOwned = Miuix307MaterialPipeline.install(classLoader, config);
            if (zeroCopyOwned) {
                log("[DC] MiuiX 307 zero-copy material active");
            } else {
                log("[DC] MiuiX 307 zero-copy material unavailable; liquid glass disabled");
            }
        }
        order.advance(LauncherInstallSequence.Stage.GLASS_DECISION);
        if (zeroCopyOwned) {
            order.finishAtGlassOwner();
            return;
        }

        if (!config.dock.enabled) {
            log("[DC] Dock customization disabled; legacy hooks installed inertly");
        }
        DockCustomizationHook.install(
                classLoader, DockInstallConfig.from(config.dock, density));
        order.advance(LauncherInstallSequence.Stage.FALLBACK_DOCK);
        order.finishFallback();
    }

    /**
     * Short-term compatibility facade while domain callers migrate independently.
     * MainHook no longer stores or mutates Workstation state.
     */
    static boolean isWorkstationMode() {
        return WorkstationRuntimeState.isActive();
    }

    // ── process logging ─────────────────────────────────────────────

    static boolean debugLogging;

    static void log(String message) {
        if (!debugLogging) return;
        Api101Bridge.log(message);
        fileLog(message);
    }

    private static void fileLog(String message) {
        try {
            String line = new java.text.SimpleDateFormat(
                    "HH:mm:ss.SSS", java.util.Locale.ROOT)
                    .format(new java.util.Date()) + " " + message + "\n";
            java.io.File dir = new java.io.File("/sdcard/Download");
            if (!dir.canWrite()) dir = new java.io.File("/data/local/tmp");
            java.io.FileOutputStream out = new java.io.FileOutputStream(
                    new java.io.File(dir, "liquiddock.log"), true);
            out.write(line.getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {}
    }
}
