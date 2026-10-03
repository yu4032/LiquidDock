package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Field;

/**
 * Bounds and cycle guard for MIUI's generic LayoutSqueezePlaces push traversal.
 *
 * <p>The vendor traversal only deduplicates the pending queue. On malformed/overlapping free-grid
 * occupancy, an item can be discovered again after it was already processed, producing an
 * unbounded queue and eventually an OOM. This replacement keeps the vendor cardinal push semantics
 * but uses identity-based scheduled/visited state and transactional rollback.</p>
 */
final class HomeGridSqueezeCycleGuard {
    private static final String TAG = "[DC][GRID]";
    private static final Set<Object> OWNED_PLANNERS = Collections.newSetFromMap(
            Collections.synchronizedMap(new IdentityHashMap<>()));

    private static Object space;
    private static Object mark;
    private static boolean installed;

    private HomeGridSqueezeCycleGuard() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        try {
            Class<?> planner = Class.forName(
                    "com.miui.home.launcher.compat.LayoutSqueezePlaces",
                    false, classLoader);
            Class<?> info = Class.forName(
                    "com.miui.home.launcher.bean.SqueezeInfo",
                    false, classLoader);
            space = HookUtil.findField(info, "SPACE").get(null);
            mark = HookUtil.findField(info, "MARK").get(null);

            HookUtil.hookMethod(
                    planner,
                    "pushViewInDistance",
                    new Class<?>[]{info, int.class, int.class},
                    chain -> {
                        Object owner = chain.getThisObject();
                        if (!OWNED_PLANNERS.contains(owner)) return chain.proceed();

                        Object root = chain.getArg(0);
                        Object distanceValue = chain.getArg(1);
                        Object directionValue = chain.getArg(2);
                        if (!(distanceValue instanceof Integer)
                                || !(directionValue instanceof Integer)) {
                            return false;
                        }
                        return pushSafely(
                                owner,
                                root,
                                (Integer) distanceValue,
                                (Integer) directionValue);
                    });

            installed = true;
        } catch (Throwable error) {
            MainHook.log(TAG + " squeeze cycle guard unavailable: " + error);
        }
    }

    static void register(Object planner) {
        if (planner != null) OWNED_PLANNERS.add(planner);
    }

    private static boolean pushSafely(
            Object planner, Object root, int distance, int direction) {
        if (root == null || root == space || root == mark || distance <= 0
                || direction < 0 || direction > 3) {
            return false;
        }

        Object[] matrix;
        try {
            matrix = (Object[]) readField(planner, "mDstOccupied");
        } catch (Throwable error) {
            MainHook.log(TAG + " squeeze guard matrix unavailable: " + error);
            return false;
        }
        if (matrix == null || matrix.length == 0) return false;

        int columns = matrix.length;
        Object[] firstColumn = (Object[]) matrix[0];
        if (firstColumn == null || firstColumn.length == 0) return false;
        int rows = firstColumn.length;

        Object[][] snapshot = snapshot(matrix, columns, rows);
        IdentityHashMap<Object, int[]> positions = snapshotPositions(
                matrix, columns, rows, root);

        try {
            for (int step = 0; step < distance; step++) {
                List<Object> chain = collectPushChain(
                        matrix, columns, rows, root, direction);
                if (chain == null || chain.isEmpty()) {
                    restore(matrix, snapshot, positions, columns, rows);
                    return false;
                }
                if (!shiftOneStep(
                        matrix, columns, rows, chain, direction)) {
                    restore(matrix, snapshot, positions, columns, rows);
                    return false;
                }
            }
            return true;
        } catch (Throwable error) {
            restore(matrix, snapshot, positions, columns, rows);
            MainHook.log(TAG + " squeeze guard aborted unsafe push: " + error);
            return false;
        }
    }

    private static List<Object> collectPushChain(
            Object[] matrix,
            int columns,
            int rows,
            Object root,
            int direction) {
        return HomeGridSqueezeIdentityTraversal.collect(
                root,
                columns * rows,
                (current, discovered) -> {
                    if (!isGeometryValid(current, columns, rows)) {
                        throw new IllegalStateException("invalid squeeze geometry");
                    }

                    int x;
                    int y;
                    int spanX;
                    int spanY;
                    try {
                        x = readInt(current, "cellX");
                        y = readInt(current, "cellY");
                        spanX = readInt(current, "spanX");
                        spanY = readInt(current, "spanY");
                    } catch (Exception error) {
                        throw new IllegalStateException(error);
                    }

                    if (direction == 0) {
                        appendEdge(matrix, x - 1, y, 1, spanY,
                                current, discovered, columns, rows);
                    } else if (direction == 1) {
                        appendEdge(matrix, x, y - 1, spanX, 1,
                                current, discovered, columns, rows);
                    } else if (direction == 2) {
                        appendEdge(matrix, x + spanX, y, 1, spanY,
                                current, discovered, columns, rows);
                    } else {
                        appendEdge(matrix, x, y + spanY, spanX, 1,
                                current, discovered, columns, rows);
                    }
                });
    }

    private static void appendEdge(
            Object[] matrix,
            int startX,
            int startY,
            int width,
            int height,
            Object current,
            ArrayList<Object> discovered,
            int columns,
            int rows) {
        if (startX < 0 || startY < 0
                || startX + width > columns
                || startY + height > rows) {
            return;
        }
        for (int x = startX; x < startX + width; x++) {
            Object[] column = (Object[]) matrix[x];
            for (int y = startY; y < startY + height; y++) {
                Object candidate = column[y];
                if (candidate == null || candidate == space || candidate == mark
                        || candidate == current) {
                    continue;
                }
                discovered.add(candidate);
            }
        }
    }

    private static boolean shiftOneStep(
            Object[] matrix,
            int columns,
            int rows,
            List<Object> chain,
            int direction) {
        IdentityHashMap<Object, Boolean> moving = new IdentityHashMap<>();
        for (Object item : chain) moving.put(item, Boolean.TRUE);

        // Clear every matrix reference owned by the moving chain. Scanning the matrix instead of
        // trusting item metadata also repairs duplicate/stale references from earlier bad layouts.
        for (int x = 0; x < columns; x++) {
            Object[] column = (Object[]) matrix[x];
            for (int y = 0; y < rows; y++) {
                if (moving.containsKey(column[y])) column[y] = space;
            }
        }

        boolean[][] reserved = new boolean[columns][rows];
        int[][] targets = new int[chain.size()][2];

        for (int index = 0; index < chain.size(); index++) {
            Object item = chain.get(index);
            int x = readInt(item, "cellX");
            int y = readInt(item, "cellY");
            int spanX = readInt(item, "spanX");
            int spanY = readInt(item, "spanY");

            if (direction == 0) x--;
            else if (direction == 1) y--;
            else if (direction == 2) x++;
            else y++;

            if (!fits(columns, rows, x, y, spanX, spanY)) return false;

            for (int px = x; px < x + spanX; px++) {
                Object[] column = (Object[]) matrix[px];
                for (int py = y; py < y + spanY; py++) {
                    if (reserved[px][py] || column[py] != space) return false;
                    reserved[px][py] = true;
                }
            }
            targets[index][0] = x;
            targets[index][1] = y;
        }

        for (int index = 0; index < chain.size(); index++) {
            Object item = chain.get(index);
            int x = targets[index][0];
            int y = targets[index][1];
            int spanX = readInt(item, "spanX");
            int spanY = readInt(item, "spanY");

            writeInt(item, "cellX", x);
            writeInt(item, "cellY", y);
            for (int px = x; px < x + spanX; px++) {
                Object[] column = (Object[]) matrix[px];
                for (int py = y; py < y + spanY; py++) {
                    column[py] = item;
                }
            }
        }
        return true;
    }

    private static boolean isGeometryValid(Object item, int columns, int rows) {
        try {
            return fits(
                    columns,
                    rows,
                    readInt(item, "cellX"),
                    readInt(item, "cellY"),
                    readInt(item, "spanX"),
                    readInt(item, "spanY"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object readField(Object target, String name) {
        try {
            return findField(target.getClass(), name).get(target);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static int readInt(Object target, String name) {
        try {
            return findField(target.getClass(), name).getInt(target);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void writeInt(Object target, String name, int value) {
        try {
            findField(target.getClass(), name).setInt(target, value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "#" + name);
    }

    private static boolean fits(
            int columns, int rows, int x, int y, int spanX, int spanY) {
        return HomeGridSqueezeBoundsPolicy.fits(
                columns, rows, x, y, spanX, spanY);
    }

    private static Object[][] snapshot(Object[] matrix, int columns, int rows) {
        Object[][] copy = new Object[columns][rows];
        for (int x = 0; x < columns; x++) {
            Object[] column = (Object[]) matrix[x];
            System.arraycopy(column, 0, copy[x], 0, rows);
        }
        return copy;
    }

    private static IdentityHashMap<Object, int[]> snapshotPositions(
            Object[] matrix, int columns, int rows, Object root) {
        IdentityHashMap<Object, int[]> result = new IdentityHashMap<>();
        capturePosition(result, root);
        for (int x = 0; x < columns; x++) {
            Object[] column = (Object[]) matrix[x];
            for (int y = 0; y < rows; y++) {
                capturePosition(result, column[y]);
            }
        }
        return result;
    }

    private static void capturePosition(
            IdentityHashMap<Object, int[]> result, Object item) {
        if (item == null || item == space || item == mark || result.containsKey(item)) return;
        try {
            result.put(item, new int[]{
                    readInt(item, "cellX"),
                    readInt(item, "cellY")
            });
        } catch (Throwable ignored) {}
    }

    private static void restore(
            Object[] matrix,
            Object[][] snapshot,
            Map<Object, int[]> positions,
            int columns,
            int rows) {
        try {
            for (int x = 0; x < columns; x++) {
                Object[] column = (Object[]) matrix[x];
                System.arraycopy(snapshot[x], 0, column, 0, rows);
            }
            for (Map.Entry<Object, int[]> entry : positions.entrySet()) {
                int[] pos = entry.getValue();
                writeInt(entry.getKey(), "cellX", pos[0]);
                writeInt(entry.getKey(), "cellY", pos[1]);
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " squeeze guard rollback failed: " + error);
        }
    }
}
