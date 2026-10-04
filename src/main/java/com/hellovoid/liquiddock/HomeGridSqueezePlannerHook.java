package com.hellovoid.liquiddock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Routes custom HOME-grid occupancy operations between MIUI's stock Pad planners and its generic
 * rectangular planners.
 *
 * <p>LayoutSwapPlaces is only safe on the vendor 6x4/4x6 matrix because its 1x1 path also assumes
 * fixed 2x2 macroblocks. Every non-vendor full-screen or split HOME grid therefore uses the generic
 * dimension-aware planners for icons and widgets alike.</p>
 */
final class HomeGridSqueezePlannerHook {
    private static final String TAG = "[DC][GRID]";
    private static final String CONTROLLER = "com.miui.home.GridOccupancyController";
    private static final String GRID_CONFIG = "com.miui.home.launcher.grid.GridConfig";
    private static final String SAVE_LISTENER =
            "com.miui.home.GridOccupancyController$OnSaveLayoutListener";
    private static final String SQUEEZE_RULE =
            "com.miui.home.launcher.compat.LayoutSqueezeRule";
    private static final String DROP_RULE =
            "com.miui.home.launcher.compat.LayoutDropRule";
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
            Class<?> squeezeRule = Class.forName(SQUEEZE_RULE, false, classLoader);
            Class<?> dropRule = Class.forName(DROP_RULE, false, classLoader);
            Class<?> genericSqueeze = Class.forName(GENERIC_SQUEEZE, false, classLoader);
            Class<?> genericDrop = Class.forName(GENERIC_DROP, false, classLoader);
            HomeGridSqueezeCycleGuard.install(classLoader);
            HomeGridSqueezeTransactionGuard.install(classLoader);

            HookUtil.hookMethod(
                    controller,
                    "loadGridConfig",
                    new Class<?>[]{gridConfig, boolean.class, boolean.class, saveListener},
                    chain -> {
                        Object result = chain.proceed();
                        Object activeGrid = chain.getArg(0);
                        if (!isFreeHomeGrid(activeGrid, config)) return result;

                        Object owner = chain.getThisObject();
                        int countX = (Integer) HookUtil.requireInvoke(activeGrid, "getCountX");
                        int countY = (Integer) HookUtil.requireInvoke(activeGrid, "getCountY");
                        boolean vendorPadGrid =
                                HomeGridSqueezePlannerPolicy.isVendorPadGrid(countX, countY);
                        Object transform = HookUtil.getField(owner, "mLayoutSqueezeDataTransform");
                        if (transform == null) return result;

                        Object stockSqueeze = HookUtil.getField(transform, "mLayoutSqueezeRule");
                        Object stockDrop = HookUtil.getField(owner, "mLayoutDropRule");
                        if (stockSqueeze == null || stockDrop == null) return result;

                        Object genericSqueezePlanner =
                                genericSqueeze.getDeclaredConstructor().newInstance();
                        Object genericDropPlanner =
                                genericDrop.getDeclaredConstructor().newInstance();
                        HomeGridSqueezeCycleGuard.register(genericSqueezePlanner);
                        HomeGridSqueezeTransactionGuard.register(genericSqueezePlanner);

                        Object squeezeRouter = Proxy.newProxyInstance(
                                classLoader,
                                new Class<?>[]{squeezeRule},
                                (proxy, method, args) -> {
                                    if (method.getDeclaringClass() == Object.class) {
                                        return objectMethod(proxy, method, args, "HybridSqueezeRule");
                                    }
                                    Object parameter = args != null && args.length > 0
                                            ? args[0] : null;
                                    boolean useGeneric = false;
                                    if (parameter != null) {
                                        int spanX = HookUtil.getIntField(parameter, "spanX");
                                        int spanY = HookUtil.getIntField(parameter, "spanY");
                                        if (!vendorPadGrid
                                                && !HomeGridDropLegalityPolicy.fitsBothOrientations(
                                                        countX, countY, spanX, spanY)
                                                && (method.getReturnType() == boolean.class
                                                        || method.getReturnType() == Boolean.class)) {
                                            return false;
                                        }
                                        boolean isSpanMove =
                                                HookUtil.getBooleanField(parameter, "isSpanMove");
                                        useGeneric =
                                                HomeGridSqueezePlannerPolicy.useGenericForSqueeze(
                                                        vendorPadGrid, isSpanMove, spanX, spanY);
                                    }
                                    return invoke(
                                            useGeneric
                                                    ? genericSqueezePlanner
                                                    : stockSqueeze,
                                            method,
                                            args);
                                });

                        Object dropRouter = Proxy.newProxyInstance(
                                classLoader,
                                new Class<?>[]{dropRule},
                                (proxy, method, args) -> {
                                    if (method.getDeclaringClass() == Object.class) {
                                        return objectMethod(proxy, method, args, "HybridDropRule");
                                    }
                                    int spanX = 1;
                                    int spanY = 1;
                                    if ("isLegalXY".equals(method.getName())
                                            && args != null && args.length >= 4) {
                                        int cellX = (Integer) args[0];
                                        int cellY = (Integer) args[1];
                                        spanX = (Integer) args[2];
                                        spanY = (Integer) args[3];
                                        if (!vendorPadGrid
                                                && !HomeGridDropLegalityPolicy.isLegal(
                                                        countX, countY,
                                                        cellX, cellY, spanX, spanY)) {
                                            return false;
                                        }
                                    } else if ("findNearestLinearVacantArea".equals(
                                            method.getName())
                                            && args != null && args.length >= 6) {
                                        spanX = (Integer) args[4];
                                        spanY = (Integer) args[5];
                                        if (!vendorPadGrid
                                                && !HomeGridDropLegalityPolicy.fitsBothOrientations(
                                                        countX, countY, spanX, spanY)) {
                                            return null;
                                        }
                                    }
                                    return invoke(
                                            HomeGridSqueezePlannerPolicy.useGenericForSpan(
                                                    vendorPadGrid, spanX, spanY)
                                                    ? genericDropPlanner
                                                    : stockDrop,
                                            method,
                                            args);
                                });

                        HookUtil.requireInvoke(
                                transform, "setLayoutSqueezeRule", squeezeRouter);
                        HookUtil.setField(owner, "mLayoutDropRule", dropRouter);
                        return result;
                    });

            installed = true;
        } catch (Throwable error) {
            MainHook.log(TAG + " free-grid planners unavailable: " + error);
        }
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            throw cause != null ? cause : error;
        }
    }

    private static Object objectMethod(
            Object proxy, Method method, Object[] args, String label) {
        String name = method.getName();
        if ("hashCode".equals(name)) return System.identityHashCode(proxy);
        if ("equals".equals(name)) {
            return args != null && args.length == 1 && proxy == args[0];
        }
        if ("toString".equals(name)) return label;
        throw new UnsupportedOperationException(name);
    }

    private static boolean isFreeHomeGrid(
            Object gridConfig, HomeGridInstallConfig config) {
        if (gridConfig == null) return false;
        try {
            String name = String.valueOf(HookUtil.requireInvoke(gridConfig, "getName"));
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
