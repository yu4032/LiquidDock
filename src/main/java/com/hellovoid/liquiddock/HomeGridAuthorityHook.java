package com.hellovoid.liquiddock;

import android.content.Context;

import java.util.List;

/**
 * Rewrites the actual GridController full-screen configs before they become active.
 *
 * <p>HyperOS Launcher 4.50 no longer derives the live workspace count from
 * LauncherCellCountCompatPadDevice. GridController constructs 6x4 GridConfig instances directly,
 * then DeviceConfig and CellLayout read those instances. This hook targets that authority.</p>
 */
final class HomeGridAuthorityHook {
    private HomeGridAuthorityHook() {}

    static void install(ClassLoader classLoader, HomeGridInstallConfig config) {
        try {
            Class<?> controller = Class.forName(
                    "com.miui.home.launcher.grid.GridController", false, classLoader);
            hookFullScreenFactory(controller, "calculateFullScreenGridConfigs", config);
        } catch (Throwable error) {
            throw new RuntimeException("GridController authority unavailable", error);
        }
    }

    private static void hookFullScreenFactory(
            Class<?> controller, String method, HomeGridInstallConfig config) {
        HookUtil.hookMethod(controller, method, new Class<?>[]{Context.class}, chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            if (result instanceof List<?>) {
                for (Object grid : (List<?>) result) {
                    rewriteGrid(grid, config);
                }
            }
            return result;
        });
    }

    private static void rewriteGrid(Object grid, HomeGridInstallConfig config) {
        if (grid == null || config == null || !config.enabled) return;
        try {
            Object nameValue = HookUtil.requireInvoke(grid, "getName");
            String name = nameValue == null ? "" : String.valueOf(nameValue);
            final int countX;
            final int countY;
            if ("land_grid".equals(name)) {
                countX = config.columns;
                countY = config.rows;
            } else if ("vertical_grid".equals(name)) {
                countX = config.rows;
                countY = config.columns;
            } else {
                return;
            }

            int width = (Integer) HookUtil.requireInvoke(grid, "getWidth");
            int height = (Integer) HookUtil.requireInvoke(grid, "getHeight");
            int top = (Integer) HookUtil.requireInvoke(grid, "getTop");
            int bottom = (Integer) HookUtil.requireInvoke(grid, "getBottom");
            int dock = (Integer) HookUtil.requireInvoke(grid, "getDockBarHeight");
            int indicator = (Integer) HookUtil.requireInvoke(grid, "getIndicatorBarHeight");
            HomeGridVendorConfigPolicy.Result geometry = HomeGridVendorConfigPolicy.calculate(
                    width, height, top, bottom, dock, indicator, countX, countY);
            if (geometry == null) return;

            HookUtil.requireInvoke(grid, "setCountX", geometry.countX);
            HookUtil.requireInvoke(grid, "setCountY", geometry.countY);
            HookUtil.requireInvoke(grid, "setCellSize", geometry.cellSize);
            HookUtil.requireInvoke(grid, "setLeft", geometry.left);
        } catch (Throwable error) {
            MainHook.log("[DC][HomeGridAuthority] rewrite failed: " + error);
        }
    }
}
