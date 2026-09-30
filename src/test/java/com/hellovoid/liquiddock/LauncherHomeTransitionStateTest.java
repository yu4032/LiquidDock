package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure lifecycle ownership for Launcher's native HOME RectFSpringAnim. */
public class LauncherHomeTransitionStateTest {
    @Test public void firstHomeSpringStartFreezesCapture() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object animation = new Object();

        LauncherHomeTransitionState.Decision start =
                state.onHomeAnimationStarted(animation);

        assertTrue(start.freezeBarrier);
        assertFalse(start.releaseBarrier);
        assertTrue(state.isArmed());
    }

    @Test public void duplicateListenerStartIsIdempotent() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object animation = new Object();
        state.onHomeAnimationStarted(animation);

        LauncherHomeTransitionState.Decision duplicate =
                state.onHomeAnimationStarted(animation);

        assertFalse(duplicate.freezeBarrier);
        assertFalse(duplicate.releaseBarrier);
        assertTrue(state.isArmed());
    }

    @Test public void onlyActiveSpringMayReleaseBarrier() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object active = new Object();
        Object unrelated = new Object();
        state.onHomeAnimationStarted(active);

        LauncherHomeTransitionState.Decision ignored =
                state.onHomeAnimationTerminal(unrelated);
        LauncherHomeTransitionState.Decision released =
                state.onHomeAnimationTerminal(active);

        assertFalse(ignored.releaseBarrier);
        assertTrue(released.releaseBarrier);
        assertFalse(state.isArmed());
    }

    @Test public void newerSpringSupersedesOlderWithoutDroppingBarrier() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object first = new Object();
        Object second = new Object();
        state.onHomeAnimationStarted(first);

        LauncherHomeTransitionState.Decision supersede =
                state.onHomeAnimationStarted(second);
        LauncherHomeTransitionState.Decision staleEnd =
                state.onHomeAnimationTerminal(first);
        LauncherHomeTransitionState.Decision finalEnd =
                state.onHomeAnimationTerminal(second);

        assertFalse(supersede.freezeBarrier);
        assertFalse(staleEnd.releaseBarrier);
        assertTrue(finalEnd.releaseBarrier);
        assertFalse(state.isArmed());
    }
}
