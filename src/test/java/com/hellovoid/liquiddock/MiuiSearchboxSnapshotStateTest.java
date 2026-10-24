package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MiuiSearchboxSnapshotStateTest {
    @Test
    public void oneFreshFrameIsAcceptedPerVisibleCaptureCycle() {
        MiuiSearchboxSnapshotState state = new MiuiSearchboxSnapshotState();

        state.beginCapture();
        assertTrue(state.acceptFreshFrame());
        assertFalse(state.acceptFreshFrame());
        assertFalse(state.acceptFreshFrame());

        state.beginCapture();
        assertTrue(state.acceptFreshFrame());
        assertFalse(state.acceptFreshFrame());
    }

    @Test
    public void rootRotationRequestsOneNewSnapshotAndRejectsStaleFrames() {
        MiuiSearchboxSnapshotState state = new MiuiSearchboxSnapshotState();
        assertFalse(state.updateRootSize(1200, 2400));
        assertFalse(state.updateRootSize(1200, 2400));

        state.beginCapture();
        assertTrue(state.acceptFreshFrame(1200, 2400));

        assertTrue(state.updateRootSize(2400, 1200));
        assertFalse(state.updateRootSize(2400, 1200));
        state.beginCapture();
        assertFalse(state.acceptFreshFrame(1200, 2400));
        assertTrue(state.acceptFreshFrame(2400, 1200));
        assertFalse(state.acceptFreshFrame(2400, 1200));
    }

    @Test
    public void nativeProducerRebindOnlyOnOrientationSwap() {
        assertTrue(MiuiSearchboxSnapshotState.orientationChanged(1200, 2400, 2400, 1200));
        assertTrue(MiuiSearchboxSnapshotState.orientationChanged(2400, 1200, 1200, 2400));
        assertFalse(MiuiSearchboxSnapshotState.orientationChanged(1200, 2400, 1180, 2200));
        assertFalse(MiuiSearchboxSnapshotState.orientationChanged(2400, 1200, 2100, 1180));
        assertFalse(MiuiSearchboxSnapshotState.orientationChanged(0, 0, 2400, 1200));
    }

    @Test
    public void invalidSizesDoNotInvalidateSnapshotOrConsumePendingFrame() {
        MiuiSearchboxSnapshotState state = new MiuiSearchboxSnapshotState();
        assertFalse(state.updateRootSize(1000, 2000));
        assertFalse(state.updateRootSize(0, 0));
        state.beginCapture();
        assertFalse(state.acceptFreshFrame(2000, 1000));
        assertTrue(state.acceptFreshFrame(1000, 2000));
        assertTrue(state.updateRootSize(2000, 1000));
    }
}
