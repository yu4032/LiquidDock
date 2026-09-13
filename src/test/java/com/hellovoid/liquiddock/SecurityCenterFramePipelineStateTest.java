package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Regression for the device-observed submitted-serial flood before a presentation ACK. */
public class SecurityCenterFramePipelineStateTest {
    @Test
    public void sameGenerationPredrawsCoalesceBehindOneSourceAndOnePresentation() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();

        SecurityCenterFramePipelineState.Offer first = state.offer(4L, 39L);
        assertTrue(first.requestSource);
        assertFalse(first.cancelPresentation);

        SecurityCenterFramePipelineState.Offer beforeSource = state.offer(7L, 39L);
        assertFalse("new geometry cannot invalidate an already requested source frame",
                beforeSource.requestSource);

        SecurityCenterFramePipelineState.Submission submitted = state.onFreshSource(39L);
        assertTrue(submitted.accepted);
        assertEquals("the first source frame must render only the latest pending geometry",
                7L, submitted.serial);

        SecurityCenterFramePipelineState.Offer whilePresented = state.offer(9L, 39L);
        assertFalse("pre-draw while serial 7 is awaiting TextureView ACK must only replace pending",
                whilePresented.requestSource);
        assertFalse(whilePresented.cancelPresentation);

        SecurityCenterFramePipelineState.Presentation presented = state.onPresented(7L, 39L);
        assertTrue("a physically presented frame from the current generation must be allowed to "
                        + "reveal custom glass even when a newer geometry serial is pending",
                presented.acceptedCurrentGeneration);
        assertTrue("after ACK the latest pending geometry gets exactly one new source request",
                presented.requestSource);

        SecurityCenterFramePipelineState.Submission next = state.onFreshSource(39L);
        assertTrue(next.accepted);
        assertEquals(9L, next.serial);
        SecurityCenterFramePipelineState.Presentation latest = state.onPresented(9L, 39L);
        assertTrue(latest.acceptedCurrentGeneration);
    }

    @Test
    public void currentGenerationContinuouslyRequestsSourceAfterEveryPresentationAck() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();

        SecurityCenterFramePipelineState.Offer first = state.offer(1L, 13L);
        assertTrue(first.requestSource);
        SecurityCenterFramePipelineState.Submission submitted = state.onFreshSource(13L);
        assertTrue(submitted.accepted);
        assertEquals(1L, submitted.serial);

        SecurityCenterFramePipelineState.Presentation firstPresented =
                state.onPresented(1L, 13L);
        assertTrue(firstPresented.acceptedCurrentGeneration);
        assertTrue("a live Security Center material must request the next source after every ACK",
                firstPresented.requestSource);
        assertEquals(13L, firstPresented.nextGeneration);

        SecurityCenterFramePipelineState.Submission second = state.onFreshSource(13L);
        assertTrue(second.accepted);
        assertEquals(1L, second.serial);

        SecurityCenterFramePipelineState.Presentation secondPresented =
                state.onPresented(1L, 13L);
        assertTrue(secondPresented.acceptedCurrentGeneration);
        assertTrue("the source loop must remain live after the post-handoff frame",
                secondPresented.requestSource);
        assertEquals(13L, secondPresented.nextGeneration);

        SecurityCenterFramePipelineState.Submission third = state.onFreshSource(13L);
        assertTrue(third.accepted);
        SecurityCenterFramePipelineState.Presentation thirdPresented =
                state.onPresented(1L, 13L);
        assertTrue(thirdPresented.acceptedCurrentGeneration);
        assertTrue("RootPassBlurBackend owns FPS gating; pipeline must not turn live glass into a snapshot",
                thirdPresented.requestSource);
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
        assertEquals(2L, cached.serial);
        assertEquals(21L, cached.generation);
    }

    @Test
    public void freshBackdropArrivingDuringCachedPresentationIsReplayedAfterItsAck() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();

        assertTrue(state.offer(1L, 22L).requestSource);
        assertTrue(state.onFreshSource(22L).accepted);
        assertTrue(state.onPresented(1L, 22L).requestSource);

        state.offer(2L, 22L);
        assertTrue(state.onCachedSource(22L).accepted);

        SecurityCenterFramePipelineState.Submission freshWhileBusy = state.onFreshSource(22L);
        assertFalse("a new backdrop cannot steal the physical TextureView ACK", freshWhileBusy.accepted);

        state.onPresented(2L, 22L);
        SecurityCenterFramePipelineState.Submission replay = state.onCachedSource(22L);
        assertTrue("the newer normalized backdrop must remain pending after the old physical ACK",
                replay.accepted);
        assertEquals(2L, replay.serial);
    }

    @Test
    public void cachedBackdropNeverCrossesPresentationGeneration() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();

        assertTrue(state.offer(1L, 30L).requestSource);
        assertTrue(state.onFreshSource(30L).accepted);
        state.onPresented(1L, 30L);

        SecurityCenterFramePipelineState.Offer nextGeneration = state.offer(2L, 31L);
        assertTrue(nextGeneration.requestSource);
        assertFalse("a cached normalized backdrop belongs only to the generation that made it fresh",
                state.onCachedSource(31L).accepted);
        assertTrue(state.onFreshSource(31L).accepted);
    }

    @Test
    public void newerGenerationWaitsForSubmittedPresentationAckBeforeArmingReplacement() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();
        assertTrue(state.offer(1L, 5L).requestSource);
        assertEquals(1L, state.onFreshSource(5L).serial);

        SecurityCenterFramePipelineState.Offer newer = state.offer(2L, 6L);
        assertFalse("a frame already submitted to TextureView cannot be logically cancelled; "
                        + "its Surface update could otherwise acknowledge the replacement serial",
                newer.cancelPresentation);
        assertFalse("the replacement source must wait until the submitted Surface update is consumed",
                newer.requestSource);

        SecurityCenterFramePipelineState.Presentation stale = state.onPresented(1L, 5L);
        assertFalse("a physically presented frame from an obsolete generation cannot reveal custom glass",
                stale.acceptedCurrentGeneration);
        assertTrue("after consuming the stale physical presentation, request the latest generation",
                stale.requestSource);
        assertEquals(6L, stale.nextGeneration);

        SecurityCenterFramePipelineState.Submission current = state.onFreshSource(6L);
        assertTrue(current.accepted);
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
