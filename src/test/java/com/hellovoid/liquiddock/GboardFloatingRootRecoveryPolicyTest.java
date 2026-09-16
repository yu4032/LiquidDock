package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GboardFloatingRootRecoveryPolicyTest {
    @Test public void sameRootSizeNeedsNoRecovery() {
        assertEquals(GboardFloatingRootRecoveryPolicy.Decision.NONE,
                GboardFloatingRootRecoveryPolicy.decide(1440, 3200, 1440, 3200, true));
    }

    @Test public void liveRootResizeRequestsFreshBackdrop() {
        assertEquals(GboardFloatingRootRecoveryPolicy.Decision.REQUEST_FRESH,
                GboardFloatingRootRecoveryPolicy.decide(1440, 3200, 3200, 1440, true));
    }

    @Test public void rootResizeWhileLiveCaptureIsPausedFailsClosed() {
        assertEquals(GboardFloatingRootRecoveryPolicy.Decision.FAIL_CLOSED,
                GboardFloatingRootRecoveryPolicy.decide(1440, 3200, 3200, 1440, false));
    }
}
