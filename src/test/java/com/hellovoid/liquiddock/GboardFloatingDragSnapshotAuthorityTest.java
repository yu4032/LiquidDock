package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GboardFloatingDragSnapshotAuthorityTest {
    @Test public void dragStartLatchesExactlyOneSnapshotAndPausesLiveBackdrop() {
        GboardFloatingDragSnapshotAuthority state = new GboardFloatingDragSnapshotAuthority();

        GboardFloatingDragSnapshotAuthority.Decision first = state.onDragStarted(true);
        assertTrue(first.latchSnapshot);
        assertTrue(first.pauseLiveSource);
        assertTrue(state.isDragging());
        assertFalse(state.acceptLiveBackdrop());

        GboardFloatingDragSnapshotAuthority.Decision duplicate = state.onDragStarted(true);
        assertFalse(duplicate.latchSnapshot);
        assertFalse(duplicate.pauseLiveSource);
    }

    @Test public void dragKeepsLocalGlassRenderingButRejectsBackdropMutation() {
        GboardFloatingDragSnapshotAuthority state = new GboardFloatingDragSnapshotAuthority();
        state.onDragStarted(true);

        assertTrue(state.allowGeometryRender());
        assertFalse(state.acceptLiveBackdrop());
        assertFalse(state.allowPrepareBackdrop());
        assertFalse(state.allowProducerReconcile());
        assertFalse(state.allowFreshRequest());
    }

    @Test public void dragWithoutPreparedBackdropDoesNotEnterSnapshotMode() {
        GboardFloatingDragSnapshotAuthority state = new GboardFloatingDragSnapshotAuthority();
        GboardFloatingDragSnapshotAuthority.Decision decision = state.onDragStarted(false);

        assertFalse(decision.latchSnapshot);
        assertFalse(decision.pauseLiveSource);
        assertFalse(state.isDragging());
        assertTrue(state.acceptLiveBackdrop());
    }

    @Test public void dragEndResumesLiveAndRequestsOneFreshBackdrop() {
        GboardFloatingDragSnapshotAuthority state = new GboardFloatingDragSnapshotAuthority();
        state.onDragStarted(true);

        GboardFloatingDragSnapshotAuthority.Decision end = state.onDragEnded();
        assertTrue(end.resumeLiveSource);
        assertTrue(end.reconcileProducer);
        assertTrue(end.requestFreshBackdrop);
        assertFalse(state.isDragging());
        assertTrue(state.acceptLiveBackdrop());

        GboardFloatingDragSnapshotAuthority.Decision duplicate = state.onDragEnded();
        assertFalse(duplicate.resumeLiveSource);
        assertFalse(duplicate.reconcileProducer);
        assertFalse(duplicate.requestFreshBackdrop);
    }
}
