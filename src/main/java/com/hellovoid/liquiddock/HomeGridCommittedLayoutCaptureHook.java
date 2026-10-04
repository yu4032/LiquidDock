package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captures only layouts that MIUI has actually committed.
 *
 * <p>This is deliberately write-only. It never restores or generates another orientation. The
 * hook runs after CellLayout.saveCurrentLayout(..., persist=true), so manual moves and completed
 * squeeze/drop operations refresh the sidecar for the active page/grid without reintroducing the
 * retired delayed orientation-memory mechanism.</p>
 */
final class HomeGridCommittedLayoutCaptureHook {
    private static final String TAG = "[DC][HomeGridRotation]";
    private static final String CELL_LAYOUT = "com.miui.home.launcher.CellLayout";
    private static boolean installed;

    private HomeGridCommittedLayoutCaptureHook() {}

    static void install(ClassLoader classLoader, HomeGridInstallConfig config) {
        if (installed || config == null || !config.enabled) return;
        try {
            Class<?> cellLayout = Class.forName(CELL_LAYOUT, false, classLoader);
            HookUtil.hookMethod(
                    cellLayout,
                    "saveCurrentLayout",
                    new Class<?>[]{boolean.class, boolean.class},
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (!Boolean.TRUE.equals(chain.getArg(1))) return result;
                        capture(chain.getThisObject(), config);
                        return result;
                    });
            installed = true;
            MainHook.log(TAG + " committed-layout capture installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " committed-layout capture unavailable: " + error);
        }
    }

    private static void capture(Object owner, HomeGridInstallConfig config) {
        if (!(owner instanceof ViewGroup) || MainHook.isWorkstationMode()) return;
        try {
            Object grid = HookUtil.getField(owner, "mGridConfig");
            if (grid == null) return;
            String name = String.valueOf(HookUtil.requireInvoke(grid, "getName"));
            Object xValue = HookUtil.requireInvoke(grid, "getCountX");
            Object yValue = HookUtil.requireInvoke(grid, "getCountY");
            if (!(xValue instanceof Integer) || !(yValue instanceof Integer)) return;

            int columns = (Integer) xValue;
            int rows = (Integer) yValue;
            if (!HomeGridSqueezePlannerPolicy.matches(
                    name, columns, rows, config.columns, config.rows)) {
                return;
            }

            Object screenValue = HookUtil.requireInvoke(owner, "getScreenId");
            if (!(screenValue instanceof Long)) return;
            long screenId = (Long) screenValue;
            if (screenId < 0) return;

            ViewGroup page = (ViewGroup) owner;
            boolean[][] occupied = new boolean[columns][rows];
            Map<Long, HomeGridRotationPositionMemory.Position> positions =
                    new LinkedHashMap<>();

            for (int i = 0; i < page.getChildCount(); i++) {
                View child = page.getChildAt(i);
                Object tag = child.getTag();
                if (tag == null) continue;

                final long id;
                final long itemScreenId;
                final int cellX;
                final int cellY;
                final int spanX;
                final int spanY;
                try {
                    id = HookUtil.getLongField(tag, "id");
                    if (id < 0) continue;
                    itemScreenId = HookUtil.getLongField(tag, "screenId");
                    cellX = HookUtil.getIntField(tag, "cellX");
                    cellY = HookUtil.getIntField(tag, "cellY");
                    spanX = HookUtil.getIntField(tag, "spanX");
                    spanY = HookUtil.getIntField(tag, "spanY");
                } catch (Throwable ignored) {
                    continue;
                }

                if (itemScreenId != screenId
                        || !markFree(occupied, columns, rows,
                        cellX, cellY, spanX, spanY)
                        || positions.put(id,
                        new HomeGridRotationPositionMemory.Position(
                                cellX, cellY, spanX, spanY)) != null) {
                    MainHook.log(TAG + " committed-layout capture rejected screen="
                            + screenId + " grid=" + columns + "x" + rows);
                    return;
                }
            }

            if (positions.isEmpty()) return;
            HomeGridRotationPositionMemory.save(
                    columns, rows, screenId, positions);
            MainHook.log(TAG + " committed-layout captured screen="
                    + screenId + " grid=" + columns + "x" + rows
                    + " items=" + positions.size());
        } catch (Throwable error) {
            MainHook.log(TAG + " committed-layout capture failed: " + error);
        }
    }

    private static boolean markFree(
            boolean[][] occupied,
            int columns,
            int rows,
            int x,
            int y,
            int spanX,
            int spanY) {
        if (x < 0 || y < 0 || spanX <= 0 || spanY <= 0
                || (long) x + spanX > columns
                || (long) y + spanY > rows) {
            return false;
        }
        for (int px = x; px < x + spanX; px++) {
            for (int py = y; py < y + spanY; py++) {
                if (occupied[px][py]) return false;
            }
        }
        for (int px = x; px < x + spanX; px++) {
            for (int py = y; py < y + spanY; py++) {
                occupied[px][py] = true;
            }
        }
        return true;
    }
}
