package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Typed vendor/custom ownership contract for Security Center material handoff. */
public class SecurityCenterMaterialOwnershipStateTest {
    @Test
    public void vendorSuppressionPersistsAcrossNewerTransitionGenerationsOnceCustomOwnsMaterial() {
        SecurityCenterMaterialOwnershipState state = new SecurityCenterMaterialOwnershipState();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.VENDOR, state.owner());

        assertFalse(state.canSuppressVendor(-1L, -1L));
        assertFalse(state.canSuppressVendor(5L, 5L));

        state.onCustomClaimed();
        assertTrue(state.canSuppressVendor(5L, 5L));
        assertTrue("transition generation must not reopen vendor material",
                state.canSuppressVendor(5L, 6L));
        assertTrue(state.canSuppressVendor(5L, 9L));
        assertFalse(state.canSuppressVendor(-1L, 6L));
        assertFalse(state.canSuppressVendor(6L, 5L));
    }

    @Test
    public void customClaimAndVendorReleaseAreExplicitAndIdempotent() {
        SecurityCenterMaterialOwnershipState state = new SecurityCenterMaterialOwnershipState();
        state.onCustomClaimed();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.CUSTOM, state.owner());

        state.releaseToVendor();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.VENDOR, state.owner());
        state.releaseToVendor();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.VENDOR, state.owner());
    }

    @Test
    public void vendorFinalBackgroundGateRequiresLiveCustomOwnershipAndAValidRenderedFrame() {
        SecurityCenterMaterialOwnershipState state = new SecurityCenterMaterialOwnershipState();
        long rendered = 12L;

        assertFalse(state.canSuppressVendor(rendered, rendered));

        state.onCustomClaimed();
        assertTrue(state.canSuppressVendor(rendered, rendered));
        assertTrue(state.canSuppressVendor(rendered, rendered + 1L));

        state.releaseToVendor();
        assertFalse(state.canSuppressVendor(rendered, rendered + 1L));
    }
}
