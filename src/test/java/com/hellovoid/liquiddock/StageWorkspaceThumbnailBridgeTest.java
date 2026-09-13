package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class StageWorkspaceThumbnailBridgeTest {

    @Test
    public void thumbnailComesFromActivityManagerWrapperForVendorTaskKey() {
        Object bitmap = new Object();
        FakeTask task = new FakeTask(71, bitmap);
        FakeActivityManagerWrapper wrapper = new FakeActivityManagerWrapper();

        Object result = StageWorkspaceThumbnailBridge.thumbnailFrom(wrapper, task);

        assertSame(bitmap, result);
        assertSame(task.key, wrapper.requestedKey);
    }

    @Test
    public void missingTaskKeyOrThumbnailFailsClosed() {
        assertNull(StageWorkspaceThumbnailBridge.thumbnailFrom(
                new FakeActivityManagerWrapper(), new Object()));
        assertNull(StageWorkspaceThumbnailBridge.thumbnailFrom(
                new FakeActivityManagerWrapper(), new FakeTask(72, null)));
        assertNull(StageWorkspaceThumbnailBridge.thumbnailFrom(null, new FakeTask(73, new Object())));
    }

    public static final class FakeTaskKey {
        public final int id;

        FakeTaskKey(int id) {
            this.id = id;
        }
    }

    public static final class FakeThumbnailData {
        public final Object thumbnail;

        FakeThumbnailData(Object thumbnail) {
            this.thumbnail = thumbnail;
        }
    }

    public static final class FakeTask {
        public final FakeTaskKey key;
        private final Object thumbnail;

        FakeTask(int id, Object thumbnail) {
            key = new FakeTaskKey(id);
            this.thumbnail = thumbnail;
        }
    }

    public static final class FakeActivityManagerWrapper {
        Object requestedKey;

        public FakeThumbnailData getTaskThumbnail(FakeTaskKey key) {
            requestedKey = key;
            return new FakeThumbnailData(findThumbnail(key));
        }

        private Object findThumbnail(FakeTaskKey key) {
            return key == null ? null : ThumbnailRegistry.find(key.id);
        }
    }

    private static final class ThumbnailRegistry {
        private static FakeTask current;

        static Object find(int taskId) {
            return current != null && current.key.id == taskId ? current.thumbnail : null;
        }
    }

    private static FakeTask register(FakeTask task) {
        ThumbnailRegistry.current = task;
        return task;
    }
}
