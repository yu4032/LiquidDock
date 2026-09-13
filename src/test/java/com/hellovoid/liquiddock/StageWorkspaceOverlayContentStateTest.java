package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class StageWorkspaceOverlayContentStateTest {

    @Test
    public void showReplacesCurrentSnapshot() {
        StageWorkspaceOverlayContentState state = new StageWorkspaceOverlayContentState();
        StageWorkspaceRecentsSource.Item first = item(1);
        StageWorkspaceRecentsSource.Item second = item(2);

        state.apply(StageWorkspaceOverlayState.Action.SHOW, List.of(first));
        assertEquals(1, state.items().size());
        assertSame(first, state.items().get(0));

        state.apply(StageWorkspaceOverlayState.Action.SHOW, List.of(second));
        assertEquals(1, state.items().size());
        assertSame(second, state.items().get(0));
    }

    @Test
    public void hideClearsButIgnorePreservesCurrentSnapshot() {
        StageWorkspaceOverlayContentState state = new StageWorkspaceOverlayContentState();
        StageWorkspaceRecentsSource.Item item = item(3);
        state.apply(StageWorkspaceOverlayState.Action.SHOW, List.of(item));

        state.apply(StageWorkspaceOverlayState.Action.IGNORE, List.of());
        assertEquals(1, state.items().size());
        assertSame(item, state.items().get(0));

        state.apply(StageWorkspaceOverlayState.Action.HIDE, List.of(item(4)));
        assertTrue(state.items().isEmpty());
    }

    @Test
    public void showWithUnavailableSnapshotFailsClosedToEmpty() {
        StageWorkspaceOverlayContentState state = new StageWorkspaceOverlayContentState();
        state.apply(StageWorkspaceOverlayState.Action.SHOW, List.of(item(5)));

        state.apply(StageWorkspaceOverlayState.Action.SHOW, null);

        assertTrue(state.items().isEmpty());
    }

    @Test
    public void publishedSnapshotIsImmutableCopy() {
        StageWorkspaceOverlayContentState state = new StageWorkspaceOverlayContentState();
        java.util.ArrayList<StageWorkspaceRecentsSource.Item> mutable = new java.util.ArrayList<>();
        mutable.add(item(6));

        state.apply(StageWorkspaceOverlayState.Action.SHOW, mutable);
        mutable.clear();

        assertEquals(1, state.items().size());
    }

    private static StageWorkspaceRecentsSource.Item item(int taskId) {
        Object vendor = new Object();
        StageWorkspaceTaskModel.TaskEntry entry = new StageWorkspaceTaskModel.TaskEntry(
                taskId, 0, "pkg." + taskId, taskId, false, -1);
        return new StageWorkspaceRecentsSource.Item(vendor, entry);
    }
}
