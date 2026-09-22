package com.hellovoid.liquiddock;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

/** Detects Launcher Workstation mode and publishes it to WorkstationRuntimeState. */
final class WorkstationModeHook {
    private static volatile boolean modeHookConfirmed;

    private WorkstationModeHook() {}

    static void install(ClassLoader classLoader) {
        boolean detected = false;
        try {
            Class<?> modeController = Class.forName(
                    "com.miui.home.launcher.allapps.LauncherModeController",
                    false,
                    classLoader);
            Class<?> resolvedDeviceConfig;
            try {
                resolvedDeviceConfig = Class.forName(
                        "com.miui.home.launcher.DeviceConfig", false, classLoader);
            } catch (Throwable ignored) {
                resolvedDeviceConfig = null;
            }
            final Class<?> deviceConfig = resolvedDeviceConfig;
            HookUtil.InvocationResult<Object> laptopProbe =
                    HookUtil.tryInvokeStatic(modeController, "isLaptopMode");
            Object laptopResult = laptopProbe.succeeded() ? laptopProbe.value() : null;
            if (laptopResult instanceof Boolean) {
                WorkstationRuntimeState.publish((Boolean) laptopResult);
            } else {
                HookUtil.InvocationResult<Object> fallback = deviceConfig == null
                        ? null
                        : HookUtil.tryInvokeStatic(
                                deviceConfig, "isMingouLaptopPcModeEnabled");
                Object value = fallback != null && fallback.succeeded() ? fallback.value() : null;
                WorkstationRuntimeState.publish(value instanceof Boolean && (Boolean) value);
            }

            Class<?> stateManager = Class.forName(
                    "com.miui.home.launcher.laptop.LaptopStateManager", false, classLoader);
            HookUtil.hookMethod(stateManager, "onLaptopModeChanged",
                    new Class<?>[]{boolean.class}, chain -> {
                        boolean entering = (Boolean) chain.getArg(0);
                        modeHookConfirmed = true;
                        if (entering) WorkstationNormalLayoutOwner.backupFromActiveDockRoot();
                        publish(entering);
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (!entering) {
                            WorkstationNormalLayoutOwner.scheduleRestoreFromActiveDockRoot();
                        }
                        HomeGridHook.scheduleAllPageRefresh();
                        return result;
                    });
            detected = true;
            MainHook.log("[DC] workstation guard uses LauncherModeController; active="
                    + WorkstationRuntimeState.isActive());

            // Preserve the existing startup re-check behavior; this timer is mode detection, not
            // capture/freshness authority and is intentionally unchanged by the composition refactor.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (modeHookConfirmed) return;
                try {
                    HookUtil.InvocationResult<Object> recheck =
                            HookUtil.tryInvokeStatic(modeController, "isLaptopMode");
                    Object value = recheck.succeeded() ? recheck.value() : null;
                    boolean actual = value instanceof Boolean && (Boolean) value;
                    if ((!recheck.succeeded() || value == null) && deviceConfig != null) {
                        HookUtil.InvocationResult<Object> fallback = HookUtil.tryInvokeStatic(
                                deviceConfig, "isMingouLaptopPcModeEnabled");
                        if (fallback.succeeded()) {
                            actual = fallback.value() instanceof Boolean
                                    && (Boolean) fallback.value();
                        }
                    }
                    if (actual != WorkstationRuntimeState.isActive()) publish(actual);
                } catch (Throwable ignored) {}
            }, 2000L);
        } catch (Throwable error) {
            MainHook.log("[DC] current workstation API unavailable: " + error);
        }

        if (!detected) {
            try {
                Class<?> deviceConfig = Class.forName(
                        "com.miui.home.launcher.DeviceConfig", false, classLoader);
                WorkstationRuntimeState.publish((Boolean) HookUtil.requireInvokeStatic(
                        deviceConfig, "isMingouLaptopPcModeEnabled"));
                HookUtil.hookMethod(deviceConfig, "setMingouLaptopPcModeEnabled",
                        new Class<?>[]{boolean.class}, chain -> {
                            publish((Boolean) chain.getArg(0));
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        });
                detected = true;
                MainHook.log("[DC] workstation guard uses legacy DeviceConfig; active="
                        + WorkstationRuntimeState.isActive());
            } catch (Throwable error) {
                MainHook.log("[DC] legacy workstation API unavailable: " + error);
            }
        }

        if (!detected) {
            WorkstationRuntimeState.publish(false);
            MainHook.log("[DC] ERROR: no supported workstation state API found");
        }
    }

    private static void publish(boolean enabled) {
        WorkstationRuntimeState.publish(enabled);
        HomeGridHook.setWorkstationMode(enabled);
        WorkstationDockGeometryHook.onWorkstationModeChanged(enabled);
        MainHook.log("[DC] Mingou workstation mode changed=" + enabled);

        DockShadowOwnership.refreshVendorDockShadow();
        View dockBg = DockShadowOwnership.activeBackground();
        if (!enabled) {
            if (dockBg != null) {
                dockBg.post(() -> {
                    dockBg.setAlpha(1f);
                    DockCustomizationHook.syncAll(dockBg);
                });
            }
            return;
        }
        if (dockBg != null) {
            dockBg.post(() -> dockBg.setAlpha(1f));
        }
        Miuix307ZeroCopyRenderer.setProducerUpdatesEnabled(
                true, "workstation-mode-enabled");
    }
}
