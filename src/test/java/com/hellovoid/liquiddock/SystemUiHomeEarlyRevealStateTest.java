package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** HOME presentation may reveal the cached static layer while fresh wallpaper capture stays blocked. */
public class SystemUiHomeEarlyRevealStateTest {
    @Test
    public void homeStartCanFadeCachedLayerBeforeFreshFrame() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());

        state.onGenerationInvalidated();
        assertFalse(state.isLayerVisible());

        state.beginRevealBeforeFreshFrame();
        assertTrue("cached static layer must become presentation-visible during HOME animation",
                state.isLayerVisible());
        assertTrue("early reveal must request exactly one compositor fade",
                state.consumeFadeReveal());
        assertFalse(state.consumeFadeReveal());

        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
        assertFalse("fresh frame must not start a second fade after cached early reveal",
                state.consumeFadeReveal());
    }

    @Test
    public void recentsHomeStartCanRevealBeforeVendorHide() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        state.setCovered(true);
        assertEquals(LauncherGlassSceneController.State.COVERED, state.state());
        assertFalse(state.isLayerVisible());

        // HOME START invalidates freshness before vendor onRecentViewHide arrives at animation end.
        state.onGenerationInvalidated();
        long homeGeneration = state.generation();
        assertEquals(LauncherGlassSceneController.State.COVERED, state.state());

        state.beginRevealBeforeFreshFrame();
        assertTrue("HOME START must reveal the cached layer while Recents is still semantically shown",
                state.isLayerVisible());
        assertTrue(state.consumeFadeReveal());
        assertEquals("presentation override must not invent another scene generation",
                homeGeneration, state.generation());
    }

    @Test
    public void recentsSemanticStateAndCaptureSettleDoNotOwnHomeRevealTiming() {
        assertTrue("HOME START must reveal cached glass before Recents hide and during capture settle",
                LauncherGlassSceneController.shouldBeginHomeReturnReveal(
                        true, false, true, false, true));
        assertFalse("without HOME authority no cached reveal may start",
                LauncherGlassSceneController.shouldBeginHomeReturnReveal(
                        false, false, true, false, true));
        assertFalse("unlock capture remains a conflicting presentation boundary",
                LauncherGlassSceneController.shouldBeginHomeReturnReveal(
                        true, true, true, false, true));
        assertFalse("folder coverage remains a hard presentation gate",
                LauncherGlassSceneController.shouldBeginHomeReturnReveal(
                        true, false, true, true, true));
    }
}
