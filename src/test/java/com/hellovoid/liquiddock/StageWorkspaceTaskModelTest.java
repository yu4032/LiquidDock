package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StageWorkspaceTaskModelTest {

    @Test
    public void preservesRecentsOrderAndDistinctTaskInstances() {
        List<StageWorkspaceTaskModel.TaskEntry> input = Arrays.asList(
                entry(42, 0, "com.example.mail", 100L, false, -1),
                entry(43, 0, "com.example.mail", 90L, false, -1),
                entry(9, 0, "com.example.browser", 80L, false, -1));

        List<StageWorkspaceTaskModel.TaskEntry> cards =
                StageWorkspaceTaskModel.cardsFromRecents(input, 8);

        assertEquals(3, cards.size());
        assertEquals(42, cards.get(0).taskId());
        assertEquals(43, cards.get(1).taskId());
        assertEquals(9, cards.get(2).taskId());
        assertEquals("com.example.mail", cards.get(0).packageName());
        assertEquals("com.example.mail", cards.get(1).packageName());
    }

    @Test
    public void taskIdentityIncludesUserAndNeverUsesPackageAsIdentity() {
        StageWorkspaceTaskModel.TaskEntry personal =
                entry(7, 0, "com.example.docs", 100L, false, -1);
        StageWorkspaceTaskModel.TaskEntry work =
                entry(7, 10, "com.example.docs", 99L, false, -1);

        assertFalse(StageWorkspaceTaskModel.sameInstance(personal, work));
        assertTrue(StageWorkspaceTaskModel.sameInstance(
                personal, entry(7, 0, "com.example.docs", 1L, false, -1)));
    }

    @Test
    public void groupedTaskRetainsPartnerTaskId() {
        StageWorkspaceTaskModel.TaskEntry grouped =
                entry(20, 0, "com.example.video", 100L, true, 21);

        List<StageWorkspaceTaskModel.TaskEntry> cards =
                StageWorkspaceTaskModel.cardsFromRecents(List.of(grouped), 8);

        assertEquals(1, cards.size());
        assertTrue(cards.get(0).grouped());
        assertEquals(21, cards.get(0).partnerTaskId());
    }

    @Test
    public void boundedCardsTakePrefixWithoutResorting() {
        List<StageWorkspaceTaskModel.TaskEntry> input = Arrays.asList(
                entry(3, 0, "c", 1L, false, -1),
                entry(2, 0, "b", 999L, false, -1),
                entry(1, 0, "a", 500L, false, -1));

        List<StageWorkspaceTaskModel.TaskEntry> cards =
                StageWorkspaceTaskModel.cardsFromRecents(input, 2);

        assertEquals(2, cards.size());
        assertEquals(3, cards.get(0).taskId());
        assertEquals(2, cards.get(1).taskId());
    }

    @Test
    public void invalidTaskIdsFailClosedWithoutCollapsingValidInstances() {
        List<StageWorkspaceTaskModel.TaskEntry> input = Arrays.asList(
                entry(-1, 0, "bad", 100L, false, -1),
                entry(5, 0, "same.pkg", 90L, false, -1),
                entry(6, 0, "same.pkg", 80L, false, -1));

        List<StageWorkspaceTaskModel.TaskEntry> cards =
                StageWorkspaceTaskModel.cardsFromRecents(input, 8);

        assertEquals(2, cards.size());
        assertEquals(5, cards.get(0).taskId());
        assertEquals(6, cards.get(1).taskId());
    }

    private static StageWorkspaceTaskModel.TaskEntry entry(
            int taskId,
            int userId,
            String packageName,
            long lastActiveTime,
            boolean grouped,
            int partnerTaskId) {
        return new StageWorkspaceTaskModel.TaskEntry(
                taskId,
                userId,
                packageName,
                lastActiveTime,
                grouped,
                partnerTaskId);
    }
}
