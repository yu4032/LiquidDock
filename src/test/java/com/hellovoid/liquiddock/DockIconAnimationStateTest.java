package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DockIconAnimationStateTest {
    @Test public void zeroDurationRevealCompletesImmediately() {
        DockIconAnimationState state = new DockIconAnimationState(0L);
        Object icon = new Object();
        state.begin(icon);
        state.end(icon, 100L);
        assertEquals(1f, state.opacity(icon, 100L), 0f);
    }

    @Test
    public void launchAnimationHidesGlassUntilFadeBegins() {
        DockIconAnimationState state = new DockIconAnimationState(180L);
        Object icon = new Object();
        state.begin(icon);
        assertEquals(0f, state.opacity(icon, 5_000L), 0f);
    }

    @Test
    public void animationEndRestoresGlassWithDeceleratingFade() {
        DockIconAnimationState state = new DockIconAnimationState(180L);
        Object icon = new Object();
        state.begin(icon);
        state.end(icon, 1_000L);
        assertEquals(0f, state.opacity(icon, 1_000L), 0f);
        assertEquals(0.75f, state.opacity(icon, 1_090L), 0.0001f);
        assertEquals(1f, state.opacity(icon, 1_180L), 0f);
        assertEquals(1f, state.opacity(icon, 9_000L), 0f);
    }

    @Test
    public void repeatedAnimationDuringFadeHidesGlassAgain() {
        DockIconAnimationState state = new DockIconAnimationState(180L);
        Object icon = new Object();
        state.begin(icon);
        state.end(icon, 1_000L);
        assertEquals(0.75f, state.opacity(icon, 1_090L), 0.0001f);
        state.begin(icon);
        assertEquals(0f, state.opacity(icon, 1_100L), 0f);
    }

    @Test
    public void unmatchedEndDoesNotFadeAStableIcon() {
        DockIconAnimationState state = new DockIconAnimationState(180L);
        Object icon = new Object();
        state.end(icon, 1_000L);
        assertEquals(1f, state.opacity(icon, 1_000L), 0f);
    }

    @Test
    public void onlyTheRestorePhaseRequestsAnimationFrames() {
        DockIconAnimationState state = new DockIconAnimationState(180L);
        Object icon = new Object();
        state.begin(icon);
        assertFalse(state.isFading(icon));
        state.end(icon, 1_000L);
        assertTrue(state.isFading(icon));
        state.opacity(icon, 1_180L);
        assertFalse(state.isFading(icon));
    }

    @Test
    public void curveTailStartsRestoreAtNinetyPercentProgress() {
        DockIconAnimationState state = new DockIconAnimationState(180L);
        Object icon = new Object();
        state.observeProxyFrame(icon, 0.89f, 1_000L);
        assertEquals(0f, state.opacity(icon, 1_050L), 0f);
        state.observeProxyFrame(icon, 0.90f, 1_100L);
        assertTrue(state.isFading(icon));
        assertEquals(0.75f, state.opacity(icon, 1_190L), 0.0001f);
    }

    @Test
    public void proxyFrameReportsOnlyRealAnimationStateChanges() {
        DockIconAnimationState state = new DockIconAnimationState(180L);
        Object icon = new Object();
        assertFalse(state.observeProxyFrame(icon, 0.20f, 1_000L));
        assertFalse(state.observeProxyFrame(icon, 0.89f, 1_050L));
        assertTrue(state.observeProxyFrame(icon, 0.90f, 1_100L));
        assertFalse(state.observeProxyFrame(icon, 0.95f, 1_120L));
    }

    @Test
    public void oneAnimationSampleCarriesOpacityAndFadeState() {
        DockIconAnimationState state = new DockIconAnimationState(180L);
        Object icon = new Object();
        state.begin(icon);
        state.end(icon, 1_000L);

        DockIconAnimationState.Sample sample = state.sample(icon, 1_090L);
        assertNotNull(sample);
        assertEquals(0.75f, sample.opacity, 0.0001f);
        assertTrue(sample.fading);

        sample = state.sample(icon, 1_180L);
        assertNotNull(sample);
        assertEquals(1f, sample.opacity, 0f);
        assertFalse(sample.fading);
    }

    @Test
    public void visibleCallbackDoesNotRestartAnEarlyFade() {
        DockIconAnimationState state = new DockIconAnimationState(180L);
        Object icon = new Object();
        state.observeProxyFrame(icon, 0.89f, 1_000L);
        state.observeProxyFrame(icon, 0.90f, 1_020L);
        assertEquals(0.75f, state.opacity(icon, 1_110L), 0.0001f);
        state.end(icon, 1_110L);
        assertEquals(0.75f, state.opacity(icon, 1_110L), 0.0001f);
    }

    @Test
    public void completedEarlyFadeStaysVisibleUntilAnimationEnd() {
        DockIconAnimationState state = new DockIconAnimationState(180L);
        Object icon = new Object();
        state.observeProxyFrame(icon, 0.89f, 1_000L);
        state.observeProxyFrame(icon, 0.90f, 1_020L);
        assertEquals(1f, state.opacity(icon, 1_200L), 0f);
        state.observeProxyFrame(icon, 0.95f, 1_210L);
        assertEquals(1f, state.opacity(icon, 1_210L), 0f);
        state.end(icon, 1_220L);
        assertEquals(1f, state.opacity(icon, 1_220L), 0f);
    }

    @Test
    public void progressReversalBeforeTailDoesNotStartRestore() {
        DockIconAnimationState state = new DockIconAnimationState(180L);
        Object icon = new Object();
        state.observeProxyFrame(icon, 0.6f, 1_000L);
        state.observeProxyFrame(icon, 0.4f, 1_020L);
        assertFalse(state.isFading(icon));
        assertEquals(0f, state.opacity(icon, 1_020L), 0f);
    }
}
