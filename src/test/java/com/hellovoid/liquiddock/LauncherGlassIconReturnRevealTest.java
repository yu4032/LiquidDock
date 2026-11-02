package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import org.junit.Test;

public class LauncherGlassIconReturnRevealTest {
    @Test
    public void onlyHomeReturnDefersIconGlassUntilSourceHandoff() {
        LauncherGlassIconReturnReveal state = new LauncherGlassIconReturnReveal();
        assertEquals(1f, state.alpha(), 0f);
        state.onNativeHidden();
        assertEquals(0f, state.alpha(), 0f);
        state.onProxyFrame(true);
        assertEquals(0f, state.alpha(), 0f);
        assertTrue(state.onNativeVisible());
        assertEquals(0f, state.alpha(), 0f);
        assertTrue(state.setRevealProgress(.5f));
        assertEquals(.5f, state.alpha(), 0f);
        assertTrue(state.setRevealProgress(1f));
        assertEquals(1f, state.alpha(), 0f);
    }

    @Test
    public void launchAwayAndRetargetNeverLeaveGlassWaiting() {
        LauncherGlassIconReturnReveal state = new LauncherGlassIconReturnReveal();
        state.onNativeHidden();
        state.onProxyFrame(false);
        assertEquals(1f, state.alpha(), 0f);
        assertFalse(state.onNativeVisible());
        state.onNativeHidden();
        state.onProxyFrame(true);
        state.onProxyFrame(false);
        assertFalse(state.onNativeVisible());
        assertEquals(1f, state.alpha(), 0f);
    }

    @Test
    public void duplicateVisibleAndLateProxyUpdatesCannotRestartFade() {
        LauncherGlassIconReturnReveal state = new LauncherGlassIconReturnReveal();
        state.onNativeHidden();
        state.onProxyFrame(true);
        assertTrue(state.onNativeVisible());
        state.setRevealProgress(.6f);
        assertFalse(state.onNativeVisible());
        assertFalse(state.acceptsProxyUpdates());
        state.onProxyFrame(true);
        assertEquals(.6f, state.alpha(), 0f);
        state.onNativeHidden();
        assertTrue(state.acceptsProxyUpdates());
        assertEquals(0f, state.alpha(), 0f);
    }

    @Test
    public void invalidSamplesAndDetachResetAreSafe() {
        LauncherGlassIconReturnReveal state = new LauncherGlassIconReturnReveal();
        state.onNativeHidden();
        state.onProxyFrame(true);
        state.onNativeVisible();
        assertFalse(state.setRevealProgress(Float.NaN));
        assertEquals(0f, state.alpha(), 0f);
        assertTrue(state.setRevealProgress(2f));
        assertEquals(1f, state.alpha(), 0f);
        assertTrue(state.setRevealProgress(-1f));
        assertEquals(0f, state.alpha(), 0f);
        state.reset();
        assertEquals(1f, state.alpha(), 0f);
        assertTrue(state.acceptsProxyUpdates());
        assertTrue(LauncherGlassIconReturnReveal.MAX_FADE_MS > 0);
    }
}
