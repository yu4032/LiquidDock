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
                state.onHomeAnimationStarted(new Object(), animation);

        assertTrue(start.freezeBarrier);
        assertFalse(start.releaseBarrier);
        assertTrue(state.isArmed());
    }

    @Test public void duplicateListenerStartIsIdempotent() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object animation = new Object();
        state.onHomeAnimationStarted(new Object(), animation);

        LauncherHomeTransitionState.Decision duplicate =
                state.onHomeAnimationStarted(new Object(), animation);

        assertFalse(duplicate.freezeBarrier);
        assertFalse(duplicate.releaseBarrier);
        assertTrue(state.isArmed());
    }

    @Test public void onlyActiveSpringMayReleaseBarrier() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object active = new Object();
        Object unrelated = new Object();
        Object owner = new Object();
        state.onHomeAnimationStarted(owner, active);

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
        Object owner = new Object();
        state.onHomeAnimationStarted(owner, first);

        LauncherHomeTransitionState.Decision supersede =
                state.onHomeAnimationStarted(owner, second);
        LauncherHomeTransitionState.Decision staleEnd =
                state.onHomeAnimationTerminal(first);
        LauncherHomeTransitionState.Decision finalEnd =
                state.onHomeAnimationTerminal(second);

        assertFalse(supersede.freezeBarrier);
        assertFalse(staleEnd.releaseBarrier);
        assertTrue(finalEnd.releaseBarrier);
        assertFalse(state.isArmed());
    }
    @Test public void ownerTerminalReleasesWhenSpringCallbackIsUnavailable() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object owner = new Object();
        Object animation = new Object();
        state.onHomeAnimationStarted(owner, animation);

        LauncherHomeTransitionState.Decision wrong =
                state.onHomeOwnerTerminal(new Object());
        LauncherHomeTransitionState.Decision released =
                state.onHomeOwnerTerminal(owner);

        assertFalse(wrong.releaseBarrier);
        assertTrue(released.releaseBarrier);
        assertFalse(state.isArmed());
    }

}
