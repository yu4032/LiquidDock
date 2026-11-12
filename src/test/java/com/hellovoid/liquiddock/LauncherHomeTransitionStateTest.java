package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure lifecycle ownership for Launcher's native HOME WindowElement + RectFSpringAnim. */
public class LauncherHomeTransitionStateTest {
    @Test public void firstHomeSpringStartFreezesCapture() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object owner = new Object();
        Object animation = new Object();

        LauncherHomeTransitionState.Decision start =
                state.onHomeAnimationStarted(owner, animation);

        assertTrue(start.freezeBarrier);
        assertFalse(start.releaseBarrier);
        assertTrue(state.isArmed());
    }

    @Test public void duplicateListenerStartInsideSamePhysicalCycleIsIdempotent() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object owner = new Object();
        Object animation = new Object();
        state.onHomeAnimationStarted(owner, animation);
        LauncherHomeTransitionState.Decision duplicate =
                state.onHomeAnimationStarted(owner, animation);

        assertFalse(duplicate.freezeBarrier);
        assertFalse(duplicate.releaseBarrier);
        assertTrue(state.isArmed());
    }

    @Test public void physicalSpringEndDoesNotReleaseBeforeShellCompletion() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object owner = new Object();
        Object animation = new Object();
        state.onHomeAnimationStarted(owner, animation);

        state.onSpringPhysicalTerminal(animation);

        assertTrue("merge may still own the presentation after physical spring end",
                state.isArmed());
    }

    @Test public void matchingHomeFinishCompletionReleasesBarrier() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object owner = new Object();
        Object animation = new Object();
        state.onHomeAnimationStarted(owner, animation);
        state.onSpringPhysicalTerminal(animation);

        state.onOwnerFinishRequested(owner);
        LauncherHomeTransitionState.Decision completed =
                state.onOwnerFinishCompleted(owner);

        assertTrue(completed.releaseBarrier);
        assertFalse(state.isArmed());
    }

    @Test public void finishRequestWaitsForLauncherCompletion() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object owner = new Object();
        Object animation = new Object();
        state.onHomeAnimationStarted(owner, animation);

        state.onOwnerFinishRequested(owner);

        assertTrue(state.isArmed());
        assertTrue(state.onOwnerFinishCompleted(owner).releaseBarrier);
        assertFalse(state.isArmed());
    }

    @Test public void repeatedSpringStartAfterPhysicalEndCreatesNewCycle() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object owner = new Object();
        Object animation = new Object();
        state.onHomeAnimationStarted(owner, animation);
        state.onSpringPhysicalTerminal(animation);
        state.onOwnerFinishRequested(owner);

        LauncherHomeTransitionState.Decision next =
                state.onHomeAnimationStarted(owner, animation);

        assertFalse("barrier is already armed across the cycle handoff", next.freezeBarrier);
        assertFalse("old completion must not release the newer HOME cycle",
                state.onOwnerFinishCompleted(owner).releaseBarrier);
        assertTrue(state.isArmed());
    }

    @Test public void newerSpringSupersedesOlderWithoutDroppingBarrier() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object owner = new Object();
        Object first = new Object();
        Object second = new Object();
        state.onHomeAnimationStarted(owner, first);

        LauncherHomeTransitionState.Decision supersede =
                state.onHomeAnimationStarted(owner, second);
        state.onSpringPhysicalTerminal(first);

        assertFalse(supersede.freezeBarrier);
        assertTrue(state.isArmed());

        state.onOwnerFinishRequested(owner);
        assertTrue(state.onOwnerFinishCompleted(owner).releaseBarrier);
        assertFalse(state.isArmed());
    }

    @Test public void physicalTerminalWithoutOwnerCompletionEventuallyReleases() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object owner = new Object();
        Object spring = new Object();
        state.onHomeAnimationStarted(owner, spring);
        long cycle = state.onSpringPhysicalTerminal(spring);
        assertTrue(cycle > 0L);
        assertTrue("physical terminal alone cannot release a merged HOME owner", state.isArmed());
        assertTrue(state.onTerminalGraceExpired(cycle).releaseBarrier);
        assertFalse(state.isArmed());
        assertFalse(state.onTerminalGraceExpired(cycle).releaseBarrier);
    }

    @Test public void failOpenCannotReleaseLiveOrRestartedSpring() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object owner = new Object();
        Object spring = new Object();
        state.onHomeAnimationStarted(owner, spring);
        assertFalse(state.onTerminalGraceExpired(1L).releaseBarrier);
        long oldCycle = state.onSpringPhysicalTerminal(spring);
        state.onHomeAnimationStarted(owner, spring);
        assertFalse(state.onTerminalGraceExpired(oldCycle).releaseBarrier);
        assertTrue(state.isArmed());
        long newCycle = state.onSpringPhysicalTerminal(spring);
        assertTrue(state.onTerminalGraceExpired(newCycle).releaseBarrier);
    }

    @Test public void naturalCompletionOrRetargetSupersedesPendingTimeout() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object owner = new Object();
        Object spring = new Object();
        state.onHomeAnimationStarted(owner, spring);
        long cycle = state.onSpringPhysicalTerminal(spring);
        state.onOwnerFinishRequested(owner);
        assertTrue(state.onOwnerFinishCompleted(owner).releaseBarrier);
        assertFalse(state.onTerminalGraceExpired(cycle).releaseBarrier);
        state.onHomeAnimationStarted(owner, spring);
        long nextCycle = state.onSpringPhysicalTerminal(spring);
        assertTrue(state.onAnimationRetargetedAway(owner, spring).releaseBarrier);
        assertFalse(state.onTerminalGraceExpired(nextCycle).releaseBarrier);
    }

    @Test public void stalePhysicalTerminalCannotArmRecoveryForNewOwner() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object owner = new Object();
        Object previousSpring = new Object();
        Object activeSpring = new Object();
        state.onHomeAnimationStarted(owner, previousSpring);
        state.onHomeAnimationStarted(owner, activeSpring);
        assertTrue(state.onSpringPhysicalTerminal(previousSpring) < 0L);
        assertFalse(state.onTerminalGraceExpired(1L).releaseBarrier);
        assertTrue(state.isArmed());
    }

    @Test public void acceptedRetargetAwayReleasesOnlyMatchingHomeSpring() {
        LauncherHomeTransitionState state = new LauncherHomeTransitionState();
        Object owner = new Object();
        Object animation = new Object();
        state.onHomeAnimationStarted(owner, animation);

        LauncherHomeTransitionState.Decision wrong =
                state.onAnimationRetargetedAway(new Object(), animation);
        LauncherHomeTransitionState.Decision released =
                state.onAnimationRetargetedAway(owner, animation);

        assertFalse(wrong.releaseBarrier);
        assertTrue(released.releaseBarrier);
        assertFalse(state.isArmed());
    }
}
