package com.hellovoid.liquiddock;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Transaction wrapper for MIUI's stock 1x1 squeeze planner on custom HOME grids.
 *
 * <p>The stock Pad planner gives the desired icon reorder animation, but on arbitrary grids it can
 * attempt to push a multi-cell widget as part of the same displacement chain. Multi-cell items are
 * barriers here: the stock calculation may run, but if it moves any span larger than 1x1 the whole
 * attempt is rolled back and reported as a failed squeeze.</p>
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
            if (snapshot.movedMultiCellItem()) {
                snapshot.restore(src, dst);
                return false;
            }
            return result;
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

    static boolean movedMultiCell(
            int spanX, int spanY, int oldX, int oldY, int newX, int newY) {
        return (spanX > 1 || spanY > 1) && (oldX != newX || oldY != newY);
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
    }

    private static final class Snapshot {
        final Object[][] src;
        final Object[][] dst;
        final IdentityHashMap<Object, ItemState> items;

        Snapshot(
                Object[][] src,
                Object[][] dst,
                IdentityHashMap<Object, ItemState> items) {
            this.src = src;
            this.dst = dst;
            this.items = items;
        }

        static Snapshot capture(Object[] src, Object[] dst) {
            IdentityHashMap<Object, ItemState> items = new IdentityHashMap<>();
            Object[][] srcCopy = copy(src, items);
            Object[][] dstCopy = copy(dst, items);
            return new Snapshot(srcCopy, dstCopy, items);
        }

        boolean movedMultiCellItem() {
            for (Map.Entry<Object, ItemState> entry : items.entrySet()) {
                Object item = entry.getKey();
                ItemState before = entry.getValue();
                int newX = readInt(item, "cellX");
                int newY = readInt(item, "cellY");
                if (movedMultiCell(
                        before.spanX, before.spanY,
                        before.cellX, before.cellY,
                        newX, newY)) {
                    return true;
                }
            }
            return false;
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
                    captureItem(items, item);
                }
            }
            return copy;
        }

        private static void captureItem(
                IdentityHashMap<Object, ItemState> items,
                Object item) {
            if (item == null || items.containsKey(item)) return;
            try {
                ItemState state = new ItemState(item);
                if (state.spanX > 0 && state.spanY > 0) {
                    items.put(item, state);
                }
            } catch (Throwable ignored) {
                // SPACE/MARK sentinels do not expose normal item fields.
            }
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
