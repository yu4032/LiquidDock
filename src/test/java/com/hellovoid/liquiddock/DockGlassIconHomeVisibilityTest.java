package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class DockGlassIconHomeVisibilityTest {
    @Test public void nativeReturnHidesAndRevealsOnlyIconScene() {
        DockGlassIconHomeVisibility state = new DockGlassIconHomeVisibility();
        assertEquals(1f, state.alpha(), 0f);
        assertTrue(state.begin());
        assertEquals(0f, state.alpha(), 0f);
        assertFalse(state.observe(.5f));
        assertFalse(state.observe(.89f));
        assertTrue(state.observe(.95f));
        assertEquals(LauncherGlassReturnFade.alpha(.95f), state.alpha(), 0f);
        assertTrue(state.observe(1f));
        assertEquals(1f, state.alpha(), 0f);
        assertTrue(state.finish());
        assertFalse(state.finish());
    }

    @Test public void retargetAndRepeatedCyclesReleaseVisibility() {
        DockGlassIconHomeVisibility state = new DockGlassIconHomeVisibility();
        state.begin();
        state.observe(.97f);
        state.observe(.92f);
        assertEquals(LauncherGlassReturnFade.alpha(.92f), state.alpha(), 0f);
        assertTrue(state.finish());
        assertEquals(1f, state.alpha(), 0f);
        state.begin();
        assertEquals(0f, state.alpha(), 0f);
        assertTrue(state.finish()); // no update callbacks: never leave icons hidden
        assertFalse(state.observe(.95f)); // late callbacks after terminal are ignored
        assertEquals(1f, state.alpha(), 0f);
    }

    @Test public void invalidProgressDoesNotChangeScene() {
        DockGlassIconHomeVisibility state = new DockGlassIconHomeVisibility();
        state.begin();
        assertFalse(state.observe(Float.NaN));
        assertFalse(state.observe(Float.POSITIVE_INFINITY));
        assertEquals(0f, state.alpha(), 0f);
        state.finish();
    }
}
