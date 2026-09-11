package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

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
    public void endpointRaceRetriesInsideCurrentRolloverInsteadOfStartingNestedRebind()
            throws Exception {
        String backend = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/RootPassBlurBackend.java"));
        assertTrue("A ViewRoot generation race must keep the current producer and retry binding",
                backend.contains("Miuix307PassBlurBridge.unbind(next);\n"
                        + "            // The producer endpoint is still valid."));
        assertTrue("The retry must stay frame/lifecycle driven",
                backend.contains("retryBind(attempt);"));
        assertFalse("Nested rebind is rejected while the current rollover is pending",
                backend.contains("requestRebind(\"bind-endpoint-raced\")"));
    }
}
