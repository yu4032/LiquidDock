package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Regression coverage for Security Center source/presentation liveness and handoff safety. */
public class SecurityCenterFramePipelineStateTest {
    @Test
    public void sameGenerationPredrawsCoalesceBehindInitialPhysicalHandoffOnly() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();

        SecurityCenterFramePipelineState.Offer first = state.offer(4L, 39L);
        assertTrue(first.requestSource);
        assertFalse(first.cancelPresentation);

        SecurityCenterFramePipelineState.Offer beforeSource = state.offer(7L, 39L);
        assertFalse("new geometry cannot invalidate an already requested source frame",
                beforeSource.requestSource);

        SecurityCenterFramePipelineState.Submission submitted = state.onFreshSource(39L);
        assertTrue(submitted.accepted);
        assertTrue(submitted.awaitPresentationAck);
        assertEquals("the first source frame must render only the latest pending geometry",
                7L, submitted.serial);

        SecurityCenterFramePipelineState.Offer whileHandoffPending = state.offer(9L, 39L);
        assertFalse("pre-draw while the initial frame awaits TextureView ACK only replaces pending",
                whileHandoffPending.requestSource);
        assertFalse(whileHandoffPending.cancelPresentation);

        SecurityCenterFramePipelineState.Presentation presented = state.onPresented(7L, 39L);
        assertTrue("a physically presented frame from the current generation may reveal custom glass",
                presented.acceptedCurrentGeneration);
        assertTrue("after initial ACK the source loop continues for the latest geometry",
                presented.requestSource);

        SecurityCenterFramePipelineState.Submission next = state.onFreshSource(39L);
        assertTrue(next.accepted);
        assertFalse("steady-state frames must not depend on TextureView ACK for liveness",
                next.awaitPresentationAck);
        assertEquals(9L, next.serial);
        assertEquals(39L, state.onSteadySubmitted(
                next.serial, next.generation, next.revision));
    }

    @Test
    public void currentGenerationContinuouslyRequestsSourceAfterSteadyEglSubmissions() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();

        assertTrue(state.offer(1L, 13L).requestSource);
        SecurityCenterFramePipelineState.Submission handoff = state.onFreshSource(13L);
        assertTrue(handoff.accepted);
        assertTrue(handoff.awaitPresentationAck);

        SecurityCenterFramePipelineState.Presentation firstPresented =
                state.onPresented(1L, 13L);
        assertTrue(firstPresented.acceptedCurrentGeneration);
        assertTrue(firstPresented.requestSource);
        assertEquals(13L, firstPresented.nextGeneration);

        SecurityCenterFramePipelineState.Submission second = state.onFreshSource(13L);
        assertTrue(second.accepted);
        assertFalse(second.awaitPresentationAck);
        assertEquals(13L, state.onSteadySubmitted(
                second.serial, second.generation, second.revision));

        SecurityCenterFramePipelineState.Submission third = state.onFreshSource(13L);
        assertTrue(third.accepted);
        assertFalse(third.awaitPresentationAck);
        assertEquals("RootPassBlurBackend owns FPS gating; steady presentation must keep requesting source",
                13L, state.onSteadySubmitted(third.serial, third.generation, third.revision));
    }

    @Test
    public void geometryCanReplayCachedBackdropWhileNextFreshSourceIsStillPending() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();

        assertTrue(state.offer(1L, 21L).requestSource);
        assertTrue(state.onFreshSource(21L).accepted);
        SecurityCenterFramePipelineState.Presentation first = state.onPresented(1L, 21L);
        assertTrue(first.requestSource);

        SecurityCenterFramePipelineState.Offer geometry = state.offer(2L, 21L);
        assertFalse("the existing fresh request stays outstanding", geometry.requestSource);

        SecurityCenterFramePipelineState.Submission cached = state.onCachedSource(21L);
        assertTrue("geometry must not wait for another PassBlur producer buffer once this generation "
                        + "already has a valid normalized backdrop",
                cached.accepted);
        assertFalse(cached.awaitPresentationAck);
        assertEquals(2L, cached.serial);
        assertEquals(21L, cached.generation);
    }

    @Test
    public void freshBackdropDoesNotWaitForSteadyTextureAck() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();

        assertTrue(state.offer(1L, 22L).requestSource);
        assertTrue(state.onFreshSource(22L).accepted);
        assertTrue(state.onPresented(1L, 22L).requestSource);

        state.offer(2L, 22L);
        SecurityCenterFramePipelineState.Submission cached = state.onCachedSource(22L);
        assertTrue(cached.accepted);
        assertFalse(cached.awaitPresentationAck);
        assertEquals(-1L, state.onSteadySubmitted(
                cached.serial, cached.generation, cached.revision));

        SecurityCenterFramePipelineState.Submission fresh = state.onFreshSource(22L);
        assertTrue("fresh backdrop must stay live even if no steady TextureView update callback occurs",
                fresh.accepted);
        assertTrue(fresh.backdropUpdated);
        assertFalse(fresh.awaitPresentationAck);
        assertEquals(2L, fresh.serial);
        assertEquals(22L, state.onSteadySubmitted(
                fresh.serial, fresh.generation, fresh.revision));
    }

    @Test
    public void cachedBackdropNeverCrossesPresentationGeneration() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();

        assertTrue(state.offer(1L, 30L).requestSource);
        assertTrue(state.onFreshSource(30L).accepted);
        state.onPresented(1L, 30L);

        SecurityCenterFramePipelineState.Offer nextGeneration = state.offer(2L, 31L);
        assertFalse("an outstanding old-generation source request is retargeted by generation authority",
                nextGeneration.cancelPresentation);
        assertTrue(nextGeneration.requestSource);
        assertFalse("a cached normalized backdrop belongs only to the generation that made it fresh",
                state.onCachedSource(31L).accepted);
        SecurityCenterFramePipelineState.Submission current = state.onFreshSource(31L);
        assertTrue(current.accepted);
        assertTrue("the first frame of a new generation must prove physical presentation",
                current.awaitPresentationAck);
    }

    @Test
    public void newerGenerationWaitsForUnconfirmedSubmittedPresentationAckBeforeArmingReplacement() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();
        assertTrue(state.offer(1L, 5L).requestSource);
        assertEquals(1L, state.onFreshSource(5L).serial);

        SecurityCenterFramePipelineState.Offer newer = state.offer(2L, 6L);
        assertFalse("an unconfirmed frame already submitted to TextureView cannot be logically cancelled",
                newer.cancelPresentation);
        assertFalse("replacement generation waits until the old physical update is consumed",
                newer.requestSource);

        SecurityCenterFramePipelineState.Presentation stale = state.onPresented(1L, 5L);
        assertFalse("a physically presented frame from an obsolete generation cannot reveal custom glass",
                stale.acceptedCurrentGeneration);
        assertTrue("after consuming stale handoff, request the latest generation",
                stale.requestSource);
        assertEquals(6L, stale.nextGeneration);

        SecurityCenterFramePipelineState.Submission current = state.onFreshSource(6L);
        assertTrue(current.accepted);
        assertTrue(current.awaitPresentationAck);
        assertEquals(2L, current.serial);
    }

    @Test
    public void confirmedGenerationNeverFreezesBehindMissingSteadyStateTextureAck() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();

        assertTrue(state.offer(1L, 44L).requestSource);
        SecurityCenterFramePipelineState.Submission handoff = state.onFreshSource(44L);
        assertTrue(handoff.accepted);
        assertTrue("the first frame of a generation still needs physical presentation proof",
                handoff.awaitPresentationAck);
        assertTrue(state.onPresented(1L, 44L).acceptedCurrentGeneration);

        state.offer(2L, 44L);
        SecurityCenterFramePipelineState.Submission middle = state.onCachedSource(44L);
        assertTrue(middle.accepted);
        assertFalse("after handoff, geometry replay must not make TextureView ACK a liveness gate",
                middle.awaitPresentationAck);

        // Simulate the device failure: serial 2 never receives onSurfaceTextureUpdated().
        state.offer(3L, 44L);
        SecurityCenterFramePipelineState.Submission latest = state.onCachedSource(44L);
        assertTrue("latest same-generation geometry must supersede an unacknowledged steady frame",
                latest.accepted);
        assertEquals(3L, latest.serial);
        assertFalse(latest.awaitPresentationAck);
    }

    @Test
    public void outputReplacementRearmsPhysicalAckWithinConfirmedGeneration() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();

        assertTrue(state.offer(1L, 50L).requestSource);
        assertTrue(state.onFreshSource(50L).awaitPresentationAck);
        assertTrue(state.onPresented(1L, 50L).acceptedCurrentGeneration);

        state.invalidatePresentationConfirmation();
        state.offer(2L, 50L);
        SecurityCenterFramePipelineState.Submission replacement = state.onCachedSource(50L);
        assertTrue(replacement.accepted);
        assertTrue("a new TextureView/EGL output must prove one physical frame before steady replay",
                replacement.awaitPresentationAck);
    }
}
