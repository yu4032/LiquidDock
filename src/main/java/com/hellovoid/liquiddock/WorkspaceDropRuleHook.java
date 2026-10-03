package com.hellovoid.liquiddock;

/**
 * Replaces MIUI's stock swap-placement pattern rule only while LiquidDock's custom workspace grid
 * is enabled. Native occupancy remains authoritative for collision resolution; this hook only
 * removes vendor profile-pattern restrictions while preserving strict live-grid bounds.
 */
final class WorkspaceDropRuleHook {
    private static final String TAG = "[DC][GRID]";
    private static final String DEVICE_CONFIG = "com.miui.home.launcher.DeviceConfig";
    private static boolean installed;

    private WorkspaceDropRuleHook() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled) {
        if (!customGridEnabled || installed) return;
        try {
            Class<?> rule = Class.forName(
                    "com.miui.home.launcher.compat.LayoutDropRuleForSwapPlaces",
                    false, classLoader);
            Class<?> deviceConfig = Class.forName(DEVICE_CONFIG, false, classLoader);
            HookUtil.hookMethod(rule, "isLegalXY",
                    new Class<?>[]{int.class, int.class, int.class, int.class},
                    chain -> {
                        if (MainHook.isWorkstationMode()) return chain.proceed();

                        Object xValue = chain.getArg(0);
                        Object yValue = chain.getArg(1);
                        Object spanXValue = chain.getArg(2);
                        Object spanYValue = chain.getArg(3);
                        if (!(xValue instanceof Integer) || !(yValue instanceof Integer)
                                || !(spanXValue instanceof Integer)
                                || !(spanYValue instanceof Integer)) {
                            return chain.proceed();
                        }

                        HookUtil.InvocationResult<Object> columnsResult =
                                HookUtil.tryInvokeStatic(deviceConfig, "getCellCountX");
                        HookUtil.InvocationResult<Object> rowsResult =
                                HookUtil.tryInvokeStatic(deviceConfig, "getCellCountY");
                        Object columnsValue = columnsResult.succeeded() ? columnsResult.value() : null;
                        Object rowsValue = rowsResult.succeeded() ? rowsResult.value() : null;
                        if (!(columnsValue instanceof Integer) || !(rowsValue instanceof Integer)) {
                            // Never guess while GridController/DeviceConfig is transitioning. The
                            // old implementation returned true here and could feed an out-of-bounds
                            // widget placement into MIUI's occupancy matrix.
                            return chain.proceed();
                        }

                        return HomeGridDropLegalityPolicy.isLegal(
                                (Integer) columnsValue,
                                (Integer) rowsValue,
                                (Integer) xValue,
                                (Integer) yValue,
                                (Integer) spanXValue,
                                (Integer) spanYValue);
                    });
            installed = true;
            MainHook.log(TAG + " bounded free-grid drop rule installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " custom-grid swap placement rule unavailable: " + error);
        }
    }
}
