package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class StageWorkspaceRecentsSourceTest {

    @Test
    public void snapshotPreservesVendorTaskAndRecentsOrder() {
        FakeTask first = task(11, 0, "same.pkg");
        FakeTask second = task(12, 0, "same.pkg");

        List<StageWorkspaceRecentsSource.Item> items =
                StageWorkspaceRecentsSource.fromVendorTasks(Arrays.asList(first, second), 8);

        assertEquals(2, items.size());
        assertSame(first, items.get(0).vendorTask());
        assertSame(second, items.get(1).vendorTask());
        assertEquals(11, items.get(0).entry().taskId());
        assertEquals(12, items.get(1).entry().taskId());
    }

    @Test
    public void malformedTasksAreSkippedBeforeApplyingCardLimit() {
        FakeTask malformed = task(-1, 0, "bad.pkg");
        FakeTask first = task(21, 0, "a.pkg");
        FakeTask second = task(22, 0, "b.pkg");

        List<StageWorkspaceRecentsSource.Item> items =
                StageWorkspaceRecentsSource.fromVendorTasks(
                        Arrays.asList(malformed, first, second), 2);

        assertEquals(2, items.size());
        assertSame(first, items.get(0).vendorTask());
        assertSame(second, items.get(1).vendorTask());
    }

    @Test
    public void emptyOrNonPositiveLimitFailsClosed() {
        assertTrue(StageWorkspaceRecentsSource.fromVendorTasks(null, 8).isEmpty());
        assertTrue(StageWorkspaceRecentsSource.fromVendorTasks(List.of(task(1, 0, "a")), 0)
                .isEmpty());
    }

    private static FakeTask task(int id, int userId, String packageName) {
        return new FakeTask(id, userId, packageName);
    }

    public static final class FakeTaskKey {
        public final int id;
        public final int userId;
        public final long lastActiveTime = 1L;
        private final String packageName;

        FakeTaskKey(int id, int userId, String packageName) {
            this.id = id;
            this.userId = userId;
            this.packageName = packageName;
        }

        public String getPackageName() {
            return packageName;
        }
    }

    public static final class FakeTask {
        public final FakeTaskKey key;

        FakeTask(int id, int userId, String packageName) {
            key = new FakeTaskKey(id, userId, packageName);
        }

        public boolean hasMultipleTasks() {
            return false;
        }

        public int getAnotherMultiTaskId(int taskId) {
            return -1;
        }
    }
}
