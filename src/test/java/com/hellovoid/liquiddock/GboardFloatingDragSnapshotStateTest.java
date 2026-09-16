package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GboardFloatingDragSnapshotStateTest {
    @Test public void dragWithPresentedFrameFreezesLiveUpdates() {
        GboardFloatingDragSnapshotState state = new GboardFloatingDragSnapshotState();
        assertTrue(state.onDragStarted(true).pauseUpdates);
        assertTrue(state.isSnapshotDragging());
        assertFalse(state.shouldAcceptFreshFrame());
        assertFalse(state.shouldSampleGeometry());
    }

    @Test public void dragWithoutPresentedFrameStaysLive() {
        GboardFloatingDragSnapshotState state = new GboardFloatingDragSnapshotState();
        assertFalse(state.onDragStarted(false).pauseUpdates);
        assertFalse(state.isSnapshotDragging());
        assertTrue(state.shouldAcceptFreshFrame());
        assertTrue(state.shouldSampleGeometry());
    }

    @Test public void dragEndRequestsSingleFreshRecovery() {
        GboardFloatingDragSnapshotState state = new GboardFloatingDragSnapshotState();
        state.onDragStarted(true);
        GboardFloatingDragSnapshotState.Decision end = state.onDragEnded();
        assertTrue(end.resumeUpdates);
        assertTrue(end.reconcileRoot);
        assertTrue(end.requestFresh);
        assertTrue(state.isRecovering());
        assertTrue(state.shouldAcceptFreshFrame());
    }

    @Test public void recoveryReturnsLiveOnlyAfterFreshFrameIsPresented() {
        GboardFloatingDragSnapshotState state = new GboardFloatingDragSnapshotState();
        state.onDragStarted(true);
        state.onDragEnded();
        state.onRecoveryFreshFramePrepared();
        assertTrue(state.isRecovering());
        state.onOutputPresented();
        assertFalse(state.isRecovering());
        assertTrue(state.shouldSampleGeometry());
    }

    @Test public void ordinaryPresentationDoesNotChangeLiveState() {
        GboardFloatingDragSnapshotState state = new GboardFloatingDragSnapshotState();
        state.onOutputPresented();
        assertFalse(state.isRecovering());
        assertTrue(state.shouldAcceptFreshFrame());
    }
}
