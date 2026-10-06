package com.hellovoid.liquiddock;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Transaction boundary for every generic free-grid squeeze operation.
 *
 * <p>Vendor helpers can mutate SqueezeInfo coordinates and the destination occupancy matrix before
 * discovering an impossible placement. For free grids, guard both squeeze entry points so any
 * exception or inconsistent result rolls back the complete operation instead of leaving partial
 * occupancy or crashing Launcher.</p>
 */
final class HomeGridSqueezeTransactionGuard {
    private static final String TAG = "[DC][GRID]";
    private static final Set<Object> OWNED_PLANNERS = Collections.newSetFromMap(
            Collections.synchronizedMap(new IdentityHashMap<>()));

    private static Object space;
    private static Object mark;
    private static boolean installed;

    private HomeGridSqueezeTransactionGuard() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        try {
            Class<?> planner = Class.forName(
                    "com.miui.home.launcher.compat.LayoutSqueezePlaces",
                    false, classLoader);
            Class<?> parameter = Class.forName(
                    "com.miui.home.launcher.bean.SqueezeParameter",
                    false, classLoader);
            Class<?> info = Class.forName(
                    "com.miui.home.launcher.bean.SqueezeInfo",
                    false, classLoader);
            Class<?> matrix = Array.newInstance(info, 0, 0).getClass();

            space = HookUtil.findField(info, "SPACE").get(null);
            mark = HookUtil.findField(info, "MARK").get(null);

            hookEntry(planner, "squeezeFromSingleDrag", parameter, matrix);
            hookEntry(planner, "squeezeFromMultipleDrag", parameter, matrix);
            installed = true;
        } catch (Throwable error) {
            MainHook.log(TAG + " squeeze transaction guard unavailable: " + error);
        }
    }

    static void register(Object planner) {
        if (planner != null) OWNED_PLANNERS.add(planner);
    }

    private static void hookEntry(
            Class<?> planner,
            String method,
            Class<?> parameter,
            Class<?> matrix) {
        HookUtil.hookMethod(
                planner,
                method,
                new Class<?>[]{parameter, matrix, matrix},
                chain -> {
                    Object owner = chain.getThisObject();
                    if (!OWNED_PLANNERS.contains(owner)) return chain.proceed();

                    Object[] src = asMatrix(chain.getArg(1));
                    Object[] dst = asMatrix(chain.getArg(2));
                    MatrixSnapshot snapshot = MatrixSnapshot.capture(src, dst);

                    try {
                        Object result = chain.proceed();
                        if (!(result instanceof Boolean) || !((Boolean) result)) {
                            MainHook.log(TAG + " generic " + method
                                    + " result=" + result + " -> rollback");
                            snapshot.restore(src, dst);
                            return result;
                        }
                        if (isConsistent(dst)) {
                            MainHook.log(TAG + " generic " + method
                                    + " accepted dst=" + matrixSize(dst));
                            return result;
                        }
                        MainHook.log(TAG + " generic " + method
                                + " inconsistent dst=" + matrixSize(dst)
                                + " -> rollback");
                        snapshot.restore(src, dst);
                        return false;
                    } catch (Throwable error) {
                        snapshot.restore(src, dst);
                        MainHook.log(TAG + " squeeze transaction rolled back: " + error);
                        return false;
                    }
                });
    }

    private static Object[] asMatrix(Object value) {
        return value instanceof Object[] ? (Object[]) value : null;
    }

    private static String matrixSize(Object[] matrix) {
        if (matrix == null) return "null";
        Object[] first = matrix.length > 0 ? column(matrix, 0) : null;
        return matrix.length + "x" + (first == null ? 0 : first.length);
    }

    private static boolean isConsistent(Object[] matrix) {
        if (matrix == null || matrix.length == 0) return false;
        int columns = matrix.length;
        Object[] first = column(matrix, 0);
        if (first == null || first.length == 0) return false;
        int rows = first.length;

        IdentityHashMap<Object, Boolean> checked = new IdentityHashMap<>();
        for (int x = 0; x < columns; x++) {
            Object[] column = column(matrix, x);
            if (column == null || column.length != rows) return false;
            for (int y = 0; y < rows; y++) {
                Object item = column[y];
                if (isSentinel(item)) continue;

                int cellX = readInt(item, "cellX");
                int cellY = readInt(item, "cellY");
                int spanX = readInt(item, "spanX");
                int spanY = readInt(item, "spanY");
                if (!HomeGridSqueezeBoundsPolicy.fits(
                        columns, rows, cellX, cellY, spanX, spanY)) {
                    return false;
                }

                if (x < cellX || x >= cellX + spanX
                        || y < cellY || y >= cellY + spanY) {
                    return false;
                }

                if (checked.put(item, Boolean.TRUE) == null) {
                    for (int px = cellX; px < cellX + spanX; px++) {
                        Object[] targetColumn = column(matrix, px);
                        for (int py = cellY; py < cellY + spanY; py++) {
                            if (targetColumn[py] != item) return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean isSentinel(Object item) {
        return item == null || item == space || item == mark;
    }

    private static Object[] column(Object[] matrix, int x) {
        Object value = matrix[x];
        return value instanceof Object[] ? (Object[]) value : null;
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

    private static final class MatrixSnapshot {
        private final Object[][] src;
        private final Object[][] dst;
        private final IdentityHashMap<Object, int[]> positions;

        private MatrixSnapshot(
                Object[][] src,
                Object[][] dst,
                IdentityHashMap<Object, int[]> positions) {
            this.src = src;
            this.dst = dst;
            this.positions = positions;
        }

        static MatrixSnapshot capture(Object[] src, Object[] dst) {
            IdentityHashMap<Object, int[]> positions = new IdentityHashMap<>();
            Object[][] srcCopy = copy(src, positions);
            Object[][] dstCopy = copy(dst, positions);
            return new MatrixSnapshot(srcCopy, dstCopy, positions);
        }

        void restore(Object[] srcMatrix, Object[] dstMatrix) {
            try {
                restoreMatrix(srcMatrix, src);
                restoreMatrix(dstMatrix, dst);
                for (Map.Entry<Object, int[]> entry : positions.entrySet()) {
                    int[] xy = entry.getValue();
                    writeInt(entry.getKey(), "cellX", xy[0]);
                    writeInt(entry.getKey(), "cellY", xy[1]);
                }
            } catch (Throwable error) {
                MainHook.log(TAG + " squeeze transaction rollback failed: " + error);
            }
        }

        private static Object[][] copy(
                Object[] matrix,
                IdentityHashMap<Object, int[]> positions) {
            if (matrix == null || matrix.length == 0) return null;
            Object[] first = column(matrix, 0);
            if (first == null) return null;
            int rows = first.length;
            Object[][] copy = new Object[matrix.length][rows];
            for (int x = 0; x < matrix.length; x++) {
                Object[] source = column(matrix, x);
                if (source == null || source.length != rows) return null;
                System.arraycopy(source, 0, copy[x], 0, rows);
                for (Object item : source) {
                    capturePosition(positions, item);
                }
            }
            return copy;
        }

        private static void capturePosition(
                IdentityHashMap<Object, int[]> positions,
                Object item) {
            if (isSentinel(item) || positions.containsKey(item)) return;
            positions.put(item, new int[]{
                    readInt(item, "cellX"),
                    readInt(item, "cellY")
            });
        }

        private static void restoreMatrix(Object[] matrix, Object[][] snapshot) {
            if (matrix == null || snapshot == null || matrix.length != snapshot.length) return;
            for (int x = 0; x < matrix.length; x++) {
                Object[] target = column(matrix, x);
                if (target == null || target.length != snapshot[x].length) return;
                System.arraycopy(snapshot[x], 0, target, 0, target.length);
            }
        }
    }
}
