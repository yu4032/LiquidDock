package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure task-card projection for Stage. Task identity is taskId + userId, never package name. */
final class StageWorkspaceTaskModel {
    private StageWorkspaceTaskModel() {}

    static final class TaskEntry {
        private final int taskId;
        private final int userId;
        private final String packageName;
        private final long lastActiveTime;
        private final boolean grouped;
        private final int partnerTaskId;

        TaskEntry(int taskId,
                  int userId,
                  String packageName,
                  long lastActiveTime,
                  boolean grouped,
                  int partnerTaskId) {
            this.taskId = taskId;
            this.userId = userId;
            this.packageName = packageName;
            this.lastActiveTime = lastActiveTime;
            this.grouped = grouped;
            this.partnerTaskId = partnerTaskId;
        }

        int taskId() {
            return taskId;
        }

        int userId() {
            return userId;
        }

        String packageName() {
            return packageName;
        }

        long lastActiveTime() {
            return lastActiveTime;
        }

        boolean grouped() {
            return grouped;
        }

        int partnerTaskId() {
            return partnerTaskId;
        }
    }

    static boolean sameInstance(TaskEntry first, TaskEntry second) {
        return first != null
                && second != null
                && first.taskId == second.taskId
                && first.userId == second.userId;
    }

    static List<TaskEntry> cardsFromRecents(List<TaskEntry> recents, int maxCards) {
        if (recents == null || recents.isEmpty() || maxCards <= 0) {
            return Collections.emptyList();
        }
        ArrayList<TaskEntry> result = new ArrayList<>(Math.min(recents.size(), maxCards));
        for (TaskEntry entry : recents) {
            if (entry == null || entry.taskId < 0) continue;
            result.add(entry);
            if (result.size() >= maxCards) break;
        }
        return Collections.unmodifiableList(result);
    }
}
