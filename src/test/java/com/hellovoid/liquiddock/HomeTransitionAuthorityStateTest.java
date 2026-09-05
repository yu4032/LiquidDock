package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure authority behavior for Launcher <-> SystemUI HOME presentation handoff. */
public class HomeTransitionAuthorityStateTest {
    @Test public void launcherHomeStartFreezesWithoutOwningVisualReveal() {
        HomeTransitionAuthorityState state = new HomeTransitionAuthorityState();

        HomeTransitionAuthorityState.Decision start = state.onLauncherHomeStarted();
        assertTrue(start.freezeBarrier);
        assertFalse("animTo may freeze freshness but must not reveal cached glass",
                start.beginReveal);
        assertFalse(start.releaseBarrier);
    }

    @Test public void launcherAnimationStartRevealsEvenWithSystemUiAuthority() {
        HomeTransitionAuthorityState state = new HomeTransitionAuthorityState();
        state.onLauncherHomeStarted();
        state.onSystemUiStarted(true, 8L, 100L);

        HomeTransitionAuthorityState.Decision animationStart =
                state.onLauncherHomeAnimationStarted();

        assertTrue("the real Launcher HOME spring start must own visual reveal even while SystemUI is armed",
                animationStart.beginReveal);
        assertFalse("animation start must not release the freshness barrier",
                animationStart.releaseBarrier);
    }

    @Test public void launcherEndReleasesFallbackButWaitsForActiveSystemUiAuthority() {
        HomeTransitionAuthorityState state = new HomeTransitionAuthorityState();
        state.onLauncherHomeStarted();

        HomeTransitionAuthorityState.Decision fallbackEnd = state.onLauncherHomeEnded(100L);
        assertTrue(fallbackEnd.releaseBarrier);
        assertFalse(fallbackEnd.waitForSystemUi);

        state.onLauncherHomeStarted();
        state.onSystemUiStarted(true, 9L, 200L);
        HomeTransitionAuthorityState.Decision heldEnd = state.onLauncherHomeEnded(250L);
        assertFalse(heldEnd.releaseBarrier);
        assertTrue(heldEnd.waitForSystemUi);
    }

    @Test public void systemUiStartOwnsBarrierButNotVisualReveal() {
        HomeTransitionAuthorityState state = new HomeTransitionAuthorityState();

        HomeTransitionAuthorityState.Decision start = state.onSystemUiStarted(true, 11L, 100L);
        assertTrue(start.freezeBarrier);
        assertFalse("SystemUI transition tracking must not own Launcher cached-layer reveal timing",
                start.beginReveal);
        assertFalse(start.releaseBarrier);

        HomeTransitionAuthorityState.Decision wrong =
                state.onSystemUiFinished(true, 12L, 150L);
        assertFalse(wrong.releaseBarrier);

        HomeTransitionAuthorityState.Decision finish =
                state.onSystemUiFinished(true, 11L, 200L);
        assertTrue(finish.releaseBarrier);
        assertFalse(state.isSystemUiAuthorityActive());
    }

    @Test public void staleSystemUiStartCannotRearmAfterLauncherFinished() {
        HomeTransitionAuthorityState state = new HomeTransitionAuthorityState();
        state.onLauncherHomeStarted();
        state.onLauncherHomeEnded(500L);

        HomeTransitionAuthorityState.Decision stale =
                state.onSystemUiStarted(true, 13L, 400L);
        assertFalse(stale.freezeBarrier);
        assertFalse(stale.beginReveal);
        assertFalse(state.isSystemUiAuthorityActive());
    }

    @Test public void newerHomeHiddenStartSupersedesActiveAuthorityAndReleases() {
        HomeTransitionAuthorityState state = new HomeTransitionAuthorityState();
        state.onSystemUiStarted(true, 21L, 100L);

        HomeTransitionAuthorityState.Decision hidden =
                state.onSystemUiStarted(false, 22L, 150L);
        assertTrue(hidden.releaseBarrier);
        assertFalse(hidden.freezeBarrier);
        assertFalse(state.isSystemUiAuthorityActive());
    }

    @Test public void staleTimestampAndInvalidFinishAreIgnored() {
        HomeTransitionAuthorityState state = new HomeTransitionAuthorityState();
        state.onSystemUiStarted(true, 30L, 100L);

        assertFalse(state.onSystemUiStarted(true, 31L, 100L).freezeBarrier);
        assertFalse(state.onSystemUiFinished(false, 30L, 110L).releaseBarrier);
        assertFalse(state.onSystemUiFinished(true, 31L, 120L).releaseBarrier);
        assertTrue(state.isSystemUiAuthorityActive());
    }
}
