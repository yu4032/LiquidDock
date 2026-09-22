package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class WorkstationRuntimeStateTest {
    @After
    public void reset() {
        WorkstationRuntimeState.resetForTest();
    }

    @Test
    public void productionStateOwnsActiveMode() {
        assertFalse(WorkstationRuntimeState.isActive());
        assertTrue(WorkstationRuntimeState.publish(true));
        assertTrue(WorkstationRuntimeState.isActive());
        assertFalse("publishing the same state is not a transition",
                WorkstationRuntimeState.publish(true));
        assertTrue(WorkstationRuntimeState.publish(false));
        assertFalse(WorkstationRuntimeState.isActive());
    }
}
