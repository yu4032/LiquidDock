package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HandleMenuEarlyDetachPolicyTest {
    @Test
    public void openingFadeNeverTriggersDetach() {
        HandleMenuEarlyDetachPolicy policy = new HandleMenuEarlyDetachPolicy();
        assertFalse(policy.shouldBeginDetach(0f, false));
        assertFalse(policy.shouldBeginDetach(0.5f, true));
        assertFalse(policy.shouldBeginDetach(0.98f, true));
        assertFalse(policy.shouldBeginDetach(1f, true));
    }

    @Test
    public void closingFadeAfterOpenTriggersExactlyOnce() {
        HandleMenuEarlyDetachPolicy policy = new HandleMenuEarlyDetachPolicy();
        assertFalse(policy.shouldBeginDetach(1f, true));
        assertFalse(policy.shouldBeginDetach(0.95f, true));
        assertTrue(policy.shouldBeginDetach(0.85f, true));
        assertFalse(policy.shouldBeginDetach(0.3f, true));
        assertFalse(policy.shouldBeginDetach(1f, true));
    }

    @Test
    public void requiresCompletedOpenAndPresentedGlass() {
        HandleMenuEarlyDetachPolicy policy = new HandleMenuEarlyDetachPolicy();
        assertFalse(policy.shouldBeginDetach(0.7f, true));
        assertFalse(policy.shouldBeginDetach(1f, false));
        assertFalse(policy.shouldBeginDetach(0.7f, false));
        assertTrue(policy.shouldBeginDetach(0.7f, true));
    }
}
