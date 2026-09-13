package com.hellovoid.liquiddock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reads the vendor Recents thumbnail for a task without creating a screenshot fallback. */
final class StageWorkspaceThumbnailBridge {
    private StageWorkspaceThumbnailBridge() {}

    static Object thumbnailFrom(Object activityManagerWrapper, Object vendorTask) {
        if (activityManagerWrapper == null || vendorTask == null) return null;
        try {
            Field keyField = findField(vendorTask.getClass(), "key");
            if (keyField == null) return null;
            keyField.setAccessible(true);
            Object taskKey = keyField.get(vendorTask);
            if (taskKey == null) return null;

            Method getTaskThumbnail = findMethod(
                    activityManagerWrapper.getClass(), "getTaskThumbnail", taskKey.getClass());
            if (getTaskThumbnail == null) return null;
            getTaskThumbnail.setAccessible(true);
            Object thumbnailData = getTaskThumbnail.invoke(activityManagerWrapper, taskKey);
            if (thumbnailData == null) return null;

            Field thumbnailField = findField(thumbnailData.getClass(), "thumbnail");
            if (thumbnailField == null) return null;
            thumbnailField.setAccessible(true);
            return thumbnailField.get(thumbnailData);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Method findMethod(
            Class<?> type, String name, Class<?> parameterType) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameterType);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
