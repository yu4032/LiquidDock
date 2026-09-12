package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

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

    @Test
    public void endpointRaceKeepsAcceptedRolloverPendingUntilRetryBinds() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestFresh(41L));
        assertTrue(state.requestRebind().accepted);
        assertTrue(state.onProducerRecreated().requestBind);

        assertTrue("Binding retries must remain inside the accepted producer rollover",
                state.isRebindPending());
        ZeroCopyProducerRecoveryState.Decision nestedRebind = state.requestRebind();
        assertFalse("A nested rollover request is intentionally rejected while binding is pending",
                nestedRebind.accepted);
        assertTrue("An endpoint race must not terminate the current rollover before retry succeeds",
                state.isRebindPending());

        state.onBindSucceeded();
        assertFalse(state.isRebindPending());
        assertFalse("A successful bind still requires a new producer frame before reveal",
                state.hasFreshFrame(41L));
    }

    @Test
    public void inFlightBackendRolloverExposesCompletionPiggyback() throws Exception {
        Method attach = RootPassBlurBackend.class.getDeclaredMethod(
                "attachRolloverCompletion",
                LauncherGlassSessionRegistry.RolloverCompletion.class);

        assertEquals(boolean.class, attach.getReturnType());
    }
}
