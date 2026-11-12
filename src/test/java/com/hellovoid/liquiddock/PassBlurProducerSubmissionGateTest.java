package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PassBlurProducerSubmissionGateTest {
    @Test public void oneProducerMayBeSubmittedOnlyOnce() {
        Object producer = new Object();
        assertFalse(PassBlurProducerSubmissionGate.wasSubmitted(producer));
        assertTrue(PassBlurProducerSubmissionGate.trySubmit(producer));
        assertTrue(PassBlurProducerSubmissionGate.wasSubmitted(producer));
        assertFalse(PassBlurProducerSubmissionGate.trySubmit(producer));
        assertTrue(PassBlurProducerSubmissionGate.trySubmit(new Object()));
    }

    @Test public void failedNativeSubmissionStillPreventsReuse() {
        Object producer = new Object();
        assertTrue(PassBlurProducerSubmissionGate.trySubmit(producer));
        // Native throw/partial-apply may have parceled the IGBP already.
        assertFalse(PassBlurProducerSubmissionGate.trySubmit(producer));
        assertFalse(PassBlurProducerSubmissionGate.trySubmit(null));
    }
}
