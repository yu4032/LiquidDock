package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SecurityCenterPresentationBarrierTest {
    @Test public void revealRequiresEveryCurrentOutputAck() {
        SecurityCenterPresentationBarrier barrier = new SecurityCenterPresentationBarrier();
        Object dock = new Object();
        Object apps = new Object();
        barrier.begin(7L, new Object[]{dock, apps});

        assertFalse(barrier.acknowledge(7L, dock));
        assertFalse(barrier.acknowledge(7L, dock));
        assertTrue(barrier.acknowledge(7L, apps));
    }

    @Test public void supersededSerialCannotReveal() {
        SecurityCenterPresentationBarrier barrier = new SecurityCenterPresentationBarrier();
        Object dock = new Object();
        barrier.begin(3L, new Object[]{dock});
        barrier.begin(4L, new Object[]{dock});

        assertFalse(barrier.acknowledge(3L, dock));
        assertTrue(barrier.acknowledge(4L, dock));
    }
}
