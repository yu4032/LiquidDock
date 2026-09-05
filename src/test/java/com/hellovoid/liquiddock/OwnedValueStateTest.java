package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OwnedValueStateTest {
    @Test public void repeatedClaimNeverOverwritesFirstOriginalValue() {
        OwnedValueState<Integer> state = new OwnedValueState<>();

        OwnedValueState.ClaimDecision<Integer> first = state.claim(180);
        OwnedValueState.ClaimDecision<Integer> repeated = state.claim(0);

        assertTrue(first.newClaim);
        assertEquals(Integer.valueOf(180), first.originalValue);
        assertFalse(repeated.newClaim);
        assertEquals(Integer.valueOf(180), repeated.originalValue);
        assertTrue(state.isClaimed());
    }

    @Test public void releaseRestoresOnlyWhileOwnerValueIsStillApplied() {
        OwnedValueState<String> state = new OwnedValueState<>();
        state.claim("vendor-background");

        OwnedValueState.ReleaseDecision<String> release = state.release(true);

        assertTrue(release.restoreOriginal);
        assertEquals("vendor-background", release.originalValue);
        assertFalse(state.isClaimed());
    }

    @Test public void nullOriginalValueIsStillARealClaim() {
        OwnedValueState<String> state = new OwnedValueState<>();

        OwnedValueState.ClaimDecision<String> claim = state.claim(null);
        OwnedValueState.ReleaseDecision<String> release = state.release(true);

        assertTrue(claim.newClaim);
        assertNull(claim.originalValue);
        assertTrue(release.restoreOriginal);
        assertNull(release.originalValue);
        assertFalse(state.isClaimed());
    }

    @Test public void releaseDoesNotOverwriteValueChangedByAnotherOwner() {
        OwnedValueState<String> state = new OwnedValueState<>();
        state.claim("vendor-background");

        OwnedValueState.ReleaseDecision<String> release = state.release(false);

        assertFalse(release.restoreOriginal);
        assertEquals("vendor-background", release.originalValue);
        assertFalse(state.isClaimed());
        assertFalse(state.release(true).restoreOriginal);
    }
}
