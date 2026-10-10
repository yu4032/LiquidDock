package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PassBlurSourceStallRecoveryPolicyTest {
    @Test public void onlyStableBoundStarvedProducerIsEligible() {
        assertTrue(PassBlurSourceStallRecoveryPolicy.shouldRollover(
                true, false, 145L, 145L, 3L, 3L, 12000L, 0L));
        assertFalse(PassBlurSourceStallRecoveryPolicy.shouldRollover(
                true, false, 146L, 145L, 3L, 3L, 12000L, 0L));
        // A formerly fresh generation can still be stalled; only actual new arrivals count.
        assertTrue(PassBlurSourceStallRecoveryPolicy.shouldRollover(
                true, false, 145L, 145L, 3L, 3L, 12000L, 0L));
        assertFalse(PassBlurSourceStallRecoveryPolicy.shouldRollover(
                true, true, 145L, 145L, 3L, 3L, 12000L, 0L));
        assertFalse(PassBlurSourceStallRecoveryPolicy.shouldRollover(
                false, false, 145L, 145L, 3L, 3L, 12000L, 0L));
    }

    @Test public void rootEpochAndCooldownBlockRepeatedRollovers() {
        assertFalse(PassBlurSourceStallRecoveryPolicy.shouldRollover(
                true, false, 145L, 145L, 4L, 3L, 12000L, 0L));
        assertFalse(PassBlurSourceStallRecoveryPolicy.shouldRollover(
                true, false, 145L, 145L, 3L, 3L, 12000L, 20000L));
        assertTrue(PassBlurSourceStallRecoveryPolicy.shouldRollover(
                true, false, 145L, 145L, 3L, 3L, 20000L, 20000L));
    }
}
