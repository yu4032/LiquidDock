package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PassBlurFirstFrameRecoveryStateTest {
    @Test
    public void missingFirstFrameRequestsBoundedProducerRebuilds() {
        PassBlurFirstFrameRecoveryState state = new PassBlurFirstFrameRecoveryState(2);

        PassBlurFirstFrameRecoveryState.Ticket first = state.arm(9L);
        assertTrue(state.onTimeout(first).rebind);

        PassBlurFirstFrameRecoveryState.Ticket second = state.arm(9L);
        assertTrue(state.onTimeout(second).rebind);

        PassBlurFirstFrameRecoveryState.Ticket exhausted = state.arm(9L);
        assertTrue(state.onTimeout(exhausted).terminalFailure);
    }

    @Test
    public void realFrameOrNewerRequestInvalidatesOldWatchdog() {
        PassBlurFirstFrameRecoveryState state = new PassBlurFirstFrameRecoveryState(2);
        PassBlurFirstFrameRecoveryState.Ticket stale = state.arm(17L);
        state.onFreshFrame(17L);
        assertFalse(state.onTimeout(stale).rebind);
        assertFalse(state.arm(17L).armed);

        PassBlurFirstFrameRecoveryState.Ticket oldGeneration = state.arm(18L);
        PassBlurFirstFrameRecoveryState.Ticket current = state.arm(19L);
        assertFalse(state.onTimeout(oldGeneration).rebind);
        assertTrue(state.onTimeout(current).rebind);
    }
}
