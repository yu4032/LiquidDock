package com.hellovoid.liquiddock;

import android.content.Context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    static void scheduleAllPageRefresh() {
        HomeGridRotationRefreshHook.scheduleAllPageRefresh();
    }

    static void install(ClassLoader classLoader, HomeGridInstallConfig config) {
        if (config == null || !config.enabled) return;
        try {
            Class<?> compat = Class.forName(PAD_CELL_COUNT, false, classLoader);
            hookAxis(compat, "getCellCountXMin", true, config);
            hookAxis(compat, "getCellCountXDef", true, config);
            hookAxis(compat, "getCellCountYMin", false, config);
            hookAxis(compat, "getCellCountYDef", false, config);

            HomeGridAuthorityHook.install(classLoader, config);
            installRotationTransform(classLoader, config);
            HomeGridPageIndicatorHook.install(classLoader, config);
            HomeGridCellGeometryHook.install(classLoader, config);
            HomeGridFolderAlignmentHook.install(classLoader);
            HomeGridRotationRefreshHook.install(classLoader);
        } catch (Throwable error) {
            MainHook.log("[DC] home grid hook unavailable: " + error);
        }
    }

    private static void installRotationTransform(
            ClassLoader classLoader, HomeGridInstallConfig config) {
        final Class<?> rule;
        try {
            rule = Class.forName(
                    "com.miui.home.launcher.compat.LayoutTransformRuleGridChanged",
                    false,
                    classLoader);
        } catch (ClassNotFoundException error) {
            throw new RuntimeException(error);
        }

        HookUtil.hookMethod(rule, "transformToDstLayout", new Class<?>[0], chain -> {
            Object owner = chain.getThisObject();
            int h = (Integer) HookUtil.requireInvoke(owner, "getMHCells");
            int v = (Integer) HookUtil.requireInvoke(owner, "getMVCells");
            if (!config.matchesGrid(h, v)) {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }
            if (!reflowOccupiedMatrix(owner)) {
                MainHook.log("[DC][HomeGridRotation] generic transform failed for "
                        + h + "x" + v + "; preserving empty destination rather than invoking "
                        + "the vendor 4x6-only transform");
            }
            return owner;
        });
    }

    private static boolean reflowOccupiedMatrix(Object owner) {
        try {
            Object[][] src = (Object[][]) HookUtil.getField(owner, "mSrcOccupied");
            Object[][] dst = (Object[][]) HookUtil.getField(owner, "mDstOccupied");
            if (!rectangular(src) || !rectangular(dst)) return false;

            int srcCols = src.length;
            int srcRows = src[0].length;
            int dstCols = dst.length;
            int dstRows = dst[0].length;

            /*
             * LayoutTransformRuleGridChanged can be re-entered while Launcher is settling a
             * physical rotation. Once source and destination already have the same topology,
             * re-planning from that intermediate matrix would rotate/repack icons a second time.
             * Preserve it verbatim instead.
             */
            HomeGridRotationTopologyPolicy.Action topology =
                    HomeGridRotationTopologyPolicy.classify(
                            srcCols, srcRows, dstCols, dstRows);
            if (topology == HomeGridRotationTopologyPolicy.Action.PRESERVE) {
                return commitDataMatrix(dst, extractDataMatrix(src, dstCols, dstRows));
            }
            if (topology != HomeGridRotationTopologyPolicy.Action.TRANSPOSE) {
                MainHook.log("[DC][HomeGridRotation] defer topology src="
                        + srcCols + "x" + srcRows + " dst=" + dstCols + "x" + dstRows);
                return false;
            }

            Map<Object, TransformItem> byData = new LinkedHashMap<>();
            for (int x = 0; x < srcCols; x++) {
                for (int y = 0; y < srcRows; y++) {
                    Object info = src[x][y];
                    if (info == null) continue;
                    HookUtil.InvocationResult<Object> dataResult =
                            HookUtil.tryInvoke(info, "getMData");
                    if (!dataResult.succeeded() || dataResult.value() == null) continue;
                    Object data = dataResult.value();
                    TransformItem item = byData.get(data);
                    if (item == null) {
                        item = new TransformItem(info, data, x, y);
                        byData.put(data, item);
                    } else {
                        item.include(x, y);
                    }
                }
            }

            List<TransformItem> items = new ArrayList<>(byData.values());
            items.sort(Comparator
                    .comparingInt((TransformItem item) -> item.area() == 1 ? 1 : 0)
                    .thenComparingLong(item -> item.stableKey)
                    .thenComparingInt(item -> item.minY * srcCols + item.minX));

            boolean[][] occupied = new boolean[dstCols][dstRows];
            Object[][] plannedData = new Object[dstCols][dstRows];

            for (TransformItem item : items) {
                int spanX = item.width();
                int spanY = item.height();
                if (spanX > dstCols || spanY > dstRows) {
                    MainHook.log("[DC][HomeGridRotation] item span " + spanX + "x" + spanY
                            + " cannot fit destination " + dstCols + "x" + dstRows);
                    return false;
                }

                int[] cell = HomeGridRotationPlacementPolicy.findNearestFreeCell(
                        occupied,
                        dstCols, dstRows,
                        spanX, spanY,
                        srcCols, srcRows,
                        item.minX, item.minY);
                if (cell == null
                        || !HomeGridRotationPlacementPolicy.reserve(
                                occupied, dstCols, dstRows,
                                cell[0], cell[1], spanX, spanY)) {
                    MainHook.log("[DC][HomeGridRotation] no destination slot for span "
                            + spanX + "x" + spanY);
                    return false;
                }

                for (int x = cell[0]; x < cell[0] + spanX; x++) {
                    for (int y = cell[1]; y < cell[1] + spanY; y++) {
                        plannedData[x][y] = item.data;
                    }
                }
            }

            // mDstOccupied is a dense LayoutTransformInfo[][]: empty cells are wrappers whose
            // mData is null. Never replace a wrapper with null; vendor transformToHVArray()
            // dereferences every cell before checking its data.
            return commitDataMatrix(dst, plannedData);
        } catch (Throwable error) {
            MainHook.log("[DC][HomeGridRotation] reflow error: " + error);
            return false;
        }
    }

    private static boolean rectangular(Object[][] matrix) {
        if (matrix == null || matrix.length == 0 || matrix[0] == null
                || matrix[0].length == 0) {
            return false;
        }
        int rows = matrix[0].length;
        for (Object[] column : matrix) {
            if (column == null || column.length != rows) return false;
        }
        return true;
    }

    private static Object[][] extractDataMatrix(
            Object[][] source, int columns, int rows) {
        Object[][] data = new Object[columns][rows];
        for (int x = 0; x < columns; x++) {
            for (int y = 0; y < rows; y++) {
                Object wrapper = source[x][y];
                if (wrapper == null) return null;
                HookUtil.InvocationResult<Object> result =
                        HookUtil.tryInvoke(wrapper, "getMData");
                if (!result.succeeded()) return null;
                data[x][y] = result.value();
            }
        }
        return data;
    }

    private static boolean commitDataMatrix(
            Object[][] destination, Object[][] plannedData) {
        if (!rectangular(destination) || plannedData == null
                || destination.length != plannedData.length
                || plannedData.length == 0) {
            return false;
        }
        for (int x = 0; x < destination.length; x++) {
            if (plannedData[x] == null
                    || destination[x].length != plannedData[x].length) {
                return false;
            }
            for (int y = 0; y < destination[x].length; y++) {
                // Critical vendor contract: the LayoutTransformInfo wrapper itself is dense.
                if (destination[x][y] == null) return false;
            }
        }

        try {
            for (int x = 0; x < destination.length; x++) {
                for (int y = 0; y < destination[x].length; y++) {
                    HookUtil.setField(destination[x][y], "mData", plannedData[x][y]);
                }
            }
            return true;
        } catch (Throwable error) {
            MainHook.log("[DC][HomeGridRotation] failed to commit transform data: " + error);
            return false;
        }
    }

    private static long stableKey(Object data) {
        try {
            long id = HookUtil.getLongField(data, "id");
            if (id >= 0) return id;
        } catch (Throwable ignored) {}
        return 0x8000000000000000L
                | (System.identityHashCode(data) & 0xffffffffL);
    }

    private static final class TransformItem {
        final Object data;
        final long stableKey;
        int minX;
        int minY;
        int maxX;
        int maxY;

        TransformItem(Object info, Object data, int x, int y) {
            this.data = data;
            this.stableKey = stableKey(data);
            this.minX = this.maxX = x;
            this.minY = this.maxY = y;
        }

        void include(int x, int y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        int width() {
            return maxX - minX + 1;
        }

        int height() {
            return maxY - minY + 1;
        }

        int area() {
            return width() * height();
        }
    }

    private static void hookAxis(
            Class<?> compat, String method, boolean xAxis, HomeGridInstallConfig config) {
        HookUtil.hookMethod(compat, method, new Class<?>[]{Context.class}, chain -> {
            Context context = (Context) chain.getArg(0);
            boolean portrait = context.getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_PORTRAIT;
            return xAxis ? config.countX(portrait) : config.countY(portrait);
        });
    }
}
