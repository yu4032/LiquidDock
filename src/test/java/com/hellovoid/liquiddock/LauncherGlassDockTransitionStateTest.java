package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class LauncherGlassDockTransitionStateTest {
    @Test public void dockLaunchAndReturnOwnSharedWorkspacePresentation() {
        LauncherGlassDockTransitionState state = new LauncherGlassDockTransitionState();
        assertFalse(state.sample().active);
        assertTrue(state.onNativeHidden());
        assertTrue(state.sample().active);
        assertFalse(state.sample().returning);
        assertFalse(state.onNativeHidden());
        assertTrue(state.onHomeStarted());
        assertTrue(state.sample().returning);
        assertTrue(state.onProxyFrame(true, .9f));
        assertEquals(.9f, state.sample().progress, 0f);
        assertTrue(state.onHomeSettled());
        assertFalse(state.sample().active);
    }

    @Test public void dockOnlySourceHidesWholeWorkspaceAndReturnsOnSameFadeClock() {
        LauncherGlassDockTransitionState state = new LauncherGlassDockTransitionState();
        state.onNativeHidden();
        LauncherGlassDockTransitionState.Sample away =
                LauncherGlassDockTransitionState.mergeWorkspace(
                        false, false, 0f, state.sample());
        assertTrue(away.active);
        assertFalse(away.returning);
        state.onHomeStarted();
        state.onProxyFrame(true, .97f);
        LauncherGlassDockTransitionState.Sample home =
                LauncherGlassDockTransitionState.mergeWorkspace(
                        false, false, 0f, state.sample());
        assertTrue(home.active);
        assertTrue(home.returning);
        assertEquals(.97f, home.progress, .00001f);
        state.onHomeSettled();
        assertFalse(LauncherGlassDockTransitionState.mergeWorkspace(
                false, false, 0f, state.sample()).active);
    }

    @Test public void localWorkspaceProxyKeepsAuthorityWhenDockIsIdleOrRetargeted() {
        LauncherGlassDockTransitionState state = new LauncherGlassDockTransitionState();
        LauncherGlassDockTransitionState.Sample local =
                LauncherGlassDockTransitionState.mergeWorkspace(
                        true, true, .92f, state.sample());
        assertTrue(local.active);
        assertTrue(local.returning);
        assertEquals(.92f, local.progress, 0f);
        state.onNativeHidden();
        state.onHomeStarted();
        state.onHomeProgress(.95f);
        LauncherGlassDockTransitionState.Sample merged =
                LauncherGlassDockTransitionState.mergeWorkspace(
                        true, true, .92f, state.sample());
        assertTrue(merged.returning);
        assertEquals(.95f, merged.progress, 0f);
        state.onHomeRetargetedAway();
        assertTrue(LauncherGlassDockTransitionState.mergeWorkspace(
                true, true, .92f, state.sample()).returning);
    }

    @Test public void lateProxyCannotReopenCompletedHome() {
        LauncherGlassDockTransitionState state = new LauncherGlassDockTransitionState();
        state.onNativeHidden();
        state.onHomeStarted();
        state.onHomeSettled();
        assertFalse(state.onProxyFrame(true, .99f));
        assertFalse(state.onProxyFrame(false, .2f));
        assertFalse(state.sample().active);
        assertTrue(state.onNativeHidden());
        assertTrue(state.sample().active);
    }

    @Test public void nativeSourceVisibleCannotEndRunningHomeReturn() {
        LauncherGlassDockTransitionState state = new LauncherGlassDockTransitionState();
        state.onNativeHidden();
        state.onHomeStarted();
        state.onProxyFrame(true, .95f);
        assertFalse(state.onNativeVisible());
        assertTrue(state.sample().active);
        assertTrue(state.sample().returning);
        assertTrue(state.onHomeSettled());
    }

    @Test public void cancelLaunchAndRetargetAwayRestoreCorrectOwner() {
        LauncherGlassDockTransitionState state = new LauncherGlassDockTransitionState();
        state.onNativeHidden();
        assertTrue(state.onNativeVisible());
        assertFalse(state.sample().active);
        state.onNativeHidden();
        state.onHomeStarted();
        assertTrue(state.onHomeRetargetedAway());
        assertTrue(state.sample().active);
        assertFalse(state.sample().returning);
        assertTrue(state.onNativeVisible());
        assertFalse(state.sample().active);
    }

    @Test public void proxyFramesCanRecoverMissingDockHideDuringHome() {
        LauncherGlassDockTransitionState state = new LauncherGlassDockTransitionState();
        state.onHomeStarted();
        assertTrue(state.onProxyFrame(true, .5f));
        assertTrue(state.sample().active);
        assertTrue(state.sample().returning);
        assertTrue(state.onHomeProgress(.97f));
        assertEquals(.97f, state.sample().progress, 0f);
        state.onHomeSettled();
    }

    @Test public void firstDockLaunchProxyCanPrecedeNativeHidden() {
        LauncherGlassDockTransitionState state = new LauncherGlassDockTransitionState();
        assertTrue(state.onProxyFrame(false, .2f));
        assertTrue(state.sample().active);
        assertFalse(state.sample().returning);
        assertFalse("duplicate source hide must not reset proxy", state.onNativeHidden());
        assertEquals(.2f, state.sample().progress, 0f);
        state.onNativeVisible();
        assertFalse(state.onProxyFrame(false, .5f));
    }

    @Test public void newCycleAndInvalidProgressDoNotLeakOldVisibility() {
        LauncherGlassDockTransitionState state = new LauncherGlassDockTransitionState();
        state.onNativeHidden();
        state.onHomeStarted();
        assertFalse(state.onHomeProgress(Float.NaN));
        assertTrue(state.onHomeProgress(.8f));
        assertFalse(state.onHomeProgress(.8f));
        state.onHomeSettled();
        state.onNativeHidden();
        assertEquals(0f, state.sample().progress, 0f);
        assertFalse(state.sample().returning);
    }
}
