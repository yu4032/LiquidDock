package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GestureHandleSystemUiSceneStateTest {
    @Test
    public void homeAndOtherTasksSwitchVisibilityWithoutLauncherState() {
        GestureHandleSystemUiSceneState state = new GestureHandleSystemUiSceneState();

        assertFalse(state.shouldHide());
        assertTrue(state.onTaskMovedToFront(true));
        assertTrue(state.snapshot().home);
        assertFalse(state.onTaskMovedToFront(false));
        assertFalse(state.snapshot().home);
    }

    @Test
    public void recentsStaysHiddenAfterAnimationSettlesWhenOverviewWasShown() {
        GestureHandleSystemUiSceneState state = new GestureHandleSystemUiSceneState();

        assertTrue(state.onRecentsAnimationChanged(true));
        assertTrue(state.onOverviewShown());
        assertTrue(state.onRecentsAnimationChanged(false));
        assertTrue(state.snapshot().overview);

        assertFalse(state.onTaskMovedToFront(false));
        assertFalse(state.snapshot().overview);
    }

    @Test
    public void homeRemainsHiddenAcrossOverviewExitToHome() {
        GestureHandleSystemUiSceneState state = new GestureHandleSystemUiSceneState();

        assertTrue(state.onTaskMovedToFront(true));
        assertTrue(state.onOverviewShown());
        assertTrue(state.onTaskMovedToFront(true));
        assertTrue(state.shouldHide());
        assertFalse(state.snapshot().overview);
        assertTrue(state.snapshot().home);
    }

    @Test
    public void proxyDisconnectClearsOnlyRecentsAuthority() {
        GestureHandleSystemUiSceneState state = new GestureHandleSystemUiSceneState();

        state.onTaskMovedToFront(true);
        state.onRecentsAnimationChanged(true);
        state.onOverviewShown();
        assertTrue(state.onLauncherProxyDisconnected());
        assertTrue(state.snapshot().home);
        assertFalse(state.snapshot().recentsAnimation);
        assertFalse(state.snapshot().overview);
    }
}
