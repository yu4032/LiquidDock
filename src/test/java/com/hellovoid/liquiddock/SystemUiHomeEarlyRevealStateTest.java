package com.hellovoid.liquiddock;

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
}
