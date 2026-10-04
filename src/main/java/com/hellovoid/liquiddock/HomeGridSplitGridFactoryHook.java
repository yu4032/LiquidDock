package com.hellovoid.liquiddock;

import android.content.Context;

/**
 * Replaces only the hard-coded 4x6 cell-count inputs of MIUI's split GridCalculator call.
 *
 * <p>Width, height, insets, scales, left offset and cell size remain calculated by the vendor
 * calculateGridConfigBasedVertical() implementation. This preserves the real SOSC pane coordinate
 * system instead of post-processing a completed GridConfig with full-screen assumptions.</p>
 */
final class HomeGridSplitGridFactoryHook {
    private static final ThreadLocal<Integer> SPLIT_FACTORY_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    private static boolean installed;

    private HomeGridSplitGridFactoryHook() {}

    static void install(
            ClassLoader classLoader,
            Class<?> gridController,
            HomeGridInstallConfig config) {
        if (installed || config == null || !config.enabled) return;
        try {
            HookUtil.hookMethod(
                    gridController,
                    "calculateSplitGridConfigs",
                    new Class<?>[]{Context.class},
                    chain -> {
                        int depth = SPLIT_FACTORY_DEPTH.get();
                        SPLIT_FACTORY_DEPTH.set(depth + 1);
                        try {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        } finally {
                            if (depth == 0) {
                                SPLIT_FACTORY_DEPTH.remove();
                            } else {
                                SPLIT_FACTORY_DEPTH.set(depth);
                            }
                        }
                    });

            Class<?> calculator = Class.forName(
                    "com.miui.home.launcher.grid.GridCalculator", false, classLoader);
            HookUtil.hookMethod(
                    calculator,
                    "calculateGridConfigBasedVertical",
                    new Class<?>[]{
                            int.class, int.class, int.class, int.class, int.class,
                            float.class, float.class, int.class, int.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (SPLIT_FACTORY_DEPTH.get() > 0 && args.length == 9) {
                            int[] counts = HomeGridWorkspaceGridPolicy.splitCounts(
                                    config.columns, config.rows);
                            if (counts != null) {
                                args[7] = counts[0];
                                args[8] = counts[1];
                            }
                        }
                        return chain.proceed(args);
                    });
            installed = true;
        } catch (Throwable error) {
            throw new RuntimeException("split GridCalculator authority unavailable", error);
        }
    }
}
