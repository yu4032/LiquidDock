package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;

/** Terminal teardown must forget carrier identity even when vendor objects are later reused. */
public class SecurityCenterMaterialEpochResetTest {
    @Test
    public void terminalResetAllowsSameCarrierObjectsToFormFreshEpochs() throws Exception {
        SecurityCenterMaterialEpochState state = new SecurityCenterMaterialEpochState();
        Object turbo = new Object();
        Object dock = new Object();
        Object toolbox = new Object();
        Object apps = new Object();

        assertTrue(state.bindAssistant(turbo, dock, toolbox, 1));
        assertTrue(state.attachAllApps(turbo, apps));
        assertEquals(2L, state.generation());

        Method reset;
        try {
            reset = SecurityCenterMaterialEpochState.class.getDeclaredMethod("reset");
        } catch (NoSuchMethodException missing) {
            fail("terminal lifecycle requires SecurityCenterMaterialEpochState.reset()");
            return;
        }
        reset.setAccessible(true);
        assertTrue((Boolean) reset.invoke(state));
        assertEquals(3L, state.generation());

        assertTrue("the same vendor objects after terminal teardown are a fresh material epoch",
                state.bindAssistant(turbo, dock, toolbox, 1));
        assertEquals(4L, state.generation());
        assertTrue("the same All Apps object must not be suppressed by stale identity",
                state.attachAllApps(turbo, apps));
        assertEquals(5L, state.generation());

        assertTrue((Boolean) reset.invoke(state));
        long afterSecondReset = state.generation();
        assertFalse("reset must be idempotent once no carrier is current",
                (Boolean) reset.invoke(state));
        assertEquals(afterSecondReset, state.generation());
    }
}
