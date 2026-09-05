package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Test;

/** Regression contracts for Dock PassBlur producer endpoint rollover. */
public class Miuix307PassBlurTextureViewProducerRolloverContractTest {
    @Test public void dockProducerTracksOneBindEpochPerEndpointGeneration() throws Exception {
        Field epoch = Miuix307PassBlurTextureView.class.getDeclaredField("producerBindEpoch");
        assertNotNull(epoch);
        assertEquals(long.class, epoch.getType());
    }

    @Test public void finishBindRequiresCapturedEndpointEpoch() {
        Method finishBind = null;
        for (Method method : Miuix307PassBlurTextureView.class.getDeclaredMethods()) {
            if ("finishBindProducer".equals(method.getName())) {
                finishBind = method;
                break;
            }
        }
        assertNotNull(finishBind);
        Class<?>[] parameters = finishBind.getParameterTypes();
        assertEquals(4, parameters.length);
        assertEquals(long.class, parameters[3]);
        assertTrue(parameters[2] == int.class);
    }
}
