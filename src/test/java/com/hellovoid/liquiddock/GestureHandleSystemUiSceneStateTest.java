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
    public void swipeUpKeepsHandleVisibleUntilOverviewIsActuallyShown() {
        GestureHandleSystemUiSceneState state = new GestureHandleSystemUiSceneState();

        assertFalse(state.onRecentsAnimationChanged(true));
        assertFalse(state.shouldHide());

        assertTrue(state.onOverviewShown());
        assertTrue(state.onRecentsAnimationChanged(false));
        assertTrue(state.snapshot().overview);

        assertFalse(state.onTaskMovedToFront(false));
        assertFalse(state.snapshot().overview);
    }

    @Test
    public void homeHidesOnlyAfterRecentsAnimationSettles() {
        GestureHandleSystemUiSceneState state = new GestureHandleSystemUiSceneState();

        assertFalse(state.onRecentsAnimationChanged(true));
        assertFalse(state.onTaskMovedToFront(true));
        assertTrue(state.snapshot().home);
        assertTrue(state.snapshot().recentsAnimation);

        assertTrue(state.onRecentsAnimationChanged(false));
        assertTrue(state.shouldHide());
        assertTrue(state.snapshot().home);
        assertFalse(state.snapshot().recentsAnimation);
    }

    @Test
    public void cancelledSwipeNeverHidesTheHandle() {
        GestureHandleSystemUiSceneState state = new GestureHandleSystemUiSceneState();

        assertFalse(state.onRecentsAnimationChanged(true));
        assertFalse(state.onRecentsAnimationChanged(false));
        assertFalse(state.shouldHide());
    }

    @Test
    public void futureGenerationResetDiscardsStaleOverviewAndHome() {
        GestureHandleSystemUiSceneState state = new GestureHandleSystemUiSceneState();
        state.onTaskMovedToFront(true);
        state.onOverviewShown();
        assertTrue(state.shouldHide());
        state.resetForFutureReload();
        assertFalse(state.snapshot().home);
        assertFalse(state.snapshot().recentsAnimation);
        assertFalse(state.snapshot().overview);
        assertFalse(state.shouldHide());
        assertFalse(state.onTaskMovedToFront(false));
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
