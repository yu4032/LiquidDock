package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Test;

/** Regression contracts for Launcher shared producer endpoint rollover. */
public class LauncherGlassProducerRolloverContractTest {
    @Test public void everyEndpointRolloverUsesOneSharedBindEpoch() throws Exception {
        Field epoch = LauncherGlassSession.class.getDeclaredField("producerBindEpoch");
        assertNotNull(epoch);

        try {
            LauncherGlassSession.class.getDeclaredField("workstationBindEpoch");
            fail("Workstation-only bind epoch leaves generic rollover callbacks stale");
        } catch (NoSuchFieldException expected) {
            // The epoch must represent producer endpoint identity, not one lifecycle caller.
        }
    }

    @Test public void retiredEndpointKeepsItsOwnCapturedEpoch() throws Exception {
        Field endpointEpoch = LauncherGlassSession.class.getDeclaredField("inputProducerBindEpoch");
        assertNotNull(endpointEpoch);
        assertEquals(long.class, endpointEpoch.getType());
    }

    @Test public void genericRolloverReportsTerminalSuccessOrFailure() throws Exception {
        Method method = LauncherGlassSession.class.getDeclaredMethod(
                "rebindProducer", LauncherGlassSessionRegistry.RolloverCompletion.class);
        assertEquals(boolean.class, method.getReturnType());
    }
}
