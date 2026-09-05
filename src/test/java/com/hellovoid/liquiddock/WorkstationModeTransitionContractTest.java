package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;

/** Regression contracts for Workstation mode authority and edge-triggered transitions. */
public class WorkstationModeTransitionContractTest {
    private static Class<?> requirePolicyClass() {
        try {
            return Class.forName("com.hellovoid.liquiddock.WorkstationModeTransitionPolicy");
        } catch (ClassNotFoundException missing) {
            fail("missing runtime WorkstationModeTransitionPolicy");
            throw new AssertionError(missing);
        }
    }

    private static Method requireMethod(String name, Class<?>... parameterTypes) throws Exception {
        try {
            Method method = requirePolicyClass().getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException missing) {
            fail("missing Workstation transition contract method: " + name);
            throw missing;
        }
    }

    @Test public void unknownProbeDoesNotBecomeFalse() throws Exception {
        Method resolve = requireMethod("resolveProbe",
                boolean.class, Object.class, boolean.class, Object.class);
        Object value = resolve.invoke(null, false, null, false, null);
        assertNull(value);
    }

    @Test public void fallbackProbeCanResolvePrimaryUnknown() throws Exception {
        Method resolve = requireMethod("resolveProbe",
                boolean.class, Object.class, boolean.class, Object.class);
        assertEquals(Boolean.TRUE, resolve.invoke(null, true, null, true, Boolean.TRUE));
        assertEquals(Boolean.FALSE, resolve.invoke(null, false, null, true, Boolean.FALSE));
    }

    @Test public void onlyRealModeEdgesOwnLayoutTransaction() throws Exception {
        Method shouldApply = requireMethod("shouldTransition", boolean.class, Boolean.class);
        assertFalse((Boolean) shouldApply.invoke(null, false, null));
        assertFalse((Boolean) shouldApply.invoke(null, false, Boolean.FALSE));
        assertFalse((Boolean) shouldApply.invoke(null, true, Boolean.TRUE));
        assertTrue((Boolean) shouldApply.invoke(null, false, Boolean.TRUE));
        assertTrue((Boolean) shouldApply.invoke(null, true, Boolean.FALSE));
    }
}
