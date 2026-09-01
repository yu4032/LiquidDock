package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression coverage for SystemUI keyguard transitions that finish directly in GONE. */
public class SystemUiKeyguardGonePolicyTest {
    @Test public void bouncerFinishedToGoneReleasesUnlockBarrier() {
        assertTrue(SystemUiKeyguardGonePolicy.shouldPublishFinished(
                "PRIMARY_BOUNCER", "GONE", "FINISHED"));
        assertTrue(SystemUiKeyguardGonePolicy.shouldPublishFinished(
                "ALTERNATE_BOUNCER", "GONE", "FINISHED"));
    }

    @Test public void asleepStatesFinishedToGoneReleaseUnlockBarrier() {
        assertTrue(SystemUiKeyguardGonePolicy.shouldPublishFinished(
                "AOD", "GONE", "FINISHED"));
        assertTrue(SystemUiKeyguardGonePolicy.shouldPublishFinished(
                "DOZING", "GONE", "FINISHED"));
    }

    @Test public void lockscreenAndOccludedFinishedToGoneRemainValidReleaseBoundaries() {
        assertTrue(SystemUiKeyguardGonePolicy.shouldPublishFinished(
                "LOCKSCREEN", "GONE", "FINISHED"));
        assertTrue(SystemUiKeyguardGonePolicy.shouldPublishFinished(
                "OCCLUDED", "GONE", "FINISHED"));
    }

    @Test public void unfinishedOrNonGoneTransitionsDoNotRelease() {
        assertFalse(SystemUiKeyguardGonePolicy.shouldPublishFinished(
                "PRIMARY_BOUNCER", "GONE", "RUNNING"));
        assertFalse(SystemUiKeyguardGonePolicy.shouldPublishFinished(
                "LOCKSCREEN", "PRIMARY_BOUNCER", "FINISHED"));
        assertFalse(SystemUiKeyguardGonePolicy.shouldPublishFinished(
                "GONE", "GONE", "FINISHED"));
    }

    @Test public void anyRealTransitionTowardGoneResetsDuplicateGuard() {
        assertTrue(SystemUiKeyguardGonePolicy.isGoneTransitionAttempt(
                "PRIMARY_BOUNCER", "GONE"));
        assertTrue(SystemUiKeyguardGonePolicy.isGoneTransitionAttempt(
                "DOZING", "GONE"));
        assertFalse(SystemUiKeyguardGonePolicy.isGoneTransitionAttempt(
                "GONE", "GONE"));
        assertFalse(SystemUiKeyguardGonePolicy.isGoneTransitionAttempt(
                "LOCKSCREEN", "PRIMARY_BOUNCER"));
    }
}
