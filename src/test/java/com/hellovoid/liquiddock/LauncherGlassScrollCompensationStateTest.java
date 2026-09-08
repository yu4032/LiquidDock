package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LauncherGlassScrollCompensationStateTest {
    @Test
    public void firstForwardScrollUsesPreviousScrollAsRenderedAnchor() {
        LauncherGlassScrollCompensationState state =
                new LauncherGlassScrollCompensationState();

        assertEquals(-40f, state.onScrollMutation(100, 140), 0.001f);
    }

    @Test
    public void presentedFrameClearsCompensationWhenRendererCatchesUp() {
        LauncherGlassScrollCompensationState state =
                new LauncherGlassScrollCompensationState();
        state.onScrollMutation(100, 140);

        assertEquals(0f, state.onFramePresented(140), 0.001f);
    }

    @Test
    public void olderPresentedFrameKeepsResidualCompensationForNewerScroll() {
        LauncherGlassScrollCompensationState state =
                new LauncherGlassScrollCompensationState();
        state.onScrollMutation(100, 140);
        state.onScrollMutation(140, 160);

        assertEquals(-20f, state.onFramePresented(140), 0.001f);
    }

    @Test
    public void reverseScrollCompensatesInOppositeDirection() {
        LauncherGlassScrollCompensationState state =
                new LauncherGlassScrollCompensationState();

        assertEquals(40f, state.onScrollMutation(140, 100), 0.001f);
    }

    @Test
    public void resetDropsOldRenderedAnchor() {
        LauncherGlassScrollCompensationState state =
                new LauncherGlassScrollCompensationState();
        state.onScrollMutation(100, 140);
        state.reset();

        assertEquals(-10f, state.onScrollMutation(300, 310), 0.001f);
    }
}
