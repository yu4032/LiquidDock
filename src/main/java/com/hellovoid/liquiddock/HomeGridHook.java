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
            if (!config.matchesGrid(h, v) || isVendorSixByFour(h, v)) {
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

    private static boolean isVendorSixByFour(int h, int v) {
        return (h == 6 && v == 4) || (h == 4 && v == 6);
    }

    private static boolean reflowOccupiedMatrix(Object owner) {
        try {
            Object[][] src = (Object[][]) HookUtil.getField(owner, "mSrcOccupied");
            Object[][] dst = (Object[][]) HookUtil.getField(owner, "mDstOccupied");
            if (src == null || dst == null || src.length == 0 || dst.length == 0
                    || src[0] == null || dst[0] == null) {
                return false;
            }

            int srcCols = src.length;
            int srcRows = src[0].length;
            int dstCols = dst.length;
            int dstRows = dst[0].length;
            Map<Object, TransformItem> byData = new LinkedHashMap<>();

            for (int x = 0; x < srcCols; x++) {
                if (src[x] == null) continue;
                for (int y = 0; y < Math.min(srcRows, src[x].length); y++) {
                    Object info = src[x][y];
                    if (info == null) continue;
                    HookUtil.InvocationResult<Object> dataResult =
                            HookUtil.tryInvoke(info, "getMData");
                    if (!dataResult.succeeded() || dataResult.value() == null) continue;
                    Object data = dataResult.value();
                    TransformItem item = byData.get(data);
                    if (item == null) {
                        item = new TransformItem(info, x, y);
                        byData.put(data, item);
                    } else {
                        item.include(x, y);
                    }
                }
            }

            List<TransformItem> items = new ArrayList<>(byData.values());
            items.sort(Comparator
                    .comparingInt((TransformItem item) -> item.area() == 1 ? 1 : 0)
                    .thenComparingInt(item -> item.minY * srcCols + item.minX));

            boolean[][] occupied = new boolean[dstCols][dstRows];
            for (TransformItem item : items) {
                int spanX = item.width();
                int spanY = item.height();
                if (spanX > dstCols || spanY > dstRows) {
                    MainHook.log("[DC][HomeGridRotation] item span " + spanX + "x" + spanY
                            + " cannot fit destination " + dstCols + "x" + dstRows);
                    return false;
                }
                int preferred = Math.min(
                        dstCols * dstRows - 1,
                        item.minY * srcCols + item.minX);
                int[] cell = findFreeCell(occupied, dstCols, dstRows, spanX, spanY, preferred);
                if (cell == null) {
                    MainHook.log("[DC][HomeGridRotation] no destination slot for span "
                            + spanX + "x" + spanY);
                    return false;
                }
                for (int x = cell[0]; x < cell[0] + spanX; x++) {
                    for (int y = cell[1]; y < cell[1] + spanY; y++) {
                        occupied[x][y] = true;
                        dst[x][y] = item.info;
                    }
                }
            }            return true;
        } catch (Throwable error) {
            MainHook.log("[DC][HomeGridRotation] reflow error: " + error);
            return false;
        }
    }

    private static int[] findFreeCell(
            boolean[][] occupied,
            int cols,
            int rows,
            int spanX,
            int spanY,
            int preferredLinear) {
        int total = cols * rows;
        for (int offset = 0; offset < total; offset++) {
            int index = (preferredLinear + offset) % total;
            int x = index % cols;
            int y = index / cols;
            if (x + spanX > cols || y + spanY > rows) continue;
            boolean free = true;
            for (int px = x; px < x + spanX && free; px++) {
                for (int py = y; py < y + spanY; py++) {
                    if (occupied[px][py]) {
                        free = false;
                        break;
                    }
                }
            }
            if (free) return new int[]{x, y};
        }
        return null;
    }

    private static final class TransformItem {
        final Object info;
        int minX;
        int minY;
        int maxX;
        int maxY;

        TransformItem(Object info, int x, int y) {
            this.info = info;
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
