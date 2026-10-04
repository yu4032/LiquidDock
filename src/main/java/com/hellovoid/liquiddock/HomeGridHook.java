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
    private static final String DEVICE_CONFIG =
            "com.miui.home.launcher.DeviceConfig";

    private HomeGridHook() {}

    static void setWorkstationMode(boolean enabled) {
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
            HomeGridCommittedLayoutCaptureHook.install(classLoader, config);
            HomeGridSqueezePlannerHook.install(classLoader, config);
            HomeGridWidgetAdmissionGuard.install(classLoader, config);
            installGridChangePreflight(classLoader, config);
            installRotationTransform(classLoader, config);
            HomeGridPageIndicatorHook.install(classLoader, config);
            HomeGridCellGeometryHook.install(classLoader, config);
            HomeGridFolderAlignmentHook.install(classLoader);
            HomeGridRotationRefreshHook.install(classLoader);
        } catch (Throwable error) {
            MainHook.log("[DC] home grid hook unavailable: " + error);
        }
    }

    private static void installGridChangePreflight(
            ClassLoader classLoader, HomeGridInstallConfig config) {
        final Class<?> cellLayout;
        final Class<?> gridConfig;
        try {
            cellLayout = Class.forName(
                    "com.miui.home.launcher.CellLayout", false, classLoader);
            gridConfig = Class.forName(
                    "com.miui.home.launcher.grid.GridConfig", false, classLoader);
        } catch (ClassNotFoundException error) {
            throw new RuntimeException(error);
        }

        HookUtil.hookMethod(cellLayout, "setGrid", new Class<?>[]{gridConfig}, chain -> {
            Object target = chain.getArg(0);
            Object owner = chain.getThisObject();
            if (target == null || !(owner instanceof android.view.View)) {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }

            Object current = HookUtil.tryInvoke(owner, "getGridConfig").value();
            if (current == null) {
                // First load has no safe previous GridConfig to retain.
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }

            try {
                String name = String.valueOf(HookUtil.requireInvoke(target, "getName"));
                int[] expected = HomeGridWorkspaceGridPolicy.fullScreenCounts(
                        name, config.columns, config.rows);
                if (expected == null) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }

                int columns = (Integer) HookUtil.requireInvoke(target, "getCountX");
                int rows = (Integer) HookUtil.requireInvoke(target, "getCountY");
                if (columns != expected[0] || rows != expected[1]) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }

                HomeGridWorkspaceSpanPreflight.Result preflight =
                        HomeGridWorkspaceSpanRuntime.scan(columns, rows);
                if (preflight.available && !preflight.compatible) {
                    MainHook.log("[DC][HomeGridRotation] blocked incompatible grid switch "
                            + name + " " + columns + "x" + rows
                            + " blockingSpan=" + preflight.blockingSpanX
                            + "x" + preflight.blockingSpanY);
                    return null;
                }
            } catch (Throwable error) {
                MainHook.log("[DC][HomeGridRotation] grid preflight unavailable: " + error);
            }
            return chain.proceed(chain.getArgs().toArray(new Object[0]));
        });
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
            if (!transformAtomic(owner)) {
                MainHook.log("[DC][HomeGridRotation] atomic transform failed for "
                        + h + "x" + v);
            }
            return owner;
        });
    }

    private static boolean isVendorSixByFour(int h, int v) {
        return (h == 6 && v == 4) || (h == 4 && v == 6);
    }

    /**
     * Uses orientation-specific positions synchronously inside MIUI's transform.
     *
     * <p>The current orientation is captured before any move. If the target orientation has already
     * been seen, its exact positions are restored directly into mDstOccupied. Only the first visit
     * to an orientation is generated from MIUI's macroblock order. There is no delayed View restore
     * after configuration change, so widgets cannot be snapped again after Launcher has laid out the
     * target workspace.</p>
     */
    private static boolean transformAtomic(Object owner) {
        try {
            Object[][] src = (Object[][]) HookUtil.getField(owner, "mSrcOccupied");
            Object[][] dst = (Object[][]) HookUtil.getField(owner, "mDstOccupied");
            if (!validMatrix(src) || !validMatrix(dst)) return false;

            int srcCols = src.length;
            int srcRows = src[0].length;
            int dstCols = dst.length;
            int dstRows = dst[0].length;
            if (srcCols * srcRows != dstCols * dstRows) return false;

            Map<Object, TransformItem> byData = collectItems(src, srcCols, srcRows);
            if (byData.isEmpty()) return true;

            if (!allRectangular(byData.values())) {
                MainHook.log("[DC][HomeGridRotation] rejected malformed source "
                        + srcCols + "x" + srcRows);
                return false;
            }

            Long sourceScreenId = commonScreenId(byData.values());
            Map<Long, HomeGridRotationPositionMemory.Position> sourcePositions =
                    positionsOf(byData.values());

            // LayoutTransformRule.init() creates mDstOccupied as a fresh SPACE_INFO matrix.
            // Preserve those sentinels: LayoutTransformHelperGridChanged.transformToHVArray()
            // dereferences every target cell unconditionally, including empty cells.
            Object[][] staged = HomeGridTransformMatrixPolicy.copyTemplate(
                    dst, dstCols, dstRows);
            boolean[][] occupied = new boolean[dstCols][dstRows];

            if (sourceScreenId != null && restoreRemembered(
                    byData.values(), staged, occupied,
                    dstCols, dstRows, sourceScreenId)) {
                copyInto(staged, dst, dstCols, dstRows);
                HomeGridRotationPositionMemory.save(
                        srcCols, srcRows, sourceScreenId, sourcePositions);
                return true;
            }

            staged = HomeGridTransformMatrixPolicy.copyTemplate(
                    dst, dstCols, dstRows);
            occupied = new boolean[dstCols][dstRows];
            Map<Long, HomeGridRotationPositionMemory.Position> targetPositions =
                    new LinkedHashMap<>();

            List<TransformItem> widgets = new ArrayList<>();
            List<TransformItem> icons = new ArrayList<>();
            for (TransformItem item : byData.values()) {
                if (item.isWidget()) widgets.add(item);
                else icons.add(item);
            }

            widgets.sort(Comparator
                    .comparingInt(TransformItem::area).reversed()
                    .thenComparingInt(item -> HomeGridRotationCellMap.rank(
                            srcCols, srcRows, item.minX, item.minY)));

            for (TransformItem widget : widgets) {
                HomeGridRotationCellMap.Cell mapped = HomeGridRotationCellMap.map(
                        srcCols, srcRows, dstCols, dstRows,
                        widget.minX, widget.minY);
                if (mapped == null) return false;

                int preferredX = Math.max(
                        0, Math.min(mapped.x, dstCols - widget.width()));
                int preferredY = Math.max(
                        0, Math.min(mapped.y, dstRows - widget.height()));
                int[] target = findNearestWidgetCell(
                        occupied, dstCols, dstRows,
                        widget.width(), widget.height(),
                        preferredX, preferredY);
                if (target == null) return false;

                occupy(staged, occupied, widget.info,
                        target[0], target[1], widget.width(), widget.height());
                remember(targetPositions, widget, target[0], target[1]);
            }

            boolean rtl = isLayoutRtl(owner);
            icons.sort((left, right) -> {
                int byRow = Integer.compare(left.minY, right.minY);
                if (byRow != 0) return byRow;
                return rtl
                        ? Integer.compare(right.minX, left.minX)
                        : Integer.compare(left.minX, right.minX);
            });

            int iconIndex = 0;
            for (int y = 0; y < dstRows && iconIndex < icons.size(); y++) {
                if (rtl) {
                    for (int x = dstCols - 1; x >= 0 && iconIndex < icons.size(); x--) {
                        if (occupied[x][y]) continue;
                        TransformItem icon = icons.get(iconIndex++);
                        occupy(staged, occupied, icon.info, x, y, 1, 1);
                        remember(targetPositions, icon, x, y);
                    }
                } else {
                    for (int x = 0; x < dstCols && iconIndex < icons.size(); x++) {
                        if (occupied[x][y]) continue;
                        TransformItem icon = icons.get(iconIndex++);
                        occupy(staged, occupied, icon.info, x, y, 1, 1);
                        remember(targetPositions, icon, x, y);
                    }
                }
            }
            if (iconIndex != icons.size()) return false;

            copyInto(staged, dst, dstCols, dstRows);
            if (sourceScreenId != null) {
                HomeGridRotationPositionMemory.save(
                        srcCols, srcRows, sourceScreenId, sourcePositions);
                HomeGridRotationPositionMemory.save(
                        dstCols, dstRows, sourceScreenId, targetPositions);
            }
            return true;
        } catch (Throwable error) {
            MainHook.log("[DC][HomeGridRotation] atomic transform error: " + error);
            return false;
        }
    }

    private static boolean allRectangular(Iterable<TransformItem> items) {
        for (TransformItem item : items) {
            if (item == null || !item.isRectangular()) return false;
        }
        return true;
    }

    private static boolean restoreRemembered(
            Iterable<TransformItem> items,
            Object[][] staged,
            boolean[][] occupied,
            int cols,
            int rows,
            long screenId) {
        ArrayList<Long> ids = new ArrayList<>();
        ArrayList<TransformItem> ordered = new ArrayList<>();
        for (TransformItem item : items) {
            if (item.stableId == null) return false;
            ids.add(item.stableId);
            ordered.add(item);
        }

        Map<Long, HomeGridRotationPositionMemory.Position> remembered =
                HomeGridRotationPositionMemory.load(cols, rows, screenId, ids);
        if (remembered.size() != ordered.size()) return false;

        ordered.sort(Comparator.comparingInt(TransformItem::area).reversed());
        for (TransformItem item : ordered) {
            HomeGridRotationPositionMemory.Position position =
                    remembered.get(item.stableId);
            if (position == null
                    || position.spanX != item.width()
                    || position.spanY != item.height()
                    || !fitsFree(occupied, cols, rows,
                            position.x, position.y,
                            item.width(), item.height())) {
                return false;
            }
            occupy(staged, occupied, item.info,
                    position.x, position.y,
                    item.width(), item.height());
        }
        return true;
    }

    private static Map<Long, HomeGridRotationPositionMemory.Position> positionsOf(
            Iterable<TransformItem> items) {
        LinkedHashMap<Long, HomeGridRotationPositionMemory.Position> positions =
                new LinkedHashMap<>();
        for (TransformItem item : items) {
            if (item.stableId == null) continue;
            positions.put(item.stableId,
                    new HomeGridRotationPositionMemory.Position(
                            item.minX, item.minY, item.width(), item.height()));
        }
        return positions;
    }

    private static void remember(
            Map<Long, HomeGridRotationPositionMemory.Position> positions,
            TransformItem item,
            int x,
            int y) {
        if (item.stableId == null) return;
        positions.put(item.stableId,
                new HomeGridRotationPositionMemory.Position(
                        x, y, item.width(), item.height()));
    }

    private static Map<Object, TransformItem> collectItems(
            Object[][] src, int srcCols, int srcRows) {
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
                    item = new TransformItem(
                            info, data, x, y, isWidgetInfo(info));
                    byData.put(data, item);
                } else {
                    item.include(x, y);
                }
            }
        }
        return byData;
    }

    private static Long commonScreenId(Iterable<TransformItem> items) {
        Long screenId = null;
        for (TransformItem item : items) {
            if (item.stableId == null || item.screenId == null) return null;
            if (screenId == null) {
                screenId = item.screenId;
            } else if (!screenId.equals(item.screenId)) {
                return null;
            }
        }
        return screenId;
    }

    private static boolean isWidgetInfo(Object info) {
        HookUtil.InvocationResult<Object> typeResult = HookUtil.tryInvoke(info, "getMType");
        if (!typeResult.succeeded() || typeResult.value() == null) return false;
        return !"ICON".equals(String.valueOf(typeResult.value()));
    }

    private static int[] findNearestWidgetCell(
            boolean[][] occupied,
            int cols,
            int rows,
            int spanX,
            int spanY,
            int preferredX,
            int preferredY) {
        int bestX = -1;
        int bestY = -1;
        long bestDistance = Long.MAX_VALUE;
        int bestRank = Integer.MAX_VALUE;

        int maxX = cols - spanX;
        int maxY = rows - spanY;
        for (int y = 0; y <= maxY; y++) {
            for (int x = 0; x <= maxX; x++) {
                if (!fitsFree(occupied, cols, rows, x, y, spanX, spanY)) continue;
                long dx = (long) x - preferredX;
                long dy = (long) y - preferredY;
                long distance = dx * dx + dy * dy;
                int rank = HomeGridRotationCellMap.rank(cols, rows, x, y);
                if (distance < bestDistance
                        || (distance == bestDistance && rank < bestRank)) {
                    bestDistance = distance;
                    bestRank = rank;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        return bestX < 0 ? null : new int[]{bestX, bestY};
    }

    private static boolean fitsFree(
            boolean[][] occupied,
            int cols,
            int rows,
            int x,
            int y,
            int spanX,
            int spanY) {
        if (x < 0 || y < 0 || spanX <= 0 || spanY <= 0
                || (long) x + spanX > cols || (long) y + spanY > rows) {
            return false;
        }
        for (int px = x; px < x + spanX; px++) {
            for (int py = y; py < y + spanY; py++) {
                if (occupied[px][py]) return false;
            }
        }
        return true;
    }

    private static void occupy(
            Object[][] staged,
            boolean[][] occupied,
            Object info,
            int x,
            int y,
            int spanX,
            int spanY) {
        for (int px = x; px < x + spanX; px++) {
            for (int py = y; py < y + spanY; py++) {
                staged[px][py] = info;
                occupied[px][py] = true;
            }
        }
    }

    private static boolean validMatrix(Object[][] matrix) {
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

    private static void copyInto(
            Object[][] source, Object[][] target, int cols, int rows) {
        for (int x = 0; x < cols; x++) {
            System.arraycopy(source[x], 0, target[x], 0, rows);
        }
    }

    private static boolean isLayoutRtl(Object owner) {
        try {
            ClassLoader classLoader = owner.getClass().getClassLoader();
            Class<?> deviceConfig = Class.forName(DEVICE_CONFIG, false, classLoader);
            HookUtil.InvocationResult<Object> result =
                    HookUtil.tryInvokeStatic(deviceConfig, "isLayoutRtl");
            return result.succeeded() && Boolean.TRUE.equals(result.value());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class TransformItem {
        final Object info;
        final boolean widgetType;
        final Long stableId;
        final Long screenId;
        final int expectedSpanX;
        final int expectedSpanY;
        int minX;
        int minY;
        int maxX;
        int maxY;
        int cells = 1;

        TransformItem(
                Object info, Object data, int x, int y, boolean widgetType) {
            this.info = info;
            this.widgetType = widgetType;
            HomeGridRotationPositionMemory.Identity identity =
                    HomeGridRotationPositionMemory.identity(data);
            this.stableId = identity == null ? null : identity.itemId;
            this.screenId = identity == null ? null : identity.screenId;
            int expectedX = -1;
            int expectedY = -1;
            if (data instanceof android.view.View) {
                Object tag = ((android.view.View) data).getTag();
                if (tag != null) {
                    try {
                        expectedX = HookUtil.getIntField(tag, "spanX");
                        expectedY = HookUtil.getIntField(tag, "spanY");
                    } catch (Throwable ignored) {}
                }
            }
            this.expectedSpanX = expectedX;
            this.expectedSpanY = expectedY;
            this.minX = this.maxX = x;
            this.minY = this.maxY = y;
        }

        void include(int x, int y) {
            cells++;
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

        boolean isRectangular() {
            if (cells != area()) return false;
            return expectedSpanX <= 0 || expectedSpanY <= 0
                    || (width() == expectedSpanX && height() == expectedSpanY);
        }

        boolean isWidget() {
            return widgetType || width() > 1 || height() > 1;
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
