package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Regression contracts for shared producer endpoint rollover after the root-backend extraction. */
public class LauncherGlassProducerRolloverContractTest {
    @Test
    public void endpointRolloverUsesSharedProductionRecoveryState() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestFresh(40L));
        assertTrue(state.onFreshFrame(40L));

        ZeroCopyProducerRecoveryState.Decision first = state.requestRebind();
        assertTrue(first.accepted);
        assertTrue(first.recreateProducer);
        assertFalse(state.hasFreshFrame(40L));

        ZeroCopyProducerRecoveryState.Decision duplicate = state.requestRebind();
        assertFalse(duplicate.accepted);

        ZeroCopyProducerRecoveryState.Decision recreated = state.onProducerRecreated();
        assertTrue(recreated.accepted);
        assertTrue(recreated.requestBind);
        state.onBindSucceeded();

        assertFalse(state.isRebindPending());
        assertFalse(state.hasFreshFrame(40L));
    }
}
