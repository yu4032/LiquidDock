package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class LauncherGlassLayerAlphaTest {
    @Test public void motionStartFadesAnAlreadyVisibleSceneFromZero() {
        LauncherGlassLayerAlpha state = new LauncherGlassLayerAlpha();
        state.setSceneAlpha(1f);
        assertTrue(state.startMotionReveal(450));
        assertEquals(0f, state.alpha(), 0f);
        state.setMotionAlpha(0.5f);
        assertEquals(0.5f, state.alpha(), 0f);
        state.setMotionAlpha(1f);
        assertEquals(1f, state.alpha(), 0f);
    }

    @Test public void motionCompletionCannotRevealCoveredScene() {
        LauncherGlassLayerAlpha state = new LauncherGlassLayerAlpha();
        state.setSceneAlpha(1f);
        state.startMotionReveal(450);
        state.setSceneAlpha(0f);
        state.setMotionAlpha(1f);
        assertEquals(0f, state.alpha(), 0f);
    }

    @Test public void repeatedVisibilityUpdatesDoNotResetMotionProgress() {
        LauncherGlassLayerAlpha state = new LauncherGlassLayerAlpha();
        state.setMotionAlpha(0.4f);
        state.setSceneAlpha(1f);
        state.setSceneAlpha(1f);
        assertEquals(0.4f, state.alpha(), 0.0001f);
        state.setSceneAlpha(0.5f);
        assertEquals(0.2f, state.alpha(), 0.0001f);
    }

    @Test public void aNewMotionRestartsButZeroDurationNeverFlashes() {
        LauncherGlassLayerAlpha state = new LauncherGlassLayerAlpha();
        state.setSceneAlpha(0.7f);
        state.setMotionAlpha(0.8f);
        assertTrue(state.startMotionReveal(450));
        assertEquals(0f, state.alpha(), 0f);
        assertFalse(state.startMotionReveal(0));
        assertEquals(0.7f, state.alpha(), 0f);
    }

    @Test public void alphaFactorsStayFiniteAndBounded() {
        LauncherGlassLayerAlpha state = new LauncherGlassLayerAlpha();
        state.setSceneAlpha(9f);
        state.setMotionAlpha(9f);
        assertEquals(1f, state.alpha(), 0f);
        state.setMotionAlpha(Float.NaN);
        assertEquals(0f, state.alpha(), 0f);
    }
}
