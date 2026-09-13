package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StageWorkspaceActivationTest {

    @Test
    public void firstActivationPersistsTargetBeforeApplyingIt() {
        List<String> events = new ArrayList<>();
        FakeStore store = new FakeStore(null, true, events);
        FakeApplier applier = new FakeApplier(true, events);
        StageWorkspaceActivation activation = new StageWorkspaceActivation(store, applier);

        StageWorkspaceActivation.Result result = activation.ensureMapped(Arrays.asList(
                item(1, 0), item(2, 5)));

        assertTrue(result.active());
        assertTrue(result.changed());
        assertEquals(Arrays.asList("save", "apply"), events);
        assertEquals(1, store.saveCalls);
        assertEquals(2, store.target.get(0).cellX());
        assertEquals(7, store.target.get(1).cellX());
        assertEquals(1, applier.calls);
    }

    @Test
    public void failedTargetPersistenceNeverMutatesLauncherLayout() {
        List<String> events = new ArrayList<>();
        FakeStore store = new FakeStore(null, false, events);
        FakeApplier applier = new FakeApplier(true, events);
        StageWorkspaceActivation activation = new StageWorkspaceActivation(store, applier);

        StageWorkspaceActivation.Result result = activation.ensureMapped(
                Arrays.asList(item(1, 0)));

        assertFalse(result.active());
        assertFalse(result.changed());
        assertEquals(Collections.singletonList("save"), events);
        assertEquals(0, applier.calls);
    }

    @Test
    public void persistedTargetReplaysAfterRestartWithoutAddingTwoAgain() {
        List<HomeGridItemPosition> target = Arrays.asList(item(1, 2), item(2, 7));
        FakeStore store = new FakeStore(target, true, new ArrayList<>());
        FakeApplier applier = new FakeApplier(true, new ArrayList<>());
        StageWorkspaceActivation activation = new StageWorkspaceActivation(store, applier);

        StageWorkspaceActivation.Result result = activation.ensureMapped(Arrays.asList(
                item(1, 0), item(2, 5)));

        assertTrue(result.active());
        assertTrue(result.changed());
        assertEquals(1, applier.calls);
        assertEquals(2, applier.last.get(0).cellX());
        assertEquals(7, applier.last.get(1).cellX());
        assertEquals(0, store.saveCalls);
    }

    @Test
    public void alreadyMatchingTargetDoesNotReapplyLayout() {
        List<HomeGridItemPosition> target = Arrays.asList(item(1, 2), item(2, 7));
        FakeStore store = new FakeStore(target, true, new ArrayList<>());
        FakeApplier applier = new FakeApplier(true, new ArrayList<>());
        StageWorkspaceActivation activation = new StageWorkspaceActivation(store, applier);

        StageWorkspaceActivation.Result result = activation.ensureMapped(target);

        assertTrue(result.active());
        assertFalse(result.changed());
        assertEquals(0, applier.calls);
        assertEquals(0, store.saveCalls);
    }

    @Test
    public void invalidPersistedTargetFailsClosedWithoutApplying() {
        FakeStore store = new FakeStore(
                Arrays.asList(item(1, 1)), true, new ArrayList<>());
        FakeApplier applier = new FakeApplier(true, new ArrayList<>());
        StageWorkspaceActivation activation = new StageWorkspaceActivation(store, applier);

        StageWorkspaceActivation.Result result = activation.ensureMapped(
                Arrays.asList(item(1, 0)));

        assertFalse(result.active());
        assertEquals(0, applier.calls);
    }

    @Test
    public void failedReplayKeepsPersistedTargetForSafeRetry() {
        List<HomeGridItemPosition> target = Arrays.asList(item(1, 2));
        FakeStore store = new FakeStore(target, true, new ArrayList<>());
        FakeApplier applier = new FakeApplier(false, new ArrayList<>());
        StageWorkspaceActivation activation = new StageWorkspaceActivation(store, applier);

        StageWorkspaceActivation.Result result = activation.ensureMapped(
                Arrays.asList(item(1, 0)));

        assertFalse(result.active());
        assertEquals(1, applier.calls);
        assertEquals(2, store.target.get(0).cellX());
    }

    @Test
    public void invalidLegacyPlanDoesNotPersistOrApply() {
        FakeStore store = new FakeStore(null, true, new ArrayList<>());
        FakeApplier applier = new FakeApplier(true, new ArrayList<>());
        StageWorkspaceActivation activation = new StageWorkspaceActivation(store, applier);

        StageWorkspaceActivation.Result result = activation.ensureMapped(Arrays.asList(
                new HomeGridItemPosition(1, 10, 5, 0, 2, 1)));

        assertFalse(result.active());
        assertEquals(0, store.saveCalls);
        assertEquals(0, applier.calls);
    }

    private static HomeGridItemPosition item(long id, int x) {
        return new HomeGridItemPosition(id, 10, x, 0, 1, 1);
    }

    private static final class FakeStore implements StageWorkspaceActivation.MappingStore {
        List<HomeGridItemPosition> target;
        final boolean saveResult;
        final List<String> events;
        int saveCalls;

        FakeStore(Collection<HomeGridItemPosition> target,
                  boolean saveResult,
                  List<String> events) {
            this.target = target == null ? null : new ArrayList<>(target);
            this.saveResult = saveResult;
            this.events = events;
        }

        @Override public Collection<HomeGridItemPosition> loadTarget() {
            return target == null ? null : new ArrayList<>(target);
        }

        @Override public boolean saveTarget(Collection<HomeGridItemPosition> positions) {
            events.add("save");
            saveCalls++;
            if (!saveResult) return false;
            target = new ArrayList<>(positions);
            return true;
        }
    }

    private static final class FakeApplier implements StageWorkspaceActivation.LayoutApplier {
        final boolean result;
        final List<String> events;
        int calls;
        List<HomeGridItemPosition> last;

        FakeApplier(boolean result, List<String> events) {
            this.result = result;
            this.events = events;
        }

        @Override public boolean apply(Collection<HomeGridItemPosition> positions) {
            events.add("apply");
            calls++;
            last = new ArrayList<>(positions);
            return result;
        }
    }
}
