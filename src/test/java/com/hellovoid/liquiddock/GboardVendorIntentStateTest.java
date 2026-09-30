package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GboardVendorIntentStateTest {
    @Test public void releaseRestoresInitialVendorValueWithoutLaterWrites() {
        GboardVendorIntentState<String> state = new GboardVendorIntentState<>();
        state.claim("initial");

        GboardVendorIntentState.RestoreDecision<String> release = state.release();

        assertTrue(release.restore);
        assertEquals("initial", release.value);
        assertFalse(state.isClaimed());
    }

    @Test public void claimedStateRecordsLatestVendorIntentAndSuppressesWrite() {
        GboardVendorIntentState<String> state = new GboardVendorIntentState<>();
        state.claim("initial");

        assertTrue(state.recordVendorWrite("theme-a"));
        assertTrue(state.recordVendorWrite("theme-b"));

        GboardVendorIntentState.RestoreDecision<String> release = state.release();
        assertTrue(release.restore);
        assertEquals("theme-b", release.value);
    }

    @Test public void unclaimedVendorWritePassesThroughAndReleaseDoesNothing() {
        GboardVendorIntentState<String> state = new GboardVendorIntentState<>();

        assertFalse(state.recordVendorWrite("vendor"));
        GboardVendorIntentState.RestoreDecision<String> release = state.release();

        assertFalse(release.restore);
        assertNull(release.value);
    }

    @Test public void repeatedClaimDoesNotReplaceOriginalVendorIntent() {
        GboardVendorIntentState<String> state = new GboardVendorIntentState<>();
        state.claim("initial");
        state.claim("liquiddock-owned");

        GboardVendorIntentState.RestoreDecision<String> release = state.release();
        assertEquals("initial", release.value);
    }

    @Test public void nullVendorBackgroundIsAValidIntent() {
        GboardVendorIntentState<String> state = new GboardVendorIntentState<>();
        state.claim("initial");
        assertTrue(state.recordVendorWrite(null));

        GboardVendorIntentState.RestoreDecision<String> release = state.release();
        assertTrue(release.restore);
        assertNull(release.value);
    }
}
