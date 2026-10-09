package com.hellovoid.liquiddock;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * Adapts MIUI's database-orientation bookkeeping to arbitrary HOME grid dimensions.
 *
 * <p>OS3 Launcher 4.50 hard-codes portrait database data as
 * {@code DeviceConfig.getCellCountX() == 4}. That is only valid for the stock 6x4 / 4x6 grid.
 * For a free grid such as 8x5 / 5x8 the predicate reports landscape in both orientations, which
 * can desynchronize the saved DB-orientation bit from the actual cell coordinates. On the next
 * model load MIUI may rotate the favorites table from the wrong source orientation.</p>
 *
 * <p>The legacy orientation probe is adapted without changing the counts used by MIUI's actual
 * database transform. After a persisted HOME layout change, the vendor orientation marker is
 * synchronized through LauncherModel so coordinates and orientation metadata stay atomic from the
 * next model load's point of view.</p>
 */
final class HomeGridDbOrientationHook {
    private static final String DB_ORIENTATION_HELPER =
            "com.miui.home.launcher.util.DbOrientationHelper";
    private static final String LAUNCHER_MODE =
            "com.miui.home.launcher.allapps.LauncherMode";
    private static final String DEVICE_CONFIG =
            "com.miui.home.launcher.DeviceConfig";
    private static final String LAUNCHER_MODEL =
            "com.miui.home.launcher.LauncherModel";
    private static final String CELL_LAYOUT =
            "com.miui.home.launcher.CellLayout";

    private static final ThreadLocal<Integer> PREDICATE_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> TRANSFORM_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private static Class<?> launcherModelClass;
    private static boolean installed;

    private HomeGridDbOrientationHook() {}

    static void install(ClassLoader classLoader, HomeGridInstallConfig config) {
        if (installed || config == null || !config.enabled) return;
        try {
            Class<?> helper = Class.forName(DB_ORIENTATION_HELPER, false, classLoader);
            Class<?> launcherMode = Class.forName(LAUNCHER_MODE, false, classLoader);
            Class<?> deviceConfig = Class.forName(DEVICE_CONFIG, false, classLoader);
            Class<?> launcherModel = Class.forName(LAUNCHER_MODEL, false, classLoader);
            Class<?> cellLayout = Class.forName(CELL_LAYOUT, false, classLoader);

            Method check = HookUtil.findMethodExact(
                    helper,
                    "checkDatabaseDataOrientationWhenLoading",
                    new Class<?>[]{ContentResolver.class, launcherMode});
            Method update = HookUtil.findMethodExact(
                    helper,
                    "updateSavedDBDataOrientation",
                    new Class<?>[]{launcherMode});
            Method transform = HookUtil.findMethodExact(
                    helper,
                    "transformDatabaseData",
                    new Class<?>[]{boolean.class, ContentResolver.class, launcherMode});
            Method getCellCountX = HookUtil.findMethodExact(
                    deviceConfig, "getCellCountX", new Class<?>[0]);
            HookUtil.findMethodExact(
                    launcherModel, "updateDBDataOrientation", new Class<?>[]{launcherMode});

            launcherModelClass = launcherModel;
            hookScope(check, PREDICATE_DEPTH);
            hookScope(update, PREDICATE_DEPTH);
            hookScope(transform, TRANSFORM_DEPTH);
            hookCommittedLayoutSync(cellLayout, config);

            HookUtil.hookWithPriority(getCellCountX, XposedInterface.PRIORITY_HIGHEST,
                    chain -> {
                        Object result = chain.proceed();
                        if (!(result instanceof Integer)
                                || !isOrientationPredicateScope()
                                || MainHook.isWorkstationMode()) {
                            return result;
                        }
                        Boolean portrait = currentPortrait();
                        if (portrait == null) return result;
                        return HomeGridDbOrientationPolicy.probeCellCountX(
                                portrait, (Integer) result);
                    });
            installed = true;
        } catch (Throwable error) {
            MainHook.log("[DC][HomeGridDbOrientation] hook unavailable: " + error);
        }
    }

    static boolean isOrientationPredicateScope() {
        return PREDICATE_DEPTH.get() > 0 && TRANSFORM_DEPTH.get() == 0;
    }

    private static void syncAfterCommittedLayout(Object cellLayout, HomeGridInstallConfig config) {
        if (!installed || launcherModelClass == null || cellLayout == null || config == null
                || !config.enabled || MainHook.isWorkstationMode()) {
            return;
        }
        try {
            Object grid = HookUtil.getField(cellLayout, "mGridConfig");
            if (grid == null) return;
            String name = String.valueOf(HookUtil.requireInvoke(grid, "getName"));
            Object xValue = HookUtil.requireInvoke(grid, "getCountX");
            Object yValue = HookUtil.requireInvoke(grid, "getCountY");
            if (!(xValue instanceof Integer) || !(yValue instanceof Integer)
                    || !HomeGridDbOrientationPolicy.isConfiguredFullScreenGrid(
                            name,
                            (Integer) xValue,
                            (Integer) yValue,
                            config.columns,
                            config.rows)) {
                return;
            }

            Object launcher = HookUtil.getField(cellLayout, "mLauncher");
            if (launcher == null) return;
            Object launcherMode = HookUtil.requireInvoke(launcher, "getLauncherMode");
            if (launcherMode == null) return;

            HookUtil.requireInvokeStatic(
                    launcherModelClass, "updateDBDataOrientation", launcherMode);
        } catch (Throwable error) {
            MainHook.log("[DC][HomeGridDbOrientation] saved orientation sync failed: " + error);
        }
    }

    private static void hookCommittedLayoutSync(
            Class<?> cellLayout, HomeGridInstallConfig config) {
        HookUtil.hookMethod(
                cellLayout,
                "saveCurrentLayout",
                new Class<?>[]{boolean.class, boolean.class},
                chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (Boolean.TRUE.equals(chain.getArg(1))) {
                        syncAfterCommittedLayout(chain.getThisObject(), config);
                    }
                    return result;
                });
    }

    private static void hookScope(Method method, ThreadLocal<Integer> scope) {
        HookUtil.hookWithPriority(method, XposedInterface.PRIORITY_HIGHEST,
                    chain -> {
                    enter(scope);
                    try {
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    } finally {
                        exit(scope);
                    }
                });
    }

    private static void enter(ThreadLocal<Integer> scope) {
        scope.set(scope.get() + 1);
    }

    private static void exit(ThreadLocal<Integer> scope) {
        int depth = scope.get();
        if (depth <= 1) scope.remove();
        else scope.set(depth - 1);
    }

    private static Boolean currentPortrait() {
        HookUtil.InvocationResult<Object> applicationResult =
                HookUtil.tryInvokeActivityThreadCurrentApplication();
        Object application = applicationResult.succeeded() ? applicationResult.value() : null;
        if (!(application instanceof Context)) return null;
        int orientation =
                ((Context) application).getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) return Boolean.TRUE;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) return Boolean.FALSE;
        return null;
    }
}
