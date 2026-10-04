package com.hellovoid.liquiddock;

import android.content.Context;

import java.util.List;

/**
 * Rewrites the actual GridController full-screen configs before they become active.
 *
 * <p>Split-screen geometry remains vendor-owned and is handled at the GridCalculator input
 * boundary by HomeGridSplitGridFactoryHook so MIUI keeps its native pane coordinate system.</p>
 */
final class HomeGridAuthorityHook {
    private HomeGridAuthorityHook() {}

    static void install(ClassLoader classLoader, HomeGridInstallConfig config) {
        try {
            Class<?> controller = Class.forName(
                    "com.miui.home.launcher.grid.GridController", false, classLoader);
            hookGridFactory(controller, "calculateFullScreenGridConfigs", config);
            HomeGridSplitGridFactoryHook.install(classLoader, controller, config);
        } catch (Throwable error) {
            throw new RuntimeException("GridController authority unavailable", error);
        }
    }

    private static void hookGridFactory(
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
            int[] counts = HomeGridWorkspaceGridPolicy.fullScreenCounts(
                    name, config.columns, config.rows);
            if (counts == null) return;
            countX = counts[0];
            countY = counts[1];

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
