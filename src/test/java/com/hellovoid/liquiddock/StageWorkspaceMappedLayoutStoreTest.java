package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

public class StageWorkspaceMappedLayoutStoreTest {

    @Test
    public void completeMappedTargetRoundTrips() {
        MemoryStore raw = new MemoryStore();
        StageWorkspaceMappedLayoutStore store = new StageWorkspaceMappedLayoutStore(raw);

        store.save(Arrays.asList(
                item(1, 10, 2, 0, 1, 1),
                item(2, 10, 6, 1, 2, 2)));

        StageWorkspaceMappedLayoutStore.Snapshot snapshot = store.load();
        assertNotNull(snapshot);
        assertEquals(2, snapshot.positions().size());
        assertEquals(2, snapshot.positions().get(0).cellX());
        assertEquals(6, snapshot.positions().get(1).cellX());
    }

    @Test
    public void corruptPayloadFailsClosed() {
        MemoryStore raw = new MemoryStore();
        raw.write(StageWorkspaceMappedLayoutStore.KEY, "v1\n1,10,2,0,1");
        StageWorkspaceMappedLayoutStore store = new StageWorkspaceMappedLayoutStore(raw);

        assertNull(store.load());
    }

    @Test
    public void duplicateIdsFailClosed() {
        MemoryStore raw = new MemoryStore();
        raw.write(StageWorkspaceMappedLayoutStore.KEY,
                "v1\n1,10,2,0,1,1\n1,10,3,0,1,1");
        StageWorkspaceMappedLayoutStore store = new StageWorkspaceMappedLayoutStore(raw);

        assertNull(store.load());
    }

    @Test
    public void stageColumnOrOverflowTargetFailsClosed() {
        MemoryStore raw = new MemoryStore();
        raw.write(StageWorkspaceMappedLayoutStore.KEY,
                "v1\n1,10,1,0,1,1");
        StageWorkspaceMappedLayoutStore store = new StageWorkspaceMappedLayoutStore(raw);
        assertNull(store.load());

        raw.write(StageWorkspaceMappedLayoutStore.KEY,
                "v1\n1,10,7,0,2,1");
        assertNull(store.load());
    }

    @Test
    public void emptyTargetRoundTripsAsCompleteEmptyLayout() {
        MemoryStore raw = new MemoryStore();
        StageWorkspaceMappedLayoutStore store = new StageWorkspaceMappedLayoutStore(raw);
        store.save(java.util.Collections.emptyList());

        StageWorkspaceMappedLayoutStore.Snapshot snapshot = store.load();
        assertNotNull(snapshot);
        assertEquals(0, snapshot.positions().size());
    }

    private static HomeGridItemPosition item(long id, long screen, int x, int y,
                                             int spanX, int spanY) {
        return new HomeGridItemPosition(id, screen, x, y, spanX, spanY);
    }

    private static final class MemoryStore implements HomeGridOrientationMemoryStore {
        private final Map<String, String> values = new HashMap<>();

        @Override public String read(String key) { return values.get(key); }
        @Override public void write(String key, String value) { values.put(key, value); }
        @Override public void remove(String key) { values.remove(key); }
    }
}
