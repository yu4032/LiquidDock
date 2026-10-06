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
 * <p>The stock Pad planner gives the desired icon reorder animation, but on arbitrary grids it can
 * attempt to push a multi-cell widget as part of the same displacement chain. Multi-cell items are
 * barriers here: the stock calculation may run, but if it moves any span larger than 1x1 the whole
 * attempt is rolled back and reported as a failed squeeze.</p>
 */
final class HomeGridStockSqueezeGuard {
    private static final String TAG = "[DC][GridSqueeze]";

    private HomeGridStockSqueezeGuard() {}

    static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        if (!isSqueezeCall(method, args)) {
            return invokeRaw(target, method, args);
        }

        Object[] src = asMatrix(args[1]);
        Object[] dst = asMatrix(args[2]);
        Snapshot snapshot = Snapshot.capture(src, dst);
        logBegin(method, args, snapshot, src, dst);

        try {
            Object result = invokeRaw(target, method, args);
            if (!(result instanceof Boolean) || !((Boolean) result)) {
                MainHook.log(TAG + " result=" + result + " -> rollback");
                snapshot.restore(src, dst);
                return result;
            }

            String violation = snapshot.findMultiCellMovement(src, dst);
            if (violation != null) {
                MainHook.log(TAG + " BLOCK " + violation + " -> rollback");
                MainHook.log(TAG + " after src=" + describeMatrix(src));
                MainHook.log(TAG + " after dst=" + describeMatrix(dst));
                snapshot.restore(src, dst);
                return false;
            }

            MainHook.log(TAG + " allow 1x1-only squeeze");
            return result;
        } catch (Throwable error) {
            snapshot.restore(src, dst);
            MainHook.log(TAG + " exception -> rollback: " + error);
            return false;
        }
    }

    private static void logBegin(
            Method method,
            Object[] args,
            Snapshot snapshot,
            Object[] src,
            Object[] dst) {
        StringBuilder line = new StringBuilder(TAG)
                .append(" begin method=").append(method.getName());
        Object parameter = args != null && args.length > 0 ? args[0] : null;
        if (parameter != null) {
            line.append(" drag=");
            appendInt(line, parameter, "cellX");
            line.append(',');
            appendInt(line, parameter, "cellY");
            line.append(" span=");
            appendInt(line, parameter, "spanX");
            line.append('x');
            appendInt(line, parameter, "spanY");
            line.append(" spanMove=");
            appendBoolean(line, parameter, "isSpanMove");
        }
        MainHook.log(line.toString());
        MainHook.log(TAG + " before src=" + snapshot.describeBeforeSrc());
        MainHook.log(TAG + " before dst=" + snapshot.describeBeforeDst());
        MainHook.log(TAG + " live src=" + describeMatrix(src));
        MainHook.log(TAG + " live dst=" + describeMatrix(dst));
    }

    private static void appendInt(StringBuilder out, Object target, String name) {
        try {
            out.append(readInt(target, name));
        } catch (Throwable ignored) {
            out.append('?');
        }
    }

    private static void appendBoolean(StringBuilder out, Object target, String name) {
        try {
            out.append(readBoolean(target, name));
        } catch (Throwable ignored) {
            out.append('?');
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

    private static boolean readBoolean(Object target, String name) {
        try {
            return findField(target.getClass(), name).getBoolean(target);
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

    private static String describeMatrix(Object[] matrix) {
        if (matrix == null) return "null";
        IdentityHashMap<Object, List<String>> cells = collectCells(matrix);
        StringBuilder out = new StringBuilder();
        out.append(matrix.length).append('x');
        Object[] first = matrix.length > 0 ? column(matrix, 0) : null;
        out.append(first != null ? first.length : 0).append(' ');
        boolean firstItem = true;
        for (Map.Entry<Object, List<String>> entry : cells.entrySet()) {
            Object item = entry.getKey();
            ItemState state = ItemState.tryRead(item);
            if (state == null) continue;
            if (!firstItem) out.append(" | ");
            firstItem = false;
            out.append(shortName(item.getClass().getName()))
                    .append('@').append(Integer.toHexString(System.identityHashCode(item)))
                    .append(" cell=").append(state.cellX).append(',').append(state.cellY)
                    .append(" span=").append(state.spanX).append('x').append(state.spanY)
                    .append(" occ=").append(entry.getValue());
        }
        return out.toString();
    }

    private static String shortName(String name) {
        int at = name.lastIndexOf('.');
        return at >= 0 ? name.substring(at + 1) : name;
    }

    private static IdentityHashMap<Object, List<String>> collectCells(Object[] matrix) {
        IdentityHashMap<Object, List<String>> cells = new IdentityHashMap<>();
        if (matrix == null) return cells;
        for (int x = 0; x < matrix.length; x++) {
            Object[] col = column(matrix, x);
            if (col == null) continue;
            for (int y = 0; y < col.length; y++) {
                Object item = col[y];
                if (ItemState.tryRead(item) == null) continue;
                List<String> positions = cells.get(item);
                if (positions == null) {
                    positions = new ArrayList<>();
                    cells.put(item, positions);
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
        final IdentityHashMap<Object, List<String>> srcCells;
        final IdentityHashMap<Object, List<String>> dstCells;

        Snapshot(
                Object[][] src,
                Object[][] dst,
                IdentityHashMap<Object, ItemState> items,
                IdentityHashMap<Object, List<String>> srcCells,
                IdentityHashMap<Object, List<String>> dstCells) {
            this.src = src;
            this.dst = dst;
            this.items = items;
            this.srcCells = srcCells;
            this.dstCells = dstCells;
        }

        static Snapshot capture(Object[] src, Object[] dst) {
            IdentityHashMap<Object, ItemState> items = new IdentityHashMap<>();
            Object[][] srcCopy = copy(src, items);
            Object[][] dstCopy = copy(dst, items);
            return new Snapshot(
                    srcCopy,
                    dstCopy,
                    items,
                    collectCells(src),
                    collectCells(dst));
        }

        String describeBeforeSrc() {
            return describeSnapshot(src, items);
        }

        String describeBeforeDst() {
            return describeSnapshot(dst, items);
        }

        String findMultiCellMovement(Object[] srcNow, Object[] dstNow) {
            IdentityHashMap<Object, List<String>> srcAfter = collectCells(srcNow);
            IdentityHashMap<Object, List<String>> dstAfter = collectCells(dstNow);

            for (Map.Entry<Object, ItemState> entry : items.entrySet()) {
                Object item = entry.getKey();
                ItemState before = entry.getValue();
                if (before.spanX <= 1 && before.spanY <= 1) continue;

                ItemState after = ItemState.tryRead(item);
                if (after != null && movedMultiCell(
                        before.spanX, before.spanY,
                        before.cellX, before.cellY,
                        after.cellX, after.cellY)) {
                    return describeMove("field", item, before, after);
                }

                if (!sameCells(srcCells.get(item), srcAfter.get(item))
                        || !sameCells(dstCells.get(item), dstAfter.get(item))) {
                    return "matrix item="
                            + shortName(item.getClass().getName())
                            + '@' + Integer.toHexString(System.identityHashCode(item))
                            + " span=" + before.spanX + 'x' + before.spanY
                            + " src " + srcCells.get(item) + "->" + srcAfter.get(item)
                            + " dst " + dstCells.get(item) + "->" + dstAfter.get(item);
                }
            }

            // Also catch a planner that replaces a multi-cell SqueezeInfo with a new instance.
            String replacement = findNewMultiCell(dstCells, dstAfter);
            if (replacement != null) return replacement;
            return findNewMultiCell(srcCells, srcAfter);
        }

        private static String findNewMultiCell(
                IdentityHashMap<Object, List<String>> before,
                IdentityHashMap<Object, List<String>> after) {
            for (Map.Entry<Object, List<String>> entry : after.entrySet()) {
                Object item = entry.getKey();
                if (before.containsKey(item)) continue;
                ItemState state = ItemState.tryRead(item);
                if (state == null || (state.spanX <= 1 && state.spanY <= 1)) continue;
                return "replacement item="
                        + shortName(item.getClass().getName())
                        + '@' + Integer.toHexString(System.identityHashCode(item))
                        + " cell=" + state.cellX + ',' + state.cellY
                        + " span=" + state.spanX + 'x' + state.spanY
                        + " occ=" + entry.getValue();
            }
            return null;
        }

        private static String describeMove(
                String source, Object item, ItemState before, ItemState after) {
            return source
                    + " item=" + shortName(item.getClass().getName())
                    + '@' + Integer.toHexString(System.identityHashCode(item))
                    + " span=" + before.spanX + 'x' + before.spanY
                    + " cell=" + before.cellX + ',' + before.cellY
                    + "->" + after.cellX + ',' + after.cellY;
        }

        private static boolean sameCells(List<String> a, List<String> b) {
            if (a == null || a.isEmpty()) return b == null || b.isEmpty();
            if (b == null || a.size() != b.size()) return false;
            return a.containsAll(b) && b.containsAll(a);
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
                MainHook.log(TAG + " rollback failed: " + error);
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
            ItemState state = ItemState.tryRead(item);
            if (state != null) items.put(item, state);
        }

        private static String describeSnapshot(
                Object[][] matrix,
                IdentityHashMap<Object, ItemState> states) {
            if (matrix == null) return "null";
            StringBuilder out = new StringBuilder();
            out.append(matrix.length).append('x')
                    .append(matrix.length > 0 ? matrix[0].length : 0).append(' ');
            IdentityHashMap<Object, List<String>> cells = new IdentityHashMap<>();
            for (int x = 0; x < matrix.length; x++) {
                for (int y = 0; y < matrix[x].length; y++) {
                    Object item = matrix[x][y];
                    if (!states.containsKey(item)) continue;
                    List<String> positions = cells.get(item);
                    if (positions == null) {
                        positions = new ArrayList<>();
                        cells.put(item, positions);
                    }
                    positions.add(x + "," + y);
                }
            }
            boolean first = true;
            for (Map.Entry<Object, List<String>> entry : cells.entrySet()) {
                ItemState state = states.get(entry.getKey());
                if (!first) out.append(" | ");
                first = false;
                out.append(shortName(entry.getKey().getClass().getName()))
                        .append('@')
                        .append(Integer.toHexString(System.identityHashCode(entry.getKey())))
                        .append(" cell=").append(state.cellX).append(',').append(state.cellY)
                        .append(" span=").append(state.spanX).append('x').append(state.spanY)
                        .append(" occ=").append(entry.getValue());
            }
            return out.toString();
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
