package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure producer rollover/fresh-frame recovery behavior for the zero-copy PassBlur path. */
public class ZeroCopyProducerRecoveryStateTest {
    @Test public void rebindRequestClearsFreshnessAndAcceptsOnlyOnce() {
        ZeroCopyProducerRecoveryState state = new ZeroCopyProducerRecoveryState();
        state.onFreshFrameConsumed();

        ZeroCopyProducerRecoveryState.Decision first = state.onRebindRequested();
        assertTrue(first.accepted);
        assertTrue(first.clearFrameworkBinding);
        assertTrue(first.clearFrameAvailable);
        assertTrue(first.recreateProducer);
        assertFalse(state.hasFreshFrame());
        assertTrue(state.isRebindPending());

        ZeroCopyProducerRecoveryState.Decision duplicate = state.onRebindRequested();
        assertFalse(duplicate.accepted);
        assertFalse(duplicate.recreateProducer);
    }

    @Test public void producerRecreationRequestsBindButDoesNotCreateFreshness() {
        ZeroCopyProducerRecoveryState state = new ZeroCopyProducerRecoveryState();
        state.onRebindRequested();

        ZeroCopyProducerRecoveryState.Decision recreated = state.onProducerRecreated();
        assertTrue(recreated.requestBind);
        assertTrue(state.isRebindPending());
        assertFalse(state.hasFreshFrame());
    }

    @Test public void successfulBindEndsRolloverButStillWaitsForFreshFrame() {
        ZeroCopyProducerRecoveryState state = new ZeroCopyProducerRecoveryState();
        state.onRebindRequested();
        state.onProducerRecreated();

        state.onBindSucceeded();

        assertFalse(state.isRebindPending());
        assertFalse(state.isActivationExhausted());
        assertFalse(state.hasFreshFrame());
        state.onFreshFrameConsumed();
        assertTrue(state.hasFreshFrame());
    }

    @Test public void exhaustedOrFailedRecoveryRemainsNotFreshAndRetryable() {
        ZeroCopyProducerRecoveryState state = new ZeroCopyProducerRecoveryState();
        state.onRebindRequested();
        state.onBindExhausted();

        assertFalse(state.isRebindPending());
        assertTrue(state.isActivationExhausted());
        assertFalse(state.hasFreshFrame());

        ZeroCopyProducerRecoveryState.Decision retry = state.onRebindRequested();
        assertTrue(retry.accepted);
        assertFalse(state.isActivationExhausted());

        state.onRecreateFailed();
        assertFalse(state.isRebindPending());
        assertTrue(state.isActivationExhausted());
        assertFalse(state.hasFreshFrame());
    }

    @Test public void geometryInvalidationDropsFreshFrameWithoutStartingRollover() {
        ZeroCopyProducerRecoveryState state = new ZeroCopyProducerRecoveryState();
        state.onFreshFrameConsumed();

        ZeroCopyProducerRecoveryState.Decision invalidated = state.onGeometryInvalidated();

        assertTrue(invalidated.clearFrameAvailable);
        assertFalse(invalidated.recreateProducer);
        assertFalse(state.hasFreshFrame());
        assertFalse(state.isRebindPending());
    }

    @Test public void shutdownClearsPendingRecovery() {
        ZeroCopyProducerRecoveryState state = new ZeroCopyProducerRecoveryState();
        state.onRebindRequested();

        state.onShutdown();

        assertFalse(state.isRebindPending());
        assertFalse(state.hasFreshFrame());
    }
}
