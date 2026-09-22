package com.hellovoid.liquiddock;

import android.content.Context;

import java.lang.reflect.Constructor;

/** Composition root for the custom HOME grid feature; MIUI retains placement/occupancy authority. */
final class HomeGridHook {
    private static final String PAD_CELL_COUNT =
            "com.miui.home.launcher.compat.LauncherCellCountCompatPadDevice";

    private HomeGridHook() {}

    static void setWorkstationMode(boolean enabled) {
        // Workstation state is owned outside Grid. This callback only requests the same refresh
        // that the former local boolean setter requested.
        HomeGridRotationRefreshHook.scheduleAllPageRefresh();
    }

    static void setWorkstationGeometryConfig(HomeGridWorkstationGeometryConfig config) {
        HomeGridCellGeometryHook.setWorkstationConfig(config);
    }

    static void install(ClassLoader classLoader, HomeGridInstallConfig config) {
        if (config == null || !config.enabled) {
            MainHook.log("[DC] home grid customization disabled; using stock layout");
            return;
        }
        try {
            Class<?> compat = Class.forName(PAD_CELL_COUNT, false, classLoader);
            hookAxis(compat, "getCellCountXMin", true);
            hookAxis(compat, "getCellCountXDef", true);
            hookAxis(compat, "getCellCountYMin", false);
            hookAxis(compat, "getCellCountYDef", false);

            Class<?> gridConfig = Class.forName(
                    "com.miui.home.launcher.grid.GridConfig", false, classLoader);
            hookGridCountSetter(gridConfig, "setCountX");
            hookGridCountSetter(gridConfig, "setCountY");
            hookGridCountGetter(gridConfig, "getCountX");
            hookGridCountGetter(gridConfig, "getCountY");

            installRotationTransform(classLoader);
            HomeGridPageIndicatorHook.install(classLoader, config);
            HomeGridCellGeometryHook.install(classLoader, config);
            HomeGridFolderAlignmentHook.install(classLoader);
            HomeGridRotationRefreshHook.install(classLoader);

            MainHook.log("[DC] home grid hooks: 8x4=true land="
                    + config.landscape.left + "," + config.landscape.right + ","
                    + config.landscape.top + "," + config.landscape.bottom + " port="
                    + config.portrait.left + "," + config.portrait.right + ","
                    + config.portrait.top + "," + config.portrait.bottom);
        } catch (Throwable error) {
            MainHook.log("[DC] home grid hook unavailable: " + error);
        }
    }

    private static void installRotationTransform(ClassLoader classLoader) {
        // MIUI owns occupied-matrix storage and indexing. Only extend the transform rule metadata;
        // never replace addOccupied()/transformToHVArray() or infer occupancy ourselves.
        final Class<?> rule;
        try {
            rule = Class.forName(
                    "com.miui.home.launcher.compat.LayoutTransformRuleGridChanged",
                    false,
                    classLoader);
        } catch (ClassNotFoundException error) {
            throw new RuntimeException(error);
        }

        for (Constructor<?> constructor : rule.getDeclaredConstructors()) {
            HookUtil.hook(constructor, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                int h = (Integer) args[0];
                int v = (Integer) args[1];
                if (!isEightByFourGrid(h, v)) return result;

                int[][] portrait = new int[][]{
                        {0, 0}, {2, 0}, {0, 2}, {2, 2},
                        {0, 4}, {2, 4}, {0, 6}, {2, 6}
                };
                int[][] landscape = new int[][]{
                        {0, 0}, {2, 0}, {4, 0}, {6, 0},
                        {0, 2}, {2, 2}, {4, 2}, {6, 2}
                };
                Object owner = chain.getThisObject();
                HookUtil.setField(owner, "vScreenCoordinate", portrait);
                HookUtil.setField(owner, "hScreenCoordinate", landscape);
                HookUtil.setIntField(owner, "totalBlocks", 8);
                return result;
            });
        }

        HookUtil.hookMethod(rule, "checkCellCount", new Class<?>[0], chain -> {
            Object owner = chain.getThisObject();
            int h = (Integer) HookUtil.requireInvoke(owner, "getMHCells");
            int v = (Integer) HookUtil.requireInvoke(owner, "getMVCells");
            if (isEightByFourGrid(h, v)) return null;
            return chain.proceed(chain.getArgs().toArray(new Object[0]));
        });
    }

    private static boolean isEightByFourGrid(int h, int v) {
        return (h == 8 && v == 4) || (h == 4 && v == 8);
    }

    private static void hookGridCountSetter(Class<?> gridConfig, String method) {
        HookUtil.hookMethod(gridConfig, method, new Class<?>[]{int.class}, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            if ((Integer) args[0] == 6) args[0] = 8;
            return chain.proceed(args);
        });
    }

    private static void hookGridCountGetter(Class<?> gridConfig, String method) {
        HookUtil.hookMethod(gridConfig, method, new Class<?>[0], chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            if ((Integer) result == 6) result = 8;
            return result;
        });
    }

    private static void hookAxis(Class<?> compat, String method, boolean xAxis) {
        HookUtil.hookMethod(compat, method, new Class<?>[]{Context.class}, chain -> {
            Context context = (Context) chain.getArg(0);
            boolean portrait = context.getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_PORTRAIT;
            return xAxis ? (portrait ? 4 : 8) : (portrait ? 8 : 4);
        });
    }
}
