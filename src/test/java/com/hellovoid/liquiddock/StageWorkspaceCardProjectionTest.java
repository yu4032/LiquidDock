package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StageWorkspaceCardProjectionTest {

    @Test
    public void projectionPreservesRecentsOrderAndTaskInstanceIdentity() {
        Object firstVendor = new Object();
        Object secondVendor = new Object();
        StageWorkspaceRecentsSource.Item first = item(firstVendor, 41, "same.pkg");
        StageWorkspaceRecentsSource.Item second = item(secondVendor, 42, "same.pkg");

        List<StageWorkspaceCardProjection.Card> cards =
                StageWorkspaceCardProjection.project(List.of(first, second));

        assertEquals(2, cards.size());
        assertEquals(0, cards.get(0).slotIndex());
        assertEquals(1, cards.get(1).slotIndex());
        assertEquals(41, cards.get(0).entry().taskId());
        assertEquals(42, cards.get(1).entry().taskId());
        assertSame(firstVendor, cards.get(0).vendorTask());
        assertSame(secondVendor, cards.get(1).vendorTask());
    }

    @Test
    public void projectionPublishesImmutableCopy() {
        ArrayList<StageWorkspaceRecentsSource.Item> mutable = new ArrayList<>();
        mutable.add(item(new Object(), 51, "a.pkg"));

        List<StageWorkspaceCardProjection.Card> cards =
                StageWorkspaceCardProjection.project(mutable);
        mutable.clear();

        assertEquals(1, cards.size());
        try {
            cards.clear();
            fail("projected cards must be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void unavailableSnapshotFailsClosed() {
        assertTrue(StageWorkspaceCardProjection.project(null).isEmpty());
        assertTrue(StageWorkspaceCardProjection.project(List.of()).isEmpty());
    }

    private static StageWorkspaceRecentsSource.Item item(
            Object vendorTask, int taskId, String packageName) {
        StageWorkspaceTaskModel.TaskEntry entry = new StageWorkspaceTaskModel.TaskEntry(
                taskId, 0, packageName, taskId, false, -1);
        return new StageWorkspaceRecentsSource.Item(vendorTask, entry);
    }
}
