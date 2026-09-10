package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Typed vendor/custom ownership contract for Security Center material handoff. */
public class SecurityCenterMaterialOwnershipStateTest {
    @Test
    public void vendorIsInitialOwnerAndOnlyCurrentRenderedGenerationMaySuppressIt() {
        SecurityCenterMaterialOwnershipState state = new SecurityCenterMaterialOwnershipState();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.VENDOR, state.owner());

        assertFalse(state.canSuppressVendor(-1L, -1L));
        assertFalse(state.canSuppressVendor(4L, 5L));
        assertFalse(state.canSuppressVendor(6L, 5L));
        assertTrue(state.canSuppressVendor(5L, 5L));
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
    public void vendorFinalBackgroundGateRequiresOwnerAndCurrentGenerationTogether() {
        SecurityCenterMaterialOwnershipState state = new SecurityCenterMaterialOwnershipState();
        long current = 12L;

        assertFalse(state.owner() == SecurityCenterMaterialOwnershipState.Owner.CUSTOM
                && state.canSuppressVendor(current, current));

        state.onCustomClaimed();
        assertTrue(state.owner() == SecurityCenterMaterialOwnershipState.Owner.CUSTOM
                && state.canSuppressVendor(current, current));
        assertFalse(state.owner() == SecurityCenterMaterialOwnershipState.Owner.CUSTOM
                && state.canSuppressVendor(current - 1L, current));

        state.releaseToVendor();
        assertFalse(state.owner() == SecurityCenterMaterialOwnershipState.Owner.CUSTOM
                && state.canSuppressVendor(current, current));
    }
}
