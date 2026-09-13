package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StageWorkspaceOrientationCoexistenceTest {

    @Test
    public void activeStageOwnsLandscapeTargetButNotPortraitTarget() {
        assertFalse(StageWorkspacePolicy.orientationMemoryOwnsTarget(
                HomeGridOrientation.LANDSCAPE, true));
        assertTrue(StageWorkspacePolicy.orientationMemoryOwnsTarget(
                HomeGridOrientation.PORTRAIT, true));
    }

    @Test
    public void inactiveStageLeavesOrientationMemoryOwnershipUnchanged() {
        assertTrue(StageWorkspacePolicy.orientationMemoryOwnsTarget(
                HomeGridOrientation.LANDSCAPE, false));
        assertTrue(StageWorkspacePolicy.orientationMemoryOwnsTarget(
                HomeGridOrientation.PORTRAIT, false));
    }

    @Test
    public void settledMappedUserLayoutReplacesPersistedStageTarget() {
        FakeStore store = new FakeStore(Arrays.asList(item(1, 2), item(2, 7)));
        StageWorkspaceActivation activation = new StageWorkspaceActivation(
                store, positions -> true);

        boolean saved = activation.refreshMappedTarget(Arrays.asList(
                item(1, 3), item(2, 6)));

        assertTrue(saved);
        assertEquals(1, store.saveCalls);
        assertEquals(3, store.target.get(0).cellX());
        assertEquals(6, store.target.get(1).cellX());
    }

    @Test
    public void invalidSettledLayoutNeverReplacesPersistedTarget() {
        FakeStore store = new FakeStore(Arrays.asList(item(1, 2)));
        StageWorkspaceActivation activation = new StageWorkspaceActivation(
                store, positions -> true);

        boolean saved = activation.refreshMappedTarget(Arrays.asList(item(1, 1)));

        assertFalse(saved);
        assertEquals(0, store.saveCalls);
        assertEquals(2, store.target.get(0).cellX());
    }

    private static HomeGridItemPosition item(long id, int x) {
        return new HomeGridItemPosition(id, 10, x, 0, 1, 1);
    }

    private static final class FakeStore implements StageWorkspaceActivation.MappingStore {
        List<HomeGridItemPosition> target;
        int saveCalls;

        FakeStore(Collection<HomeGridItemPosition> target) {
            this.target = new ArrayList<>(target);
        }

        @Override public Collection<HomeGridItemPosition> loadTarget() {
            return new ArrayList<>(target);
        }

        @Override public boolean saveTarget(Collection<HomeGridItemPosition> positions) {
            saveCalls++;
            target = new ArrayList<>(positions);
            return true;
        }
    }
}
