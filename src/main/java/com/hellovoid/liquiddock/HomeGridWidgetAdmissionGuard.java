package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.Resources;

import java.util.HashMap;
import java.util.Map;

/**
 * Routes incompatible multi-cell items into Launcher 4.50's native no-space paths.
 *
 * <p>The picker path is blocked before Launcher.addAppWidget() can allocate/place the item and uses
 * Launcher.showError(R.string.out_of_space). Drag/drop paths are blocked through CellLayout's own
 * vacancy/space checks, where Launcher already calls DragObject.showNoSpaceToast(true). The
 * drop/squeeze planners remain the hard commit barrier.</p>
 */
final class HomeGridWidgetAdmissionGuard {
    private static final String TAG = "[DC][HomeGridAdmission]";
    private static final String CELL_LAYOUT = "com.miui.home.launcher.CellLayout";
    private static final String DRAG_OBJECT =
            "com.miui.home.launcher.DragController$DragObject";
    private static final String LAUNCHER = "com.miui.home.launcher.Launcher";
    private static final String WIDGET_INFO = "com.miui.home.launcher.LauncherAppWidgetInfo";
    private static final String CELL_SCREEN = "com.miui.home.launcher.CellScreen";
    private static boolean installed;
    private static final Map<String, String> LAST_TRACE = new HashMap<>();

    private HomeGridWidgetAdmissionGuard() {}

    static void install(ClassLoader classLoader, HomeGridInstallConfig config) {
        if (installed || config == null || !config.enabled) return;
        try {
            Class<?> cellLayout = Class.forName(CELL_LAYOUT, false, classLoader);

            HookUtil.hookMethod(
                    cellLayout,
                    "findNearestVacantAreaByCellPos",
                    new Class<?>[]{int.class, int.class, int.class, int.class},
                    chain -> {
                        int spanX = (Integer) chain.getArg(2);
                        int spanY = (Integer) chain.getArg(3);
                        if (shouldRejectSpan(chain.getThisObject(), spanX, spanY, config)) {
                            return null;
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });

            installPickerGuard(classLoader, config);
            installDragGuard(classLoader, cellLayout, config);
            installed = true;
            MainHook.log(TAG + " installed cellLayout=" + cellLayout.getName());
        } catch (Throwable error) {
            MainHook.log(TAG + " admission guard unavailable: " + error);
        }
    }

    private static void installPickerGuard(
            ClassLoader classLoader, HomeGridInstallConfig config) {
        try {
            Class<?> launcher = Class.forName(LAUNCHER, false, classLoader);
            Class<?> widgetInfo = Class.forName(WIDGET_INFO, false, classLoader);
            Class<?> cellScreen = Class.forName(CELL_SCREEN, false, classLoader);

            HookUtil.hookMethod(
                    launcher,
                    "addAppWidget",
                    new Class<?>[]{widgetInfo, cellScreen},
                    chain -> {
                        Object info = chain.getArg(0);
                        Object screen = chain.getArg(1);
                        if (info == null || screen == null) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }

                        int spanX = HookUtil.getIntField(info, "spanX");
                        int spanY = HookUtil.getIntField(info, "spanY");
                        Object cellLayout = HookUtil.tryInvoke(screen, "getCellLayout").value();
                        if (!shouldRejectSpan(cellLayout, spanX, spanY, config)) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }

                        showNativeOutOfSpace(chain.getThisObject());
                        return -1;
                    });
        } catch (Throwable error) {
            MainHook.log(TAG + " picker guard unavailable: " + error);
        }
    }

    private static void installDragGuard(
            ClassLoader classLoader,
            Class<?> cellLayout,
            HomeGridInstallConfig config) {
        try {
            Class<?> dragObject = Class.forName(DRAG_OBJECT, false, classLoader);
            MainHook.log(TAG + " drag hook class=" + dragObject.getName());
            HookUtil.hookMethod(
                    cellLayout,
                    "isSpaceEnough",
                    new Class<?>[]{dragObject},
                    chain -> {
                        Object drag = chain.getArg(0);
                        boolean reject = shouldRejectDrag(
                                chain.getThisObject(), drag, config);
                        traceDrag("isSpaceEnough", chain.getThisObject(), drag, config, reject);
                        if (reject) {
                            return false;
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });

            HookUtil.hookMethod(
                    cellLayout,
                    "findDropTargetPosition",
                    new Class<?>[]{dragObject},
                    chain -> {
                        Object drag = chain.getArg(0);
                        boolean reject = shouldRejectDrag(
                                chain.getThisObject(), drag, config);
                        traceDrag(
                                "findDropTargetPosition",
                                chain.getThisObject(), drag, config, reject);
                        if (reject) {
                            return null;
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
        } catch (Throwable error) {
            // Picker + vacancy guards still protect commit paths on Launcher variants where the
            // package-private DragObject class is not exposed by the decompiler/class loader.
            MainHook.log(TAG + " drag no-space hook unavailable: " + error);
        }
    }

    private static void traceDrag(
            String stage,
            Object cellLayout,
            Object dragObject,
            HomeGridInstallConfig config,
            boolean reject) {
        if (!MainHook.debugLogging) return;
        try {
            Object dragInfo = dragObject == null
                    ? null : HookUtil.tryInvoke(dragObject, "getDragInfo").value();
            int spanX = dragInfo == null ? -1 : HookUtil.getIntField(dragInfo, "spanX");
            int spanY = dragInfo == null ? -1 : HookUtil.getIntField(dragInfo, "spanY");

            String gridName = "?";
            int countX = -1;
            int countY = -1;
            if (cellLayout != null) {
                Object grid = HookUtil.tryInvoke(cellLayout, "getGridConfig").value();
                if (grid == null) grid = HookUtil.getField(cellLayout, "mGridConfig");
                if (grid != null) {
                    Object name = HookUtil.tryInvoke(grid, "getName").value();
                    Object x = HookUtil.tryInvoke(grid, "getCountX").value();
                    Object y = HookUtil.tryInvoke(grid, "getCountY").value();
                    if (name != null) gridName = String.valueOf(name);
                    if (x instanceof Integer) countX = (Integer) x;
                    if (y instanceof Integer) countY = (Integer) y;
                }
            }

            String snapshot = "drag="
                    + (dragObject == null ? "null" : dragObject.getClass().getName())
                    + " info="
                    + (dragInfo == null ? "null" : dragInfo.getClass().getName())
                    + " span=" + spanX + "x" + spanY
                    + " grid=" + gridName + " " + countX + "x" + countY
                    + " configured=" + config.columns + "x" + config.rows
                    + " reject=" + reject;
            synchronized (LAST_TRACE) {
                if (snapshot.equals(LAST_TRACE.get(stage))) return;
                LAST_TRACE.put(stage, snapshot);
            }
            MainHook.log(TAG + " " + stage + " " + snapshot);
        } catch (Throwable error) {
            MainHook.log(TAG + " " + stage + " trace unavailable: " + error);
        }
    }

    private static boolean shouldRejectDrag(
            Object cellLayout, Object dragObject, HomeGridInstallConfig config) {
        if (cellLayout == null || dragObject == null) return false;
        try {
            Object dragInfo = HookUtil.tryInvoke(dragObject, "getDragInfo").value();
            if (dragInfo == null) return false;
            int spanX = HookUtil.getIntField(dragInfo, "spanX");
            int spanY = HookUtil.getIntField(dragInfo, "spanY");
            return shouldRejectSpan(cellLayout, spanX, spanY, config);
        } catch (Throwable error) {
            MainHook.log(TAG + " drag compatibility check unavailable: " + error);
            return false;
        }
    }

    private static boolean shouldRejectSpan(
            Object cellLayout, int spanX, int spanY, HomeGridInstallConfig config) {
        if (cellLayout == null) return false;
        try {
            Object grid = HookUtil.tryInvoke(cellLayout, "getGridConfig").value();
            if (grid == null) grid = HookUtil.getField(cellLayout, "mGridConfig");
            if (grid == null) return false;

            String name = String.valueOf(HookUtil.requireInvoke(grid, "getName"));
            int countX = (Integer) HookUtil.requireInvoke(grid, "getCountX");
            int countY = (Integer) HookUtil.requireInvoke(grid, "getCountY");

            boolean reject = HomeGridWidgetAdmissionPolicy.shouldReject(
                    name, countX, countY,
                    config.columns, config.rows,
                    spanX, spanY);
            if (reject) {
                MainHook.log(TAG + " rejected span=" + spanX + "x" + spanY
                        + " grid=" + name + " " + countX + "x" + countY);
            }
            return reject;
        } catch (Throwable error) {
            MainHook.log(TAG + " compatibility check unavailable: " + error);
            return false;
        }
    }

    private static void showNativeOutOfSpace(Object launcher) {
        if (!(launcher instanceof Context)) return;
        try {
            Context context = (Context) launcher;
            Resources resources = context.getResources();
            int resId = resources.getIdentifier(
                    "out_of_space", "string", context.getPackageName());
            if (resId != 0) {
                HookUtil.requireInvoke(launcher, "showError", resId);
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " native out-of-space prompt unavailable: " + error);
        }
    }
}
