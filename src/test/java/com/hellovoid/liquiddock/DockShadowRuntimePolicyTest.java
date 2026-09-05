package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DockShadowRuntimePolicyTest {
    @Test
    public void geometryIsPassiveDuringAnimationAndWorkstation() {
        assertEquals(DockShadowRuntimePolicy.GeometrySync.REMEMBER_ONLY,
                DockShadowRuntimePolicy.geometrySync(false, true));
        assertEquals(DockShadowRuntimePolicy.GeometrySync.REMEMBER_ONLY,
                DockShadowRuntimePolicy.geometrySync(true, false));
        assertEquals(DockShadowRuntimePolicy.GeometrySync.SYNC_CONFIG,
                DockShadowRuntimePolicy.geometrySync(false, false));
    }

    @Test
    public void temporaryOverridesRequireSingleLiquidDockOwnershipWindow() {
        assertTrue(DockShadowRuntimePolicy.shouldApplyTemporaryOverrides(false, true, true));
        assertFalse(DockShadowRuntimePolicy.shouldApplyTemporaryOverrides(true, true, true));
        assertFalse(DockShadowRuntimePolicy.shouldApplyTemporaryOverrides(false, false, true));
        assertFalse(DockShadowRuntimePolicy.shouldApplyTemporaryOverrides(false, true, false));
    }

    @Test
    public void vendorRefreshRequiresNormalModeAndCustomizationOwnership() {
        assertTrue(DockShadowRuntimePolicy.shouldRefreshVendorShadow(false, true));
        assertFalse(DockShadowRuntimePolicy.shouldRefreshVendorShadow(true, true));
        assertFalse(DockShadowRuntimePolicy.shouldRefreshVendorShadow(false, false));
    }
}
