package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SystemUiHomeTransitionTimingPolicyTest {
    @Test
    public void sourceLatencyIsClampedToFadeWindow() {
        assertEquals(0L, SystemUiHomeTransitionTimingPolicy.elapsedMs(1000L, 900L, 450L));
        assertEquals(35L, SystemUiHomeTransitionTimingPolicy.elapsedMs(1000L, 1035L, 450L));
        assertEquals(450L, SystemUiHomeTransitionTimingPolicy.elapsedMs(1000L, 1700L, 450L));
    }

    @Test
    public void remainingDurationKeepsSystemUiAsTimingAnchor() {
        assertEquals(450L, SystemUiHomeTransitionTimingPolicy.remainingMs(450L, 0L));
        assertEquals(415L, SystemUiHomeTransitionTimingPolicy.remainingMs(450L, 35L));
        assertEquals(0L, SystemUiHomeTransitionTimingPolicy.remainingMs(450L, 450L));
    }
}
