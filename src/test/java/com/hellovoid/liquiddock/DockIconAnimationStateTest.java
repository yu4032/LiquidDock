package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.*;

/** Native CLOSE_TO_HOME progress is the sole Dock icon glass reveal clock. */
public class DockIconAnimationStateTest {
    @Test public void stableIconIsVisibleAndNoClockIsRequired() {
        DockIconAnimationState state = new DockIconAnimationState();
        assertEquals(1f, state.sample(new Object()).opacity, 0f);
    }

    @Test public void nativeProgressUsesExactlyTheWorkspaceReturnCurve() {
        DockIconAnimationState state = new DockIconAnimationState();
        Object icon = new Object();
        assertTrue(state.onNativeHidden(icon));
        assertEquals(0f, state.sample(icon).opacity, 0f);
        for (float progress : new float[]{0f, .5f, .89f, .9f, .925f, .95f, .975f, 1f}) {
            state.observeHomeReturnProgress(icon, progress);
            assertEquals(LauncherGlassReturnFade.alpha(progress),
                    state.sample(icon).opacity, .00001f);
        }
    }

    @Test public void nativeProgressReversalReversesOpacityWithoutRestartingClock() {
        DockIconAnimationState state = new DockIconAnimationState();
        Object icon = new Object();
        state.onNativeHidden(icon);
        assertTrue(state.observeHomeReturnProgress(icon, .98f));
        float peak = state.sample(icon).opacity;
        assertTrue(peak > .8f);
        assertTrue(state.observeHomeReturnProgress(icon, .93f));
        assertTrue(state.sample(icon).opacity < peak);
        assertTrue(state.observeHomeReturnProgress(icon, .9f));
        assertEquals(0f, state.sample(icon).opacity, 0f);
    }

    @Test public void retargetFromHomeReturnToLaunchImmediatelyHidesPartialGlass() {
        DockIconAnimationState state = new DockIconAnimationState();
        Object icon = new Object();
        state.onNativeHidden(icon);
        state.observeHomeReturnProgress(icon, .98f);
        assertTrue(state.sample(icon).opacity > .8f);
        assertTrue(state.cancelHomeReturn(icon));
        assertEquals(0f, state.sample(icon).opacity, 0f);
        assertFalse(state.cancelHomeReturn(icon));
        state.observeHomeReturnProgress(icon, .95f);
        assertEquals(.5f, state.sample(icon).opacity, .00001f);
    }

    @Test public void repeatedNativeHideDoesNotEraseProgress() {
        DockIconAnimationState state = new DockIconAnimationState();
        Object icon = new Object();
        state.onNativeHidden(icon);
        state.observeHomeReturnProgress(icon, .95f);
        assertFalse(state.onNativeHidden(icon));
        assertEquals(.5f, state.sample(icon).opacity, .00001f);
        assertFalse(state.observeHomeReturnProgress(icon, .95f));
    }

    @Test public void visibleHandoffRestoresImmediatelyWithoutPostAnimationFade() {
        DockIconAnimationState state = new DockIconAnimationState();
        Object icon = new Object();
        state.onNativeHidden(icon);
        state.observeHomeReturnProgress(icon, .95f);
        assertTrue(state.end(icon));
        assertEquals(1f, state.sample(icon).opacity, 0f);
        assertFalse(state.end(icon));
        state.onNativeHidden(icon);
        assertEquals(0f, state.sample(icon).opacity, 0f);
    }

    @Test public void nativeCompletionIsSafeWithoutAnyProxyFrames() {
        DockIconAnimationState state = new DockIconAnimationState();
        Object icon = new Object();
        state.onNativeHidden(icon);
        assertEquals(0f, state.sample(icon).opacity, 0f);
        assertTrue(state.end(icon));
        assertEquals(1f, state.sample(icon).opacity, 0f);
        assertFalse(state.observeHomeReturnProgress(icon, Float.NaN));
    }

    @Test public void latestReturnDoesNotDamageProxyGeometry() {
        DockIconAnimationState state = new DockIconAnimationState();
        Object icon = new Object();
        float[] bounds = {10f, 20f, 40f, 50f};
        assertTrue(state.updateProxyGeometry(icon, bounds));
        assertTrue(state.onNativeHidden(icon));
        state.observeHomeReturnProgress(icon, .95f);
        DockIconAnimationState.Sample sample = state.sample(icon);
        assertTrue(sample.proxyActive);
        assertArrayEquals(bounds, sample.proxyRect, 0f);
        assertEquals(.5f, sample.opacity, .00001f);
        sample.proxyRect[0] = 900f;
        assertArrayEquals(bounds, state.sample(icon).proxyRect, 0f);
        assertTrue(state.endProxyGeometry(icon));
        assertFalse(state.sample(icon).proxyActive);
    }

    @Test public void finalNativeCommitCannotBeOverwrittenByLateProxyFrame() {
        DockIconAnimationState state = new DockIconAnimationState();
        Object icon = new Object();
        state.updateProxyGeometry(icon, new float[]{1f, 2f, 21f, 22f});
        state.onNativeHidden(icon);
        state.observeHomeReturnProgress(icon, .95f);
        assertTrue(state.end(icon));
        assertFalse(state.observeHomeReturnProgress(icon, .96f));
        assertEquals(1f, state.sample(icon).opacity, 0f);
        assertTrue(state.endProxyGeometry(icon));
    }

    @Test public void proxyWithoutRectangleOwnsGeometryButDoesNotCreateGhost() {
        DockIconAnimationState state = new DockIconAnimationState();
        Object icon = new Object();
        assertTrue(state.holdProxyHidden(icon));
        state.onNativeHidden(icon);
        DockIconAnimationState.Sample sample = state.sample(icon);
        assertTrue(sample.proxyActive);
        assertNull(sample.proxyRect);
        assertEquals(0f, sample.opacity, 0f);
        assertTrue(state.updateProxyGeometry(icon, new float[]{1f, 2f, 11f, 12f}));
        assertArrayEquals(new float[]{1f, 2f, 11f, 12f}, state.sample(icon).proxyRect, 0f);
    }

    @Test public void invalidProgressIsIgnored() {
        DockIconAnimationState state = new DockIconAnimationState();
        Object icon = new Object();
        state.onNativeHidden(icon);
        assertFalse(state.observeHomeReturnProgress(icon, Float.NaN));
        assertFalse(state.observeHomeReturnProgress(icon, Float.POSITIVE_INFINITY));
        assertEquals(0f, state.sample(icon).opacity, 0f);
    }
}
