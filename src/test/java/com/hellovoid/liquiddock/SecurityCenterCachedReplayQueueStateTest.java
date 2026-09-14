package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Regression for device-observed SC crashes during continuous glass morph open/close. */
public class SecurityCenterCachedReplayQueueStateTest {
    @Test
    public void continuousGeometryCoalescesToOneTailRepostInsteadOfInlineDrainLoop() {
        SecurityCenterCachedReplayQueueState state = new SecurityCenterCachedReplayQueueState();

        assertTrue("first replay request posts one render-thread task", state.request());
        assertFalse("geometry arriving while the task is running must only mark it dirty",
                state.request());
        assertFalse(state.request());

        assertTrue("completion schedules exactly one tail task so detach/resize work can run first",
                state.complete());
        assertFalse("the repost is already owned; another geometry update only marks it dirty",
                state.request());

        assertTrue("continuous animation may request another tail turn, never an inline while loop",
                state.complete());
        assertFalse("when no geometry arrived during the last turn, replay stops",
                state.complete());
    }

    @Test
    public void failedPostReleasesOwnership() {
        SecurityCenterCachedReplayQueueState state = new SecurityCenterCachedReplayQueueState();
        assertTrue(state.request());
        state.cancelQueued();
        assertTrue("a failed render-thread post must not leave replay permanently armed",
                state.request());
    }
}