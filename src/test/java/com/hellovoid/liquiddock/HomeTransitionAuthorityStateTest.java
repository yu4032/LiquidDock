package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure capture-authority behavior for Launcher <-> SystemUI HOME handoff. */
public class HomeTransitionAuthorityStateTest {
    @Test public void launcherHomeStartFreezesCapture() {
        HomeTransitionAuthorityState state = new HomeTransitionAuthorityState();

        HomeTransitionAuthorityState.Decision start = state.onLauncherHomeStarted();
        assertTrue(start.freezeBarrier);
        assertFalse(start.releaseBarrier);
        assertTrue(state.isBarrierFrozen());
    }

    @Test public void launcherEndReleasesFallbackButWaitsForActiveSystemUiAuthority() {
        HomeTransitionAuthorityState state = new HomeTransitionAuthorityState();
        state.onLauncherHomeStarted();

        HomeTransitionAuthorityState.Decision fallbackEnd = state.onLauncherHomeEnded(100L);
        assertTrue(fallbackEnd.releaseBarrier);
        assertFalse(fallbackEnd.waitForSystemUi);
        assertFalse(state.isBarrierFrozen());

        state.onLauncherHomeStarted();
        state.onSystemUiStarted(true, 9L, 200L);
        HomeTransitionAuthorityState.Decision heldEnd = state.onLauncherHomeEnded(250L);
        assertFalse(heldEnd.releaseBarrier);
        assertTrue(heldEnd.waitForSystemUi);
        assertTrue(state.isBarrierFrozen());
    }

    @Test public void matchingSystemUiStartAndFinishOwnCaptureBarrier() {
        HomeTransitionAuthorityState state = new HomeTransitionAuthorityState();

        HomeTransitionAuthorityState.Decision start = state.onSystemUiStarted(true, 11L, 100L);
        assertTrue(start.freezeBarrier);
        assertFalse(start.releaseBarrier);
        assertTrue(state.isBarrierFrozen());

        HomeTransitionAuthorityState.Decision wrong =
                state.onSystemUiFinished(true, 12L, 150L);
        assertFalse(wrong.releaseBarrier);
        assertTrue(state.isBarrierFrozen());

        HomeTransitionAuthorityState.Decision finish =
                state.onSystemUiFinished(true, 11L, 200L);
        assertTrue(finish.releaseBarrier);
        assertFalse(state.isSystemUiAuthorityActive());
        assertFalse(state.isBarrierFrozen());
    }

    @Test public void staleSystemUiStartCannotRearmAfterLauncherFinished() {
        HomeTransitionAuthorityState state = new HomeTransitionAuthorityState();
        state.onLauncherHomeStarted();
        state.onLauncherHomeEnded(500L);

        HomeTransitionAuthorityState.Decision stale =
                state.onSystemUiStarted(true, 13L, 400L);
        assertFalse(stale.freezeBarrier);
        assertFalse(state.isSystemUiAuthorityActive());
        assertFalse(state.isBarrierFrozen());
    }

    @Test public void newerHomeHiddenStartSupersedesActiveAuthorityAndReleases() {
        HomeTransitionAuthorityState state = new HomeTransitionAuthorityState();
        state.onSystemUiStarted(true, 21L, 100L);

        HomeTransitionAuthorityState.Decision hidden =
                state.onSystemUiStarted(false, 22L, 150L);
        assertTrue(hidden.releaseBarrier);
        assertFalse(hidden.freezeBarrier);
        assertFalse(state.isSystemUiAuthorityActive());
        assertFalse(state.isBarrierFrozen());
    }

    @Test public void staleTimestampAndInvalidFinishAreIgnored() {
        HomeTransitionAuthorityState state = new HomeTransitionAuthorityState();
        state.onSystemUiStarted(true, 30L, 100L);

        assertFalse(state.onSystemUiStarted(true, 31L, 100L).freezeBarrier);
        assertFalse(state.onSystemUiFinished(false, 30L, 110L).releaseBarrier);
        assertFalse(state.onSystemUiFinished(true, 31L, 120L).releaseBarrier);
        assertTrue(state.isSystemUiAuthorityActive());
        assertTrue(state.isBarrierFrozen());
    }
}
