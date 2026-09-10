package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SecurityCenterVendorMaterialCapabilityTest {
    @Test public void os3AlwaysUsesBackgroundBlur() {
        assertEquals(SecurityCenterVendorMaterialCapability.BACKGROUND_BLUR,
                SecurityCenterVendorMaterialCapability.resolve(
                        SecurityCenterVendorGeneration.OS3_BLUR_ONLY, false));
        assertEquals(SecurityCenterVendorMaterialCapability.BACKGROUND_BLUR,
                SecurityCenterVendorMaterialCapability.resolve(
                        SecurityCenterVendorGeneration.OS3_BLUR_ONLY, true));
    }

    @Test public void os4RequiresActiveNativeSoftLightForGlass() {
        assertEquals(SecurityCenterVendorMaterialCapability.BACKGROUND_BLUR,
                SecurityCenterVendorMaterialCapability.resolve(
                        SecurityCenterVendorGeneration.OS4_SOFT_LIGHT_CAPABLE, false));
        assertEquals(SecurityCenterVendorMaterialCapability.SOFT_LIGHT_GLASS,
                SecurityCenterVendorMaterialCapability.resolve(
                        SecurityCenterVendorGeneration.OS4_SOFT_LIGHT_CAPABLE, true));
    }
}
