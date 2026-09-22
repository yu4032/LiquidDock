package com.hellovoid.liquiddock;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

import java.util.WeakHashMap;

/**
 * Adapts MIUI CellLayout fields to the Android-free geometry policy.
 *
 * <p>MIUI remains authoritative for placement, occupancy and item matrices. This hook only rewrites
 * geometry derived from the vendor snapshot and reasserts widget frames against that geometry.</p>
 */
final class HomeGridCellGeometryHook {
    private static final WeakHashMap<View, Long> PREPARED_GEOMETRY = new WeakHashMap<>();

    private static HomeGridInstallConfig installConfig;
    private static HomeGridWorkstationGeometryConfig workstationConfig =
            HomeGridWorkstationGeometryConfig.NONE;

    private HomeGridCellGeometryHook() {}

    static void install(ClassLoader classLoader, HomeGridInstallConfig config) {
        installConfig = config;
        Class<?> cellLayout;
        try {
            cellLayout = Class.forName("com.miui.home.launcher.CellLayout", false, classLoader);
        } catch (ClassNotFoundException error) {
            throw new RuntimeException(error);
        }

        HookUtil.hookMethod(cellLayout, "calculateXsAndYs", new Class<?>[0], chain -> {
            Object owner = chain.getThisObject();
            applyCellLayoutOffsets(owner);
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            applyCellLayoutOffsets(owner);
            rebuildCellCoordinates(owner);
            return result;
        });

        try {
            Class<?> itemInfo = Class.forName(
                    "com.miui.home.launcher.ItemInfo", false, classLoader);
            Class<?> layoutParams = Class.forName(
                    "com.miui.home.launcher.CellLayout$LayoutParams", false, classLoader);
            HookUtil.hookMethod(cellLayout, "setupLayoutParam",
                    new Class<?>[]{int.class, int.class, itemInfo, layoutParams}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        applyWidgetGridSize(
                                chain.getThisObject(),
                                (Integer) args[0],
                                (Integer) args[1],
                                args[2],
                                args[3]);
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC] widget span sizing hook unavailable: " + error);
        }

        HookUtil.hookMethod(cellLayout, "onLayout",
                new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class},
                chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    ViewGroup layout = (ViewGroup) chain.getThisObject();
                    prepareGeometryForLayout(layout);
                    Object result = chain.proceed(args);
                    enforceWidgetGridFrames(layout);
                    return result;
                });
    }

    static void setWorkstationConfig(HomeGridWorkstationGeometryConfig config) {
        workstationConfig = config == null
                ? HomeGridWorkstationGeometryConfig.NONE : config;
    }

    static boolean sizeMatchesOrientation(View view, int width, int height) {
        if (view == null) return false;
        boolean portrait = view.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        return HomeGridCellGeometryPolicy.sizeMatchesOrientation(portrait, width, height);
    }

    private static void prepareGeometryForLayout(View layout) {
        HomeGridInstallConfig current = installConfig;
        if (current == null || !current.enabled) return;
        int width = layout.getWidth();
        int height = layout.getHeight();
        if (width <= 0 || height <= 0 || !sizeMatchesOrientation(layout, width, height)) return;

        int orientation = layout.getResources().getConfiguration().orientation;
        long signature = (((long) orientation & 0xffL) << 56)
                ^ (((long) width & 0x0fffffffL) << 28)
                ^ ((long) height & 0x0fffffffL);
        synchronized (PREPARED_GEOMETRY) {
            Long previous = PREPARED_GEOMETRY.get(layout);
            if (previous != null && previous == signature) return;
            PREPARED_GEOMETRY.put(layout, signature);
        }

        applyCellLayoutOffsets(layout);
        rebuildCellCoordinates(layout);
        MainHook.log("[DC] CellLayout geometry prepared "
                + width + "x" + height + " orientation=" + orientation);
    }

    private static void applyCellLayoutOffsets(Object cellLayout) {
        try {
            HomeGridInstallConfig current = installConfig;
            if (current == null) return;
            Object gridConfig = HookUtil.getField(cellLayout, "mGridConfig");
            if (gridConfig == null || !(cellLayout instanceof View)) return;
            View layout = (View) cellLayout;
            boolean portrait = layout.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT;

            int countX = (Integer) HookUtil.requireInvoke(gridConfig, "getCountX");
            int countY = (Integer) HookUtil.requireInvoke(gridConfig, "getCountY");
            if (countX <= 0 || countY <= 0) return;

            // Occupancy remains vendor-owned. We only mirror the dimensions of MIUI's real matrix
            // when its GridConfig is transiently stale during a rotation/layout boundary.
            Object gridCells = HookUtil.getField(cellLayout, "mGridCell");
            if (gridCells != null) {
                int matrixX = java.lang.reflect.Array.getLength(gridCells);
                int matrixY = matrixX == 0 ? 0
                        : java.lang.reflect.Array.getLength(java.lang.reflect.Array.get(gridCells, 0));
                if (matrixX != countX || matrixY != countY) {
                    MainHook.log("[DC] grid count/matrix mismatch: config="
                            + countX + "x" + countY + " matrix=" + matrixX + "x" + matrixY);
                    countX = matrixX;
                    countY = matrixY;
                }
            }
            if (countX <= 0 || countY <= 0) return;

            int[] xs = (int[]) HookUtil.getField(cellLayout, "mXs");
            int[] ys = (int[]) HookUtil.getField(cellLayout, "mYs");
            if (xs == null || xs.length != countX) {
                HookUtil.setField(cellLayout, "mXs", new int[countX]);
            }
            if (ys == null || ys.length != countY) {
                HookUtil.setField(cellLayout, "mYs", new int[countY]);
            }
            HookUtil.setIntField(cellLayout, "mHCells", countX);
            HookUtil.setIntField(cellLayout, "mVCells", countY);

            int baseCell = (Integer) HookUtil.requireInvoke(gridConfig, "getCellSize");
            if (baseCell <= 0) return;
            int configLeft = (Integer) HookUtil.requireInvoke(gridConfig, "getLeft");
            int baseTop = (Integer) HookUtil.requireInvoke(gridConfig, "getTop");
            int baseWidthGap = HookUtil.getIntField(cellLayout, "mWidthGap");
            int baseHeightGap = Math.max(0, HookUtil.getIntField(cellLayout, "mHeightGap"));
            int width = layout.getWidth();
            int height = layout.getHeight();
            if (width <= 0 || height <= 0) return;

            boolean workstationAllApps = isLaptopAllApps(cellLayout);
            if (!workstationAllApps && !sizeMatchesOrientation(layout, width, height)) return;
            boolean workstationActive = workstationAllApps || MainHook.isWorkstationMode();

            int dockBarHeight = 0;
            if (!workstationAllApps && current.enabled) {
                HookUtil.InvocationResult<Object> dockResult =
                        HookUtil.tryInvoke(gridConfig, "getDockBarHeight");
                if (dockResult.succeeded() && dockResult.value() instanceof Integer) {
                    dockBarHeight = Math.max(0, (Integer) dockResult.value());
                }
            }

            HomeGridCellGeometryPolicy.Result geometry =
                    HomeGridCellGeometryPolicy.calculate(new HomeGridCellGeometryPolicy.Input(
                            current,
                            workstationConfig,
                            portrait,
                            workstationActive,
                            workstationAllApps,
                            width,
                            height,
                            countX,
                            countY,
                            baseCell,
                            configLeft,
                            baseTop,
                            baseWidthGap,
                            baseHeightGap,
                            dockBarHeight));
            if (geometry == null) return;

            HookUtil.setIntField(cellLayout, "mCellPaddingLeft", geometry.left);
            HookUtil.setIntField(cellLayout, "mCellPaddingTop", geometry.top);
            HookUtil.setIntField(cellLayout, "mCellWidth", geometry.cellSize);
            HookUtil.setIntField(cellLayout, "mCellHeight", geometry.cellSize);
            HookUtil.setIntField(cellLayout, "mWidthGap", geometry.widthGap);
            HookUtil.setIntField(cellLayout, "mHeightGap", geometry.heightGap);
        } catch (Throwable error) {
            MainHook.log("[DC] CellLayout offset apply failed: " + error);
        }
    }

    private static boolean isLaptopAllApps(Object cellLayout) {
        // Stable Launcher identity: CellLayout.setGridType() stores
        // GRID_TYPE_IN_ALL_APPS_WORKSPACE directly in CellLayout.mGridType.
        String gridType = "";
        try {
            Object value = HookUtil.getField(cellLayout, "mGridType");
            if (value != null) gridType = String.valueOf(value);
        } catch (Throwable ignored) {}
        if (gridType.isEmpty()) {
            HookUtil.InvocationResult<Object> gridTypeResult =
                    HookUtil.tryInvoke(cellLayout, "getGridType");
            if (gridTypeResult.succeeded() && gridTypeResult.value() != null) {
                gridType = String.valueOf(gridTypeResult.value());
            }
        }

        boolean exact = false;
        HookUtil.InvocationResult<Object> exactResult =
                HookUtil.tryInvoke(cellLayout, "isInLapTopAllApps");
        if (exactResult.succeeded()) exact = Boolean.TRUE.equals(exactResult.value());

        StringBuilder ancestry = new StringBuilder();
        if (cellLayout instanceof View) {
            android.view.ViewParent parent = ((View) cellLayout).getParent();
            int depth = 0;
            while (parent != null && depth++ < 8) {
                if (ancestry.length() > 0) ancestry.append('>');
                ancestry.append(parent.getClass().getName());
                parent = parent.getParent();
            }
        }
        return WorkstationLayoutClassifier.matches(exact, gridType, ancestry.toString());
    }

    private static void rebuildCellCoordinates(Object cellLayout) {
        try {
            int countX = HookUtil.getIntField(cellLayout, "mHCells");
            int countY = HookUtil.getIntField(cellLayout, "mVCells");
            int cellWidth = HookUtil.getIntField(cellLayout, "mCellWidth");
            int cellHeight = HookUtil.getIntField(cellLayout, "mCellHeight");
            int widthGap = HookUtil.getIntField(cellLayout, "mWidthGap");
            int heightGap = HookUtil.getIntField(cellLayout, "mHeightGap");
            int left = HookUtil.getIntField(cellLayout, "mCellPaddingLeft");
            int top = HookUtil.getIntField(cellLayout, "mCellPaddingTop");
            if (countX <= 0 || countY <= 0 || cellWidth <= 0 || cellHeight <= 0) return;
            int[] xs = new int[countX];
            int[] ys = new int[countY];
            for (int x = 0; x < countX; x++) xs[x] = left + x * (cellWidth + widthGap);
            for (int y = 0; y < countY; y++) ys[y] = top + y * (cellHeight + heightGap);
            HookUtil.setField(cellLayout, "mXs", xs);
            HookUtil.setField(cellLayout, "mYs", ys);
        } catch (Throwable error) {
            MainHook.log("[DC] final cell coordinate rebuild failed: " + error);
        }
    }

    private static void applyWidgetGridSize(
            Object cellLayout, int cellX, int cellY, Object info, Object layoutParams) {
        try {
            HomeGridInstallConfig current = installConfig;
            if (current == null || !current.enabled || info == null || layoutParams == null) return;

            if (!isWidget(info)) return;
            int spanX = HookUtil.getIntField(info, "spanX");
            int spanY = HookUtil.getIntField(info, "spanY");
            if (!WidgetGridSizing.isSupportedSpec(spanX, spanY)
                    || !(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
                return;
            }

            int cellWidth = HookUtil.getIntField(cellLayout, "mCellWidth");
            int cellHeight = HookUtil.getIntField(cellLayout, "mCellHeight");
            int widthGap = Math.max(0, HookUtil.getIntField(cellLayout, "mWidthGap"));
            int heightGap = Math.max(0, HookUtil.getIntField(cellLayout, "mHeightGap"));
            int[] xs = (int[]) HookUtil.getField(cellLayout, "mXs");
            int[] ys = (int[]) HookUtil.getField(cellLayout, "mYs");
            if (cellWidth <= 0 || cellHeight <= 0 || xs == null || ys == null) return;

            int[] rect = WidgetGridSizing.gridRect(
                    cellX, cellY, spanX, spanY, xs, ys,
                    cellWidth, cellHeight, widthGap, heightGap);
            if (rect[2] <= 0 || rect[3] <= 0) return;

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) layoutParams;
            params.width = rect[2];
            params.height = rect[3];
            HookUtil.setIntField(layoutParams, "x", rect[0]);
            HookUtil.setIntField(layoutParams, "y", rect[1]);
        } catch (Throwable error) {
            MainHook.log("[DC] widget grid bounds failed: " + error);
        }
    }

    private static void enforceWidgetGridFrames(ViewGroup cellLayout) {
        HomeGridInstallConfig current = installConfig;
        if (current == null || !current.enabled || cellLayout == null) return;
        try {
            int cellWidth = HookUtil.getIntField(cellLayout, "mCellWidth");
            int cellHeight = HookUtil.getIntField(cellLayout, "mCellHeight");
            int widthGap = Math.max(0, HookUtil.getIntField(cellLayout, "mWidthGap"));
            int heightGap = Math.max(0, HookUtil.getIntField(cellLayout, "mHeightGap"));
            int[] xs = (int[]) HookUtil.getField(cellLayout, "mXs");
            int[] ys = (int[]) HookUtil.getField(cellLayout, "mYs");
            if (cellWidth <= 0 || cellHeight <= 0 || xs == null || ys == null) return;

            for (int i = 0; i < cellLayout.getChildCount(); i++) {
                View child = cellLayout.getChildAt(i);
                if (child == null || child.getVisibility() == View.GONE) continue;
                Object info = child.getTag();
                if (info == null || !isWidget(info)) continue;

                Object paramsObject = child.getLayoutParams();
                if (paramsObject == null) continue;
                try {
                    if (HookUtil.getBooleanField(paramsObject, "isDragging")) continue;
                } catch (Throwable ignored) {}

                int spanX;
                int spanY;
                int cellX;
                int cellY;
                try {
                    spanX = HookUtil.getIntField(info, "spanX");
                    spanY = HookUtil.getIntField(info, "spanY");
                    cellX = HookUtil.getIntField(info, "cellX");
                    cellY = HookUtil.getIntField(info, "cellY");
                } catch (Throwable ignored) {
                    continue;
                }
                if (!WidgetGridSizing.isSupportedSpec(spanX, spanY)) continue;

                int[] rect = WidgetGridSizing.gridRect(
                        cellX, cellY, spanX, spanY, xs, ys,
                        cellWidth, cellHeight, widthGap, heightGap);
                int targetWidth = rect[2];
                int targetHeight = rect[3];
                if (targetWidth <= 0 || targetHeight <= 0) continue;

                if (paramsObject instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams params =
                            (ViewGroup.MarginLayoutParams) paramsObject;
                    params.width = targetWidth;
                    params.height = targetHeight;
                }
                try {
                    HookUtil.setIntField(paramsObject, "x", rect[0]);
                    HookUtil.setIntField(paramsObject, "y", rect[1]);
                } catch (Throwable ignored) {}

                if (child.getMeasuredWidth() != targetWidth
                        || child.getMeasuredHeight() != targetHeight) {
                    child.measure(
                            View.MeasureSpec.makeMeasureSpec(
                                    targetWidth, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(
                                    targetHeight, View.MeasureSpec.EXACTLY));
                }
                int right = rect[0] + targetWidth;
                int bottom = rect[1] + targetHeight;
                if (child.getLeft() != rect[0] || child.getTop() != rect[1]
                        || child.getRight() != right || child.getBottom() != bottom) {
                    child.layout(rect[0], rect[1], right, bottom);
                }
            }
        } catch (Throwable error) {
            MainHook.log("[DC] final widget frame enforcement failed: " + error);
        }
    }

    private static boolean isWidget(Object info) {
        HookUtil.InvocationResult<Object> widgetResult = HookUtil.tryInvoke(info, "isWidget");
        if (widgetResult.succeeded() && Boolean.TRUE.equals(widgetResult.value())) return true;
        try {
            int itemType = HookUtil.getIntField(info, "itemType");
            return itemType == 4 || itemType == 5 || itemType == 19;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
