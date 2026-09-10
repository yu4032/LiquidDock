package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Typed vendor/custom ownership contract for Security Center material handoff. */
public class SecurityCenterMaterialOwnershipStateTest {
    @Test
    public void preparingOwnershipSuppressesVendorBeforeAnyCustomFrameIsVisible() {
        SecurityCenterMaterialOwnershipState state = new SecurityCenterMaterialOwnershipState();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.VENDOR, state.owner());
        assertFalse(state.canSuppressVendor(-1L, 5L));

        state.onCustomPreparing();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.CUSTOM_PREPARING, state.owner());
        assertTrue("vendor final background must stay suppressed after physical material clear",
                state.canSuppressVendor(-1L, 5L));

        state.onCustomPresented();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.CUSTOM, state.owner());
        assertTrue(state.canSuppressVendor(5L, 5L));
        assertTrue(state.canSuppressVendor(5L, 9L));
    }

    @Test
    public void releaseFromPreparingOrVisibleCustomRestoresVendorAuthority() {
        SecurityCenterMaterialOwnershipState state = new SecurityCenterMaterialOwnershipState();
        state.onCustomPreparing();
        state.releaseToVendor();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.VENDOR, state.owner());

        state.onCustomPreparing();
        state.onCustomPresented();
        state.releaseToVendor();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.VENDOR, state.owner());
        state.releaseToVendor();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.VENDOR, state.owner());
    }

    @Test
    public void visibleCustomStillRejectsInvalidGenerationOrdering() {
        SecurityCenterMaterialOwnershipState state = new SecurityCenterMaterialOwnershipState();
        state.onCustomPreparing();
        state.onCustomPresented();

        assertFalse(state.canSuppressVendor(-1L, 6L));
        assertFalse(state.canSuppressVendor(6L, 5L));
        assertTrue(state.canSuppressVendor(5L, 6L));
    }
}
