package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StageWorkspaceRecentsTaskAdapterTest {

    @Test
    public void mapsTaskKeyIdentityAndPackageWithoutPackageDeduplication() {
        FakeTask first = new FakeTask(31, 0, 900L, "com.example.same", false, -1);
        FakeTask second = new FakeTask(32, 0, 800L, "com.example.same", false, -1);

        StageWorkspaceTaskModel.TaskEntry a = StageWorkspaceRecentsTaskAdapter.fromTask(first);
        StageWorkspaceTaskModel.TaskEntry b = StageWorkspaceRecentsTaskAdapter.fromTask(second);

        assertEquals(31, a.taskId());
        assertEquals(32, b.taskId());
        assertEquals(0, a.userId());
        assertEquals(900L, a.lastActiveTime());
        assertEquals("com.example.same", a.packageName());
        assertFalse(StageWorkspaceTaskModel.sameInstance(a, b));
    }

    @Test
    public void preservesGroupedPartnerIdentityUsingCurrentTaskId() {
        FakeTask task = new FakeTask(40, 10, 700L, "com.example.video", true, 41);

        StageWorkspaceTaskModel.TaskEntry entry = StageWorkspaceRecentsTaskAdapter.fromTask(task);

        assertTrue(entry.grouped());
        assertEquals(41, entry.partnerTaskId());
        assertEquals(40, task.lastPartnerLookupTaskId);
        assertEquals(10, entry.userId());
    }

    @Test
    public void missingTaskKeyFailsClosed() {
        FakeTask task = new FakeTask(1, 0, 1L, "com.example", false, -1);
        task.key = null;

        assertNull(StageWorkspaceRecentsTaskAdapter.fromTask(task));
    }

    @Test
    public void invalidTaskIdFailsClosed() {
        FakeTask task = new FakeTask(-1, 0, 1L, "com.example", false, -1);

        assertNull(StageWorkspaceRecentsTaskAdapter.fromTask(task));
    }

    @Test
    public void absentRequiredRuntimeMethodFailsClosed() {
        class IncompleteTask {
            public final FakeTaskKey key = new FakeTaskKey(9, 0, 1L, "com.example");
        }

        assertNull(StageWorkspaceRecentsTaskAdapter.fromTask(new IncompleteTask()));
    }

    public static final class FakeTaskKey {
        public final int id;
        public final int userId;
        public final long lastActiveTime;
        private final String packageName;

        FakeTaskKey(int id, int userId, long lastActiveTime, String packageName) {
            this.id = id;
            this.userId = userId;
            this.lastActiveTime = lastActiveTime;
            this.packageName = packageName;
        }

        public String getPackageName() {
            return packageName;
        }
    }

    public static final class FakeTask {
        public FakeTaskKey key;
        private final boolean multiple;
        private final int partnerTaskId;
        int lastPartnerLookupTaskId = Integer.MIN_VALUE;

        FakeTask(int id,
                 int userId,
                 long lastActiveTime,
                 String packageName,
                 boolean multiple,
                 int partnerTaskId) {
            key = new FakeTaskKey(id, userId, lastActiveTime, packageName);
            this.multiple = multiple;
            this.partnerTaskId = partnerTaskId;
        }

        public boolean hasMultipleTasks() {
            return multiple;
        }

        public int getAnotherMultiTaskId(int taskId) {
            lastPartnerLookupTaskId = taskId;
            return partnerTaskId;
        }
    }
}
