package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherGlassPressStateTest {
    @Test public void pressClampsCenterAndPlansAnimationFromCurrentProgress() {
        LauncherGlassPressState state = new LauncherGlassPressState();
        state.setProgress(0.25f);

        LauncherGlassPressState.Decision decision = state.setPressed(true, 1.4f, -0.2f);

        assertTrue(decision.animate);
        assertEquals(0.25f, decision.startProgress, 0.0001f);
        assertEquals(1f, decision.targetProgress, 0.0001f);
        assertEquals(1f, state.glowCenterX(), 0.0001f);
        assertEquals(0f, state.glowCenterY(), 0.0001f);
        assertTrue(state.isPressedTarget());
    }

    @Test public void repeatedPressWithMovedCenterPublishesWithoutRestartingAnimation() {
        LauncherGlassPressState state = new LauncherGlassPressState();
        state.setPressed(true, 0.2f, 0.3f);
        state.setProgress(1f);

        LauncherGlassPressState.Decision decision = state.setPressed(true, 0.8f, 0.7f);

        assertFalse(decision.animate);
        assertTrue(decision.publishImmediately);
        assertEquals(0.8f, state.glowCenterX(), 0.0001f);
        assertEquals(0.7f, state.glowCenterY(), 0.0001f);
    }

    @Test public void immediateResetClearsTargetProgressAndGlowCenter() {
        LauncherGlassPressState state = new LauncherGlassPressState();
        state.setPressed(true, 0.8f, 0.7f);
        state.setProgress(0.6f);

        LauncherGlassPressState.Decision decision = state.reset(false);

        assertFalse(decision.animate);
        assertTrue(decision.publishImmediately);
        assertFalse(state.isPressedTarget());
        assertEquals(0f, state.progress(), 0.0001f);
        assertEquals(0.5f, state.glowCenterX(), 0.0001f);
        assertEquals(0.5f, state.glowCenterY(), 0.0001f);
    }

    @Test public void animatedResetKeepsCurrentProgressUntilAnimatorAdvances() {
        LauncherGlassPressState state = new LauncherGlassPressState();
        state.setPressed(true, 0.5f, 0.5f);
        state.setProgress(0.6f);

        LauncherGlassPressState.Decision decision = state.reset(true);

        assertTrue(decision.animate);
        assertEquals(0.6f, decision.startProgress, 0.0001f);
        assertEquals(0f, decision.targetProgress, 0.0001f);
        assertEquals(0.6f, state.progress(), 0.0001f);
        assertFalse(state.isPressedTarget());
    }
}
