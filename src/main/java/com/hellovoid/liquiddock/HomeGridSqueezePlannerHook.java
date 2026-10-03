package com.hellovoid.liquiddock;

/**
 * Selects MIUI's generic occupancy planners for free HOME grids.
 *
 * <p>Pad's stock LayoutSwapPlaces / LayoutDropRuleForSwapPlaces are designed around six complete
 * 2x2 macroblocks. Free grids allow arbitrary dimensions and non-macroblock widget positions, so
 * those rules can address partial blocks outside the matrix or overwrite cells owned by another
 * rectangle. MIUI already ships bounds-checked rectangular planners for the alternate squeeze
 * mode. Keep CellLayout/StayConfirm/animation untouched and replace only the two planner objects
 * for active HOME land_grid / vertical_grid controllers matching the configured free grid.</p>
 */
final class HomeGridSqueezePlannerHook {
    private static final String TAG = "[DC][GRID]";
    private static final String CONTROLLER = "com.miui.home.GridOccupancyController";
    private static final String GRID_CONFIG = "com.miui.home.launcher.grid.GridConfig";
    private static final String SAVE_LISTENER =
            "com.miui.home.GridOccupancyController$OnSaveLayoutListener";
    private static final String GENERIC_SQUEEZE =
            "com.miui.home.launcher.compat.LayoutSqueezePlaces";
    private static final String GENERIC_DROP =
            "com.miui.home.launcher.compat.LayoutDropRuleSqueezePlaces";
    private static boolean installed;

    private HomeGridSqueezePlannerHook() {}

    static void install(ClassLoader classLoader, HomeGridInstallConfig config) {
        if (installed || config == null || !config.enabled) return;
        try {
            Class<?> controller = Class.forName(CONTROLLER, false, classLoader);
            Class<?> gridConfig = Class.forName(GRID_CONFIG, false, classLoader);
            Class<?> saveListener = Class.forName(SAVE_LISTENER, false, classLoader);
            Class<?> genericSqueeze = Class.forName(GENERIC_SQUEEZE, false, classLoader);
            Class<?> genericDrop = Class.forName(GENERIC_DROP, false, classLoader);
            HomeGridSqueezeCycleGuard.install(classLoader);

            HookUtil.hookMethod(
                    controller,
                    "loadGridConfig",
                    new Class<?>[]{gridConfig, boolean.class, boolean.class, saveListener},
                    chain -> {
                        Object result = chain.proceed();
                        Object activeGrid = chain.getArg(0);
                        if (!isFreeHomeGrid(activeGrid, config)) return result;

                        Object owner = chain.getThisObject();
                        Object transform = HookUtil.getField(owner, "mLayoutSqueezeDataTransform");
                        if (transform == null) return result;

                        Object squeezePlanner =
                                genericSqueeze.getDeclaredConstructor().newInstance();
                        Object dropPlanner =
                                genericDrop.getDeclaredConstructor().newInstance();
                        HomeGridSqueezeCycleGuard.register(squeezePlanner);

                        HookUtil.requireInvoke(
                                transform, "setLayoutSqueezeRule", squeezePlanner);
                        HookUtil.setField(owner, "mLayoutDropRule", dropPlanner);

                        return result;
                    });

            installed = true;
        } catch (Throwable error) {
            MainHook.log(TAG + " free-grid planners unavailable: " + error);
        }
    }

    private static boolean isFreeHomeGrid(
            Object gridConfig, HomeGridInstallConfig config) {
        if (gridConfig == null) return false;
        try {
            String name = String.valueOf(HookUtil.requireInvoke(gridConfig, "getName"));
            if (!"land_grid".equals(name) && !"vertical_grid".equals(name)) {
                return false;
            }
            Object xValue = HookUtil.requireInvoke(gridConfig, "getCountX");
            Object yValue = HookUtil.requireInvoke(gridConfig, "getCountY");
            return xValue instanceof Integer
                    && yValue instanceof Integer
                    && HomeGridSqueezePlannerPolicy.matches(
                            name,
                            (Integer) xValue,
                            (Integer) yValue,
                            config.columns,
                            config.rows);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
