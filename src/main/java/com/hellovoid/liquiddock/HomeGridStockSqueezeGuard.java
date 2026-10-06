package com.hellovoid.liquiddock;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transaction wrapper for MIUI's stock 1x1 squeeze planner on custom HOME grids.
 *
 * <p>The stock Pad planner preserves MIUI's native 1x1 reorder animation, but its macroblock
 * assumptions are unsafe when a displacement chain reaches a multi-cell item on arbitrary grids.
 * Treat multi-cell items as barriers: their final destination footprint must exactly match the
 * original source footprint or the entire stock squeeze is rolled back.</p>
 */
final class HomeGridStockSqueezeGuard {
    private static final String TAG = "[DC][GRID]";

    private HomeGridStockSqueezeGuard() {}

    static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        if (!isSqueezeCall(method, args)) {
            return invokeRaw(target, method, args);
        }

        Object[] src = asMatrix(args[1]);
        Object[] dst = asMatrix(args[2]);
        Snapshot snapshot = Snapshot.capture(src, dst);

        try {
            Object result = invokeRaw(target, method, args);
            if (!(result instanceof Boolean) || !((Boolean) result)) {
                snapshot.restore(src, dst);
                return result;
            }

            String violation = snapshot.findUnsafeMultiCellChange(src, dst);
            if (violation == null) {
                return result;
            }

            snapshot.restore(src, dst);
            MainHook.log(TAG + " blocked unsafe stock squeeze: " + violation);
            return false;
        } catch (Throwable error) {
            snapshot.restore(src, dst);
            MainHook.log(TAG + " stock squeeze rolled back: " + error);
            return false;
        }
    }

    private static boolean isSqueezeCall(Method method, Object[] args) {
        return method != null
                && method.getName().startsWith("squeezeFrom")
                && args != null
                && args.length >= 3
                && args[1] instanceof Object[]
                && args[2] instanceof Object[];
    }

    private static Object invokeRaw(Object target, Method method, Object[] args) throws Throwable {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            throw cause != null ? cause : error;
        }
    }

    private static Object[] asMatrix(Object value) {
        return value instanceof Object[] ? (Object[]) value : null;
    }

    private static Object[] column(Object[] matrix, int x) {
        if (matrix == null || x < 0 || x >= matrix.length) return null;
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

    private static Object logicalKey(Object item) {
        if (item == null) return null;
        HookUtil.InvocationResult<Object> data = HookUtil.tryInvoke(item, "getMData");
        if (data.succeeded() && data.value() != null) return data.value();
        try {
            Object field = findField(item.getClass(), "mData").get(item);
            if (field != null) return field;
        } catch (Throwable ignored) {}
        return item;
    }

    private static IdentityHashMap<Object, List<String>> collectCells(Object[] matrix) {
        IdentityHashMap<Object, List<String>> cells = new IdentityHashMap<>();
        if (matrix == null) return cells;

        for (int x = 0; x < matrix.length; x++) {
            Object[] source = column(matrix, x);
            if (source == null) continue;
            for (int y = 0; y < source.length; y++) {
                Object item = source[y];
                if (ItemState.tryRead(item) == null) continue;

                Object key = logicalKey(item);
                if (key == null) continue;
                List<String> positions = cells.get(key);
                if (positions == null) {
                    positions = new ArrayList<>();
                    cells.put(key, positions);
                }
                positions.add(x + "," + y);
            }
        }
        return cells;
    }

    private static final class ItemState {
        final int cellX;
        final int cellY;
        final int spanX;
        final int spanY;

        ItemState(Object item) {
            cellX = readInt(item, "cellX");
            cellY = readInt(item, "cellY");
            spanX = readInt(item, "spanX");
            spanY = readInt(item, "spanY");
        }

        static ItemState tryRead(Object item) {
            if (item == null) return null;
            try {
                ItemState state = new ItemState(item);
                return state.spanX > 0 && state.spanY > 0 ? state : null;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static final class Snapshot {
        final Object[][] src;
        final Object[][] dst;
        final IdentityHashMap<Object, ItemState> items;
        final IdentityHashMap<Object, List<String>> sourceCells;
        final IdentityHashMap<Object, ItemState> sourceStates;

        Snapshot(
                Object[][] src,
                Object[][] dst,
                IdentityHashMap<Object, ItemState> items,
                IdentityHashMap<Object, List<String>> sourceCells,
                IdentityHashMap<Object, ItemState> sourceStates) {
            this.src = src;
            this.dst = dst;
            this.items = items;
            this.sourceCells = sourceCells;
            this.sourceStates = sourceStates;
        }

        static Snapshot capture(Object[] src, Object[] dst) {
            IdentityHashMap<Object, ItemState> items = new IdentityHashMap<>();
            return new Snapshot(
                    copy(src, items),
                    copy(dst, items),
                    items,
                    collectCells(src),
                    collectLogicalStates(src));
        }

        String findUnsafeMultiCellChange(Object[] srcNow, Object[] dstNow) {
            IdentityHashMap<Object, List<String>> srcAfter = collectCells(srcNow);
            IdentityHashMap<Object, List<String>> dstAfter = collectCells(dstNow);

            for (Map.Entry<Object, ItemState> entry : sourceStates.entrySet()) {
                Object key = entry.getKey();
                ItemState state = entry.getValue();
                if (state.spanX <= 1 && state.spanY <= 1) continue;

                List<String> baseline = sourceCells.get(key);
                List<String> srcCurrent = srcAfter.get(key);
                List<String> dstCurrent = dstAfter.get(key);

                if (!sameCells(baseline, srcCurrent)) {
                    return "multi-cell source changed span="
                            + state.spanX + "x" + state.spanY;
                }
                if (!sameCells(baseline, dstCurrent)) {
                    return "multi-cell destination moved span="
                            + state.spanX + "x" + state.spanY;
                }
            }

            String replacement = findNewMultiCell(sourceCells, dstAfter, dstNow);
            if (replacement != null) return replacement;
            return findNewMultiCell(sourceCells, srcAfter, srcNow);
        }

        void restore(Object[] srcMatrix, Object[] dstMatrix) {
            try {
                restoreMatrix(srcMatrix, src);
                restoreMatrix(dstMatrix, dst);
                for (Map.Entry<Object, ItemState> entry : items.entrySet()) {
                    ItemState state = entry.getValue();
                    writeInt(entry.getKey(), "cellX", state.cellX);
                    writeInt(entry.getKey(), "cellY", state.cellY);
                }
            } catch (Throwable error) {
                MainHook.log(TAG + " stock squeeze rollback failed: " + error);
            }
        }

        private static String findNewMultiCell(
                IdentityHashMap<Object, List<String>> before,
                IdentityHashMap<Object, List<String>> after,
                Object[] matrix) {
            IdentityHashMap<Object, ItemState> states = collectLogicalStates(matrix);
            for (Object key : after.keySet()) {
                if (before.containsKey(key)) continue;
                ItemState state = states.get(key);
                if (state != null && (state.spanX > 1 || state.spanY > 1)) {
                    return "unexpected multi-cell item span="
                            + state.spanX + "x" + state.spanY;
                }
            }
            return null;
        }

        private static boolean sameCells(List<String> left, List<String> right) {
            if (left == null || left.isEmpty()) return right == null || right.isEmpty();
            if (right == null || left.size() != right.size()) return false;
            return left.containsAll(right) && right.containsAll(left);
        }

        private static Object[][] copy(
                Object[] matrix,
                IdentityHashMap<Object, ItemState> items) {
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
                    if (item == null || items.containsKey(item)) continue;
                    ItemState state = ItemState.tryRead(item);
                    if (state != null) items.put(item, state);
                }
            }
            return copy;
        }

        private static IdentityHashMap<Object, ItemState> collectLogicalStates(Object[] matrix) {
            IdentityHashMap<Object, ItemState> states = new IdentityHashMap<>();
            if (matrix == null) return states;

            for (int x = 0; x < matrix.length; x++) {
                Object[] source = column(matrix, x);
                if (source == null) continue;
                for (Object item : source) {
                    ItemState state = ItemState.tryRead(item);
                    if (state == null) continue;
                    Object key = logicalKey(item);
                    if (key != null && !states.containsKey(key)) {
                        states.put(key, state);
                    }
                }
            }
            return states;
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
