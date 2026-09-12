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
        assertTrue(presented.acceptedCurrentGeneration);
        assertTrue("after ACK the latest pending geometry gets exactly one new source request",
                presented.requestSource);

        SecurityCenterFramePipelineState.Submission next = state.onFreshSource(39L);
        assertTrue(next.accepted);
        assertEquals(9L, next.serial);
    }

    @Test
    public void firstPresentationAlwaysArmsExactlyOnePostHandoffRefresh() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();

        SecurityCenterFramePipelineState.Offer first = state.offer(1L, 13L);
        assertTrue(first.requestSource);
        SecurityCenterFramePipelineState.Submission submitted = state.onFreshSource(13L);
        assertTrue(submitted.accepted);
        assertEquals(1L, submitted.serial);

        SecurityCenterFramePipelineState.Presentation firstPresented =
                state.onPresented(1L, 13L);
        assertTrue(firstPresented.acceptedCurrentGeneration);
        assertTrue("the material handoff producer rebind must always have one accepted fresh source",
                firstPresented.requestSource);
        assertEquals(13L, firstPresented.nextGeneration);

        SecurityCenterFramePipelineState.Submission refreshed = state.onFreshSource(13L);
        assertTrue(refreshed.accepted);
        assertEquals(1L, refreshed.serial);

        SecurityCenterFramePipelineState.Presentation refreshedPresented =
                state.onPresented(1L, 13L);
        assertTrue(refreshedPresented.acceptedCurrentGeneration);
        assertFalse("the post-handoff refresh must be one-shot for the generation",
                refreshedPresented.requestSource);
    }

    @Test
    public void newerGenerationSupersedesOldPresentationButOldGenerationCannotReveal() {
        SecurityCenterFramePipelineState state = new SecurityCenterFramePipelineState();
        assertTrue(state.offer(1L, 5L).requestSource);
        assertEquals(1L, state.onFreshSource(5L).serial);

        SecurityCenterFramePipelineState.Offer newer = state.offer(2L, 6L);
        assertTrue(newer.cancelPresentation);
        assertEquals(1L, newer.cancelledSerial);
        assertTrue(newer.requestSource);

        SecurityCenterFramePipelineState.Presentation stale = state.onPresented(1L, 5L);
        assertFalse(stale.acceptedCurrentGeneration);

        SecurityCenterFramePipelineState.Submission current = state.onFreshSource(6L);
        assertTrue(current.accepted);
        assertEquals(2L, current.serial);
    }
}
