package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure behavior coverage for Workspace presentation/freshness ownership. */
public class LauncherGlassSceneControllerTest {
    @Test public void nativeFolderMustHideIndependentCachedWorkspaceGlass() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        state.consumeFadeReveal();
        assertTrue(state.isLayerVisible());

        // Production folder entry passes followNativeAlpha=false:
        // the TextureView is not a child of the OEM-faded Workspace.
        state.setFolderCovered(true, false);
        assertFalse("Stale source-folder glass cannot stay at its desktop position",
                state.isLayerVisible());
        assertFalse(state.followsNativeFolderFade());
        long prior = state.generation();
        state.setFolderCovered(false, false);
        assertFalse("Do not reveal stale folder glass after native close",
                state.isLayerVisible());
        assertTrue(state.generation() > prior);
        state.onFreshFrameReady(prior);
        assertFalse(state.isLayerVisible());
        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
        assertFalse("OEM close already has its own transition", state.consumeFadeReveal());
    }

    @Test public void unsupportedFolderRetainsTheExistingHardCoverageFallback() throws Exception {
        LauncherGlassSceneController.StateMachine state = new LauncherGlassSceneController.StateMachine();
        state.onRootReady(); state.onFreshFrameReady(state.generation()); state.consumeFadeReveal();
        java.lang.reflect.Method cover = state.getClass().getDeclaredMethod("setFolderCovered", boolean.class, boolean.class);
        cover.invoke(state, true, false);
        assertFalse(state.isLayerVisible());
        cover.invoke(state, false, false);
        assertFalse(state.isLayerVisible());
        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
        assertFalse("Folder close must not add a second fade after OEM spring",
                state.consumeFadeReveal());
    }
    @Test public void folderCloseUsesFreshFrameButNotAnAlphaReveal() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        state.consumeFadeReveal();
        state.setFolderCovered(true, false);
        long stale = state.generation();
        state.setFolderCovered(false, false);
        assertFalse(state.isLayerVisible());
        state.onFreshFrameReady(stale);
        assertFalse("Stale pixels must remain covered", state.isLayerVisible());
        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
        assertFalse("Native folder closing already provides the transition", state.consumeFadeReveal());
    }
    @Test
    public void hardCoveredSceneRequiresFreshFrameBeforeReveal() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        long first = state.generation();
        assertFalse(state.isLayerVisible());
        state.onFreshFrameReady(first);
        assertTrue(state.isLayerVisible());

        state.setHardCovered(true);
        assertFalse(state.isLayerVisible());
        state.setHardCovered(false);
        assertFalse(state.isLayerVisible());
        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
    }

    @Test
    public void staleGenerationCannotRevealLayer() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        long stale = state.generation();
        state.onGenerationInvalidated();
        assertTrue(state.generation() > stale);
        state.onFreshFrameReady(stale);
        assertFalse(state.isLayerVisible());
        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
    }

    @Test
    public void onlyFirstFreshFrameAfterHardCoverageRequestsFadeReveal() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        assertTrue(state.consumeFadeReveal());

        state.setHardCovered(true);
        state.setHardCovered(false);
        state.onFreshFrameReady(state.generation());
        assertTrue(state.consumeFadeReveal());
        assertFalse(state.consumeFadeReveal());

        state.onGenerationInvalidated();
        state.onFreshFrameReady(state.generation());
        assertFalse(state.consumeFadeReveal());
    }

    @Test
    public void homePresentationFencePreservesCurrentSourceUntilRealInvalidation() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        state.consumeFadeReveal();

        long current = state.generation();
        state.onPresentationStarted();
        assertEquals("HOME presentation alone is not a source discontinuity",
                current, state.generation());
        assertTrue("cached/current-generation glass remains presentation-safe",
                state.isLayerVisible());
        assertFalse("settle must not demand a new static-wallpaper OES frame",
                state.shouldRequestFreshAfterPresentation());

        state.onGenerationInvalidated();
        assertTrue(state.shouldRequestFreshAfterPresentation());
    }

    @Test
    public void acceptedWorkstationRolloverStillWaitsForFreshSceneWithoutHidingCache() {
        WorkstationRecentsRecoveryPolicy.Decision recovery =
                WorkstationRecentsRecoveryPolicy.onRecentsReturn(true, true);
        assertTrue(recovery.allowUncover);

        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
        state.consumeFadeReveal();

        state.setCovered(true);
        assertEquals(LauncherGlassSceneController.State.COVERED, state.state());
        assertTrue("Recents capture cover leaves cached glass under MIUI's native layer",
                state.isLayerVisible());

        if (recovery.allowUncover) state.setCovered(false);
        assertEquals(LauncherGlassSceneController.State.HOME_WAITING_FRESH_FRAME, state.state());
        assertTrue("endpoint rollover acceptance is not a fresh frame, but must not hide the cache",
                state.isLayerVisible());
        assertFalse(state.consumeFadeReveal());

        state.onFreshFrameReady(state.generation());
        assertEquals(LauncherGlassSceneController.State.HOME_VISIBLE, state.state());
        assertTrue(state.isLayerVisible());
        assertFalse("fresh Workstation recovery replaces content without a second visual reveal",
                state.consumeFadeReveal());
    }
}
