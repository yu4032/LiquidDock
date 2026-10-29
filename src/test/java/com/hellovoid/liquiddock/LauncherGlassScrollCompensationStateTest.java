package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherGlassScrollCompensationStateTest {
    @Test
    public void forwardScrollProjectsCapturedGeometryToLiveScroll() {
        LauncherGlassScrollProjectionState state = new LauncherGlassScrollProjectionState();
        state.onScrollMutation(100, 140);

        assertEquals(460f, state.projectCenterX(500f, 100, true), 0.001f);
    }

    @Test
    public void currentGeometryAnchorNeedsNoProjection() {
        LauncherGlassScrollProjectionState state = new LauncherGlassScrollProjectionState();
        state.onScrollMutation(100, 140);

        assertEquals(500f, state.projectCenterX(500f, 140, true), 0.001f);
    }

    @Test
    public void newerScrollKeepsResidualProjectionForOlderGeometry() {
        LauncherGlassScrollProjectionState state = new LauncherGlassScrollProjectionState();
        state.onScrollMutation(100, 140);
        state.onScrollMutation(140, 160);

        assertEquals(480f, state.projectCenterX(500f, 140, true), 0.001f);
    }

    @Test
    public void reverseScrollProjectsInOppositeDirection() {
        LauncherGlassScrollProjectionState state = new LauncherGlassScrollProjectionState();
        state.onScrollMutation(140, 100);

        assertEquals(540f, state.projectCenterX(500f, 140, true), 0.001f);
    }

    @Test
    public void firstMutationProvidesAnchorForGeometryWithoutCapturedScroll() {
        LauncherGlassScrollProjectionState state = new LauncherGlassScrollProjectionState();
        state.onScrollMutation(100, 140);

        assertEquals(460f, state.projectCenterX(500f, 0, false), 0.001f);
    }

    @Test
    public void resetDropsOldScrollProjection() {
        LauncherGlassScrollProjectionState state = new LauncherGlassScrollProjectionState();
        state.onScrollMutation(100, 140);
        state.reset();

        assertEquals(500f, state.projectCenterX(500f, 100, true), 0.001f);
    }

    @Test
    public void rapidMutationsExposeLatestImmutableScrollSnapshot() {
        LauncherGlassScrollProjectionState state = new LauncherGlassScrollProjectionState();
        state.onScrollMutation(100, 120);
        LauncherGlassScrollProjectionState.Snapshot first = state.snapshot();
        state.onScrollMutation(120, 150);
        assertFalse(state.isCurrent(first));
        assertEquals(480f, first.projectCenterX(500f, 100, true), 0.001f);
        assertEquals(450f, state.snapshot().projectCenterX(500f, 100, true), 0.001f);
    }

    @Test
    public void oneGlFrameCannotMixDifferentScrollRevisions() {
        LauncherGlassScrollProjectionState state = new LauncherGlassScrollProjectionState();
        state.onScrollMutation(100, 130);
        LauncherGlassScrollProjectionState.Snapshot glFrame = state.snapshot();
        assertTrue(state.isCurrent(glFrame));
        assertEquals(470f, glFrame.projectCenterX(500f, 100, true), 0.001f);

        state.onScrollMutation(130, 160);
        // Every node keeps the original GL-frame projection, rather than reading 160.
        assertEquals(470f, glFrame.projectCenterX(500f, 100, true), 0.001f);
        assertFalse(state.isCurrent(glFrame));
        assertEquals(440f, state.snapshot().projectCenterX(500f, 100, true), 0.001f);
    }

    @Test
    public void resetInvalidatesAnInFlightGlFrame() {
        LauncherGlassScrollProjectionState state = new LauncherGlassScrollProjectionState();
        state.onScrollMutation(100, 140);
        LauncherGlassScrollProjectionState.Snapshot old = state.snapshot();
        state.reset();

        assertFalse(state.isCurrent(old));
        assertEquals(500f, state.snapshot().projectCenterX(500f, 100, true), 0.001f);
    }

    @Test
    public void unchangedScrollDoesNotSupersedeAFrame() {
        LauncherGlassScrollProjectionState state = new LauncherGlassScrollProjectionState();
        LauncherGlassScrollProjectionState.Snapshot frame = state.snapshot();
        state.onScrollMutation(100, 100);
        assertTrue(state.isCurrent(frame));
    }

}
