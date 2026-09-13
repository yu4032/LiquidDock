package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Typed lifecycle/freshness contract consumed by the production root PassBlur backend. */
public class RootPassBlurBackendStateTest {
    @Test
    public void bindSuccessNeverCountsAsFreshContent() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestFresh(7L));
        state.onBindSucceeded();

        assertFalse(state.hasFreshFrame(7L));
        assertTrue(state.onFreshFrame(7L));
        assertTrue(state.hasFreshFrame(7L));
    }

    @Test
    public void rebindInvalidatesFreshnessUntilRealFrameForCurrentGeneration() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestFresh(10L));
        assertTrue(state.onFreshFrame(10L));
        assertTrue(state.hasFreshFrame(10L));

        ZeroCopyProducerRecoveryState.Decision rebind = state.requestRebind();
        assertTrue(rebind.accepted);
        assertTrue(rebind.recreateProducer);
        assertFalse(state.hasFreshFrame(10L));

        ZeroCopyProducerRecoveryState.Decision recreated = state.onProducerRecreated();
        assertTrue(recreated.accepted);
        assertTrue(recreated.requestBind);
        state.onBindSucceeded();
        assertFalse(state.hasFreshFrame(10L));

        assertTrue(state.onFreshFrame(10L));
        assertTrue(state.hasFreshFrame(10L));
    }

    @Test
    public void staleGenerationCannotAuthorizeFreshOutput() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestFresh(20L));
        assertFalse(state.onFreshFrame(19L));
        assertFalse(state.hasFreshFrame(20L));
        assertTrue(state.onFreshFrame(20L));
        assertTrue(state.hasFreshFrame(20L));

        assertTrue(state.requestFresh(21L));
        assertFalse(state.hasFreshFrame(20L));
        assertFalse(state.onFreshFrame(20L));
        assertTrue(state.onFreshFrame(21L));
        assertTrue(state.hasFreshFrame(21L));
    }

    @Test
    public void qualityChangeInvalidatesFreshnessWithoutRecreatingNativeEndpoint() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestFresh(25L));
        assertTrue(state.onFreshFrame(25L));

        ZeroCopyProducerRecoveryState.Decision quality = state.onQualityChanged();

        assertTrue(quality.accepted);
        assertTrue(quality.clearFrameAvailable);
        assertFalse(quality.recreateProducer);
        assertFalse(quality.requestBind);
        assertFalse(state.isRebindPending());
        assertFalse(state.hasFreshFrame(25L));
    }

    @Test
    public void terminalFailureAndShutdownFailClosed() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestFresh(30L));
        state.onTerminalFailure();
        assertFalse(state.hasFreshFrame(30L));
        assertTrue(state.isActivationExhausted());

        state.onShutdown();
        assertTrue(state.isShutdown());
        assertFalse(state.requestFresh(31L));
        assertFalse(state.onFreshFrame(31L));
    }
}
