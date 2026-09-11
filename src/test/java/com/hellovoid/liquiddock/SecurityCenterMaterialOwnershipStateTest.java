package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Typed vendor/custom ownership contract for Security Center material handoff. */
public class SecurityCenterMaterialOwnershipStateTest {
    @Test
    public void preparingKeepsVendorUntilCurrentGenerationPresentationAck() {
        SecurityCenterMaterialOwnershipState state = new SecurityCenterMaterialOwnershipState();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.VENDOR, state.owner());

        state.onCustomPreparing();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.CUSTOM_PREPARING, state.owner());
        assertFalse("vendor must remain authoritative before presentation ACK",
                state.canSuppressVendor(-1L, 5L));
        assertFalse(state.canSuppressVendor(5L, 5L));

        state.onCustomPresented();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.CUSTOM, state.owner());
        assertTrue(state.canSuppressVendor(5L, 5L));
        assertTrue("active custom ownership remains authoritative while a newer scene frame is prepared",
                state.canSuppressVendor(5L, 6L));
    }

    @Test
    public void closeRequestRetainsCustomUntilTerminalRelease() {
        SecurityCenterMaterialOwnershipState state = new SecurityCenterMaterialOwnershipState();
        state.onCustomPreparing();
        state.onCustomPresented();

        state.onVendorClosing();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.CUSTOM_CLOSING, state.owner());
        assertTrue("active custom presentation remains authoritative during vendor close motion",
                state.canSuppressVendor(9L, 9L));

        state.releaseToVendor();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.VENDOR, state.owner());
        assertFalse(state.canSuppressVendor(9L, 9L));
    }

    @Test
    public void closeDuringPreparingNeverSuppressesVendor() {
        SecurityCenterMaterialOwnershipState state = new SecurityCenterMaterialOwnershipState();
        state.onCustomPreparing();
        state.onVendorClosing();

        assertEquals(SecurityCenterMaterialOwnershipState.Owner.CUSTOM_CLOSING, state.owner());
        assertFalse(state.hasSuppressedVendor());
        assertFalse(state.canSuppressVendor(3L, 3L));

        state.releaseToVendor();
        assertEquals(SecurityCenterMaterialOwnershipState.Owner.VENDOR, state.owner());
    }
}
