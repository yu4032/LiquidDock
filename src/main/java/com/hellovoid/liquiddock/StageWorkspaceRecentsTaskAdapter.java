package com.hellovoid.liquiddock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads the stable semantic surface exposed by HyperOS 4.50's shared Recents Task model.
 * Missing required members fail closed rather than inventing identity from package/order.
 */
final class StageWorkspaceRecentsTaskAdapter {
    private StageWorkspaceRecentsTaskAdapter() {}

    static StageWorkspaceTaskModel.TaskEntry fromTask(Object task) {
        if (task == null) return null;
        try {
            Object key = readField(task, "key");
            if (key == null) return null;

            int taskId = readIntField(key, "id");
            int userId = readIntField(key, "userId");
            long lastActiveTime = readLongField(key, "lastActiveTime");
            if (taskId < 0) return null;

            Object packageValue = invokeRequired(key, "getPackageName");
            Object multipleValue = invokeRequired(task, "hasMultipleTasks");
            if (!(packageValue == null || packageValue instanceof String)
                    || !(multipleValue instanceof Boolean)) {
                return null;
            }

            boolean grouped = (Boolean) multipleValue;
            int partnerTaskId = -1;
            if (grouped) {
                Object partnerValue = invokeRequired(task, "getAnotherMultiTaskId",
                        new Class<?>[]{int.class}, taskId);
                if (!(partnerValue instanceof Number)) return null;
                partnerTaskId = ((Number) partnerValue).intValue();
            }

            return new StageWorkspaceTaskModel.TaskEntry(
                    taskId,
                    userId,
                    (String) packageValue,
                    lastActiveTime,
                    grouped,
                    partnerTaskId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object readField(Object owner, String name) throws Exception {
        Field field = findField(owner.getClass(), name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static int readIntField(Object owner, String name) throws Exception {
        Object value = readField(owner, name);
        if (!(value instanceof Number)) throw new IllegalStateException(name);
        return ((Number) value).intValue();
    }

    private static long readLongField(Object owner, String name) throws Exception {
        Object value = readField(owner, name);
        if (!(value instanceof Number)) throw new IllegalStateException(name);
        return ((Number) value).longValue();
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object invokeRequired(Object owner, String name) throws Exception {
        return invokeRequired(owner, name, new Class<?>[0]);
    }

    private static Object invokeRequired(Object owner,
                                         String name,
                                         Class<?>[] parameterTypes,
                                         Object... args) throws Exception {
        Method method = findMethod(owner.getClass(), name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(owner, args);
    }

    private static Method findMethod(Class<?> type,
                                     String name,
                                     Class<?>[] parameterTypes) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }
}
