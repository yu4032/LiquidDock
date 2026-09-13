package com.hellovoid.liquiddock;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Current-snapshot projection of vendor Recents tasks for Stage. */
final class StageWorkspaceRecentsSource {
    private static final String RECENTS_MODEL = "com.miui.home.recents.RecentsModel";

    private StageWorkspaceRecentsSource() {}

    static final class Item {
        private final Object vendorTask;
        private final StageWorkspaceTaskModel.TaskEntry entry;

        Item(Object vendorTask, StageWorkspaceTaskModel.TaskEntry entry) {
            this.vendorTask = vendorTask;
            this.entry = entry;
        }

        Object vendorTask() {
            return vendorTask;
        }

        StageWorkspaceTaskModel.TaskEntry entry() {
            return entry;
        }
    }

    static List<Item> current(Context context, ClassLoader classLoader, int maxCards) {
        if (context == null || classLoader == null || maxCards <= 0) {
            return Collections.emptyList();
        }
        try {
            Class<?> modelClass = Class.forName(RECENTS_MODEL, false, classLoader);
            Method getInstance = modelClass.getDeclaredMethod("getInstance", Context.class);
            getInstance.setAccessible(true);
            Object model = getInstance.invoke(null, context);
            return fromModel(model, maxCards);
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    static List<Item> fromModel(Object model, int maxCards) {
        if (model == null || maxCards <= 0) return Collections.emptyList();
        try {
            Method getTaskList = model.getClass().getDeclaredMethod("getTaskList");
            getTaskList.setAccessible(true);
            Object value = getTaskList.invoke(model);
            if (!(value instanceof List<?>)) return Collections.emptyList();
            return fromVendorTasks((List<?>) value, maxCards);
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    static List<Item> fromVendorTasks(List<?> vendorTasks, int maxCards) {
        if (vendorTasks == null || vendorTasks.isEmpty() || maxCards <= 0) {
            return Collections.emptyList();
        }
        ArrayList<Item> result = new ArrayList<>(Math.min(vendorTasks.size(), maxCards));
        for (Object vendorTask : vendorTasks) {
            StageWorkspaceTaskModel.TaskEntry entry =
                    StageWorkspaceRecentsTaskAdapter.fromTask(vendorTask);
            if (entry == null) continue;
            result.add(new Item(vendorTask, entry));
            if (result.size() >= maxCards) break;
        }
        return Collections.unmodifiableList(result);
    }
}
