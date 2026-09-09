package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

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
}
