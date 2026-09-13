package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class StageWorkspaceThumbnailBridgeTest {

    @Test
    public void thumbnailComesFromActivityManagerWrapperForVendorTaskKey() {
        Object bitmap = new Object();
        FakeTask task = new FakeTask(71);
        FakeActivityManagerWrapper wrapper = new FakeActivityManagerWrapper(bitmap);

        Object result = StageWorkspaceThumbnailBridge.thumbnailFrom(wrapper, task);

        assertSame(bitmap, result);
        assertSame(task.key, wrapper.requestedKey);
    }

    @Test
    public void missingTaskKeyOrThumbnailFailsClosed() {
        assertNull(StageWorkspaceThumbnailBridge.thumbnailFrom(
                new FakeActivityManagerWrapper(new Object()), new Object()));
        assertNull(StageWorkspaceThumbnailBridge.thumbnailFrom(
                new FakeActivityManagerWrapper(null), new FakeTask(72)));
        assertNull(StageWorkspaceThumbnailBridge.thumbnailFrom(null, new FakeTask(73)));
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

        FakeTask(int id) {
            key = new FakeTaskKey(id);
        }
    }

    public static final class FakeActivityManagerWrapper {
        private final Object thumbnail;
        Object requestedKey;

        FakeActivityManagerWrapper(Object thumbnail) {
            this.thumbnail = thumbnail;
        }

        public FakeThumbnailData getTaskThumbnail(FakeTaskKey key) {
            requestedKey = key;
            return new FakeThumbnailData(thumbnail);
        }
    }
}
