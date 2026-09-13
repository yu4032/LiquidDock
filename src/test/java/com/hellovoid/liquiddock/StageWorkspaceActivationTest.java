package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StageWorkspaceActivationTest {

    @Test
    public void firstActivationAppliesCompletePlanThenMarksMapped() {
        FakeStore store = new FakeStore(false);
        FakeApplier applier = new FakeApplier(true);
        StageWorkspaceActivation activation = new StageWorkspaceActivation(store, applier);

        StageWorkspaceActivation.Result result = activation.ensureMapped(Arrays.asList(
                item(1, 0), item(2, 5)));

        assertTrue(result.active());
        assertTrue(result.changed());
        assertEquals(1, applier.calls);
        assertEquals(2, applier.last.get(0).cellX());
        assertEquals(7, applier.last.get(1).cellX());
        assertTrue(store.mapped);
        assertEquals(1, store.markCalls);
    }

    @Test
    public void failedApplyNeverMarksMapped() {
        FakeStore store = new FakeStore(false);
        FakeApplier applier = new FakeApplier(false);
        StageWorkspaceActivation activation = new StageWorkspaceActivation(store, applier);

        StageWorkspaceActivation.Result result = activation.ensureMapped(
                Arrays.asList(item(1, 0)));

        assertFalse(result.active());
        assertFalse(result.changed());
        assertFalse(store.mapped);
        assertEquals(0, store.markCalls);
        assertEquals(1, applier.calls);
    }

    @Test
    public void mappedStateValidatesButNeverAppliesSecondShift() {
        FakeStore store = new FakeStore(true);
        FakeApplier applier = new FakeApplier(true);
        StageWorkspaceActivation activation = new StageWorkspaceActivation(store, applier);

        StageWorkspaceActivation.Result result = activation.ensureMapped(Arrays.asList(
                item(1, 2), item(2, 7)));

        assertTrue(result.active());
        assertFalse(result.changed());
        assertEquals(0, applier.calls);
        assertEquals(0, store.markCalls);
    }

    @Test
    public void mappedStateWithStageColumnContentFailsClosed() {
        FakeStore store = new FakeStore(true);
        FakeApplier applier = new FakeApplier(true);
        StageWorkspaceActivation activation = new StageWorkspaceActivation(store, applier);

        StageWorkspaceActivation.Result result = activation.ensureMapped(
                Arrays.asList(item(1, 1)));

        assertFalse(result.active());
        assertFalse(result.changed());
        assertEquals(0, applier.calls);
        assertTrue(store.mapped);
    }

    @Test
    public void invalidLegacyPlanDoesNotApplyOrMark() {
        FakeStore store = new FakeStore(false);
        FakeApplier applier = new FakeApplier(true);
        StageWorkspaceActivation activation = new StageWorkspaceActivation(store, applier);

        StageWorkspaceActivation.Result result = activation.ensureMapped(Arrays.asList(
                new HomeGridItemPosition(1, 10, 5, 0, 2, 1)));

        assertFalse(result.active());
        assertEquals(0, applier.calls);
        assertEquals(0, store.markCalls);
    }

    private static HomeGridItemPosition item(long id, int x) {
        return new HomeGridItemPosition(id, 10, x, 0, 1, 1);
    }

    private static final class FakeStore implements StageWorkspaceActivation.MappingStore {
        boolean mapped;
        int markCalls;

        FakeStore(boolean mapped) { this.mapped = mapped; }

        @Override public boolean isMapped() { return mapped; }

        @Override public boolean markMapped() {
            mapped = true;
            markCalls++;
            return true;
        }
    }

    private static final class FakeApplier implements StageWorkspaceActivation.LayoutApplier {
        final boolean result;
        int calls;
        List<HomeGridItemPosition> last;

        FakeApplier(boolean result) { this.result = result; }

        @Override public boolean apply(Collection<HomeGridItemPosition> positions) {
            calls++;
            last = new java.util.ArrayList<>(positions);
            return result;
        }
    }
}
