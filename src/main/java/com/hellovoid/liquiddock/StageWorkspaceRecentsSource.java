package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Current-snapshot projection of vendor Recents tasks for Stage. */
final class StageWorkspaceRecentsSource {
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
